package com.k6loadtestmcp.dashboard.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Prunes old runs once a day. Disabled by default (DASHBOARD_RETENTION_DAYS unset/0) -- self-hosted/
 * private deployments keep every run forever, exactly as before this existed. Only relevant for the
 * "public demo" posture (see README), where unbounded growth from anonymous traffic is the concern.
 */
@Component
public class RunRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RunRetentionScheduler.class);

    private final RunRepository runRepository;
    private final int retentionDays;

    public RunRetentionScheduler(RunRepository runRepository, @Value("${dashboard.retention-days:0}") int retentionDays) {
        this.runRepository = runRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void pruneOldRuns() {
        if (retentionDays <= 0) return;
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        long deleted = runRepository.deleteByStartedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Pruned {} run(s) older than {} day(s)", deleted, retentionDays);
        }
    }
}
