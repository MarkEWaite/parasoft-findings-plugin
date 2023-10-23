package com.parasoft.findings.jenkins.coverage.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.parasoft.findings.jenkins.coverage.Assertions.*;

/**
 * Tests the class {@link Metric}.
 *
 * @author Ullrich Hafner
 */
class MetricTest {
    @EnumSource(Metric.class)
    @ParameterizedTest(name = "{0} should be converted to a tag name and then back to a metric")
    void shouldConvertToTags(final Metric metric) {
        var tag = metric.toTagName();
        assertThat(tag).matches("^[a-z-]*$");

        var converted = Metric.fromTag(tag);
        assertThat(converted).isSameAs(metric);
    }
}
