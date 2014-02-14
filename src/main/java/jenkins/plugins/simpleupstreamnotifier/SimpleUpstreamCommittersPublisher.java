package jenkins.plugins.simpleupstreamnotifier;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import java.util.*;

@SuppressWarnings({ "unchecked" })
public class SimpleUpstreamCommittersPublisher extends Notifier {

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
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, UnsupportedEncodingException {

        if (build.getResult() == Result.SUCCESS || build.getCause(Cause.UpstreamCause.class) == null) return true;

        ArrayList<Cause.UpstreamCause> upstreamCauses = getUpstreamCauses(build);
        ArrayList<AbstractProject> upstreamProjects = getUpstreamProjects(upstreamCauses);
        Set<User> culprits = getCulprits(upstreamCauses);

        if (upstreamProjects.isEmpty()) {
            listener.getLogger().println("No upstream projects found");
            return true;
        }

        Collection projectNames = getProjectNames(upstreamProjects);

        if (culprits.isEmpty()) {
            listener.getLogger().println("No culprits found in the following projects:");
            listener.getLogger().println(StringUtils.join(projectNames, ","));
            return true;
        }

        listener.getLogger().println("Upstream projects changes detected. Mailing upstream committers in the following projects:");
        listener.getLogger().println(StringUtils.join(projectNames, ","));

        Set<InternetAddress> internetAddresses = buildCulpritList(listener, culprits);
        Collection emails = getEmails(internetAddresses);

        return new hudson.tasks.MailSender(StringUtils.join(emails, ","), false, true, "UTF-8").execute(build,listener);
    }

    private Collection<String> getEmails(Set<InternetAddress> internetAddresses) {
        Collection<String> emailAddressCollection = Collections2.transform(internetAddresses, new Function<InternetAddress, String>() {
            public String apply(InternetAddress from) {
                return from.getAddress();
            }
        });

        return emailAddressCollection;
    }

    private Set<InternetAddress> buildCulpritList(BuildListener listener, Set<User> culprits) throws UnsupportedEncodingException {
        Set<InternetAddress> r = new HashSet<InternetAddress>();
        for (User a : culprits) {
            String addresses = Util.fixEmpty(a.getProperty(hudson.tasks.Mailer.UserProperty.class).getAddress());

            listener.getLogger().println("  User "+a.getId()+" -> "+addresses);

            if (addresses != null)
                try {
                    r.add(hudson.tasks.Mailer.StringToAddress(addresses, "UTF-8"));
                } catch(AddressException e) {
                    listener.getLogger().println("Invalid address: " + addresses);
                }
            else {
                listener.getLogger().println(hudson.tasks.i18n.Messages.MailSender_NoAddress(a.getFullName()));
            }
        }
        return r;
    }

    private Set<User> getCulprits(ArrayList<Cause.UpstreamCause> upstreamCauses) {

        Set<User> culprits = new HashSet<User>();

        for (Cause.UpstreamCause cause : upstreamCauses) {
            AbstractBuild build = ((AbstractProject)Jenkins.getInstance().getItem(cause.getUpstreamProject())).getBuildByNumber(cause.getUpstreamBuild());
            culprits.addAll(build.getCulprits());
        }

        return culprits;
    }

    private Collection<String> getProjectNames(Collection<AbstractProject> projects) {
        Collection<String> namesCollection = Collections2.transform(projects, new Function<AbstractProject, String>() {
            public String apply(AbstractProject from) {
                return from.getName();
            }
        });

        return namesCollection;
    }

    private ArrayList<AbstractProject> getUpstreamProjects(ArrayList<Cause.UpstreamCause> upstreamCauses) {

        ArrayList<AbstractProject> projects = new ArrayList<AbstractProject>();

        for (Cause.UpstreamCause cause : upstreamCauses) {
            String project = cause.getUpstreamProject();
            projects.add((AbstractProject) Jenkins.getInstance().getItem(project));
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


