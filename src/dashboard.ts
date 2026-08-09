import { loadConfig } from "./lib/config.js";
import { readPlan } from "./lib/workspace.js";
import { parseK6Summary } from "./k6/parseSummary.js";
import path from "node:path";

export interface PublishResult {
  url: string;
}

/**
 * Publishes a completed run's metrics to a deployed loadtest-dashboard instance (see dashboard/).
 * Requires:
 *   - "dashboardUrl" set in ~/.k6-loadtest-mcp/config.json (not set by default -- publishing is
 *     opt-in; nothing is sent anywhere unless the user configures a target themselves).
 *   - K6_LOADTEST_DASHBOARD_TOKEN env var, matching the DASHBOARD_API_TOKEN the dashboard was
 *     deployed with. Kept out of config.json deliberately -- it's a secret, dashboardUrl isn't.
 */
export async function publishReport(runDir: string): Promise<PublishResult> {
  const { dashboardUrl } = loadConfig();
  if (!dashboardUrl) {
    throw new Error(
      'No dashboard configured. Set "dashboardUrl" in ~/.k6-loadtest-mcp/config.json to a deployed ' +
        "loadtest-dashboard instance's base URL (e.g. \"https://myvps.example.com/loadtest-dashboard\") to enable publish_report."
    );
  }
  const token = process.env.K6_LOADTEST_DASHBOARD_TOKEN;
  if (!token) {
    throw new Error(
      "K6_LOADTEST_DASHBOARD_TOKEN env var is not set. It must match the DASHBOARD_API_TOKEN the " +
        "dashboard service was deployed with."
    );
  }

  const plan = readPlan(runDir);
  const summaryPath = path.join(runDir, "summary.json");
  const metrics = parseK6Summary(summaryPath, plan);
  const thresholdsFailed = metrics.thresholdResults.some((t) => !t.ok);

  const payload = {
    name: plan.name,
    baseUrl: plan.baseUrl,
    // Best-effort: the run's own start time isn't persisted separately, and publish_report is
    // always called immediately after the run completes, so "now" is close enough for display/
    // sort ordering purposes.
    startedAt: new Date().toISOString(),
    thresholdsFailed,
    metrics,
  };

  const endpoint = `${dashboardUrl.replace(/\/+$/, "")}/api/runs`;
  const res = await fetch(endpoint, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`Dashboard rejected the report: ${res.status} ${res.statusText}${body ? ` -- ${body}` : ""}`);
  }

  const body = (await res.json()) as { id: number; url: string };
  return { url: body.url };
}
