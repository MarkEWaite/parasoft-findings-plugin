package com.parasoft.findings.jenkins.coverage.api.metrics.steps;

import com.parasoft.findings.jenkins.coverage.api.metrics.model.Baseline;
import com.parasoft.findings.jenkins.coverage.api.metrics.model.CoverageStatistics;
import com.parasoft.findings.jenkins.coverage.api.metrics.model.ElementFormatter;
import com.parasoft.findings.jenkins.coverage.api.model.Messages;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.util.VisibleForTesting;
import hudson.Functions;
import hudson.model.Run;
import io.jenkins.plugins.forensics.reference.ReferenceBuild;
import io.jenkins.plugins.util.JenkinsFacade;
import io.jenkins.plugins.util.QualityGateResult;
import org.apache.commons.lang3.math.Fraction;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static hudson.model.Run.XSTREAM2;

public class CoverageBuildResult {

    private static final ElementFormatter FORMATTER = new ElementFormatter();
    private static final String NO_REFERENCE_BUILD = "-";
    private final String referenceBuildId;

    public String getReferenceBuildId() {
        return referenceBuildId;
    }

    private final QualityGateResult qualityGateResult;

    private Node result;

    public Node getResult() {
        return result;
    }

    public void setResult(Node result) {
        this.result = result;
    }

    /** The aggregated values of the result for the root of the tree. */
    private final List<? extends Value> projectValues;

    /** The delta of this build's coverages with respect to the reference build. */
    private NavigableMap<Metric, Fraction> difference;

    /** The coverages filtered by modified lines of the associated change request. */
    private final List<? extends Value> modifiedLinesCoverage;

    /** The coverage delta of the associated change request with respect to the reference build. */
    private NavigableMap<Metric, Fraction> modifiedLinesCoverageDifference;

    /** The coverage of the modified lines. */
    private final List<? extends Value> modifiedFilesCoverage;

    /** The coverage delta of the modified lines. */
    private NavigableMap<Metric, Fraction> modifiedFilesCoverageDifference;

    /** The indirect coverage changes of the associated change request with respect to the reference build. */
    private final List<? extends Value> indirectCoverageChanges;

    CoverageBuildResult(final Node result, final QualityGateResult qualityGateResult,
                        final String referenceBuildId) {
        this(result, qualityGateResult, referenceBuildId,
                new TreeMap<>(), List.of(), new TreeMap<>(), List.of(), new TreeMap<>(), List.of());
    }

    CoverageBuildResult(final Node result, final QualityGateResult qualityGateResult,
                        final String referenceBuildId,
                        final NavigableMap<Metric, Fraction> delta,
                        final List<? extends Value> modifiedLinesCoverage,
                        final NavigableMap<Metric, Fraction> modifiedLinesCoverageDifference,
                        final List<? extends Value> modifiedFilesCoverage,
                        final NavigableMap<Metric, Fraction> modifiedFilesCoverageDifference,
                        final List<? extends Value> indirectCoverageChanges) {

        this.result = result;
        projectValues = result.aggregateValues();
        this.qualityGateResult = qualityGateResult;
        this.referenceBuildId = referenceBuildId;

        difference = delta;
        this.modifiedLinesCoverage = new ArrayList<>(modifiedLinesCoverage);
        this.modifiedLinesCoverageDifference = modifiedLinesCoverageDifference;
        this.modifiedFilesCoverage = new ArrayList<>(modifiedFilesCoverage);
        this.modifiedFilesCoverageDifference = modifiedFilesCoverageDifference;
        this.indirectCoverageChanges = new ArrayList<>(indirectCoverageChanges);
    }

    public QualityGateResult getQualityGateResult() {
        return qualityGateResult;
    }

    public CoverageStatistics getStatistics() {
        return new CoverageStatistics(projectValues, difference, modifiedLinesCoverage, modifiedLinesCoverageDifference,
                modifiedFilesCoverage, modifiedFilesCoverageDifference);
    }

    public ElementFormatter getFormatter() {
        return FORMATTER;
    }

    /**
     * Returns the supported baselines.
     *
     * @return all supported baselines
     */
    @SuppressWarnings("unused") // Called by jelly view
    public List<Baseline> getBaselines() {
        return List.of(Baseline.PROJECT, Baseline.MODIFIED_FILES, Baseline.MODIFIED_LINES, Baseline.INDIRECT);
    }

    /**
     * Returns whether a delta metric for the specified metric exists.
     *
     * @param baseline
     *         the baseline to use
     *
     * @return {@code true} if a delta is available for the specified metric, {@code false} otherwise
     */
    @SuppressWarnings("unused") // Called by jelly view
    public boolean hasBaselineResult(final Baseline baseline) {
        return !getValues(baseline).isEmpty();
    }

