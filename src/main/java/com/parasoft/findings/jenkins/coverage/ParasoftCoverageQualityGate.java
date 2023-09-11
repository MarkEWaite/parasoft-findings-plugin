package com.parasoft.findings.jenkins.coverage;

import edu.hm.hafner.util.VisibleForTesting;
import hudson.Extension;
import hudson.PermalinkList;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.PermalinkProjectAction;
import hudson.model.Run;
import hudson.util.ListBoxModel;
import com.parasoft.findings.jenkins.coverage.api.metrics.model.Baseline;
import hudson.util.RunList;
import io.jenkins.plugins.util.JenkinsFacade;
import io.jenkins.plugins.util.QualityGate;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.verb.POST;

public class ParasoftCoverageQualityGate extends QualityGate {
    private static final long serialVersionUID = 7809201823889916005L;
    private static final ParasoftCoverageQualityGateElementFormatter FORMATTER = new ParasoftCoverageQualityGateElementFormatter();

    private Baseline baseline = Baseline.PROJECT;

    private String referenceBuildId = "-";

    @DataBoundConstructor
    public ParasoftCoverageQualityGate(final double threshold,
                                       final Baseline baseline, final QualityGateCriticality criticality) {
        super(threshold);
        setBaseline(baseline);
        setCriticality(criticality);
    }

    @DataBoundSetter
    public final void setBaseline(final Baseline baseline) {
        this.baseline = baseline;
    }

    @DataBoundSetter
    public void setReferenceBuildId(String referenceBuildId) {
        this.referenceBuildId = referenceBuildId;
    }

    @Override
    public String getName() {
        return String.format("%s - %s", FORMATTER.getDisplayName(this.getBaseline()), FORMATTER.getMetricLineDisplayName());
    }

    public Baseline getBaseline() {
        return baseline;
    }

    public String getReferenceBuildId() {
        return referenceBuildId;
    }

    @Extension
    public static class ParasoftQualityGateDescriptor extends QualityGateDescriptor {
        private final JenkinsFacade JENKINS;

        @VisibleForTesting
        ParasoftQualityGateDescriptor(final JenkinsFacade jenkinsFacade) {
            super();
            JENKINS = jenkinsFacade;
        }

        @SuppressWarnings("unused")
        public ParasoftQualityGateDescriptor() {
            this(new JenkinsFacade());
        }

        @POST
        @SuppressWarnings("unused") // used by Stapler view data binding
        public ListBoxModel doFillBaselineItems(@AncestorInPath final AbstractProject<?, ?> project) {
            if (JENKINS.hasPermission(Item.CONFIGURE, project)) {
                return FORMATTER.getBaselineItems();
            }
            return new ListBoxModel();
        }

        @POST
        @SuppressWarnings("unused") // used by Stapler view data binding
        public ListBoxModel doFillReferenceBuildIdItems(@AncestorInPath final AbstractProject<?, ?> project) {
            if (JENKINS.hasPermission(Item.CONFIGURE, project)) {
                ListBoxModel options = new ListBoxModel();
                PermalinkList permalinks = project.getPermalinks();
                for (PermalinkProjectAction.Permalink permalink : permalinks) {
                    options.add(permalink.getDisplayName(), permalink.getId());
                }

                RunList<?> builds = project.getBuilds();
                for (Run build : builds) {
                    String buildId = build.getExternalizableId();
                    String message = build.getBuildStatusSummary().message;
                    options.add(String.format("Build #%s(%s)", buildId, message), buildId);
                }

                return options;
            }
            return new ListBoxModel();
        }

        @POST
        @SuppressWarnings("unused") // used by Stapler view data binding
        public ListBoxModel doFillCriticalityItems(@AncestorInPath final AbstractProject<?, ?> project) {
            if (JENKINS.hasPermission(Item.CONFIGURE, project)) {
                return FORMATTER.getCriticalityItems();
            }
            return new ListBoxModel();
        }
    }
}
