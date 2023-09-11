package com.parasoft.findings.jenkins.coverage.api.metrics.steps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import hudson.model.Job;
import hudson.model.PermalinkProjectAction;
import io.jenkins.plugins.util.JenkinsFacade;
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
import org.apache.commons.lang3.tuple.ImmutablePair;

import static hudson.model.PermalinkProjectAction.Permalink.BUILTIN;
import static java.util.stream.Collectors.groupingBy;

/**
 * Transforms the old model to the new model and invokes all steps that work on the new model. Currently, only the
 * source code painting and copying has been moved to this new reporter class.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
public class CoverageReporter {

    private static final String NO_REFERENCE_BUILD = "-";
    @SuppressWarnings("checkstyle:ParameterNumber")
    CoverageBuildAction publishAction(final String id, final String optionalName, final String icon, final Node rootNode,
            final Run<?, ?> build, final FilePath workspace, final TaskListener listener,
            final List<CoverageQualityGate> qualityGates, final String scm, final String sourceCodeEncoding,
            final SourceCodeRetention sourceCodeRetention, final StageResultHandler resultHandler)
            throws InterruptedException {
        FilteredLog log = new FilteredLog("Errors while reporting code coverage results:");

        //Map<String, List<CoverageQualityGate>> 
        //Map<String, List<CoverageQualityGate>> qualityGatesPerReferenceBuildId =
        //        qualityGates.stream().collect(groupingBy(CoverageQualityGate::getReferenceBuildId));

        // Group coverageQualityGates with realRefBuildId?
        Map<String, List<CoverageQualityGate>> qualityGatesPerReferenceBuildId =
                qualityGates.stream().collect(groupingBy(coverageQualityGate ->
                        this.getReferenceBuildAction(build, id, log, coverageQualityGate.getReferenceBuildId())
                                .right));
        // Update qualityGates with realRefBuildId
        qualityGatesPerReferenceBuildId.forEach((referenceBuildId, coverageQualityGates) -> {
            coverageQualityGates.forEach(coverageQualityGate -> {
                coverageQualityGate.setReferenceBuildId(referenceBuildId);
            });
        });

        List<CoverageBuildResult> coverageBuildResults = new ArrayList<>();
        qualityGatesPerReferenceBuildId.forEach((referenceBuildId, coverageQualityGates) -> {
            Node node = rootNode.copyTree();

            ImmutablePair<Optional<CoverageBuildAction>, String> buildActionRefIdPair =
                    getReferenceBuildAction(build, id, log, referenceBuildId);

            Optional<CoverageBuildAction> possibleReferenceResult = buildActionRefIdPair.left;
            String realRefBuildId = buildActionRefIdPair.right;

            CoverageBuildResult coverageBuildResult;
            if (possibleReferenceResult.isPresent()) {
                CoverageBuildAction referenceAction = possibleReferenceResult.get();
                Node referenceRoot = referenceAction.getResult();

                log.logInfo("Calculating the code delta...");
                CodeDeltaCalculator codeDeltaCalculator = new CodeDeltaCalculator(build, workspace, listener, scm);
                Optional<Delta> delta = codeDeltaCalculator.calculateCodeDeltaToReference(referenceAction.getOwner(), log);
                delta.ifPresent(value -> createDeltaReports(node, log, referenceRoot, codeDeltaCalculator, value));

                log.logInfo("Calculating coverage deltas...");

                Node modifiedLinesCoverageRoot = node.filterByModifiedLines();

                NavigableMap<Metric, Fraction> modifiedLinesCoverageDelta;
                List<Value> aggregatedModifiedFilesCoverage;
                NavigableMap<Metric, Fraction> modifiedFilesCoverageDelta;
                if (hasModifiedLinesCoverage(modifiedLinesCoverageRoot)) {
                    Node modifiedFilesCoverageRoot = node.filterByModifiedFiles();
                    aggregatedModifiedFilesCoverage = modifiedFilesCoverageRoot.aggregateValues();
                    modifiedFilesCoverageDelta = modifiedFilesCoverageRoot.computeDelta(node);
                    modifiedLinesCoverageDelta = modifiedLinesCoverageRoot.computeDelta(modifiedFilesCoverageRoot);
                }
                else {
                    modifiedLinesCoverageDelta = new TreeMap<>();
                    aggregatedModifiedFilesCoverage = new ArrayList<>();
                    modifiedFilesCoverageDelta = new TreeMap<>();
                    if (node.hasModifiedLines()) {
                        log.logInfo("No detected code changes affect the code coverage");
                    }
                }

                NavigableMap<Metric, Fraction> coverageDelta = node.computeDelta(referenceRoot);

                QualityGateResult qualityGateResult = evaluateQualityGates(node, log,
                        modifiedLinesCoverageRoot.aggregateValues(), modifiedLinesCoverageDelta, coverageDelta,
                        resultHandler, coverageQualityGates);
                coverageBuildResult = new CoverageBuildResult(node, qualityGateResult, realRefBuildId, coverageDelta,
                        modifiedLinesCoverageRoot.aggregateValues(), modifiedLinesCoverageDelta,
                        aggregatedModifiedFilesCoverage, modifiedFilesCoverageDelta,
                        node.filterByIndirectChanges().aggregateValues());
            }
            else {
                QualityGateResult qualityGateResult = evaluateQualityGates(node, log,
                        List.of(), new TreeMap<>(), new TreeMap<>(), resultHandler, coverageQualityGates);
                coverageBuildResult = new CoverageBuildResult(node, qualityGateResult, realRefBuildId);
            }

            coverageBuildResults.add(coverageBuildResult);
        });

        CoverageBuildAction action = new CoverageBuildAction(build, id, optionalName, icon, rootNode, log, coverageBuildResults);


        log.logInfo("Executing source code painting...");
        // TODO Always store all files?
        List<FileNode> filesToStore = rootNode.getAllFileNodes();
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

            log.logInfo("Obtaining indirect coverage changes...");
            fileChangesProcessor.attachIndirectCoveragesChanges(rootNode, referenceRoot,
                    mappedChanges, oldPathMapping);

            log.logInfo("Obtaining coverage delta for files...");
            fileChangesProcessor.attachFileCoverageDeltas(rootNode, referenceRoot, oldPathMapping);
        }
        catch (IllegalStateException exception) {
            log.logError("An error occurred while processing code and coverage changes:");
            log.logError("-> Message: " + exception.getMessage());
            log.logError("-> Skipping calculating modified lines coverage, modified files coverage"
                    + " and indirect coverage changes");
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

    private ImmutablePair<Optional<CoverageBuildAction>, String> getReferenceBuildAction(final Run<?, ?> build, final String id, final FilteredLog log,
                                                                                         final String referenceBuildId) {
        log.logInfo("Obtaining action of reference build");


        Optional<Run<?, ?>> reference = getReferenceBuild(build.getParent(), referenceBuildId);

        String realBuildId = NO_REFERENCE_BUILD;
        Optional<CoverageBuildAction> previousResult;
        if (reference.isPresent()) {
            Run<?, ?> referenceBuild = reference.get();
            log.logInfo("-> Using reference build '%s'", referenceBuild);
            previousResult = getPreviousResult(id, reference.get());
            realBuildId = referenceBuild.getExternalizableId();
//            if (previousResult.isPresent()) {
//                Run<?, ?> fallbackBuild = previousResult.get().getOwner();
//                if (!fallbackBuild.equals(referenceBuild)) {
//                    log.logInfo("-> Reference build has no action, falling back to last build with action: '%s'",
//                            fallbackBuild.getDisplayName());
//                }
//            }
        }
        else {
            previousResult = getPreviousResult(id, build.getPreviousBuild());
            previousResult.ifPresent(coverageBuildAction ->
                    log.logInfo("-> No reference build defined, falling back to previous build: '%s'",
                            coverageBuildAction.getOwner().getDisplayName()));
            realBuildId = Objects.requireNonNull(build.getPreviousBuild()).getExternalizableId();
        }

        if (previousResult.isEmpty()) {
            log.logInfo("-> Found no reference result in reference build");

            return ImmutablePair.of(Optional.empty(), realBuildId);
        }

        CoverageBuildAction referenceAction = previousResult.get();
        log.logInfo("-> Found reference result in build '%s'", referenceAction.getOwner().getDisplayName());

        return ImmutablePair.of(Optional.of(referenceAction), realBuildId);
    }

    private Optional<Run<?, ?>> getReferenceBuild(final Job<?, ?> job, final String referenceBuildId) {
        Map<String, PermalinkProjectAction.Permalink> builtinPermalinkMap =
                BUILTIN.stream().collect(Collectors.toMap(PermalinkProjectAction.Permalink::getId, Function.identity()));
        if (builtinPermalinkMap.containsKey(referenceBuildId)) {
            PermalinkProjectAction.Permalink p = job.getPermalinks().get(referenceBuildId);
            if (p == null) {
                return Optional.empty();
            }
            Run<?,?> run = p.resolve(job);
            return (run != null) ? Optional.of(run) : Optional.empty();
        } else {
            //ReferenceFinder referenceFinder = new ReferenceFinder();
            //Optional<Run<?, ?>> reference = referenceFinder.findReference(build, log);
            return new JenkinsFacade().getBuild(referenceBuildId);
        }
    }

    private Optional<CoverageBuildAction> getPreviousResult(final String id, @CheckForNull final Run<?, ?> startSearch) {
            Run<?, ?> build = startSearch;
//        for (Run<?, ?> build = startSearch; build != null; build = build.getPreviousBuild()) {
            List<CoverageBuildAction> actions = build.getActions(CoverageBuildAction.class);
            for (CoverageBuildAction action : actions) {
                if (action.getUrlName().equals(id)) {
                    return Optional.of(action);
                }
            }
//        }
        return Optional.empty();
    }
}
