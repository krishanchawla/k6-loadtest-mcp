package com.k6loadtestmcp.dashboard.web.api;

import com.k6loadtestmcp.dashboard.domain.EndpointResult;
import com.k6loadtestmcp.dashboard.domain.Run;
import com.k6loadtestmcp.dashboard.domain.ThresholdResult;
import com.k6loadtestmcp.dashboard.repo.RunRepository;
import com.k6loadtestmcp.dashboard.web.dto.RunReportRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * Ingest endpoint the MCP server's publish_report tool POSTs to (src/dashboard.ts). Auth for this
 * whole path is handled by ApiTokenFilter (bearer token), not Spring Security's session/basic-auth
 * chain -- see SecurityConfig.
 */
@RestController
@RequestMapping("/api/runs")
public class RunIngestController {

    private final RunRepository runRepository;
    private final ObjectMapper objectMapper;
    private final String publicBaseUrl;
    private final String demoTargetHost;

    public RunIngestController(
            RunRepository runRepository,
            ObjectMapper objectMapper,
            @Value("${dashboard.public-base-url}") String publicBaseUrl,
            @Value("${dashboard.demo-target-host:}") String demoTargetHost) {
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.demoTargetHost = demoTargetHost;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody RunReportRequest req) {
        assertAllowedTarget(req.baseUrl());
        Run run = toEntity(req);
        Run saved = runRepository.save(run);
        String url = publicBaseUrl + "/runs/" + saved.getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId(), "url", url));
    }

    /**
     * Self-host default: DASHBOARD_DEMO_TARGET_HOST unset -- any baseUrl accepted, unrestricted, same
     * as before this existed. Public-demo mode: only that pinned host/port is accepted -- this is the
     * real guard against the dashboard being used as an anonymous load-testing egress point once the
     * bearer token is public (see README "Public demo mode"), not the token itself.
     */
    private void assertAllowedTarget(String baseUrl) {
        if (demoTargetHost == null || demoTargetHost.isBlank()) return;
        String actual;
        try {
            URI uri = new URI(baseUrl);
            actual = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (URISyntaxException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid baseUrl");
        }
        if (!actual.equalsIgnoreCase(demoTargetHost)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This dashboard only accepts runs against " + demoTargetHost);
        }
    }

    private Run toEntity(RunReportRequest req) {
        Run run = new Run();
        run.setName(req.name());
        run.setBaseUrl(req.baseUrl());
        run.setStartedAt(req.startedAt());
        run.setThresholdsFailed(req.thresholdsFailed());

        RunReportRequest.RunMetricsDto m = req.metrics();
        run.setTotalRequests(m.totalRequests());
        run.setTotalErrors(m.totalErrors());
        run.setErrorRatePct(m.errorRatePct());
        run.setRps(m.rps());
        run.setDurationS(m.durationS());
        run.setVusMax(m.vusMax());

        if (m.latencyMs() != null) {
            run.setMinMs(m.latencyMs().min());
            run.setAvgMs(m.latencyMs().avg());
            run.setP50Ms(m.latencyMs().p50());
            run.setP90Ms(m.latencyMs().p90());
            run.setP95Ms(m.latencyMs().p95());
            run.setP99Ms(m.latencyMs().p99());
            run.setMaxMs(m.latencyMs().max());
        }

        if (m.perEndpoint() != null) {
            for (RunReportRequest.EndpointDto e : m.perEndpoint()) {
                EndpointResult er = new EndpointResult();
                er.setName(e.name());
                er.setCount(e.count());
                er.setErrorRatePct(e.errorRatePct());
                er.setP95Ms(e.p95Ms());
                run.addEndpoint(er);
            }
        }

        if (m.thresholdResults() != null) {
            for (RunReportRequest.ThresholdDto t : m.thresholdResults()) {
                ThresholdResult tr = new ThresholdResult();
                tr.setName(t.name());
                tr.setOk(t.ok());
                run.addThreshold(tr);
            }
        }

        run.setMetricsJson(toJsonOrNull(m));
        return run;
    }

    private String toJsonOrNull(Object o) {
        // Jackson 3's ObjectMapper#writeValueAsString throws an unchecked JacksonException, not the
        // old checked JsonProcessingException -- caught explicitly anyway since this is a
        // best-effort record-keeping field, never something that should fail the whole request.
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JacksonException e) {
            return null;
        }
    }
}
