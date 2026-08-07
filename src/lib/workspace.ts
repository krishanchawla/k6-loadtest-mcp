import { mkdirSync, writeFileSync, readFileSync, existsSync } from "node:fs";
import path from "node:path";
import type { TestPlan } from "../types.js";
import { k6LoadtestMcpHome } from "./home.js";

// Deliberately under the user's home, not the installed package dir -- see home.ts for why.
const RUNS_ROOT = path.join(k6LoadtestMcpHome(), "runs");

function slugify(s: string): string {
  return s
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "")
    .slice(0, 40);
}

/** Creates a fresh timestamped directory for one test's artifacts (script, summary, logs). */
export function createRunDir(planName: string): string {
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const dir = path.join(RUNS_ROOT, `${stamp}-${slugify(planName)}`);
  mkdirSync(dir, { recursive: true });
  return dir;
}

export function writeScript(runDir: string, script: string): string {
  const scriptPath = path.join(runDir, "script.js");
  writeFileSync(scriptPath, script, "utf-8");
  return scriptPath;
}

/** The plan is saved alongside the script so later steps (parsing results) don't need it re-passed. */
export function writePlan(runDir: string, plan: TestPlan): void {
  writeFileSync(path.join(runDir, "plan.json"), JSON.stringify(plan, null, 2), "utf-8");
}

export function readPlan(runDir: string): TestPlan {
  const planPath = path.join(runDir, "plan.json");
  if (!existsSync(planPath)) {
    throw new Error(`No plan.json found in ${runDir}. Did you call generate_k6_script for this run first?`);
  }
  return JSON.parse(readFileSync(planPath, "utf-8"));
}

export function scriptPathFor(runDir: string): string {
  const p = path.join(runDir, "script.js");
  if (!existsSync(p)) {
    throw new Error(`No script.js found in ${runDir}. Did you call generate_k6_script for this run first?`);
  }
  return p;
}
