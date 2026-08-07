// Direct pipeline exercise, bypassing the MCP layer -- proves generate -> smoke -> run -> parse
// works before wiring it up as MCP tools. Run with: npx tsx src/test-harness.ts
import { TestPlan } from "./types.js";
import { generateK6Script } from "./k6/generateScript.js";
import { smokeTestScript, runLoadTest } from "./k6/runK6.js";
import { parseK6Summary } from "./k6/parseSummary.js";
import { createRunDir, writeScript } from "./lib/workspace.js";
import { assertTargetAllowed } from "./guardrails.js";

const plan = TestPlan.parse({
  name: "demo-api smoke",
  baseUrl: "http://localhost:4000",
  requests: [
    { name: "ListUsers", method: "GET", path: "/users", weight: 5, expectStatus: 200 },
    { name: "GetReports", method: "GET", path: "/reports", weight: 3, expectStatus: 200, maxDurationMs: 300 },
    { name: "CreateOrder", method: "POST", path: "/orders", weight: 2, body: { item: "widget", qty: 1 }, expectStatus: 201 },
  ],
  loadProfile: {
    type: "ramping",
    stages: [
      { duration: "10s", target: 10 },
      { duration: "20s", target: 40 },
      { duration: "10s", target: 0 },
    ],
  },
  thresholds: { p95Ms: 250, errorRatePct: 5 },
  thinkTimeMs: 200,
});

async function main() {
  assertTargetAllowed(plan.baseUrl);

  const runDir = createRunDir(plan.name);
  console.log(`Run dir: ${runDir}`);

  const script = generateK6Script(plan);
  const scriptPath = writeScript(runDir, script);
  console.log(`Script written: ${scriptPath}`);

  console.log("Smoke testing...");
  const smoke = await smokeTestScript(scriptPath);
  if (!smoke.ok) {
    console.error("Smoke test FAILED:\n" + smoke.output);
    process.exit(1);
  }
  console.log("Smoke test OK.");

  console.log("Running full load test (this takes ~40s given the staged profile)...");
  const runResult = await runLoadTest(scriptPath, runDir);
  console.log(`k6 exit code: ${runResult.exitCode} (thresholdsFailed=${runResult.thresholdsFailed})`);

  const metrics = parseK6Summary(runResult.summaryPath, plan);
  console.log("\n=== Structured metrics ===");
  console.log(JSON.stringify(metrics, null, 2));
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
