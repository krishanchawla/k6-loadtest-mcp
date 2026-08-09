package com.k6loadtestmcp.dashboard.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "endpoint_results")
public class EndpointResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private Run run;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private long count;

    @Column(name = "error_rate_pct", nullable = false)
    private double errorRatePct;

    @Column(name = "p95_ms")
    private Double p95Ms;

    public Long getId() { return id; }

    public Run getRun() { return run; }
    public void setRun(Run run) { this.run = run; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }

    public double getErrorRatePct() { return errorRatePct; }
    public void setErrorRatePct(double errorRatePct) { this.errorRatePct = errorRatePct; }

    public Double getP95Ms() { return p95Ms; }
    public void setP95Ms(Double p95Ms) { this.p95Ms = p95Ms; }
}
