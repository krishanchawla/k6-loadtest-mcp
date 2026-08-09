package com.k6loadtestmcp.dashboard.repo;

import com.k6loadtestmcp.dashboard.domain.Run;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Loads a Run with its endpoints/thresholds collections initialized. Needed because
 * spring.jpa.open-in-view=false (deliberate; see application.properties) means the Hibernate
 * session used by RunRepository is gone by the time Thymeleaf renders the detail page, so the two
 * @OneToMany collections can't be accessed lazily from the view -- they must be touched here, inside
 * the transaction. (A single fetch-joined query for both was tried first but Hibernate can't
 * fetch-join two List ("bag") collections at once -- MultipleBagFetchException -- hence the
 * explicit initialize-within-a-transaction approach instead.)
 */
@Service
public class RunQueryService {

    private final RunRepository runRepository;

    public RunQueryService(RunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Run> findDetailById(Long id) {
        Optional<Run> run = runRepository.findById(id);
        run.ifPresent(r -> {
            Hibernate.initialize(r.getEndpoints());
            Hibernate.initialize(r.getThresholds());
        });
        return run;
    }
}
