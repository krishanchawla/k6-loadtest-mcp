package com.k6loadtestmcp.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. Runs as a self-contained jar with Spring Boot's own embedded Tomcat -- deliberately
 * NOT deployed into an external servlet container. See the "Deploying the dashboard" section of the
 * root README for why: this project targets Jakarta EE (Spring Boot 4), which cannot deploy to a
 * javax.*-based Tomcat 9-or-older instance.
 *
 * @EnableScheduling powers RunRetentionScheduler -- a no-op unless DASHBOARD_RETENTION_DAYS is set.
 */
@SpringBootApplication
@EnableScheduling
public class LoadtestDashboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoadtestDashboardApplication.class, args);
    }
}
