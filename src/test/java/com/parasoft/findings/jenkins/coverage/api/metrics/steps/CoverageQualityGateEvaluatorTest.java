package com.parasoft.findings.jenkins.coverage.api.metrics.steps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.Metric;

import com.parasoft.findings.jenkins.coverage.api.metrics.AbstractCoverageTest;
import com.parasoft.findings.jenkins.coverage.api.metrics.model.Baseline;
import io.jenkins.plugins.util.QualityGate.QualityGateCriticality;
import io.jenkins.plugins.util.QualityGateResult;
import io.jenkins.plugins.util.QualityGateStatus;

import static io.jenkins.plugins.util.assertions.Assertions.*;

class CoverageQualityGateEvaluatorTest extends AbstractCoverageTest {
    @Test
    void shouldBeInactiveIfGatesAreEmpty() {
        CoverageQualityGateEvaluator evaluator = new CoverageQualityGateEvaluator(new ArrayList<>(), createStatistics());

        QualityGateResult result = evaluator.evaluate();

        assertThat(result).hasNoMessages().isInactive().isSuccessful().hasOverallStatus(QualityGateStatus.INACTIVE);
    }

    @Test
    void shouldPassForTooLowThresholds() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        qualityGates.add(new CoverageQualityGate(0, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));qualityGates.add(new CoverageQualityGate(0, Metric.LINE, Baseline.MODIFIED_LINES, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(0, Metric.LINE, Baseline.MODIFIED_LINES, QualityGateCriticality.UNSTABLE));

        CoverageQualityGateEvaluator evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());

        assertThat(evaluator).isEnabled();

        QualityGateResult result = evaluator.evaluate();

        assertThat(result).hasOverallStatus(QualityGateStatus.PASSED).isSuccessful().isNotInactive().hasMessages(
                "-> [Overall project - Line Coverage]: ≪Success≫ - (Actual value: 50.00%, Quality gate: 0.00)",
                "-> [Modified code lines - Line Coverage]: ≪Success≫ - (Actual value: 50.00%, Quality gate: 0.00)");
    }

    @Test
    void shouldSkipIfValueNotDefined() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        qualityGates.add(new CoverageQualityGate(0, Metric.LINE, Baseline.MODIFIED_LINES, QualityGateCriticality.UNSTABLE));

        CoverageQualityGateEvaluator evaluator = new CoverageQualityGateEvaluator(qualityGates, createOnlyProjectStatistics());

        assertThat(evaluator).isEnabled();

        QualityGateResult result = evaluator.evaluate();

        assertThat(result).hasOverallStatus(QualityGateStatus.INACTIVE).isInactive().hasMessages(
                "-> [Modified code lines - Line Coverage]: ≪Not built≫ - (Actual value: n/a, Quality gate: 0.00)");
    }

    @Test
    void shouldReportUnstableIfBelowThreshold() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        qualityGates.add(new CoverageQualityGate(51.0, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(51.0, Metric.LINE, Baseline.MODIFIED_LINES, QualityGateCriticality.UNSTABLE));

        CoverageQualityGateEvaluator evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());
        QualityGateResult result = evaluator.evaluate();

        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful().isNotInactive().hasMessages(
                "-> [Overall project - Line Coverage]: ≪Unstable≫ - (Actual value: 50.00%, Quality gate: 51.00)",
                "-> [Modified code lines - Line Coverage]: ≪Unstable≫ - (Actual value: 50.00%, Quality gate: 51.00)");
    }

    @Test
    void shouldReportUnstableIfLargerThanThreshold() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        qualityGates.add(new CoverageQualityGate(149.0, Metric.COMPLEXITY, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(14, Metric.COMPLEXITY_MAXIMUM, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(999, Metric.LOC, Baseline.MODIFIED_LINES, QualityGateCriticality.UNSTABLE));

        CoverageQualityGateEvaluator evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());
        QualityGateResult result = evaluator.evaluate();

        assertThat(result).hasOverallStatus(QualityGateStatus.WARNING).isNotSuccessful().isNotInactive().hasMessages(
                "-> [Overall project - Line Coverage]: ≪Unstable≫ - (Actual value: 50.00%, Quality gate: 149.00)",
                "-> [Overall project - Line Coverage]: ≪Success≫ - (Actual value: 50.00%, Quality gate: 14.00)",
                "-> [Modified code lines - Line Coverage]: ≪Unstable≫ - (Actual value: 50.00%, Quality gate: 999.00)");
    }

    @Test
    void shouldReportUnstableIfWorseAndSuccessIfBetter2() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        var minimum = 0;
        qualityGates.add(new CoverageQualityGate(minimum, Metric.COMPLEXITY, Baseline.PROJECT, QualityGateCriticality.UNSTABLE));
        qualityGates.add(new CoverageQualityGate(minimum, Metric.LOC, Baseline.MODIFIED_LINES, QualityGateCriticality.UNSTABLE));

        CoverageQualityGateEvaluator evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());
        QualityGateResult result = evaluator.evaluate();

        assertThat(result).hasOverallStatus(QualityGateStatus.PASSED);
    }

    @Test
    void shouldReportFailureIfBelowThreshold() {
        QualityGateResult result = createQualityGateResult();

        assertThat(result).hasOverallStatus(QualityGateStatus.FAILED).isNotSuccessful().isNotInactive().hasMessages(
                "-> [Overall project - Line Coverage]: ≪Failed≫ - (Actual value: 50.00%, Quality gate: 51.00)",
                "-> [Modified code lines - Line Coverage]: ≪Failed≫ - (Actual value: 50.00%, Quality gate: 51.00)");
    }

    static QualityGateResult createQualityGateResult() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();
        qualityGates.add(new CoverageQualityGate(51.0, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.FAILURE));
        qualityGates.add(new CoverageQualityGate(51.0, Metric.LINE, Baseline.MODIFIED_LINES, QualityGateCriticality.FAILURE));

        CoverageQualityGateEvaluator evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());

        return evaluator.evaluate();
    }

    @Test
    void shouldOverwriteStatus() {
        Collection<CoverageQualityGate> qualityGates = new ArrayList<>();

        qualityGates.add(new CoverageQualityGate(51.0, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.FAILURE));

        CoverageQualityGateEvaluator evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());
        assertThatStatusWillBeOverwritten(evaluator);
    }

    @Test
    void shouldAddAllQualityGates() {
        Collection<CoverageQualityGate> qualityGates = List.of(
                new CoverageQualityGate(51.0, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.FAILURE));

        CoverageQualityGateEvaluator evaluator = new CoverageQualityGateEvaluator(qualityGates, createStatistics());

        assertThatStatusWillBeOverwritten(evaluator);
    }

    private static void assertThatStatusWillBeOverwritten(final CoverageQualityGateEvaluator evaluator) {
        QualityGateResult result = evaluator.evaluate();
        assertThat(result).hasOverallStatus(QualityGateStatus.FAILED).isNotSuccessful().hasMessages(
                "-> [Overall project - Line Coverage]: ≪Failed≫ - (Actual value: 50.00%, Quality gate: 51.00)");
    }
}
