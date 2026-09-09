package org.octopusden.octopus.jira.api.service;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.label.Label;
import com.atlassian.jira.issue.link.IssueLinkManager;
import com.atlassian.jira.issue.link.LinkCollection;
import com.atlassian.jira.issue.priority.Priority;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.jql.builder.ConditionBuilder;
import com.atlassian.jira.jql.builder.JqlClauseBuilder;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.query.Query;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
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
import org.octopusden.octopus.jira.model.JiraProjectVersion;
import org.octopusden.octopus.releng.dto.ComponentVersion;
import org.octopusden.octopus.releng.dto.JiraComponentVersion;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IPSServiceTest {

    private static final String IPS_PROJECT = "IPS";
    private static final String SERVICE_USER_NAME = "svcUser";
    private static final String RELEASE = "1.0";
    private static final String TEST_PROJECT = "TEST_PROJECT";

    private IssueLinkManager issueLinkManager;
    private CustomFieldManager customFieldManager;
    private SearchService searchService;
    private ApiSettingsProvider settingsProvider;
    private ComponentRegistryService componentsRegistryService;
    private IPSService service;

    private UserManager mockUserManager;
    private ApplicationUser mockServiceUser;
    private Issue mockIssue;

    private CustomField mockProductField;
    private CustomField mockIpsReleaseField;
    private CustomField mockSystemField;
    private CustomField mockApprovedForReleaseField;

    private MockedStatic<ComponentAccessor> componentAccessorMock;
    private MockedStatic<JqlQueryBuilder> jqlQueryBuilderMock;
    private MockedStatic<PagerFilter> pagerFilterMock;

    private JqlClauseBuilder mockWhereBuilder;
    private JqlQueryBuilder mockQueryBuilder;
    private Query mockQuery;
    private PagerFilter<?> mockPagerFilter;

    @Before
    public void setUp() {
        issueLinkManager = mock(IssueLinkManager.class);
        customFieldManager = mock(CustomFieldManager.class);
        searchService = mock(SearchService.class);
        settingsProvider = mock(ApiSettingsProvider.class);
        componentsRegistryService = mock(ComponentRegistryService.class);
        mockUserManager = mock(UserManager.class);
        mockServiceUser = mock(ApplicationUser.class);
        mockProductField = mock(CustomField.class);
        mockIpsReleaseField = mock(CustomField.class);
        mockSystemField = mock(CustomField.class);
        mockApprovedForReleaseField = mock(CustomField.class);

        componentAccessorMock = Mockito.mockStatic(ComponentAccessor.class);
        jqlQueryBuilderMock = Mockito.mockStatic(JqlQueryBuilder.class);
        pagerFilterMock = Mockito.mockStatic(PagerFilter.class);

        when(settingsProvider.getString(ApiSetting.IPS_REPORTS_PROJECT)).thenReturn(IPS_PROJECT);
        when(settingsProvider.getString(ApiSetting.SERVICE_USER)).thenReturn(SERVICE_USER_NAME);
        componentAccessorMock.when(ComponentAccessor::getUserManager).thenReturn(mockUserManager);
        when(mockUserManager.getUserByName(SERVICE_USER_NAME)).thenReturn(mockServiceUser);

        JiraComponentVersion mockComponentVersion = mock(JiraComponentVersion.class);

        // Set up the component version mock with specific values
        ComponentVersion componentVersionMock = mock(ComponentVersion.class);
        when(componentVersionMock.getComponentName()).thenReturn("test-component");
        when(mockComponentVersion.getComponentVersion()).thenReturn(componentVersionMock);

        when(componentsRegistryService.getJiraComponentByProjectAndVersion(any())).thenReturn(Optional.of(mockComponentVersion));

        when(mockProductField.getIdAsLong()).thenReturn(10001L);
        when(mockIpsReleaseField.getIdAsLong()).thenReturn(10002L);
        when(mockApprovedForReleaseField.getIdAsLong()).thenReturn(10003L);

        mockWhereBuilder = mock(JqlClauseBuilder.class, Mockito.RETURNS_SELF);
        mockQueryBuilder = mock(JqlQueryBuilder.class);
        mockQuery = mock(Query.class);
        mockPagerFilter = mock(PagerFilter.class);

        ConditionBuilder mockConditionBuilder = mock(ConditionBuilder.class);
        when(mockWhereBuilder.customField(anyLong())).thenReturn(mockConditionBuilder);
        when(mockConditionBuilder.eq(anyString())).thenReturn(mockWhereBuilder);

        jqlQueryBuilderMock.when(JqlQueryBuilder::newBuilder).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.where()).thenReturn(mockWhereBuilder);
        when(mockQueryBuilder.buildQuery()).thenReturn(mockQuery);
        pagerFilterMock.when(PagerFilter::getUnlimitedFilter).thenReturn(mockPagerFilter);

        setupDefaultCustomFields();
        setupDefaultLinkCollection();

        service = createService();
    }

    private void setupDefaultCustomFields() {
        CustomField licenseMock = mock(CustomField.class);
        when(licenseMock.getValue(any())).thenReturn("Apache");
        CustomField regionMock = mock(CustomField.class);
        when(regionMock.getValue(any())).thenReturn("EU");
        CustomField ipsCodeMock = mock(CustomField.class);
        when(ipsCodeMock.getValue(any())).thenReturn("CODE-1");

        List<Version> mockApprovedVersions = new ArrayList<>();
        Version v1 = mock(Version.class);
        when(v1.getName()).thenReturn("1.0.0");
        Version v2 = mock(Version.class);
        when(v2.getName()).thenReturn("2.0.0");
        mockApprovedVersions.add(v1);
        mockApprovedVersions.add(v2);
        when(mockApprovedForReleaseField.getValue(any())).thenReturn(mockApprovedVersions);

        when(customFieldManager.getCustomFieldObjectsByName("Product")).thenReturn(Collections.singletonList(mockProductField));
        when(customFieldManager.getCustomFieldObjectsByName("IPS Release")).thenReturn(Collections.singletonList(mockIpsReleaseField));
        when(customFieldManager.getCustomFieldObjectsByName("License")).thenReturn(Collections.singletonList(licenseMock));
        when(customFieldManager.getCustomFieldObjectsByName("IPS Requirement Region")).thenReturn(Collections.singletonList(regionMock));
        when(customFieldManager.getCustomFieldObjectsByName("IPS Code")).thenReturn(Collections.singletonList(ipsCodeMock));
        when(customFieldManager.getCustomFieldObjectsByName("System")).thenReturn(Collections.singletonList(mockSystemField));
        when(customFieldManager.getCustomFieldObjectsByName("Versions Approved For Release")).thenReturn(Collections.singletonList(mockApprovedForReleaseField));
    }

    private void setupDefaultLinkCollection() {
        LinkCollection emptyLinks = mock(LinkCollection.class);
        when(emptyLinks.getInwardIssues("Implements")).thenReturn(Collections.emptyList());
        when(issueLinkManager.getLinkCollection(any(Issue.class), any())).thenReturn(emptyLinks);
    }

    @After
    public void tearDown() {
        componentAccessorMock.close();
        jqlQueryBuilderMock.close();
        pagerFilterMock.close();
    }

    private IPSService createService() {
        return new IPSService(issueLinkManager, customFieldManager, searchService, settingsProvider, componentsRegistryService);
    }

    private IPSRequest createRequest(String release, Date startDate, boolean mandatory, String system) {
        return new IPSRequest("product-1", release, startDate, system, mandatory);
    }

    private IPSRequest createRequest() {
        return createRequest(RELEASE, null, true, "CLASSIC");
    }

    private Issue createIssue(String key, String summary, String typeName, String statusName,
                              String priorityName, String resolutionName, List<String> system,
                              List<String> labels, List<String> fixVersions, List<String> componentNames) {
        Issue issue = mock(Issue.class);
        when(issue.getKey()).thenReturn(key);
        when(issue.getSummary()).thenReturn(summary);

        Status status = mock(Status.class);
        when(status.getName()).thenReturn(statusName);
        when(issue.getStatus()).thenReturn(status);

        IssueType issueType = mock(IssueType.class);
        when(issueType.getName()).thenReturn(typeName);
        when(issue.getIssueType()).thenReturn(issueType);

        Set<Label> labelSet = new HashSet<>();
        for (String l : labels) {
            Label lbl = mock(Label.class);
            when(lbl.getLabel()).thenReturn(l);
            labelSet.add(lbl);
        }
        when(issue.getLabels()).thenReturn(labelSet);

        List<Version> versions = fixVersions.stream().map(v -> {
            Version ver = mock(Version.class);
            when(ver.getName()).thenReturn(v);
            return ver;
        }).collect(Collectors.toList());
        when(issue.getFixVersions()).thenReturn(versions);

        List<ProjectComponent> comps = componentNames.stream().map(c -> {
            ProjectComponent comp = mock(ProjectComponent.class);
            when(comp.getName()).thenReturn(c);
            return comp;
        }).collect(Collectors.toList());
        when(issue.getComponents()).thenReturn(comps);

        if (priorityName != null) {
            Priority priority = mock(Priority.class);
            when(priority.getName()).thenReturn(priorityName);
            when(issue.getPriority()).thenReturn(priority);
        } else {
            when(issue.getPriority()).thenReturn(null);
        }

        if (resolutionName != null) {
            Resolution resolution = mock(Resolution.class);
            when(resolution.getName()).thenReturn(resolutionName);
            when(issue.getResolution()).thenReturn(resolution);
        } else {
            when(issue.getResolution()).thenReturn(null);
        }

        when(issue.getCustomFieldValue(mockSystemField)).thenReturn(system);
        when(issue.getSubTaskObjects()).thenReturn(Collections.emptyList());

        return issue;
    }

    private Issue createIssue(String key) {
        return createIssue(key, "Summary", "IPS Requirement", "Open", "High", "Done",
                Collections.emptyList(), Collections.singletonList("label1"),
                Collections.singletonList("v1.0"), Collections.singletonList("comp1"));
    }

    private Issue createIssue(String key, String typeName) {
        return createIssue(key, "Summary", typeName, "Open", "High", "Done",
                Collections.emptyList(), Collections.singletonList("label1"),
                Collections.singletonList("v1.0"), Collections.singletonList("comp1"));
    }

    @SuppressWarnings("unchecked")
    private void stubSearchReturns(Issue... issues) throws com.atlassian.jira.issue.search.SearchException {
        SearchResults<Issue> results = mock(SearchResults.class);
        when(results.getResults()).thenReturn(Arrays.asList(issues));
        when(searchService.search(any(), any(), any())).thenReturn(results);
    }

    private void stubInwardLinks(Issue issue, List<Issue> linkedIssues) {
        LinkCollection linkCollection = mock(LinkCollection.class);
        when(linkCollection.getInwardIssues("Implements")).thenReturn(linkedIssues);
        when(issueLinkManager.getLinkCollection(issue, mockServiceUser)).thenReturn(linkCollection);
    }

    private void stubSubtasks(Issue parent, List<Issue> subtasks) {
        when(parent.getSubTaskObjects()).thenReturn(subtasks);
    }

    private void stubIssueProject(Issue issue) {
        Project mockProject = mock(Project.class);
        when(mockProject.getKey()).thenReturn(TEST_PROJECT);
        when(issue.getProjectObject()).thenReturn(mockProject);
    }

    // ==================== A. Error Cases ====================

    @Test
    public void testGenerateThrowsWhenServiceUserNotFound() throws Exception {
        when(mockUserManager.getUserByName(SERVICE_USER_NAME)).thenReturn(null);
        IPSRequest request = createRequest();
        IllegalStateException ex = Assert.assertThrows(IllegalStateException.class, () -> service.generate(request));
        assertTrue(ex.getMessage().contains(SERVICE_USER_NAME));
    }

    @Test
    public void testGenerateThrowsWhenProductCustomFieldNotFound() throws Exception {
        when(customFieldManager.getCustomFieldObjectsByName("Product")).thenReturn(Collections.emptyList());
        IllegalStateException ex = Assert.assertThrows(IllegalStateException.class, this::createService);
        assertTrue(ex.getMessage().contains("Product"));
    }

    @Test
    public void testGenerateThrowsWhenIpsReleaseCustomFieldNotFound() throws Exception {
        when(customFieldManager.getCustomFieldObjectsByName("IPS Release")).thenReturn(Collections.emptyList());
        IllegalStateException ex = Assert.assertThrows(IllegalStateException.class, this::createService);
        assertTrue(ex.getMessage().contains("IPS Release"));
    }

    @Test
    public void testGenerateThrowsWhenNoIpsReleasesFound() throws Exception {
        stubSearchReturns();
        IPSRequest request = createRequest();
        IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, () -> service.generate(request));
        assertTrue(ex.getMessage().contains("No IPS Releases found"));
    }

    @Test
    public void testGenerateThrowsWhenMultipleIpsReleasesFound() throws Exception {
        stubSearchReturns(createIssue("IPS-1"), createIssue("IPS-2"));
        IPSRequest request = createRequest();
        IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, () -> service.generate(request));
        assertTrue(ex.getMessage().contains("Found more than one IPS Release"));
    }

    // ==================== B. JQL Query Branching ====================

    @Test
    public void testGenerateWithReleaseParam() throws Exception {
        Issue ipsRelease = createIssue("IPS-100", "IPS Release", "IPS Release", "Released",
                "High", "Done", Collections.emptyList(), Collections.singletonList("ready"),
                Collections.emptyList(), Collections.emptyList());
        stubSearchReturns(ipsRelease);
        IPSRequest request = createRequest(RELEASE, null, true, "CLASSIC");
        IPSResponse response = service.generate(request);
        assertEquals(RELEASE, response.getRelease());
        assertEquals("IPS-100", response.getKey());
    }

    @Test
    public void testGenerateWithStartDateParam() throws Exception {
        Issue ipsRelease = createIssue("IPS-101", "IPS Release", "IPS Release", "Open",
                "High", "Done", Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        stubSearchReturns(ipsRelease);
        IPSRequest request = createRequest(null, new Date(), true, "CLASSIC");
        IPSResponse response = service.generate(request);
        assertEquals("", response.getRelease());
        assertEquals("IPS-101", response.getKey());
    }

    // ==================== C. Happy Path ====================

    @Test
    public void testGenerateReturnsCorrectBaseFields() throws Exception {
        Issue ipsRelease = createIssue("IPS-200", "Summary", "IPS Release", "Released",
                "High", "Done", Collections.emptyList(), Arrays.asList("label1", "label2"),
                Collections.emptyList(), Collections.emptyList());
        stubSearchReturns(ipsRelease);
        IPSResponse response = service.generate(createRequest());
        assertEquals("IPS-200", response.getKey());
        assertEquals("product-1", response.getIps());
        assertEquals(RELEASE, response.getRelease());
        assertEquals("Released", response.getStatus());
        assertEquals(new HashSet<>(Arrays.asList("label1", "label2")), new HashSet<>(response.getLabels()));
        assertTrue(response.getRequirements().isEmpty());
    }

    @Test
    public void testGenerateWithNoRequirements() throws Exception {
        stubSearchReturns(createIssue("IPS-300"));
        assertTrue(service.generate(createRequest()).getRequirements().isEmpty());
    }

    @Test
    public void testGenerateWithRequirementsFilteredByType() throws Exception {
        Issue ipsRelease = createIssue("IPS-500");
        Issue req1 = createIssue("REQ-1", "IPS Requirement");
        Issue bug = createIssue("BUG-2", "Bug");
        Issue req2 = createIssue("REQ-2", "IPS Requirement");
        stubSearchReturns(ipsRelease);
        stubInwardLinks(ipsRelease, Arrays.asList(req1, bug, req2));
        IPSResponse response = service.generate(createRequest());
        assertEquals(2, response.getRequirements().size());
        assertEquals("REQ-1", response.getRequirements().get(0).getKey());
        assertEquals("REQ-2", response.getRequirements().get(1).getKey());
    }

    // ==================== D. Requirements Processing ====================

    @Test
    public void testGeneratePopulatesRequirementFields() throws Exception {
        Issue ipsRelease = createIssue("IPS-600");
        Issue requirement = createIssue("REQ-10", "My Requirement", "IPS Requirement", "In Progress",
                "High", "Done", Collections.emptyList(), Collections.singletonList("r1"),
                Collections.emptyList(), Collections.emptyList());
        stubSearchReturns(ipsRelease);
        stubInwardLinks(ipsRelease, Collections.singletonList(requirement));

        CustomField licenseMock = mock(CustomField.class);
        when(licenseMock.getValue(requirement)).thenReturn("Apache");
        CustomField regionMock = mock(CustomField.class);
        when(regionMock.getValue(requirement)).thenReturn("EU");
        CustomField ipsCodeMock = mock(CustomField.class);
        when(ipsCodeMock.getValue(requirement)).thenReturn("CODE-1");
        when(customFieldManager.getCustomFieldObjectsByName("License")).thenReturn(Collections.singletonList(licenseMock));
        when(customFieldManager.getCustomFieldObjectsByName("IPS Requirement Region")).thenReturn(Collections.singletonList(regionMock));
        when(customFieldManager.getCustomFieldObjectsByName("IPS Code")).thenReturn(Collections.singletonList(ipsCodeMock));
        IPSService freshService = createService();

        IPSResponse response = freshService.generate(createRequest());
        assertEquals(1, response.getRequirements().size());
        IPSRequirement req = response.getRequirements().get(0);
        assertEquals("REQ-10", req.getKey());
        assertEquals("My Requirement", req.getName());
        assertEquals("In Progress", req.getStatus());
        assertEquals("EU", req.getRegion());
        assertEquals("Apache", req.getLicense());
        assertEquals("CODE-1", req.getIpsCode());
    }

    // ==================== E. Dev Subtasks ====================

    @Test
    public void testGeneratePopulatesDevSubtasks() throws Exception {
        Issue ipsRelease = createIssue("IPS-800");
        Issue requirement = createIssue("REQ-40");
        Issue devSubtask = createIssue("DEV-1", "Dev work", "IPS Req Dev", "Done",
                "High", "Done", Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        stubSearchReturns(ipsRelease);
        stubInwardLinks(ipsRelease, Collections.singletonList(requirement));
        stubSubtasks(requirement, Collections.singletonList(devSubtask));

        List<IPSReqDev> dev = service.generate(createRequest()).getRequirements().get(0).getDevelopment();
        assertEquals(1, dev.size());
        assertEquals("DEV-1", dev.get(0).getKey());
        assertEquals("Dev work", dev.get(0).getSummary());
        assertEquals("Done", dev.get(0).getStatus());
        assertTrue(dev.get(0).getComponents().isEmpty());
    }

    // ==================== F. Mandatory Update Filtering ====================

    @Test
    public void testGenerateFiltersMandatoryUpdateWhenMandatoryTrue() throws Exception {
        Issue ipsRelease = createIssue("IPS-900");
        Issue requirement = createIssue("REQ-60");
        Issue devSubtask = createIssue("DEV-3", "IPS Req Dev");
        Issue mandatoryIssue = createIssue("MU-1", "Summary", "Mandatory Update", "Open",
                "High", "Done", Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList("1.0.0"), Collections.singletonList("core"));
        Issue bugIssue = createIssue("BUG-3", "Summary", "Bug", "Open",
                "High", "Done", Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList("2.0.0"), Collections.singletonList("core"));
        stubSearchReturns(ipsRelease);
        stubInwardLinks(ipsRelease, Collections.singletonList(requirement));
        stubSubtasks(requirement, Collections.singletonList(devSubtask));
        stubInwardLinks(devSubtask, Arrays.asList(mandatoryIssue, bugIssue));
        stubIssueProject(devSubtask);
        stubIssueProject(mandatoryIssue);

        IPSReqDev dev = service.generate(createRequest(RELEASE, null, true, "CLASSIC"))
                .getRequirements().get(0).getDevelopment().get(0);
        assertEquals(1, dev.getComponents().size());
        assertEquals(1, dev.getComponents().get(0).getIssues().size());
        assertEquals("MU-1", dev.getComponents().get(0).getIssues().get(0).getKey());
    }

    @Test
    public void testGenerateIncludesAllIssueTypesWhenMandatoryFalse() throws Exception {
        Issue ipsRelease = createIssue("IPS-950");
        Issue requirement = createIssue("REQ-70");
        Issue devSubtask = createIssue("DEV-4", "IPS Req Dev");
        Issue mu = createIssue("MU-2", "Summary", "Mandatory Update", "Open",
                "High", "Done", Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList("v1"), Collections.singletonList("core"));
        Issue bug = createIssue("BUG-4", "Summary", "Bug", "Open",
                "High", "Done", Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList("v2"), Collections.singletonList("core"));
        stubSearchReturns(ipsRelease);
        stubInwardLinks(ipsRelease, Collections.singletonList(requirement));
        stubSubtasks(requirement, Collections.singletonList(devSubtask));
        stubInwardLinks(devSubtask, Arrays.asList(mu, bug));
        stubIssueProject(mu);
        stubIssueProject(bug);

        IPSReqDev dev = service.generate(createRequest(RELEASE, null, false, "CLASSIC"))
                .getRequirements().get(0).getDevelopment().get(0);
        assertEquals(1, dev.getComponents().size());
        assertEquals(2, dev.getComponents().get(0).getIssues().size());
        Set<String> keys = dev.getComponents().get(0).getIssues().stream()
                .map(i -> i.getKey()).collect(Collectors.toSet());
        assertEquals(new HashSet<>(Arrays.asList("MU-2", "BUG-4")), keys);
    }

    // ==================== G. Component Aggregation ====================

    @Test
    public void testGenerateAggregatesComponentsFromMultipleLinkedIssues() throws Exception {
        Issue ipsRelease = createIssue("IPS-1000");
        Issue requirement = createIssue("REQ-80");
        Issue devSubtask = createIssue("DEV-5", "IPS Req Dev");
        Issue issue1 = createIssue("MU-3", "Summary", "Mandatory Update", "Open",
                "High", "Done", Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList("v1.0"), Collections.singletonList("test-component"));
        Issue issue2 = createIssue("MU-4", "Summary", "Mandatory Update", "Open",
                "High", "Done", Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList("v2.0"), Collections.singletonList("test-component"));
        stubSearchReturns(ipsRelease);
        stubInwardLinks(ipsRelease, Collections.singletonList(requirement));
        stubSubtasks(requirement, Collections.singletonList(devSubtask));
        stubInwardLinks(devSubtask, Arrays.asList(issue1, issue2));
        stubIssueProject(issue1);
        stubIssueProject(issue2);

        List<DevComponent> components = service.generate(createRequest()).getRequirements().get(0).getDevelopment().get(0).getComponents();
        assertEquals(1, components.size());
        assertEquals("test-component", components.get(0).getName());
        assertEquals(new HashSet<>(Arrays.asList("v1.0", "v2.0")), new HashSet<>(components.get(0).getFixVersions()));
        assertEquals(2, components.get(0).getIssues().size());
    }

    @Test
    public void testGenerateDeduplicatesFixVersionsPerComponent() throws Exception {
        Issue ipsRelease = createIssue("IPS-1050");
        Issue requirement = createIssue("REQ-90");
        Issue devSubtask = createIssue("DEV-6", "IPS Req Dev");
        Issue issue1 = createIssue("MU-5", "Summary", "Mandatory Update", "Open",
                "High", "Done", Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList("v1.0"), Collections.singletonList("module-a"));
        Issue issue2 = createIssue("MU-6", "Summary", "Mandatory Update", "Open",
                "High", "Done", Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList("v1.0"), Collections.singletonList("module-a"));
        stubSearchReturns(ipsRelease);
        stubInwardLinks(ipsRelease, Collections.singletonList(requirement));
        stubSubtasks(requirement, Collections.singletonList(devSubtask));
        stubInwardLinks(devSubtask, Arrays.asList(issue1, issue2));
        stubIssueProject(issue1);
        stubIssueProject(issue2);

        List<String> fixVersions = service.generate(createRequest()).getRequirements().get(0).getDevelopment().get(0).getComponents().get(0).getFixVersions();
        assertEquals(1, fixVersions.size());
        assertEquals("v1.0", fixVersions.get(0));
    }

    @Test
    public void testGenerateDeduplicatesSameIssueWithinComponent() throws Exception {
        Issue ipsRelease = createIssue("IPS-1060");
        Issue requirement = createIssue("REQ-91");
        Issue devSubtask = createIssue("DEV-7", "IPS Req Dev");
        Issue mu = createIssue("MU-7", "Summary", "Mandatory Update", "Open",
                "High", "Done", Collections.emptyList(), Collections.emptyList(),
                Arrays.asList("v1.0", "v2.0"), Collections.singletonList("test-component"));
        stubSearchReturns(ipsRelease);
        stubInwardLinks(ipsRelease, Collections.singletonList(requirement));
        stubSubtasks(requirement, Collections.singletonList(devSubtask));
        stubInwardLinks(devSubtask, Collections.singletonList(mu));
        stubIssueProject(mu);

        List<DevComponent> components = service.generate(createRequest())
                .getRequirements().get(0).getDevelopment().get(0).getComponents();
        assertEquals(1, components.size());
        assertEquals(1, components.get(0).getIssues().size());
        assertEquals("MU-7", components.get(0).getIssues().get(0).getKey());
        assertEquals(new HashSet<>(Arrays.asList("v1.0", "v2.0")),
                new HashSet<>(components.get(0).getFixVersions()));
    }

    @Test
    public void testGenerateDeduplicatesIssueAcrossComponentsAndMergesFixVersions() throws Exception {
        Issue ipsRelease = createIssue("IPS-1070");
        Issue requirement = createIssue("REQ-92");
        Issue devSubtask = createIssue("DEV-8", "IPS Req Dev");
        Issue mu = createIssue("MU-8", "Summary", "Mandatory Update", "Open",
                "High", "Done", Collections.emptyList(), Collections.emptyList(),
                Arrays.asList("v1.0", "v2.0"), Collections.singletonList("test-component"));
        stubSearchReturns(ipsRelease);
        stubInwardLinks(ipsRelease, Collections.singletonList(requirement));
        stubSubtasks(requirement, Collections.singletonList(devSubtask));
        stubInwardLinks(devSubtask, Collections.singletonList(mu));
        stubIssueProject(mu);

        when(componentsRegistryService.getJiraComponentByProjectAndVersion(any(JiraProjectVersion.class)))
                .thenAnswer(inv -> {
                    JiraProjectVersion pv = inv.getArgument(0);
                    String compName = "v1.0".equals(pv.getVersion()) ? "component-a" : "component-b";
                    JiraComponentVersion jcv = mock(JiraComponentVersion.class);
                    ComponentVersion cv = mock(ComponentVersion.class);
                    when(cv.getComponentName()).thenReturn(compName);
                    when(jcv.getComponentVersion()).thenReturn(cv);
                    return Optional.of(jcv);
                });

        List<DevComponent> components = service.generate(createRequest())
                .getRequirements().get(0).getDevelopment().get(0).getComponents();
        assertEquals(1, components.size());
        assertEquals("component-a", components.get(0).getName());
        assertEquals(1, components.get(0).getIssues().size());
        assertEquals("MU-8", components.get(0).getIssues().get(0).getKey());
        assertEquals(new HashSet<>(Arrays.asList("v1.0", "v2.0")),
                new HashSet<>(components.get(0).getFixVersions()));
    }

    // ==================== H. QA Subtasks ====================

    @Test
    public void testGeneratePopulatesQaSubtasks() throws Exception {
        Issue ipsRelease = createIssue("IPS-1300");
        Issue requirement = createIssue("REQ-130");
        Issue qaSubtask = createIssue("QA-1", "QA work", "IPS Req QA", "To Do",
                "High", "Done", Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        Issue testDevIssue = createIssue("TD-1", "Test Development");
        Issue bugIssue = createIssue("BUG-5", "Bug");
        stubSearchReturns(ipsRelease);
        stubInwardLinks(ipsRelease, Collections.singletonList(requirement));
        stubSubtasks(requirement, Collections.singletonList(qaSubtask));
        stubInwardLinks(qaSubtask, Arrays.asList(testDevIssue, bugIssue));

        List<IPSReqQA> qa = service.generate(createRequest()).getRequirements().get(0).getTesting();
        assertEquals(1, qa.size());
        assertEquals("QA-1", qa.get(0).getKey());
        assertEquals("QA work", qa.get(0).getSummary());
        assertEquals("To Do", qa.get(0).getStatus());
        assertEquals(1, qa.get(0).getCases().size());
        assertEquals("TD-1", qa.get(0).getCases().get(0).getKey());
    }

    // ==================== L. System Filtering ====================

    @Test
    public void testGenerateExcludesDevSubtaskWhenSystemDoesNotMatch() throws Exception {
        Issue ipsRelease = createIssue("IPS-3000");
        Issue requirement = createIssue("REQ-600");
        Issue devSubtask = createIssue("DEV-50", "Summary", "IPS Req Dev", "Open",
                "High", "Done", Collections.singletonList("SERVER"), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        stubSearchReturns(ipsRelease);
        stubInwardLinks(ipsRelease, Collections.singletonList(requirement));
        stubSubtasks(requirement, Collections.singletonList(devSubtask));
        assertTrue(service.generate(createRequest()).getRequirements().get(0).getDevelopment().isEmpty());
    }

    @Test
    public void testGenerateIncludesDevSubtaskWhenSystemMatches() throws Exception {
        Issue ipsRelease = createIssue("IPS-3020");
        Issue requirement = createIssue("REQ-620");
        Issue devSubtask = createIssue("DEV-51", "Summary", "IPS Req Dev", "Open",
                "High", "Done", Collections.singletonList("CLASSIC"), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        stubSearchReturns(ipsRelease);
        stubInwardLinks(ipsRelease, Collections.singletonList(requirement));
        stubSubtasks(requirement, Collections.singletonList(devSubtask));
        List<IPSReqDev> dev = service.generate(createRequest()).getRequirements().get(0).getDevelopment();
        assertEquals(1, dev.size());
        assertEquals("DEV-51", dev.get(0).getKey());
    }

    @Test
    public void testGenerateIncludesSubtaskWhenIssueSystemListIsEmpty() throws Exception {
        Issue ipsRelease = createIssue("IPS-3050");
        Issue requirement = createIssue("REQ-650");
        Issue devSubtask = createIssue("DEV-53", "Summary", "IPS Req Dev", "Open",
                "High", "Done", Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        Issue qaSubtask = createIssue("QA-13", "Summary", "IPS Req QA", "Open",
                "High", "Done", Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        stubSearchReturns(ipsRelease);
        stubInwardLinks(ipsRelease, Collections.singletonList(requirement));
        stubSubtasks(requirement, Arrays.asList(devSubtask, qaSubtask));
        IPSRequirement response = service.generate(createRequest()).getRequirements().get(0);
        assertEquals(1, response.getDevelopment().size());
        assertEquals(1, response.getTesting().size());
    }

    // ==================== I. IssueBean Mapping ====================

    @Test
    public void testToIssueBeanMapsAllFieldsCorrectly() throws Exception {
        Issue ipsRelease = createIssue("IPS-1500");
        Issue requirement = createIssue("REQ-160");
        Issue devSubtask = createIssue("DEV-10", "IPS Req Dev");
        Issue linkedIssue = createIssue("MU-11", "Summary", "Mandatory Update", "Done",
                "High", "Done", Collections.emptyList(), Collections.singletonList("l1"),
                Collections.singletonList("v1"), Collections.singletonList("comp"));
        stubSearchReturns(ipsRelease);
        stubInwardLinks(ipsRelease, Collections.singletonList(requirement));
        stubSubtasks(requirement, Collections.singletonList(devSubtask));
        stubInwardLinks(devSubtask, Collections.singletonList(linkedIssue));
        stubIssueProject(linkedIssue);

        IssueBean bean = service.generate(createRequest()).getRequirements().get(0).getDevelopment().get(0).getComponents().get(0).getIssues().get(0);
        assertEquals("MU-11", bean.getKey());
        assertEquals("Done", bean.getStatus());
        assertEquals("High", bean.getPriority());
        assertEquals("Done", bean.getResolution());
        assertEquals(Collections.singletonList("l1"), bean.getLabels());
        assertEquals(Collections.singletonList("v1"), bean.getFixVersions());
        assertEquals(Collections.singletonList("comp"), bean.getComponents());
    }

    @Test
    public void testToIssueBeanHandlesNullPriority() throws Exception {
        Issue ipsRelease = createIssue("IPS-1550");
        Issue requirement = createIssue("REQ-170");
        Issue devSubtask = createIssue("DEV-11", "IPS Req Dev");
        Issue linkedIssue = createIssue("MU-12", "Summary", "Mandatory Update", "Open",
                null, "Done", Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList("v1"), Collections.singletonList("comp"));
        stubSearchReturns(ipsRelease);
        stubInwardLinks(ipsRelease, Collections.singletonList(requirement));
        stubSubtasks(requirement, Collections.singletonList(devSubtask));
        stubInwardLinks(devSubtask, Collections.singletonList(linkedIssue));
        stubIssueProject(linkedIssue);

        assertNull(service.generate(createRequest()).getRequirements().get(0).getDevelopment().get(0).getComponents().get(0).getIssues().get(0).getPriority());
    }

    @Test
    public void testToIssueBeanHandlesNullResolution() throws Exception {
        Issue ipsRelease = createIssue("IPS-1600");
        Issue requirement = createIssue("REQ-180");
        Issue devSubtask = createIssue("DEV-12", "IPS Req Dev");
        Issue linkedIssue = createIssue("MU-13", "Summary", "Mandatory Update", "Open",
                "High", null, Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList("v1"), Collections.singletonList("comp"));
        stubSearchReturns(ipsRelease);
        stubInwardLinks(ipsRelease, Collections.singletonList(requirement));
        stubSubtasks(requirement, Collections.singletonList(devSubtask));
        stubInwardLinks(devSubtask, Collections.singletonList(linkedIssue));
        stubIssueProject(linkedIssue);

        assertEquals("Unresolved", service.generate(createRequest()).getRequirements().get(0).getDevelopment().get(0).getComponents().get(0).getIssues().get(0).getResolution());
    }

    @Test
    public void testGenerateWithEmptyInwardIssuesCollection() throws Exception {
        Issue ipsRelease = createIssue("IPS-2200");
        stubSearchReturns(ipsRelease);
        LinkCollection linkCollection = mock(LinkCollection.class);
        when(linkCollection.getInwardIssues("Implements")).thenReturn(null);
        when(issueLinkManager.getLinkCollection(ipsRelease, mockServiceUser)).thenReturn(linkCollection);
        assertTrue(service.generate(createRequest()).getRequirements().isEmpty());
    }
}
