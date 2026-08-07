import { readFileSync } from "node:fs";
import type { RunMetrics, EndpointMetrics, TestPlan } from "../types.js";

// Shapes as actually emitted by k6 v2.x's --summary-export, verified by inspection (not from docs,
// which drift across versions): Trend metrics carry avg/min/med/max/p(90)/p(95)/[p(99) if requested];
// Counter metrics carry count/rate; Rate metrics carry passes/fails/value, where `value` is the
// fraction of adds that were truthy and `passes` is the count of truthy adds (confusingly named --
// for http_req_failed and our own failRate_* metrics, "passes" means "count that failed").
interface TrendJson {
  avg?: number;
  min?: number;
  med?: number;
  max?: number;
  "p(90)"?: number;
  "p(95)"?: number;
  "p(99)"?: number;
  thresholds?: Record<string, boolean>;
}
interface CounterJson {
  count: number;
  rate: number;
}
interface RateJson {
  passes: number;
  fails: number;
  value: number;
  thresholds?: Record<string, boolean>;
}
type MetricJson = TrendJson | CounterJson | RateJson;

interface K6Summary {
  metrics: Record<string, MetricJson>;
}

function sanitizeId(name: string): string {
  return name.replace(/[^a-zA-Z0-9_]/g, "_");
}

function num(v: number | undefined): number | null {
  return typeof v === "number" ? v : null;
}

/** Parses a k6 --summary-export JSON file into deterministic, structured metrics (no LLM involved). */
export function parseK6Summary(summaryPath: string, plan: TestPlan): RunMetrics {
  const raw = readFileSync(summaryPath, "utf-8");
  const summary: K6Summary = JSON.parse(raw);
  const m = summary.metrics;

  const httpReqs = m["http_reqs"] as CounterJson | undefined;
  const httpFailed = m["http_req_failed"] as RateJson | undefined;
  const httpDuration = m["http_req_duration"] as TrendJson | undefined;
  const vusMaxMetric = m["vus_max"] as { max?: number; value?: number } | undefined;

  const totalRequests = httpReqs?.count ?? 0;
  const totalErrors = httpFailed?.passes ?? 0; // "passes" = count of truthy (failed) adds
  const errorRatePct = httpFailed ? httpFailed.value * 100 : 0;

  const perEndpoint: EndpointMetrics[] = plan.requests.map((req) => {
    const id = sanitizeId(req.name);
    const trend = m[`duration_${id}`] as TrendJson | undefined;
    const failRate = m[`failed_${id}`] as RateJson | undefined;
    const count = failRate ? failRate.passes + failRate.fails : 0;
    return {
      name: req.name,
      count,
      errorRatePct: failRate ? failRate.value * 100 : 0,
      p95Ms: num(trend?.["p(95)"]),
    };
  });

  const thresholdResults: { name: string; ok: boolean }[] = [];
  for (const [metricName, metric] of Object.entries(m)) {
    const thresholds = (metric as TrendJson | RateJson).thresholds;
    if (!thresholds) continue;
    for (const [expr, breached] of Object.entries(thresholds)) {
      // Verified empirically against k6 v2.1.0's CLI output (✓/✗ THRESHOLDS section): the JSON
      // flag is "was this threshold breached", the *opposite* of "ok" -- e.g. a passing
      // 'p(95)<250' with p(95)=200 is reported here as `false`, not `true`. Don't take this
      // at face value if you're re-verifying on a different k6 version.
      thresholdResults.push({ name: `${metricName}: ${expr}`, ok: !breached });
    }
  }

  return {
    totalRequests,
    totalErrors,
    errorRatePct,
    rps: httpReqs?.rate ?? 0,
    durationS: totalRequests > 0 && httpReqs ? totalRequests / (httpReqs.rate || 1) : 0,
    vusMax: vusMaxMetric?.max ?? vusMaxMetric?.value ?? 0,
    latencyMs: {
      min: num(httpDuration?.min),
      avg: num(httpDuration?.avg),
      p50: num(httpDuration?.med),
      p90: num(httpDuration?.["p(90)"]),
      p95: num(httpDuration?.["p(95)"]),
      p99: num(httpDuration?.["p(99)"]),
      max: num(httpDuration?.max),
    },
    perEndpoint,
    thresholdResults,
  };
}
