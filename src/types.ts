import { z } from "zod";

/** A single request definition in the mix. */
export const RequestSpec = z.object({
  name: z.string().describe("Short label for this request, used in per-endpoint metrics."),
  method: z.enum(["GET", "POST", "PUT", "PATCH", "DELETE"]).default("GET"),
  path: z.string().describe("Path relative to baseUrl, e.g. /users/1"),
  headers: z.record(z.string(), z.string()).optional(),
  body: z.union([z.string(), z.record(z.string(), z.unknown())]).optional(),
  weight: z.number().positive().default(1).describe("Relative frequency in the request mix."),
  expectStatus: z.number().int().optional().describe("Expected HTTP status; defaults to <400."),
  maxDurationMs: z.number().positive().optional().describe("Optional per-request latency check."),
});
export type RequestSpec = z.infer<typeof RequestSpec>;

export const Stage = z.object({
  duration: z.string().describe("e.g. '30s', '2m'"),
  target: z.number().int().nonnegative(),
});

export const LoadProfile = z.object({
  type: z.enum(["constant", "ramping"]).default("constant"),
  vus: z.number().int().positive().optional(),
  duration: z.string().optional(),
  stages: z.array(Stage).optional(),
});
export type LoadProfile = z.infer<typeof LoadProfile>;

export const Thresholds = z.object({
  p95Ms: z.number().positive().optional(),
  errorRatePct: z.number().min(0).max(100).optional(),
});
export type Thresholds = z.infer<typeof Thresholds>;

export const TestPlan = z.object({
  name: z.string(),
  baseUrl: z.string().url(),
  requests: z.array(RequestSpec).min(1),
  loadProfile: LoadProfile,
  thresholds: Thresholds.optional(),
  thinkTimeMs: z
    .number()
    .nonnegative()
    .default(300)
    .describe("Pause between iterations per VU, simulating user think-time. Use 0 for max-throughput stress tests."),
});
export type TestPlan = z.infer<typeof TestPlan>;

/** Structured metrics parsed out of a k6 run, deterministic (no LLM) so the report is trustworthy. */
export interface EndpointMetrics {
  name: string;
  count: number;
  errorRatePct: number;
  p95Ms: number | null;
}

export interface RunMetrics {
  totalRequests: number;
  totalErrors: number;
  errorRatePct: number;
  rps: number;
  durationS: number;
  vusMax: number;
  latencyMs: {
    min: number | null;
    avg: number | null;
    p50: number | null;
    p90: number | null;
    p95: number | null;
    p99: number | null;
    max: number | null;
  };
  perEndpoint: EndpointMetrics[];
  thresholdResults: { name: string; ok: boolean }[];
}
