package org.octopusden.octopus.jira.api.service;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.link.IssueLinkManager;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.jql.builder.JqlClauseBuilder;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import org.octopusden.octopus.jira.api.config.ApiSetting;
import org.octopusden.octopus.jira.api.config.ApiSettingsProvider;
import org.octopusden.octopus.jira.api.dto.DevComponent;
import org.octopusden.octopus.jira.api.dto.IPSReqDev;
import org.octopusden.octopus.jira.api.dto.IPSReqQA;
import org.octopusden.octopus.jira.api.dto.IPSRequest;
import org.octopusden.octopus.jira.api.dto.IPSRequirement;
import org.octopusden.octopus.jira.api.dto.IPSResponse;
import org.octopusden.octopus.jira.api.dto.IssueBean;
import org.octopusden.octopus.jira.config.ComponentRegistryService;
import org.octopusden.octopus.jira.exception.JiraApplicationException;
import org.octopusden.octopus.jira.model.JiraProjectVersion;
import org.octopusden.octopus.releng.dto.JiraComponentVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IPSService {

    private static final Logger logger = LoggerFactory.getLogger(IPSService.class);

    private static final String IPS_REQ_DEV_TYPE = "IPS Req Dev";
    private static final String IPS_REQ_QA_TYPE = "IPS Req QA";
    private static final String IPS_RELEASE_TYPE = "IPS Release";
    private static final String MANDATORY_UPDATE_TYPE = "Mandatory Update";
    private static final String TEST_DEVELOPMENT_TYPE = "Test Development";
    private static final String LINK_TYPE_IMPLEMENTS = "Implements";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String FIELD_PRODUCT = "Product";
    private static final String FIELD_IPS_RELEASE = "IPS Release";
    private static final String FIELD_LICENSE = "License";
    private static final String FIELD_IPS_REQUIREMENT_REGION = "IPS Requirement Region";
    private static final String FIELD_IPS_CODE = "IPS Code";
    private static final String FIELD_SYSTEM = "System";
    private static final String RESOLUTION_REJECTED = "Rejected";
    private static final String LABEL_IMPACT_ON_ACQ = "IMPACT_ON_ACQ";
    private static final String LABEL_IMPACT_ON_ISS = "IMPACT_ON_ISS";
    private static final String LABEL_NO_IMPACT_ON_ONLINE = "NO_IMPACT_ON_ONLINE";
    private static final String LABEL_NO_IMPACT_ON_CLEARING = "NO_IMPACT_ON_CLEARING";

    private final IssueLinkManager issueLinkManager;
    private final CustomFieldManager customFieldManager;
    private final SearchService searchService;
    private final ApiSettingsProvider settingsProvider;
    private final ComponentRegistryService componentRegistryService;

    private final CustomField fieldProduct;
    private final CustomField fieldIpsRelease;
    private final CustomField fieldLicense;
    private final CustomField fieldIpsRequirementRegion;
    private final CustomField fieldIpsCode;
    private final CustomField fieldSystem;

    public IPSService(
            IssueLinkManager issueLinkManager,
            CustomFieldManager customFieldManager,
            SearchService searchService,
            ApiSettingsProvider settingsProvider,
            ComponentRegistryService componentRegistryService
    ) {
        this.issueLinkManager = issueLinkManager;
        this.customFieldManager = customFieldManager;
        this.searchService = searchService;
        this.settingsProvider = settingsProvider;
        this.componentRegistryService = componentRegistryService;

        this.fieldProduct = firstOrThrow(customFieldManager.getCustomFieldObjectsByName(FIELD_PRODUCT), FIELD_PRODUCT);
        this.fieldIpsRelease = firstOrThrow(customFieldManager.getCustomFieldObjectsByName(FIELD_IPS_RELEASE), FIELD_IPS_RELEASE);
        this.fieldLicense = firstOrNull(customFieldManager.getCustomFieldObjectsByName(FIELD_LICENSE));
        this.fieldIpsRequirementRegion = firstOrNull(customFieldManager.getCustomFieldObjectsByName(FIELD_IPS_REQUIREMENT_REGION));
        this.fieldIpsCode = firstOrNull(customFieldManager.getCustomFieldObjectsByName(FIELD_IPS_CODE));
        this.fieldSystem = firstOrNull(customFieldManager.getCustomFieldObjectsByName(FIELD_SYSTEM));
    }

    public IPSResponse generate(IPSRequest request) {
        ApplicationUser serviceUser = getServiceUser();
        String ipsProject = settingsProvider.getString(ApiSetting.IPS_REPORTS_PROJECT);

        Issue ipsReleaseIssue = getIPSReleaseIssue(
                request.getIps(), request.getRelease(), request.getStartDate(), ipsProject, serviceUser);
        logger.info("Found IPS Release issue {} for {}:{}", new Object[]{ipsReleaseIssue.getKey(), request.getIps(), request.getRelease()});

        List<IPSRequirement> requirements = getInwardImplementsIssues(ipsReleaseIssue, serviceUser).stream()
                .filter(i -> "IPS Requirement".equals(getIssueTypeName(i)))
                .filter(i -> !isRejected(i))
                .map(requirement -> {
                    logger.debug("Processing IPS Requirement {}", requirement.getKey());

                    List<IPSReqDev> development = requirement.getSubTaskObjects().stream()
                            .filter(i -> IPS_REQ_DEV_TYPE.equals(getIssueTypeName(i)))
                            .filter(i -> !isRejected(i))
                            .map(devSubtask -> buildDevSubtask(devSubtask, request, serviceUser))
                            .filter(d -> d != null)
                            .collect(Collectors.toList());

                    List<IPSReqQA> testing = requirement.getSubTaskObjects().stream()
                            .filter(i -> IPS_REQ_QA_TYPE.equals(getIssueTypeName(i)))
                            .filter(i -> !isRejected(i))
                            .map(qaSubtask -> buildQASubtask(qaSubtask, request, serviceUser))
                            .filter(q -> q != null)
                            .collect(Collectors.toList());

                    List<String> labels = requirement.getLabels().stream()
                            .map(l -> l.getLabel())
                            .collect(Collectors.toList());

                    return new IPSRequirement(
                            requirement.getKey(),
                            requirement.getSummary() != null ? requirement.getSummary() : "",
                            requirement.getStatus().getName(),
                            labels,
                            deriveImpact(labels),
                            getCustomFieldStringValue(fieldIpsRequirementRegion, requirement),
                            getCustomFieldStringValue(fieldLicense, requirement),
                            getCustomFieldStringValue(fieldIpsCode, requirement),
                            development,
                            testing
                    );
                })
                .sorted(Comparator.comparing(IPSRequirement::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        return new IPSResponse(
                ipsReleaseIssue.getKey(),
                request.getIps(),
                request.getRelease() != null ? request.getRelease() : "",
                ipsReleaseIssue.getStatus().getName(),
                ipsReleaseIssue.getLabels().stream().map(l -> l.getLabel()).collect(Collectors.toList()),
                requirements
        );
    }

    private IPSReqDev buildDevSubtask(Issue devSubtask, IPSRequest request, ApplicationUser serviceUser) {
        logger.debug("Processing IPS Req Dev {}", devSubtask.getKey());
        List<String> system = getCustomFieldValueAsStringList(fieldSystem, devSubtask);
        if (!emptyOrContains(system, request.getSystem())) return null;

        List<Issue> linkedIssues = getInwardImplementsIssues(devSubtask, serviceUser);
        if (request.isMandatory()) {
            linkedIssues = linkedIssues.stream()
                    .filter(i -> MANDATORY_UPDATE_TYPE.equals(getIssueTypeName(i)))
                    .collect(Collectors.toList());
        }

        linkedIssues = linkedIssues.stream()
                .filter(i -> !isRejected(i))
                .collect(Collectors.toList());

        // Build (componentName, version, issueBean) triples
        List<Object[]> triples = new ArrayList<>();
        for (Issue issue : linkedIssues) {
            logger.debug("Processing {}", issue.getKey());

            IssueBean issueBean = toIssueBean(issue);
            Collection<Version> releaseVersions = new ArrayList<>(issue.getFixVersions());

            for (Version version : releaseVersions) {
                logger.debug("Checking release version {}:{}", issue.getKey(), version.getName());
                JiraComponentVersion jiraComponentVersion = getJiraComponentVersion(issue, version);
                triples.add(new Object[]{
                        jiraComponentVersion.getComponentVersion().getComponentName(),
                        version.getName(),
                        issueBean
                });
            }

            if (releaseVersions.isEmpty()) {
                triples.add(new Object[]{
                        NOT_FOUND,
                        NOT_FOUND,
                        issueBean
                });
            }

        }

        // Group triples by component, preserving first-seen order
        Map<String, List<Object[]>> byComponent = new LinkedHashMap<>();
        for (Object[] triple : triples) {
            String compName = (String) triple[0];
            byComponent.computeIfAbsent(compName, k -> new ArrayList<>()).add(triple);
        }

        List<DevComponent> components = byComponent.entrySet().stream()
                .map(entry -> {
                    String compName = entry.getKey();
                    List<Object[]> componentTriples = entry.getValue();

                    // Only fix versions that belong to this component
                    List<String> fixVersions = componentTriples.stream()
                            .map(t -> (String) t[1])
                            .filter(v -> !NOT_FOUND.equals(v))
                            .distinct()
                            .collect(Collectors.toList());

                    // Deduplicate issues within the component, preserving first-seen order,
                    // and keep only the versions the issue has in this component
                    Map<String, IssueBean> issuesByKey = new LinkedHashMap<>();
                    Map<String, List<String>> versionsByIssue = new LinkedHashMap<>();
                    for (Object[] triple : componentTriples) {
                        String version = (String) triple[1];
                        IssueBean bean = (IssueBean) triple[2];
                        issuesByKey.putIfAbsent(bean.getKey(), bean);
                        if (!NOT_FOUND.equals(version)) {
                            versionsByIssue.computeIfAbsent(bean.getKey(), k -> new ArrayList<>()).add(version);
                        }
                    }
                    List<IssueBean> issues = issuesByKey.values().stream()
                            .map(bean -> bean.withFixVersions(
                                    versionsByIssue.getOrDefault(bean.getKey(), Collections.emptyList()).stream()
                                            .distinct()
                                            .collect(Collectors.toList())))
                            .sorted(BY_ISSUE_KEY)
                            .collect(Collectors.toList());

                    return new DevComponent(compName, fixVersions, issues);
                })
                .sorted(Comparator.comparing((DevComponent c) -> NOT_FOUND.equals(c.getName()))
                        .thenComparing(DevComponent::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        return new IPSReqDev(
                devSubtask.getKey(),
                devSubtask.getSummary() != null ? devSubtask.getSummary() : "",
                devSubtask.getStatus().getName(),
                devSubtask.getLabels().stream().map(l -> l.getLabel()).collect(Collectors.toList()),
                getCustomFieldStringValue(fieldLicense, devSubtask),
                system,
                components
        );
    }

    private IPSReqQA buildQASubtask(Issue qaSubtask, IPSRequest request, ApplicationUser serviceUser) {
        logger.debug("Processing IPS Req QA {}", qaSubtask.getKey());
        List<String> system = getCustomFieldValueAsStringList(fieldSystem, qaSubtask);
        if (!emptyOrContains(system, request.getSystem())) return null;

        List<IssueBean> cases = getInwardImplementsIssues(qaSubtask, serviceUser).stream()
                .filter(i -> TEST_DEVELOPMENT_TYPE.equals(getIssueTypeName(i)))
                .filter(i -> !isRejected(i))
                .map(this::toIssueBean)
                .sorted(BY_ISSUE_KEY)
                .collect(Collectors.toList());

        return new IPSReqQA(
                qaSubtask.getKey(),
                qaSubtask.getSummary() != null ? qaSubtask.getSummary() : "",
                qaSubtask.getStatus().getName(),
                system,
                cases
        );
    }

    private static final Comparator<IssueBean> BY_ISSUE_KEY =
            (a, b) -> compareIssueKeys(a.getKey(), b.getKey());

    private static int compareIssueKeys(String left, String right) {
        int leftDash = left.lastIndexOf('-');
        int rightDash = right.lastIndexOf('-');
        String leftPrefix = leftDash >= 0 ? left.substring(0, leftDash) : left;
        String rightPrefix = rightDash >= 0 ? right.substring(0, rightDash) : right;
        int prefixCompare = leftPrefix.compareTo(rightPrefix);
        if (prefixCompare != 0) {
            return prefixCompare;
        }
        return Integer.compare(issueNumber(left, leftDash), issueNumber(right, rightDash));
    }

    private static int issueNumber(String key, int dash) {
        if (dash < 0) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(key.substring(dash + 1));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private boolean emptyOrContains(List<String> list, String value) {
        if (value == null || value.isEmpty()) return true;
        if (list.isEmpty()) return true;
        return list.stream().anyMatch(s -> s.equalsIgnoreCase(value));
    }

    private Issue getIPSReleaseIssue(String ips, String release, Date startDate, String ipsProject, ApplicationUser user) {
        JqlClauseBuilder queryBuilder = JqlQueryBuilder.newBuilder().where()
                .project(ipsProject).and()
                .issueType(IPS_RELEASE_TYPE).and()
                .customField(fieldProduct.getIdAsLong()).eq(ips);

        if (release != null) {
            queryBuilder.and().customField(fieldIpsRelease.getIdAsLong()).eq(release);
        } else if (startDate != null) {
            queryBuilder.and().createdAfter(startDate);
        }

        com.atlassian.query.Query query = queryBuilder.buildQuery();
        List<Issue> issues;
        try {
            issues = searchService.search(user, query, PagerFilter.getUnlimitedFilter()).getResults();
        } catch (com.atlassian.jira.issue.search.SearchException e) {
            throw new IllegalStateException("Search failed for IPS Release " + ips + ":" + release, e);
        }
        if (issues == null) issues = Collections.emptyList();

        if (issues.isEmpty()) {
            throw new IllegalArgumentException("No IPS Releases found for " + ips + ":" + release);
        } else if (issues.size() > 1) {
            List<String> keys = issues.stream().map(Issue::getKey).collect(Collectors.toList());
            throw new IllegalArgumentException("Found more than one IPS Release for " + ips + ":" + release + " (" + keys + ")");
        }
        return issues.get(0);
    }

    private List<Issue> getInwardImplementsIssues(Issue issue, ApplicationUser user) {
        List<Issue> result = issueLinkManager.getLinkCollection(issue, user).getInwardIssues(LINK_TYPE_IMPLEMENTS);
        return result != null ? result : Collections.emptyList();
    }

    private String getCustomFieldStringValue(CustomField field, Issue issue) {
        if (field == null) return "";
        Object value = field.getValue(issue);
        return value != null ? value.toString() : "";
    }

    private List<String> getCustomFieldValueAsStringList(CustomField field, Issue issue) {
        if (field == null) return Collections.emptyList();
        Object value = issue.getCustomFieldValue(field);
        if (value instanceof List) {
            return ((List<?>) value).stream().map(Object::toString).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    static List<String> deriveImpact(List<String> labels) {
        List<String> result = new ArrayList<>(2);

        boolean acquirer = labels.contains(LABEL_IMPACT_ON_ACQ);
        boolean issuer = labels.contains(LABEL_IMPACT_ON_ISS);
        if (acquirer || issuer) {
            result.add(acquirer ? (issuer ? "acquirer&issuer" : "acquirer") : "issuer");
        }

        boolean online = !labels.contains(LABEL_NO_IMPACT_ON_ONLINE);
        boolean clearing = !labels.contains(LABEL_NO_IMPACT_ON_CLEARING);
        if (online || clearing) {
            result.add(online ? (clearing ? "online&clearing" : "online") : "clearing");
        }

        if (result.isEmpty()) {
            result.add("no updates for all");
        }
        return result;
    }

    private boolean isRejected(Issue issue) {
        return issue.getResolution() != null && RESOLUTION_REJECTED.equals(issue.getResolution().getName());
    }

    private IssueBean toIssueBean(Issue issue) {
        return new IssueBean(
                issue.getKey(),
                issue.getSummary() != null ? issue.getSummary() : "",
                issue.getIssueType() != null ? issue.getIssueType().getName() : "",
                issue.getStatus().getName(),
                issue.getPriority() != null ? issue.getPriority().getName() : null,
                issue.getLabels().stream().map(l -> l.getLabel()).collect(Collectors.toList()),
                issue.getFixVersions().stream().map(v -> v.getName()).collect(Collectors.toList()),
                issue.getComponents().stream().map(c -> c.getName()).collect(Collectors.toList()),
                issue.getResolution() != null ? issue.getResolution().getName() : "Unresolved"
        );
    }

    private ApplicationUser getServiceUser() {
        String userName = settingsProvider.getString(ApiSetting.SERVICE_USER);
        ApplicationUser user = ComponentAccessor.getUserManager().getUserByName(userName);
        if (user == null) {
            throw new IllegalStateException("Service user '" + userName + "' not found");
        }
        return user;
    }

    private static String getIssueTypeName(Issue issue) {
        return issue.getIssueType() != null ? issue.getIssueType().getName() : "";
    }

    private static CustomField firstOrThrow(Collection<CustomField> fields, String fieldName) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalStateException("Custom field '" + fieldName + "' not found");
        }
        return fields.iterator().next();
    }

    private static CustomField firstOrNull(Collection<CustomField> fields) {
        if (fields == null || fields.isEmpty()) return null;
        return fields.iterator().next();
    }

    private JiraComponentVersion getJiraComponentVersion(Issue issue, Version version) {
        JiraProjectVersion projectVersion = new JiraProjectVersion(
                issue.getProjectObject().getKey(),
                version.getName()
        );
        return componentRegistryService.getJiraComponentByProjectAndVersion(projectVersion)
                .orElseThrow(() -> new JiraApplicationException("Unable to find " + projectVersion + " in Components Registry"));
    }
}