    /**
     * Returns the associate delta baseline for the specified baseline.
     *
     * @param baseline
     *         the baseline to get the delta baseline for
     *
     * @return the delta baseline
     * @throws NoSuchElementException
     *         if this baseline does not provide a delta baseline
     */
    @SuppressWarnings("unused") // Called by jelly view
    public Baseline getDeltaBaseline(final Baseline baseline) {
        if (baseline == Baseline.PROJECT) {
            return Baseline.PROJECT_DELTA;
        }
        if (baseline == Baseline.MODIFIED_LINES) {
            return Baseline.MODIFIED_LINES_DELTA;
        }
        if (baseline == Baseline.MODIFIED_FILES) {
            return Baseline.MODIFIED_FILES_DELTA;
        }
        if (baseline == Baseline.INDIRECT) {
            return Baseline.INDIRECT;
        }
        throw new NoSuchElementException("No delta baseline for this baseline: " + baseline);
    }

    /**
     * Returns the title text for the specified baseline.
     *
     * @param baseline
     *         the baseline to get the title for
     *
     * @return the title
     */
    public String getTitle(final Baseline baseline) {
        if (hasDelta(baseline)) {
            return getDeltaBaseline(baseline).getTitle();
        }
        else {
            return baseline.getTitle();
        }
    }

    /**
     * Returns all available values for the specified baseline.
     *
     * @param baseline
     *         the baseline to get the values for
     *
     * @return the available values
     * @throws NoSuchElementException
     *         if this baseline does not provide values
     */
    // Called by jelly view
    public List<Value> getAllValues(final Baseline baseline) {
        return getValueStream(baseline).collect(Collectors.toList());
    }

    /**
     * Returns all available deltas for the specified baseline.
     *
     * @param baseline
     *         the baseline to get the deltas for
     *
     * @return the available values
     * @throws NoSuchElementException
     *         if this baseline does not provide deltas
     */
    public NavigableMap<Metric, Fraction> getAllDeltas(final Baseline baseline) {
        if (baseline == Baseline.PROJECT_DELTA) {
            return difference;
        }
        else if (baseline == Baseline.MODIFIED_LINES_DELTA) {
            return modifiedLinesCoverageDifference;
        }
        else if (baseline == Baseline.MODIFIED_FILES_DELTA) {
            return modifiedFilesCoverageDifference;
        }
        throw new NoSuchElementException("No delta baseline: " + baseline);
    }

    /**
     * Returns all important values for the specified baseline.
     *
     * @param baseline
     *         the baseline to get the values for
     *
     * @return the available values
     * @throws NoSuchElementException
     *         if this baseline does not provide values
     */
    // Called by jelly view
    public List<Value> getValues(final Baseline baseline) {
        return filterImportantMetrics(getValueStream(baseline));
    }

    /**
     * Returns the value for the specified metric, if available.
     *
     * @param baseline
     *         the baseline to get the value for
     * @param metric
     *         the metric to get the value for
     *
     * @return the optional value
     */
    public Optional<Value> getValueForMetric(final Baseline baseline, final Metric metric) {
        return getAllValues(baseline).stream()
                .filter(value -> value.getMetric() == metric)
                .findFirst();
    }

    private List<Value> filterImportantMetrics(final Stream<? extends Value> values) {
        return values.filter(v -> getMetricsForSummary().contains(v.getMetric()))
                .collect(Collectors.toList());
    }

    private Stream<? extends Value> getValueStream(final Baseline baseline) {
        if (baseline == Baseline.PROJECT) {
            return projectValues.stream();
        }
        if (baseline == Baseline.MODIFIED_LINES) {
            return modifiedLinesCoverage.stream();
        }
        if (baseline == Baseline.MODIFIED_FILES) {
            return modifiedFilesCoverage.stream();
        }
        if (baseline == Baseline.INDIRECT) {
            return indirectCoverageChanges.stream();
        }
        throw new NoSuchElementException("No such baseline: " + baseline);
    }

    /**
     * Returns whether a delta metric for the specified baseline exists.
     *
     * @param baseline
     *         the baseline to use
     *
     * @return {@code true} if a delta is available for the specified baseline, {@code false} otherwise
     */
    @SuppressWarnings("unused") // Called by jelly view
    public boolean hasDelta(final Baseline baseline) {
        return baseline == Baseline.PROJECT || baseline == Baseline.MODIFIED_LINES
                || baseline == Baseline.MODIFIED_FILES;
    }

