#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

import { TestPlan } from "./types.js";
import { generateK6Script } from "./k6/generateScript.js";
import { smokeTestScript, runLoadTest } from "./k6/runK6.js";
import { parseK6Summary } from "./k6/parseSummary.js";
import { createRunDir, writeScript, writePlan, readPlan, scriptPathFor } from "./lib/workspace.js";
import { assertTargetAllowed } from "./guardrails.js";

const server = new McpServer({ name: "k6-loadtest-mcp", version: "0.1.0" });

function ok(payload: unknown) {
  return { content: [{ type: "text" as const, text: JSON.stringify(payload, null, 2) }] };
}

function fail(err: unknown) {
  const message = err instanceof Error ? err.message : String(err);
  return { content: [{ type: "text" as const, text: message }], isError: true };
}

// Truncate k6's console output before it goes back to the model -- full logs from a long
// run can be large and the structured metrics (get_test_metrics) already contain what matters.
function truncate(text: string, max = 4000): string {
  return text.length > max ? text.slice(0, max) + `\n... [truncated, ${text.length - max} more chars]` : text;
}

server.registerTool(
  "generate_k6_script",
  {
    title: "Generate a k6 load test script",
    description:
      "Turns a structured test plan (base URL, weighted request mix, load profile, thresholds) into a " +
      "runnable k6 JavaScript script. Deterministic templating, not an LLM call -- reviewable before running. " +
      "Returns a runDir that all later steps (smoke_test_script, run_load_test, get_test_metrics) take as input.",
    inputSchema: { plan: TestPlan },
  },
  async ({ plan }) => {
    try {
      const runDir = createRunDir(plan.name);
      const script = generateK6Script(plan);
      const scriptPath = writeScript(runDir, script);
      writePlan(runDir, plan);
      return ok({ runDir, scriptPath, script });
    } catch (err) {
      return fail(err);
    }
  }
);

server.registerTool(
  "smoke_test_script",
  {
    title: "Smoke-test a generated k6 script",
    description:
      "Runs the script for 1 VU / 1 iteration to catch syntax or runtime errors fast, before committing to a " +
      "full load test. Always call this before run_load_test.",
    inputSchema: { runDir: z.string().describe("runDir returned by generate_k6_script") },
  },
  async ({ runDir }) => {
    try {
      const scriptPath = scriptPathFor(runDir);
      const result = await smokeTestScript(scriptPath);
      return ok({ ok: result.ok, exitCode: result.exitCode, output: truncate(result.output) });
    } catch (err) {
      return fail(err);
    }
  }
);

server.registerTool(
  "run_load_test",
  {
    title: "Run the full k6 load test",
    description:
      "Executes the load test at the VUs/duration/stages baked into the generated script by generate_k6_script. " +
      "Only runs against hosts listed in perf-agent.config.json's allowedHosts -- add a host there yourself " +
      "(the tools won't do it for you) once you've confirmed you're authorized to load-test it. " +
      "Can take as long as the test's own duration/stages; call smoke_test_script first.",
    inputSchema: { runDir: z.string().describe("runDir returned by generate_k6_script") },
  },
  async ({ runDir }) => {
    try {
      const plan = readPlan(runDir);
      assertTargetAllowed(plan.baseUrl);
      const scriptPath = scriptPathFor(runDir);
      const result = await runLoadTest(scriptPath, runDir);
      return ok({
        exitCode: result.exitCode,
        thresholdsFailed: result.thresholdsFailed,
        summaryPath: result.summaryPath,
        output: truncate(result.stdout),
      });
    } catch (err) {
      return fail(err);
    }
  }
);

server.registerTool(
  "get_test_metrics",
  {
    title: "Get structured metrics for a completed run",
    description:
      "Parses the k6 summary JSON into deterministic structured metrics: overall + per-endpoint p50/p90/p95/p99 " +
      "latency, error rate, RPS, and threshold pass/fail. Use this data (not raw k6 console output) to write the " +
      "human-readable performance summary -- the numbers here are computed in code, not guessed.",
    inputSchema: { runDir: z.string().describe("runDir returned by generate_k6_script, after run_load_test has completed") },
  },
  async ({ runDir }) => {
    try {
      const plan = readPlan(runDir);
      const summaryPath = `${runDir}/summary.json`;
      const metrics = parseK6Summary(summaryPath, plan);
      return ok(metrics);
    } catch (err) {
      return fail(err);
    }
  }
);

server.registerTool(
  "run_full_test",
  {
    title: "Generate, smoke-test, run, and summarize a load test in one call",
    description:
      "Convenience tool that chains generate_k6_script -> smoke_test_script -> run_load_test -> get_test_metrics. " +
      "Use the granular tools instead when you want to inspect/adjust the script between steps, or re-run the " +
      "same script with different load without regenerating it.",
    inputSchema: { plan: TestPlan },
  },
  async ({ plan }) => {
    try {
      assertTargetAllowed(plan.baseUrl);

      const runDir = createRunDir(plan.name);
      const script = generateK6Script(plan);
      const scriptPath = writeScript(runDir, script);
      writePlan(runDir, plan);

      const smoke = await smokeTestScript(scriptPath);
      if (!smoke.ok) {
        return ok({
          runDir,
          stage: "smoke_test_failed",
          smoke: { ok: smoke.ok, output: truncate(smoke.output) },
        });
      }

      const run = await runLoadTest(scriptPath, runDir);
      const metrics = parseK6Summary(run.summaryPath, plan);

      return ok({
        runDir,
        stage: "complete",
        thresholdsFailed: run.thresholdsFailed,
        metrics,
      });
    } catch (err) {
      return fail(err);
    }
  }
);

const transport = new StdioServerTransport();
await server.connect(transport);
