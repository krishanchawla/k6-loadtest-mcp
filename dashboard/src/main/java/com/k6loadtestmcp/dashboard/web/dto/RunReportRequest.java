package com.k6loadtestmcp.dashboard.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Mirrors the payload built by src/dashboard.ts::publishReport on the MCP side, which in turn is
 * exactly the RunMetrics shape from src/types.ts (produced by src/k6/parseSummary.ts) plus a few
 * run-level fields. Field names must match the JSON keys sent there -- keep the two in sync.
 */
public record RunReportRequest(
    String name,
    String baseUrl,
    Instant startedAt,
    boolean thresholdsFailed,
    RunMetricsDto metrics
) {
    public record RunMetricsDto(
        long totalRequests,
        long totalErrors,
        double errorRatePct,
        double rps,
        double durationS,
        int vusMax,
        LatencyDto latencyMs,
        List<EndpointDto> perEndpoint,
        List<ThresholdDto> thresholdResults
    ) {}

    public record LatencyDto(Double min, Double avg, Double p50, Double p90, Double p95, Double p99, Double max) {}

    public record EndpointDto(String name, long count, double errorRatePct, Double p95Ms) {}

    public record ThresholdDto(String name, boolean ok) {}
}
