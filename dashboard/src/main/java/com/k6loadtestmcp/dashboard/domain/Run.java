package com.k6loadtestmcp.dashboard.domain;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * One published k6 run. Mirrors the payload k6-loadtest-mcp's publish_report tool POSTs to
 * /api/runs (src/dashboard.ts on the Node side) -- keep the two in sync if either shape changes.
 */
@Entity
@Table(name = "runs", indexes = { @Index(name = "idx_runs_name_started_at", columnList = "name, started_at") })
public class Run {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "thresholds_failed", nullable = false)
    private boolean thresholdsFailed;

    @Column(name = "total_requests", nullable = false)
    private long totalRequests;

    @Column(name = "total_errors", nullable = false)
    private long totalErrors;

    @Column(name = "error_rate_pct", nullable = false)
    private double errorRatePct;

    @Column(nullable = false)
    private double rps;

    @Column(name = "duration_s", nullable = false)
    private double durationS;

    @Column(name = "vus_max", nullable = false)
    private int vusMax;

    private Double minMs;
    private Double avgMs;
    private Double p50Ms;
    private Double p90Ms;
    private Double p95Ms;
    private Double p99Ms;
    private Double maxMs;

    /** Raw RunMetrics JSON as received, kept for the record / future re-parsing -- never re-derived from. */
    @Lob
    @Column(name = "metrics_json")
    private String metricsJson;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EndpointResult> endpoints = new ArrayList<>();

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ThresholdResult> thresholds = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public boolean isThresholdsFailed() { return thresholdsFailed; }
    public void setThresholdsFailed(boolean thresholdsFailed) { this.thresholdsFailed = thresholdsFailed; }

    public long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }

    public long getTotalErrors() { return totalErrors; }
    public void setTotalErrors(long totalErrors) { this.totalErrors = totalErrors; }

    public double getErrorRatePct() { return errorRatePct; }
    public void setErrorRatePct(double errorRatePct) { this.errorRatePct = errorRatePct; }

    public double getRps() { return rps; }
    public void setRps(double rps) { this.rps = rps; }

    public double getDurationS() { return durationS; }
    public void setDurationS(double durationS) { this.durationS = durationS; }

    public int getVusMax() { return vusMax; }
    public void setVusMax(int vusMax) { this.vusMax = vusMax; }

    public Double getMinMs() { return minMs; }
    public void setMinMs(Double minMs) { this.minMs = minMs; }

    public Double getAvgMs() { return avgMs; }
    public void setAvgMs(Double avgMs) { this.avgMs = avgMs; }

    public Double getP50Ms() { return p50Ms; }
    public void setP50Ms(Double p50Ms) { this.p50Ms = p50Ms; }

    public Double getP90Ms() { return p90Ms; }
    public void setP90Ms(Double p90Ms) { this.p90Ms = p90Ms; }

    public Double getP95Ms() { return p95Ms; }
    public void setP95Ms(Double p95Ms) { this.p95Ms = p95Ms; }

    public Double getP99Ms() { return p99Ms; }
    public void setP99Ms(Double p99Ms) { this.p99Ms = p99Ms; }

    public Double getMaxMs() { return maxMs; }
    public void setMaxMs(Double maxMs) { this.maxMs = maxMs; }

    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }

    public List<EndpointResult> getEndpoints() { return endpoints; }

    public List<ThresholdResult> getThresholds() { return thresholds; }

    public void addEndpoint(EndpointResult e) {
        e.setRun(this);
        endpoints.add(e);
    }

    public void addThreshold(ThresholdResult t) {
        t.setRun(this);
        thresholds.add(t);
    }

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    /** View-formatting helper -- keeps templates from having to reason about Instant formatting/zones. */
    @Transient
    public String getStartedAtDisplay() {
        return startedAt == null ? "" : DISPLAY_FORMAT.format(startedAt) + " UTC";
    }

    /** "2 hours ago" style -- shown as the primary time in the list, with the absolute time as a tooltip. */
    @Transient
    public String getStartedAtRelative() {
        if (startedAt == null) return "";
        long seconds = Math.max(0, Duration.between(startedAt, Instant.now()).getSeconds());
        if (seconds < 60) return "just now";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        long hours = minutes / 60;
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = hours / 24;
        if (days < 30) return days + (days == 1 ? " day ago" : " days ago");
        long months = days / 30;
        if (months < 12) return months + (months == 1 ? " month ago" : " months ago");
        long years = months / 12;
        return years + (years == 1 ? " year ago" : " years ago");
    }

    @Transient
    public boolean isThresholdsPassed() {
        return !thresholdsFailed;
    }
}
