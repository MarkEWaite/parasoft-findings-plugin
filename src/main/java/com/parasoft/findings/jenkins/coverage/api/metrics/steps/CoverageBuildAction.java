package com.parasoft.findings.jenkins.coverage.api.metrics.steps;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.parasoft.findings.jenkins.coverage.api.metrics.charts.CoverageTrendChart;
import com.parasoft.findings.jenkins.coverage.api.metrics.model.Baseline;
import com.parasoft.findings.jenkins.coverage.api.metrics.model.CoverageStatistics;
import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.JacksonFacade;
import io.jenkins.plugins.echarts.GenericBuildActionIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.Fraction;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;

import org.kohsuke.stapler.StaplerProxy;
import hudson.model.Run;

import com.parasoft.findings.jenkins.coverage.api.metrics.model.ElementFormatter;
import com.parasoft.findings.jenkins.coverage.api.model.Messages;
import io.jenkins.plugins.util.AbstractXmlStream;
import io.jenkins.plugins.util.BuildAction;
import io.jenkins.plugins.util.QualityGateResult;

import static hudson.model.Run.XSTREAM2;

/**
 * Controls the life cycle of the coverage results in a job. This action persists the results of a build and displays a
 * summary on the build page. The actual visualization of the results is defined in the matching {@code summary.jelly}
 * file. This action also provides access to the coverage details: these are rendered using a new view instance.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings({"PMD.GodClass", "checkstyle:ClassDataAbstractionCoupling", "checkstyle:ClassFanOutComplexity"})
public final class CoverageBuildAction extends BuildAction<Node> implements StaplerProxy {
    private static final long serialVersionUID = -6023811049340671399L;

    private static final ElementFormatter FORMATTER = new ElementFormatter();

    private final String id;
    private final String name;

    private final FilteredLog log;

    /** The aggregated values of the result for the root of the tree. */
    private final List<? extends Value> projectValues;

    private final List<CoverageBuildResult> coverageBuildResults;

    public List<CoverageBuildResult> getCoverageBuildResults() {
        return coverageBuildResults;
    }

    private final String icon;
    // FIXME: Rethink if we need a separate result object that stores all data?

    static {
        CoverageXmlStream.registerConverters(XSTREAM2);
        registerMapConverter("difference");
        registerMapConverter("modifiedLinesCoverageDifference");
        registerMapConverter("modifiedFilesCoverageDifference");
    }

    private static void registerMapConverter(final String difference) {
        XSTREAM2.registerLocalConverter(CoverageBuildResult.class, difference,
                new CoverageXmlStream.MetricFractionMapConverter());
    }

    /**
     * Creates a new instance of {@link CoverageBuildAction}.
     *
     * @param owner
     *         the associated build that created the statistics
     * @param id
     *         ID (URL) of the results
     * @param optionalName
     *         optional name that overrides the default name of the results
     * @param icon
     *         name of the icon that should be used in actions and views
     * @param result
     *         the coverage tree as a result to persist with this action
     * @param log
     *         the logging statements of the recording step
     */
    public CoverageBuildAction(final Run<?, ?> owner, final String id, final String optionalName, final String icon,
            final Node result, final FilteredLog log) {
        this(owner, id, optionalName, icon, result, log, List.of());
    }

    /**
     * Creates a new instance of {@link CoverageBuildAction}.
     *
     * @param owner
     *         the associated build that created the statistics
     * @param id
     *         ID (URL) of the results
     * @param optionalName
     *         optional name that overrides the default name of the results
     * @param icon
     *         name of the icon that should be used in actions and views
     * @param result
     *         the coverage tree as a result to persist with this action
     * @param log
     *         the logging statements of the recording step
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public CoverageBuildAction(final Run<?, ?> owner, final String id, final String optionalName, final String icon,
            final Node result, final FilteredLog log,
                               final List<CoverageBuildResult> coverageBuildResults) {
        this(owner, id, optionalName, icon, result, log, coverageBuildResults,
                true);
    }

    @VisibleForTesting
    @SuppressWarnings("checkstyle:ParameterNumber")
    CoverageBuildAction(final Run<?, ?> owner, final String id, final String name, final String icon,
            final Node result, final FilteredLog log, final List<CoverageBuildResult> coverageBuildResults,
            final boolean canSerialize) {
        super(owner, result, false);

        this.id = id;
        this.name = name;
        this.icon = icon;
        this.log = log;

        this.projectValues = result.aggregateValues();

        this.coverageBuildResults = coverageBuildResults;

        if (canSerialize) {
            createXmlStream().write(owner.getRootDir().toPath().resolve(getBuildResultBaseName()), result);
//            coverageBuildResults.forEach(coverageBuildResult -> {
//                Optional<Run<?, ?>> referenceBuild = coverageBuildResult.getReferenceBuild();
//                if(referenceBuild.isPresent()) {
//                    createXmlStream().write(owner.getRootDir().toPath()
//                            .resolve(getBuildResultBaseName() + referenceBuild.get().getExternalizableId()), coverageBuildResult.getResult());
//                } else {
//                    createXmlStream().write(owner.getRootDir().toPath()
//                            .resolve(getBuildResultBaseName() + "-"), coverageBuildResult.getResult());
//                }
//            });
        }
    }

//    @Override @SuppressFBWarnings(value = "CRLF_INJECTION_LOGS", justification = "getOwner().toString() is under our control")
//    protected Object readResolve() {
//        super.readResolve();
//        if (difference == null) {
//            difference = new TreeMap<>();
//            Logger.getLogger(CoverageBuildAction.class.getName()).log(Level.FINE, "Difference serialization was null: " + getOwner().getDisplayName());
//        }
//        if (modifiedLinesCoverageDifference == null) {
//            modifiedLinesCoverageDifference = new TreeMap<>();
//            Logger.getLogger(CoverageBuildAction.class.getName()).log(Level.FINE, "Modified lines serialization was null: " + getOwner().getDisplayName());
//        }
//        if (modifiedFilesCoverageDifference == null) {
//            modifiedFilesCoverageDifference = new TreeMap<>();
//            Logger.getLogger(CoverageBuildAction.class.getName()).log(Level.FINE, "Modified files serialization was null: " + getOwner().getDisplayName());
//        }
//
//        return this;
//    }

    /**
     * Returns the actual name of the tool. If no user-defined name is given, then the default name is returned.
     *
     * @return the name
     */
    private String getActualName() {
        return StringUtils.defaultIfBlank(name, Messages.Coverage_Link_Name());
    }

    public ElementFormatter getFormatter() {
        return FORMATTER;
    }

    public CoverageStatistics getStatistics() {
        return new CoverageStatistics(projectValues, new TreeMap<>(), List.of(), new TreeMap<>(), List.of(), new TreeMap<>());
    }

    public List<Value> getAllValues(final Baseline baseline) {
        return getValueStream(baseline).collect(Collectors.toList());
    }

    private Stream<? extends Value> getValueStream(final Baseline baseline) {
        if (baseline == Baseline.PROJECT) {
            return projectValues.stream();
        }
        throw new NoSuchElementException("No such baseline: " + baseline);
    }

    @Override
    protected AbstractXmlStream<Node> createXmlStream() {
        return new CoverageXmlStream();
    }

    @Override
    protected CoverageJobAction createProjectAction() {
        return new CoverageJobAction(getOwner().getParent(), getUrlName(), name, icon);
    }

    @Override
    protected String getBuildResultBaseName() {
        return String.format("%s.xml", id);
    }

    @Override
    public CoverageViewModel getTarget() {
//        return new CoverageViewModel(getOwner(), getUrlName(), name, getResult(),
//                getStatistics(), getQualityGateResult(), getReferenceBuildLink(), log, this::createChartModel);
        return null;
    }

//    private String createChartModel(final String configuration) {
//        // FIXME: add without optional
//        var iterable = new GenericBuildActionIterator.BuildActionIterable<>(CoverageBuildAction.class, Optional.of(this),
//                action -> getUrlName().equals(action.getUrlName()), CoverageBuildAction::getStatistics);
//        return new JacksonFacade().toJson(
//                new CoverageTrendChart().create(iterable, ChartModelConfiguration.fromJson(configuration)));
//    }

    @NonNull
    @Override
    public String getIconFileName() {
        return icon;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return getActualName();
    }

    @NonNull
    @Override
    public String getUrlName() {
        return id;
    }

    @Override
    public String toString() {
        return String.format("%s (%s): %s", getDisplayName(), getUrlName(), projectValues);
    }
}
