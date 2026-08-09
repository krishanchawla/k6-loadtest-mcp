package com.k6loadtestmcp.dashboard.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "threshold_results")
public class ThresholdResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private Run run;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean ok;

    public Long getId() { return id; }

    public Run getRun() { return run; }
    public void setRun(Run run) { this.run = run; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
}
