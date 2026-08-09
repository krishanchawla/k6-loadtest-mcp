package com.k6loadtestmcp.dashboard.web.view;

import com.k6loadtestmcp.dashboard.domain.Run;
import com.k6loadtestmcp.dashboard.repo.RunQueryService;
import com.k6loadtestmcp.dashboard.repo.RunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Serves the human-facing pages -- protected by HTTP Basic (see SecurityConfig), not the API token. */
@Controller
public class DashboardController {

    private static final int PAGE_SIZE = 20;

    private final RunRepository runRepository;
    private final RunQueryService runQueryService;

    public DashboardController(RunRepository runRepository, RunQueryService runQueryService) {
        this.runRepository = runRepository;
        this.runQueryService = runQueryService;
    }

    @GetMapping("/")
    public String list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
        Page<Run> runs = (name == null || name.isBlank())
                ? runRepository.findAllByOrderByStartedAtDesc(pageable)
                : runRepository.findAllByNameOrderByStartedAtDesc(name, pageable);

        List<String> testNames = runRepository.findDistinctNames();
        long totalRuns = runRepository.count();
        long passedRuns = runRepository.countByThresholdsFailedFalse();
        Integer passRatePct = totalRuns == 0 ? null : (int) Math.round(100.0 * passedRuns / totalRuns);

        model.addAttribute("runs", runs);
        model.addAttribute("testNames", testNames);
        model.addAttribute("selectedName", name);
        model.addAttribute("totalRuns", totalRuns);
        model.addAttribute("testCount", testNames.size());
        model.addAttribute("passRatePct", passRatePct);
        return "runs/list";
    }

    @GetMapping("/runs/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Run run = runQueryService.findDetailById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No run " + id));
        Optional<Run> previous =
                runRepository.findTopByNameAndStartedAtBeforeOrderByStartedAtDesc(run.getName(), run.getStartedAt());

        model.addAttribute("run", run);
        model.addAttribute("previous", previous.orElse(null));
        model.addAttribute("latencyBars", latencyBars(run));
        model.addAttribute("deltas", previous.map(p -> deltas(run, p)).orElse(List.of()));

        double maxEndpointP95 = run.getEndpoints().stream()
                .map(e -> e.getP95Ms())
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0);
        model.addAttribute("maxEndpointP95", maxEndpointP95);

        return "runs/detail";
    }

    /** Percentile bars for the detail page's chart, pre-computed so the template stays declarative. */
    private List<LatencyBar> latencyBars(Run run) {
        List<LatencyBar> bars = new ArrayList<>();
        bars.add(new LatencyBar("p50", run.getP50Ms()));
        bars.add(new LatencyBar("p90", run.getP90Ms()));
        bars.add(new LatencyBar("p95", run.getP95Ms()));
        bars.add(new LatencyBar("p99", run.getP99Ms()));

        double max = bars.stream()
                .map(LatencyBar::getValueMs)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(1);
        double safeMax = max <= 0 ? 1 : max;
        for (LatencyBar bar : bars) {
            bar.setHeightPct(bar.getValueMs() == null ? 0 : (bar.getValueMs() / safeMax) * 100);
        }
        return bars;
    }

    /** Delta vs. the previous run of the same test name -- the "baseline diffing" the README promises. */
    private List<TrendDelta> deltas(Run run, Run previous) {
        List<TrendDelta> out = new ArrayList<>();
        out.add(delta("p95 latency", run.getP95Ms(), previous.getP95Ms(), false, " ms"));
        out.add(delta("Error rate", run.getErrorRatePct(), previous.getErrorRatePct(), false, " pct pts"));
        out.add(delta("RPS", run.getRps(), previous.getRps(), true, ""));
        return out;
    }

    private TrendDelta delta(String label, Double current, Double previous, boolean higherIsBetter, String unit) {
        if (current == null || previous == null) {
            return new TrendDelta(label, "no previous value", "flat");
        }
        double diff = current - previous;
        if (Math.abs(diff) < 0.005) {
            return new TrendDelta(label, "→ unchanged vs previous", "flat");
        }
        boolean increased = diff > 0;
        String cssClass = (higherIsBetter == increased) ? "better" : "worse";
        String arrow = increased ? "▲" : "▼";
        String text = String.format("%s %s%.2f%s vs previous", arrow, increased ? "+" : "", diff, unit);
        return new TrendDelta(label, text, cssClass);
    }

    public record TrendDelta(String label, String text, String cssClass) {}

    public static class LatencyBar {
        private final String label;
        private final Double valueMs;
        private double heightPct;

        LatencyBar(String label, Double valueMs) {
            this.label = label;
            this.valueMs = valueMs;
        }

        public String getLabel() { return label; }
        public Double getValueMs() { return valueMs; }
        public double getHeightPct() { return heightPct; }
        void setHeightPct(double heightPct) { this.heightPct = heightPct; }
    }
}
