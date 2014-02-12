package jenkins.plugins.simpleupstreamnotifier;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.taskdefs.email.Mailer;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.mail.MailSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

@SuppressWarnings({ "unchecked" })
public class SimpleUpstreamCommittersPublisher extends Notifier {
    //public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    protected static final Logger LOGGER = Logger.getLogger(Mailer.class.getName());

    public boolean sendToIndividuals = false;

    @DataBoundConstructor
    public SimpleUpstreamCommittersPublisher()
    {
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        if (build.getResult() != Result.SUCCESS && build.getCause(Cause.UpstreamCause.class) != null)
        {
            ArrayList<Cause.UpstreamCause> upstreamCauses = getUpstreamCauses(build);
            ArrayList<AbstractProject> upstreamProjects = getUpstreamProjects(upstreamCauses);

            if (!upstreamProjects.isEmpty()) {

                Collection<String> namesCollection = Collections2.transform(upstreamProjects, new Function<AbstractProject, String>() {
                    public String apply(AbstractProject from) {
                        return from.getName();
                    }
                });

                listener.getLogger().println("Upstream projects changes detected. Mailing upstream committers in the following projects:");
                listener.getLogger().println(StringUtils.join(namesCollection, ","));

                return new hudson.tasks.MailSender( "", false, sendToIndividuals, "UTF-8", upstreamProjects).execute(build,listener);
            }
        }
        return true;
    }

    private ArrayList<AbstractProject> getUpstreamProjects(ArrayList<Cause.UpstreamCause> upstreamCauses) {

        ArrayList<AbstractProject> projects = new ArrayList<AbstractProject>();

        for (Cause.UpstreamCause cause : upstreamCauses) {
            String project = cause.getUpstreamProject();
            projects.add((AbstractProject)Jenkins.getInstance().getItem(project));
        }

        return projects;
    }

    private ArrayList<Cause.UpstreamCause> getUpstreamCauses(AbstractBuild<?, ?> build) {
        Cause.UpstreamCause buildCause = build.getCause(Cause.UpstreamCause.class);

        ArrayList<Cause.UpstreamCause> causes = new ArrayList<Cause.UpstreamCause>();

        causes.add(buildCause);

        getUpstreamCauses(buildCause.getUpstreamCauses(), causes);

        return causes;
    }

    private void getUpstreamCauses(List<Cause> upstreamCauses, final ArrayList<Cause.UpstreamCause> causes) {

        causes.addAll(CollectionUtils.select(upstreamCauses, new Predicate() {
            public boolean evaluate(Object o) {
                if (o instanceof Cause.UpstreamCause) {
                    Cause.UpstreamCause cause = (Cause.UpstreamCause) o;
                    getUpstreamCauses(cause.getUpstreamCauses(), causes);
                }

                return o instanceof Cause.UpstreamCause;
            }
        }));
    }


    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            super(SimpleUpstreamCommittersPublisher.class);
        }

        public String getDisplayName() {
            return "Mail upstream committers when the build fails";
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}


