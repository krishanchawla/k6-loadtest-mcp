package com.k6loadtestmcp.dashboard.repo;

import com.k6loadtestmcp.dashboard.domain.Run;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RunRepository extends JpaRepository<Run, Long> {

    Page<Run> findAllByOrderByStartedAtDesc(Pageable pageable);

    Page<Run> findAllByNameOrderByStartedAtDesc(String name, Pageable pageable);

    /** The most recent earlier run of the same test name -- powers the detail page's trend delta. */
    Optional<Run> findTopByNameAndStartedAtBeforeOrderByStartedAtDesc(String name, Instant startedAt);

    @Query("select distinct r.name from Run r order by r.name")
    List<String> findDistinctNames();

    /** Overview strip on the list page. */
    long countByThresholdsFailedFalse();

    /** Retention pruning (RunRetentionScheduler) -- individually removes each match so
     *  Run's cascade/orphanRemoval on endpoints/thresholds still applies, not a bulk DELETE. */
    long deleteByStartedAtBefore(Instant cutoff);
}