    /**
     * Returns whether a delta metric for the specified metric exists.
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to check
     *
     * @return {@code true} if a delta is available for the specified metric, {@code false} otherwise
     */
    public boolean hasDelta(final Baseline baseline, final Metric metric) {
        if (baseline == Baseline.PROJECT) {
            return difference.containsKey(metric);
        }
        if (baseline == Baseline.MODIFIED_LINES) {
            return modifiedLinesCoverageDifference.containsKey(metric)
                    && Set.of(Metric.BRANCH, Metric.LINE).contains(metric);
        }
        if (baseline == Baseline.MODIFIED_FILES) {
            return modifiedFilesCoverageDifference.containsKey(metric)
                    && Set.of(Metric.BRANCH, Metric.LINE).contains(metric);
        }
        if (baseline == Baseline.INDIRECT) {
            return false;
        }
        throw new NoSuchElementException("No such baseline: " + baseline);
    }

    /**
     * Returns whether a delta metric for the specified metric exists.
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to check
     *
     * @return {@code true} if a delta is available for the specified metric, {@code false} otherwise
     */
    public Optional<Fraction> getDelta(final Baseline baseline, final Metric metric) {
        if (baseline == Baseline.PROJECT) {
            return Optional.ofNullable(difference.get(metric));
        }
        if (baseline == Baseline.MODIFIED_LINES) {
            return Optional.ofNullable(modifiedLinesCoverageDifference.get(metric));
        }
        if (baseline == Baseline.MODIFIED_FILES) {
            return Optional.ofNullable(modifiedFilesCoverageDifference.get(metric));
        }
        return Optional.empty();
    }

    /**
     * Returns whether a value for the specified metric exists.
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to check
     *
     * @return {@code true} if a value is available for the specified metric, {@code false} otherwise
     */
    public boolean hasValue(final Baseline baseline, final Metric metric) {
        return getAllValues(baseline).stream()
                .anyMatch(v -> v.getMetric() == metric);
    }

    /**
     * Returns a formatted and localized String representation of the value for the specified metric (with respect to
     * the given baseline).
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to get the delta for
     *
     * @return the formatted value
     */
    public String formatValue(final Baseline baseline, final Metric metric) {
        var value = getValueForMetric(baseline, metric);
        return value.isPresent() ? FORMATTER.formatValue(value.get()) : com.parasoft.findings.jenkins.coverage.api.model.Messages.Coverage_Not_Available();
    }

    /**
     * Returns a formatted and localized String representation of the delta for the specified metric (with respect to
     * the given baseline).
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to get the delta for
     *
     * @return the delta metric
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String formatDelta(final Baseline baseline, final Metric metric) {
        var currentLocale = Functions.getCurrentLocale();
        if (baseline == Baseline.PROJECT && hasDelta(baseline, metric)) {
            return FORMATTER.formatDelta(difference.get(metric), metric, currentLocale);
        }
        if (baseline == Baseline.MODIFIED_LINES && hasDelta(baseline, metric)) {
            return FORMATTER.formatDelta(modifiedLinesCoverageDifference.get(metric), metric, currentLocale);
        }
        if (baseline == Baseline.MODIFIED_FILES && hasDelta(baseline, metric)) {
            return FORMATTER.formatDelta(modifiedFilesCoverageDifference.get(metric), metric, currentLocale);
        }
        return Messages.Coverage_Not_Available();
    }

    /**
     * Returns whether the trend of the values for the specific metric is positive or negative.
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to check
     *
     * @return {@code true} if the trend is positive, {@code false} otherwise
     */
    @SuppressWarnings("unused") // Called by jelly view
    public boolean isPositiveTrend(final Baseline baseline, final Metric metric) {
        var delta = getDelta(baseline, metric);
        if (delta.isPresent()) {
            if (delta.get().compareTo(Fraction.ZERO) > 0) {
                return metric.getTendency() == Metric.MetricTendency.LARGER_IS_BETTER;
            }
            return metric.getTendency() == Metric.MetricTendency.SMALLER_IS_BETTER;
        }
        return true;
    }

    /**
     * Returns the visible metrics for the project summary.
     *
     * @return the metrics to be shown in the project summary
     */
    @VisibleForTesting
    NavigableSet<Metric> getMetricsForSummary() {
        return new TreeSet<>(
                Set.of(Metric.LINE, Metric.LOC, Metric.BRANCH, Metric.COMPLEXITY_DENSITY, Metric.MUTATION));
    }

    /**
     * Returns the possible reference build that has been used to compute the coverage delta.
     *
     * @return the reference build, if available
     */
    public Optional<Run<?, ?>> getReferenceBuild() {
        if (NO_REFERENCE_BUILD.equals(referenceBuildId)) {
            return Optional.empty();
        }
        return new JenkinsFacade().getBuild(referenceBuildId);
    }

    /**
     * Renders the reference build as HTML-link.
     *
     * @return the reference build
     * @see #getReferenceBuild()
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String getReferenceBuildLink() {
        return ReferenceBuild.getReferenceBuildLink(referenceBuildId);
    }
}
