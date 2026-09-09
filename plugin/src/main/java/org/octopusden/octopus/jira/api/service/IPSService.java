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
                .map(requirement -> {
                    logger.debug("Processing IPS Requirement {}", requirement.getKey());

                    List<IPSReqDev> development = requirement.getSubTaskObjects().stream()
                            .filter(i -> IPS_REQ_DEV_TYPE.equals(getIssueTypeName(i)))
                            .map(devSubtask -> buildDevSubtask(devSubtask, request, serviceUser))
                            .filter(d -> d != null)
                            .collect(Collectors.toList());

                    List<IPSReqQA> testing = requirement.getSubTaskObjects().stream()
                            .filter(i -> IPS_REQ_QA_TYPE.equals(getIssueTypeName(i)))
                            .map(qaSubtask -> buildQASubtask(qaSubtask, request, serviceUser))
                            .filter(q -> q != null)
                            .collect(Collectors.toList());

                    return new IPSRequirement(
                            requirement.getKey(),
                            requirement.getSummary() != null ? requirement.getSummary() : "",
                            requirement.getStatus().getName(),
                            requirement.getLabels().stream().map(l -> l.getLabel()).collect(Collectors.toList()),
                            getCustomFieldStringValue(fieldIpsRequirementRegion, requirement),
                            getCustomFieldStringValue(fieldLicense, requirement),
                            getCustomFieldStringValue(fieldIpsCode, requirement),
                            development,
                            testing
                    );
                })
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

        // Group triples by issue key, preserving first-seen order (an issue may span
        // multiple components via its fix versions)
        Map<String, List<Object[]>> byIssue = new LinkedHashMap<>();
        for (Object[] triple : triples) {
            IssueBean bean = (IssueBean) triple[2];
            byIssue.computeIfAbsent(bean.getKey(), k -> new ArrayList<>()).add(triple);
        }

        // Emit each issue once, under its first-seen component, merging all its fix versions
        Map<String, List<String>> fixVersionsByComponent = new LinkedHashMap<>();
        Map<String, List<IssueBean>> issuesByComponent = new LinkedHashMap<>();
        for (List<Object[]> issueTriples : byIssue.values()) {
            String compName = (String) issueTriples.get(0)[0];
            IssueBean issueBean = (IssueBean) issueTriples.get(0)[2];
            List<String> fixVersions = issueTriples.stream()
                    .map(t -> (String) t[1])
                    .distinct()
                    .collect(Collectors.toList());
            fixVersionsByComponent.computeIfAbsent(compName, k -> new ArrayList<>()).addAll(fixVersions);
            issuesByComponent.computeIfAbsent(compName, k -> new ArrayList<>()).add(issueBean);
        }

        List<DevComponent> components = fixVersionsByComponent.entrySet().stream()
                .map(entry -> {
                    String compName = entry.getKey();
                    List<String> fixVersions = entry.getValue().stream().distinct().collect(Collectors.toList());
                    List<IssueBean> issues = issuesByComponent.get(compName);
                    return new DevComponent(compName, fixVersions, issues);
                })
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
                .map(this::toIssueBean)
                .collect(Collectors.toList());

        return new IPSReqQA(
                qaSubtask.getKey(),
                qaSubtask.getSummary() != null ? qaSubtask.getSummary() : "",
                qaSubtask.getStatus().getName(),
                system,
                cases
        );
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
