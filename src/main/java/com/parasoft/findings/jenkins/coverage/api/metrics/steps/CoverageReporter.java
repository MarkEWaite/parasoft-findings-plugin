package com.parasoft.findings.jenkins.coverage.api.metrics.steps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import hudson.model.Result;
import io.jenkins.plugins.util.EnvironmentResolver;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.Fraction;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.CheckForNull;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;

import com.parasoft.findings.jenkins.coverage.api.metrics.model.CoverageStatistics;
import com.parasoft.findings.jenkins.coverage.api.metrics.source.SourceCodePainter;
import io.jenkins.plugins.forensics.delta.Delta;
import io.jenkins.plugins.forensics.delta.FileChanges;
import io.jenkins.plugins.prism.SourceCodeRetention;
import io.jenkins.plugins.util.LogHandler;
import io.jenkins.plugins.util.QualityGateResult;
import io.jenkins.plugins.util.StageResultHandler;

import static com.parasoft.findings.jenkins.coverage.api.metrics.steps.ReferenceResult.DEFAULT_REFERENCE_BUILD_IDENTIFIER;
import static com.parasoft.findings.jenkins.coverage.api.metrics.steps.ReferenceResult.ReferenceStatus.*;

/**
 * Transforms the old model to the new model and invokes all steps that work on the new model. Currently, only the
 * source code painting and copying has been moved to this new reporter class.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
public class CoverageReporter {
    @SuppressWarnings("checkstyle:ParameterNumber")
    public CoverageBuildAction publishAction(final String id, final String optionalName, final String icon, final Node rootNode,
                                             final Run<?, ?> build, final FilePath workspace, final TaskListener listener, final String configRefBuild,
                                             final List<CoverageQualityGate> qualityGates, final String scm, final String sourceCodeEncoding,
                                             final SourceCodeRetention sourceCodeRetention, final StageResultHandler resultHandler)
            throws InterruptedException {
        FilteredLog log = new FilteredLog("Errors while reporting code coverage results:");

        ReferenceBuildActionResult referenceBuildActionResult = getReferenceBuildActionResult(configRefBuild, build, id, log);
        Optional<CoverageBuildAction> possibleReferenceResult = referenceBuildActionResult.getPossibleReferenceResult();

        List<FileNode> filesToStore;
        CoverageBuildAction action;
        if (possibleReferenceResult.isPresent()) {
            CoverageBuildAction referenceAction = possibleReferenceResult.get();
            Node referenceRoot = referenceAction.getResult();

            log.logInfo("Calculating the code delta...");
            CodeDeltaCalculator codeDeltaCalculator = new CodeDeltaCalculator(build, workspace, listener, scm);
            Optional<Delta> delta = codeDeltaCalculator.calculateCodeDeltaToReference(referenceAction.getOwner(), log);
            delta.ifPresent(value -> createDeltaReports(rootNode, log, referenceRoot, codeDeltaCalculator, value));

            log.logInfo("Calculating coverage deltas...");

            Node modifiedLinesCoverageRoot = rootNode.filterByModifiedLines();

            NavigableMap<Metric, Fraction> modifiedLinesCoverageDelta;
            List<Value> aggregatedModifiedFilesCoverage;
            NavigableMap<Metric, Fraction> modifiedFilesCoverageDelta;
            if (hasModifiedLinesCoverage(modifiedLinesCoverageRoot)) {
                Node modifiedFilesCoverageRoot = rootNode.filterByModifiedFiles();
                aggregatedModifiedFilesCoverage = modifiedFilesCoverageRoot.aggregateValues();
                modifiedFilesCoverageDelta = modifiedFilesCoverageRoot.computeDelta(rootNode);
                modifiedLinesCoverageDelta = modifiedLinesCoverageRoot.computeDelta(modifiedFilesCoverageRoot);
            }
            else {
                modifiedLinesCoverageDelta = new TreeMap<>();
                aggregatedModifiedFilesCoverage = new ArrayList<>();
                modifiedFilesCoverageDelta = new TreeMap<>();
                if (rootNode.hasModifiedLines()) {
                    log.logInfo("No detected code changes affect the code coverage");
                }
            }

            NavigableMap<Metric, Fraction> coverageDelta = rootNode.computeDelta(referenceRoot);

            QualityGateResult qualityGateResult = evaluateQualityGates(rootNode, log,
                    modifiedLinesCoverageRoot.aggregateValues(), modifiedLinesCoverageDelta, coverageDelta,
                    resultHandler, qualityGates);

            if (sourceCodeRetention == SourceCodeRetention.MODIFIED) {
                filesToStore = modifiedLinesCoverageRoot.getAllFileNodes();
                log.logInfo("-> Selecting %d modified files for source code painting", filesToStore.size());
            }
            else {
                filesToStore = rootNode.getAllFileNodes();
            }

            action = new CoverageBuildAction(build, id, optionalName, icon, rootNode, qualityGateResult, log,
                    referenceAction.getOwner().getExternalizableId(), coverageDelta,
                    modifiedLinesCoverageRoot.aggregateValues(), modifiedLinesCoverageDelta,
                    aggregatedModifiedFilesCoverage, modifiedFilesCoverageDelta);
        }
        else {
            QualityGateResult qualityGateStatus = evaluateQualityGates(rootNode, log,
                    List.of(), new TreeMap<>(), new TreeMap<>(), resultHandler, qualityGates);

            filesToStore = rootNode.getAllFileNodes();

            action = new CoverageBuildAction(build, id, optionalName, icon, rootNode, qualityGateStatus, log);
        }

        log.logInfo("Executing source code painting...");
        SourceCodePainter sourceCodePainter = new SourceCodePainter(build, workspace, id);
        sourceCodePainter.processSourceCodePainting(rootNode, filesToStore,
                sourceCodeEncoding, sourceCodeRetention, log);

        log.logInfo("Finished coverage processing - adding the action to the build...");

        LogHandler logHandler = new LogHandler(listener, "Coverage");
        logHandler.log(log);

        build.addAction(action);
        return action;
    }

    private void createDeltaReports(final Node rootNode, final FilteredLog log, final Node referenceRoot,
            final CodeDeltaCalculator codeDeltaCalculator, final Delta delta) {
        FileChangesProcessor fileChangesProcessor = new FileChangesProcessor();

        try {
            log.logInfo("Preprocessing code changes...");
            Set<FileChanges> changes = codeDeltaCalculator.getCoverageRelevantChanges(delta);
            var mappedChanges = codeDeltaCalculator.mapScmChangesToReportPaths(changes, rootNode, log);
            var oldPathMapping = codeDeltaCalculator.createOldPathMapping(rootNode, referenceRoot, mappedChanges, log);

            log.logInfo("Obtaining code changes for files...");
            fileChangesProcessor.attachChangedCodeLines(rootNode, mappedChanges);

            log.logInfo("Obtaining coverage delta for files...");
            fileChangesProcessor.attachFileCoverageDeltas(rootNode, referenceRoot, oldPathMapping);
        }
        catch (IllegalStateException exception) {
            log.logError("An error occurred while processing code and coverage changes:");
            log.logError("-> Message: " + exception.getMessage());
            log.logError("-> Skipping calculating modified lines coverage and modified files coverage");
        }
    }

    private QualityGateResult evaluateQualityGates(final Node rootNode, final FilteredLog log,
            final List<Value> modifiedLinesCoverageDistribution,
            final NavigableMap<Metric, Fraction> modifiedLinesCoverageDelta,
            final NavigableMap<Metric, Fraction> coverageDelta, final StageResultHandler resultHandler,
            final List<CoverageQualityGate> qualityGates) {
        var statistics = new CoverageStatistics(rootNode.aggregateValues(), coverageDelta,
                modifiedLinesCoverageDistribution, modifiedLinesCoverageDelta, List.of(), new TreeMap<>());
        CoverageQualityGateEvaluator evaluator = new CoverageQualityGateEvaluator(qualityGates, statistics);
        var qualityGateStatus = evaluator.evaluate();
        if (qualityGateStatus.isInactive()) {
            log.logInfo("No quality gates have been set - skipping");
        }
        else {
            log.logInfo("Evaluating quality gates");
            if (qualityGateStatus.isSuccessful()) {
                log.logInfo("-> All quality gates have been passed");
            }
            else {
                var message = String.format("-> Some quality gates have been missed: overall result is %s",
                        qualityGateStatus.getOverallStatus().getResult());
                log.logInfo(message);
                resultHandler.setResult(qualityGateStatus.getOverallStatus().getResult(), message);
            }
            log.logInfo("-> Details for each quality gate:");
            qualityGateStatus.getMessages().forEach(log::logInfo);
        }
        return qualityGateStatus;
    }

    private boolean hasModifiedLinesCoverage(final Node modifiedLinesCoverageRoot) {
        Optional<Value> lineCoverage = modifiedLinesCoverageRoot.getValue(Metric.LINE);
        if (lineCoverage.isPresent() && hasLineCoverageSet(lineCoverage.get())) {
            return true;
        }
        Optional<Value> branchCoverage = modifiedLinesCoverageRoot.getValue(Metric.BRANCH);
        return branchCoverage.filter(this::hasLineCoverageSet).isPresent();
    }

    private boolean hasLineCoverageSet(final Value value) {
        return ((edu.hm.hafner.coverage.Coverage) value).isSet();
    }

    private ReferenceBuildActionResult getReferenceBuildActionResult(final String configRefBuild, final Run<?, ?> build,
                                                                     final String id, final FilteredLog log) {
        if (StringUtils.isBlank(configRefBuild)) {
            log.logInfo("Using default reference build(last successful build with coverage data) " +
                    "since user defined reference build is not set");
            return getDefaultReferenceBuildAction(build, id, log);
        } else {
            log.logInfo("Obtaining action of specified reference build '%s'", configRefBuild);
            return getSpecifiedReferenceBuildAction(configRefBuild, build, id, log);
        }
    }

    private ReferenceBuildActionResult getDefaultReferenceBuildAction(final Run<?, ?> build, final String id,
                                                                      final FilteredLog log) {
        Run<?, ?> previousSuccessfulBuild = build.getPreviousSuccessfulBuild();
        if (previousSuccessfulBuild != null) {
            Optional<CoverageBuildAction> previousSuccessfulResult;
            for (Run<?, ?> b = previousSuccessfulBuild; b != null; b = b.getPreviousSuccessfulBuild()) {
                previousSuccessfulResult = getPreviousResult(id, b);
                if (previousSuccessfulResult.isPresent()) {
                    log.logInfo("-> Found reference result in build '%s'", b);
                    return new ReferenceBuildActionResult(previousSuccessfulResult,
                            new ReferenceResult(ReferenceResult.ReferenceStatus.OK, b.getExternalizableId()));
                }
            }

            log.logInfo("-> Found no reference result in all previous successful builds");
            return new ReferenceBuildActionResult(Optional.empty(),
                    new ReferenceResult(NO_CVG_DATA_IN_REF_BUILD, DEFAULT_REFERENCE_BUILD_IDENTIFIER));
        } else {
            log.logInfo("-> Found no successful build");
            return new ReferenceBuildActionResult(Optional.empty(),
                    new ReferenceResult(NO_REF_BUILD, DEFAULT_REFERENCE_BUILD_IDENTIFIER));
        }
    }

    private ReferenceBuildActionResult getSpecifiedReferenceBuildAction(final String configRefBuild, final Run<?, ?> build,
                                                                        final String id, final FilteredLog log) {
        String expandedRefBuild = expandReferenceBuild(build, configRefBuild);
        if(!StringUtils.equals(expandedRefBuild, configRefBuild)) {
            log.logInfo("-> Expanding specified reference build '%s' to '%s'", configRefBuild, expandedRefBuild);
        }

        Optional<Run<?, ?>> reference = getSpecifiedReferenceBuild(build, expandedRefBuild, log);

        if (reference.isPresent()) {
            Run<?, ?> referenceBuild = reference.get();

            Result result = referenceBuild.getResult();
            if (result == Result.SUCCESS || result == Result.UNSTABLE) {
                Optional<CoverageBuildAction> previousResult = getPreviousResult(id, referenceBuild);

                if (previousResult.isEmpty()) {
                    log.logInfo("-> Found no reference result in build '%s'", referenceBuild);
                    return new ReferenceBuildActionResult(Optional.empty(),
                            new ReferenceResult(NO_CVG_DATA_IN_REF_BUILD, referenceBuild.getExternalizableId()));
                }

                log.logInfo("-> Found reference result in build '%s'", referenceBuild);
                return new ReferenceBuildActionResult(previousResult,
                        new ReferenceResult(OK, referenceBuild.getExternalizableId()));
            } else {
                log.logInfo("-> The reference build '%s' is not successful or unstable", referenceBuild);
                return new ReferenceBuildActionResult(Optional.empty(),
                        new ReferenceResult(REF_BUILD_NOT_SUCCESSFUL_OR_UNSTABLE, referenceBuild.getExternalizableId()));
            }
        } else {
            return new ReferenceBuildActionResult(Optional.empty(),
                    new ReferenceResult(NO_REF_BUILD, expandedRefBuild));
        }
    }

    private Optional<Run<?, ?>> getSpecifiedReferenceBuild(final Run<?, ?> build, final String expandedRefBuild,
                                                           final FilteredLog log) {
        if (!StringUtils.isNumeric(expandedRefBuild)) {
            log.logInfo("-> Invalid reference build number '%s'", expandedRefBuild);
            return Optional.empty();
        }

        Run<?,?> referenceBuild;
        try {
            referenceBuild = build.getParent().getBuildByNumber(Integer.parseInt(expandedRefBuild));
        } catch (NumberFormatException nfe) {
            log.logInfo("-> Reference build number '%s' is out of range", expandedRefBuild);
            return Optional.empty();
        }

        if (referenceBuild == null) {
            log.logInfo("-> Found no specified reference build '%s'", expandedRefBuild);
            return Optional.empty();
        }

        if (StringUtils.equals(build.getExternalizableId(), referenceBuild.getExternalizableId())) {
            log.logInfo("-> Reference build '%s' was ignored since the build number set is same as the current build",
                    referenceBuild.getExternalizableId());
            return Optional.empty();
        }

        return Optional.of(referenceBuild);
    }

    private Optional<CoverageBuildAction> getPreviousResult(final String id, @CheckForNull final Run<?, ?> build) {
        if (build == null) {
            return Optional.empty();
        }
        List<CoverageBuildAction> actions = build.getActions(CoverageBuildAction.class);
        for (CoverageBuildAction action : actions) {
            if (action.getUrlName().equals(id)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }

    private String expandReferenceBuild(final Run<?, ?> run, final String configRefBuild) {
        try {
            EnvironmentResolver environmentResolver = new EnvironmentResolver();

            return environmentResolver.expandEnvironmentVariables(
                    run.getEnvironment(TaskListener.NULL), configRefBuild);
        } catch (IOException | InterruptedException ignore) {
            return configRefBuild; // fallback, no expansion
        }
    }

}
