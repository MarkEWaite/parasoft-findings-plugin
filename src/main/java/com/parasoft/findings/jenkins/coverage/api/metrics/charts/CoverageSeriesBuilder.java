package com.parasoft.findings.jenkins.coverage.api.metrics.charts;

import java.util.HashMap;
import java.util.Map;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.echarts.line.SeriesBuilder;

import com.parasoft.findings.jenkins.coverage.api.metrics.model.Baseline;
import com.parasoft.findings.jenkins.coverage.api.metrics.model.CoverageStatistics;

/**
 * Builds one x-axis point for the series of a line chart showing the line and branch coverage of a project.
 *
 * @author Ullrich Hafner
 */
public class CoverageSeriesBuilder extends SeriesBuilder<CoverageStatistics> {
    static final String LINE_COVERAGE = "line";
    static final String BRANCH_COVERAGE = "branch";
    static final String MUTATION_COVERAGE = "mutation";

    @Override
    protected Map<String, Double> computeSeries(final CoverageStatistics statistics) {
        Map<String, Double> series = new HashMap<>();

        series.put(LINE_COVERAGE, getRoundedPercentage(statistics, Metric.LINE));
        if (statistics.containsValue(Baseline.PROJECT, Metric.BRANCH)) {
            series.put(BRANCH_COVERAGE, getRoundedPercentage(statistics, Metric.BRANCH));
        }
        if (statistics.containsValue(Baseline.PROJECT, Metric.MUTATION)) {
            series.put(MUTATION_COVERAGE, getRoundedPercentage(statistics, Metric.MUTATION));
        }
        return series;
    }

    private double getRoundedPercentage(final CoverageStatistics statistics, final Metric metric) {
        Coverage coverage = (Coverage) statistics.getValue(Baseline.PROJECT, metric)
                .orElse(Coverage.nullObject(metric));
        return (coverage.getCoveredPercentage().toDouble() / 100.0) * 100.0;
    }
}
