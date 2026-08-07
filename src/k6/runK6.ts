import { execa } from "execa";
import { existsSync } from "node:fs";
import path from "node:path";

/** Finds the k6 binary: explicit override, then PATH, then common install locations. */
function resolveK6Binary(): string {
  if (process.env.K6_BIN) return process.env.K6_BIN;
  if (process.platform === "win32") {
    const common = path.join("C:", "Program Files", "k6", "k6.exe");
    if (existsSync(common)) return common;
  }
  return "k6"; // rely on PATH
}

export interface SmokeTestResult {
  ok: boolean;
  exitCode: number | null;
  output: string;
}

/** Fast 1-VU/1-iteration run to catch syntax/runtime errors before committing to a full load test. */
export async function smokeTestScript(scriptPath: string): Promise<SmokeTestResult> {
  const bin = resolveK6Binary();
  const result = await execa(bin, ["run", "--vus", "1", "--iterations", "1", "--quiet", scriptPath], {
    reject: false,
  });
  return {
    ok: result.exitCode === 0,
    exitCode: result.exitCode ?? null,
    output: `${result.stdout}\n${result.stderr}`.trim(),
  };
}

export interface LoadTestResult {
  exitCode: number | null;
  /** k6 returns non-zero when thresholds fail even though the run itself completed fine. */
  thresholdsFailed: boolean;
  summaryPath: string;
  stdout: string;
}

/** Runs the full load test (VUs/duration/stages come from the script's own `options` export). */
export async function runLoadTest(scriptPath: string, runDir: string): Promise<LoadTestResult> {
  const bin = resolveK6Binary();
  const summaryPath = path.join(runDir, "summary.json");
  const result = await execa(bin, ["run", `--summary-export=${summaryPath}`, scriptPath], {
    reject: false,
    cwd: runDir,
  });
  return {
    exitCode: result.exitCode ?? null,
    thresholdsFailed: result.exitCode !== 0,
    summaryPath,
    stdout: `${result.stdout}\n${result.stderr}`.trim(),
  };
}
