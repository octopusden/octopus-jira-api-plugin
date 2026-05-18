package org.octopusden.octopus.jira.api.service

import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.bc.project.component.ProjectComponent
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.CustomFieldManager
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.issuetype.IssueType
import com.atlassian.jira.issue.label.Label
import com.atlassian.jira.issue.link.IssueLinkManager
import com.atlassian.jira.issue.link.LinkCollection
import com.atlassian.jira.issue.priority.Priority
import com.atlassian.jira.issue.resolution.Resolution
import com.atlassian.jira.issue.search.SearchResults
import com.atlassian.jira.issue.status.Status
import com.atlassian.jira.jql.builder.JqlQueryBuilder
import com.atlassian.jira.project.version.Version
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.user.util.UserManager
import com.atlassian.jira.web.bean.PagerFilter
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.octopusden.octopus.jira.api.config.ApiSetting
import org.octopusden.octopus.jira.api.config.ApiSettingsProvider
import java.util.Date

class IPSServiceTest {

    private lateinit var issueLinkManager: IssueLinkManager
    private lateinit var customFieldManager: CustomFieldManager
    private lateinit var searchService: SearchService
    private lateinit var settingsProvider: ApiSettingsProvider
    private lateinit var service: IPSService

    private lateinit var mockUserManager: UserManager
    private lateinit var mockServiceUser: ApplicationUser
    private lateinit var mockProductField: CustomField
    private lateinit var mockIpsReleaseField: CustomField

    companion object {
        private const val IPS_PROJECT = "IPS"
        private const val SERVICE_USER_NAME = "svcUser"
        private const val RELEASE = "1.0"
    }

    @Before
    fun setUp() {
        issueLinkManager = mockk()
        customFieldManager = mockk()
        searchService = mockk()
        settingsProvider = mockk()
        mockUserManager = mockk()
        mockServiceUser = mockk()
        mockProductField = mockk()
        mockIpsReleaseField = mockk()

        mockkStatic(ComponentAccessor::class)
        mockkStatic(JqlQueryBuilder::class)
        mockkStatic(PagerFilter::class)

        every { settingsProvider.getString(ApiSetting.IPS_REPORTS_PROJECT) } returns IPS_PROJECT
        every { settingsProvider.getString(ApiSetting.SERVICE_USER) } returns SERVICE_USER_NAME
        every { ComponentAccessor.getUserManager() } returns mockUserManager
        every { mockUserManager.getUserByName(SERVICE_USER_NAME) } returns mockServiceUser

        every { mockProductField.idAsLong } returns 10001L
        every { mockIpsReleaseField.idAsLong } returns 10002L
        every { customFieldManager.getCustomFieldObjectsByName("Product") } returns listOf(mockProductField)
        every { customFieldManager.getCustomFieldObjectsByName("IPS Release") } returns listOf(mockIpsReleaseField)
        every { customFieldManager.getCustomFieldObjectsByName("License") } returns listOf(mockk { every { getValue(any()) } returns "Apache" })
        every { customFieldManager.getCustomFieldObjectsByName("IPS Requirement Region") } returns listOf(mockk { every { getValue(any()) } returns "EU" })
        every { customFieldManager.getCustomFieldObjectsByName("IPS Code") } returns listOf(mockk { every { getValue(any()) } returns "CODE-1" })
        every { customFieldManager.getCustomFieldObjectsByName("System") } returns listOf(mockk { every { getValue(any()) } returns "CLASSIC" })

        // JqlQueryBuilder relaxed chain
        val mockQuery = mockk<com.atlassian.query.Query>()
        val mockWhereBuilder = mockk<com.atlassian.jira.jql.builder.JqlClauseBuilder>(relaxed = true)
        val mockQueryBuilder = mockk<JqlQueryBuilder>(relaxed = true)
        every { JqlQueryBuilder.newBuilder() } returns mockQueryBuilder
        every { mockQueryBuilder.where() } returns mockWhereBuilder
        every { mockQueryBuilder.buildQuery() } returns mockQuery

        val mockPagerFilter = mockk<PagerFilter<*>>(relaxed = true)
        every { PagerFilter.getUnlimitedFilter() } returns mockPagerFilter

        // Default: no inward Implements links
        val emptyLinkCollection = mockk<LinkCollection>()
        every { emptyLinkCollection.getInwardIssues("Implements") } returns emptyList()
        every { issueLinkManager.getLinkCollection(any<Issue>(), any()) } returns emptyLinkCollection

        service = IPSService(issueLinkManager, customFieldManager, searchService, settingsProvider)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createRequest(
        release: String? = RELEASE,
        startDate: Date? = null,
        mandatory: Boolean = true
    ) = IPSRequest(
        ips = "product-1",
        ipsType = "type-A",
        release = release,
        startDate = startDate,
        system = "CLASSIC",
        clientCode = "CC-1",
        mandatory = mandatory
    )

    private fun createIssue(
        key: String = "ISSUE-1",
        summary: String = "Summary",
        typeName: String = "IPS Requirement",
        statusName: String = "Open",
        priorityName: String? = "High",
        resolutionName: String? = "Fixed",
        labels: List<String> = listOf("label1"),
        fixVersions: List<String> = listOf("v1.0"),
        componentNames: List<String> = listOf("comp1")
    ): Issue {
        val issue = mockk<Issue>(relaxed = true)
        every { issue.key } returns key
        every { issue.summary } returns summary
        every { issue.status } returns mockk<Status> { every { name } returns statusName }
        every { issue.labels } returns labels.map { mockk<Label> { every { label } returns it } }.toSet()
        every { issue.fixVersions } returns fixVersions.map { mockk<Version> { every { name } returns it } }
        every { issue.components } returns componentNames.map { mockk<ProjectComponent> { every { name } returns it } }
        every { issue.issueType } returns mockk<IssueType> { every { name } returns typeName }

        if (priorityName != null) {
            every { issue.priority } returns mockk<Priority> { every { name } returns priorityName }
        } else {
            every { issue.priority } returns null
        }
        if (resolutionName != null) {
            every { issue.resolution } returns mockk<Resolution> { every { name } returns resolutionName }
        } else {
            every { issue.resolution } returns null
        }

        return issue
    }

    private fun stubSearchReturns(vararg issues: Issue) {
        val mockSearchResults = mockk<SearchResults<Issue>>()
        every { mockSearchResults.results } returns issues.toList()
        every { searchService.search(any(), any(), any()) } returns mockSearchResults
    }

    private fun stubInwardLinks(issue: Issue, linkedIssues: List<Issue>) {
        val linkCollection = mockk<LinkCollection>()
        every { linkCollection.getInwardIssues("Implements") } returns linkedIssues
        every { issueLinkManager.getLinkCollection(issue, mockServiceUser) } returns linkCollection
    }

    private fun stubSubtasks(parent: Issue, subtasks: List<Issue>) {
        every { parent.subTaskObjects } returns subtasks
    }

    // ==================== A. Error Cases ====================

    @Test
    fun testGenerateThrowsWhenServiceUserNotFound() {
        every { mockUserManager.getUserByName(SERVICE_USER_NAME) } returns null

        val request = createRequest()
        val ex = assertThrows(IllegalStateException::class.java) { service.generate(request) }
        assertTrue(ex.message!!.contains(SERVICE_USER_NAME))
    }

    @Test
    fun testGenerateThrowsWhenProductCustomFieldNotFound() {
        every { customFieldManager.getCustomFieldObjectsByName("Product") } returns emptyList()

        val request = createRequest()
        val ex = assertThrows(IllegalStateException::class.java) { service.generate(request) }
        assertTrue(ex.message!!.contains("Product"))
    }

    @Test
    fun testGenerateThrowsWhenIpsReleaseCustomFieldNotFound() {
        every { customFieldManager.getCustomFieldObjectsByName("IPS Release") } returns emptyList()

        val request = createRequest()
        val ex = assertThrows(IllegalStateException::class.java) { service.generate(request) }
        assertTrue(ex.message!!.contains("IPS Release"))
    }

    @Test
    fun testGenerateThrowsWhenNoIpsReleasesFound() {
        stubSearchReturns()

        val request = createRequest()
        val ex = assertThrows(IllegalArgumentException::class.java) { service.generate(request) }
        assertTrue(ex.message!!.contains("No IPS Releases found"))
    }

    @Test
    fun testGenerateThrowsWhenMultipleIpsReleasesFound() {
        stubSearchReturns(createIssue(key = "IPS-1"), createIssue(key = "IPS-2"))

        val request = createRequest()
        val ex = assertThrows(IllegalArgumentException::class.java) { service.generate(request) }
        assertTrue(ex.message!!.contains("Found more than one IPS Release"))
    }

    // ==================== B. JQL Query Branching ====================

    @Test
    fun testGenerateWithReleaseParamBuildsJqlWithIpsReleaseField() {
        val ipsRelease = createIssue(key = "IPS-100", statusName = "Released", labels = listOf("ready"))
        stubSearchReturns(ipsRelease)

        val request = createRequest(release = RELEASE)
        val response = service.generate(request)

        assertEquals(RELEASE, response.release)
        assertEquals("IPS-100", response.key)
    }

    @Test
    fun testGenerateWithStartDateParamBuildsJqlWithCreatedAfter() {
        val ipsRelease = createIssue(key = "IPS-101", statusName = "Open", labels = emptyList())
        stubSearchReturns(ipsRelease)

        val request = createRequest(release = null, startDate = Date())
        val response = service.generate(request)

        assertEquals("", response.release)
        assertEquals("IPS-101", response.key)
    }

    @Test
    fun testGenerateWithNeitherReleaseNorStartDate() {
        val ipsRelease = createIssue(key = "IPS-102", statusName = "Draft", labels = listOf("draft"))
        stubSearchReturns(ipsRelease)

        val request = createRequest(release = null, startDate = null)
        val response = service.generate(request)

        assertEquals("", response.release)
    }

    // ==================== C. Happy Path — Response Structure ====================

    @Test
    fun testGenerateReturnsCorrectIpsResponseBaseFields() {
        val ipsRelease = createIssue(
            key = "IPS-200", statusName = "Released",
            labels = listOf("label1", "label2"), typeName = "IPS Release"
        )
        stubSearchReturns(ipsRelease)

        val request = createRequest()
        val response = service.generate(request)

        assertEquals("IPS-200", response.key)
        assertEquals("product-1", response.ips)
        assertEquals(RELEASE, response.release)
        assertEquals("Released", response.status)
        assertEquals(listOf("label1", "label2"), response.labels)
        assertTrue(response.requirements.isEmpty())
    }

    @Test
    fun testGenerateWithNoRequirements() {
        val ipsRelease = createIssue(key = "IPS-300")
        stubSearchReturns(ipsRelease)

        val request = createRequest()
        val response = service.generate(request)

        assertTrue(response.requirements.isEmpty())
    }

    @Test
    fun testGenerateWithRequirementsButNoSubtasks() {
        val ipsRelease = createIssue(key = "IPS-400")
        val bugIssue = createIssue(key = "BUG-1", typeName = "Bug")
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(bugIssue))

        val request = createRequest()
        val response = service.generate(request)

        assertTrue(response.requirements.isEmpty())
    }

    @Test
    fun testGenerateWithRequirementsFilteredByType() {
        val ipsRelease = createIssue(key = "IPS-500")
        val req1 = createIssue(key = "REQ-1", typeName = "IPS Requirement")
        val bug = createIssue(key = "BUG-2", typeName = "Bug")
        val req2 = createIssue(key = "REQ-2", typeName = "IPS Requirement")
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(req1, bug, req2))

        val request = createRequest()
        val response = service.generate(request)

        assertEquals(2, response.requirements.size)
        assertEquals("REQ-1", response.requirements[0].key)
        assertEquals("REQ-2", response.requirements[1].key)
    }

    // ==================== D. Requirements Processing ====================

    @Test
    fun testGeneratePopulatesRequirementFields() {
        val ipsRelease = createIssue(key = "IPS-600")
        val requirement = createIssue(key = "REQ-10", summary = "My Requirement", statusName = "In Progress", typeName = "IPS Requirement")
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))

        every { requirement.labels } returns listOf(mockk<Label> { every { label } returns "r1" }).toSet()
        every { customFieldManager.getCustomFieldObjectsByName("License") } returns listOf(mockk { every { getValue(requirement) } returns "Apache" })
        every { customFieldManager.getCustomFieldObjectsByName("IPS Requirement Region") } returns listOf(mockk { every { getValue(requirement) } returns "EU" })
        every { customFieldManager.getCustomFieldObjectsByName("IPS Code") } returns listOf(mockk { every { getValue(requirement) } returns "CODE-1" })

        val request = createRequest()
        val response = service.generate(request)

        assertEquals(1, response.requirements.size)
        val req = response.requirements[0]
        assertEquals("REQ-10", req.key)
        assertEquals("My Requirement", req.name)
        assertEquals("In Progress", req.status)
        assertEquals("EU", req.region)
        assertEquals("Apache", req.license)
        assertEquals("CODE-1", req.ipsCode)
    }

    @Test
    fun testGenerateHandlesNullCustomFieldValues() {
        val ipsRelease = createIssue(key = "IPS-700")
        val requirement = createIssue(key = "REQ-20", typeName = "IPS Requirement")
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))

        every { customFieldManager.getCustomFieldObjectsByName("License") } returns listOf(mockk { every { getValue(requirement) } returns null })
        every { customFieldManager.getCustomFieldObjectsByName("IPS Requirement Region") } returns listOf(mockk { every { getValue(requirement) } returns null })
        every { customFieldManager.getCustomFieldObjectsByName("IPS Code") } returns listOf(mockk { every { getValue(requirement) } returns null })

        val request = createRequest()
        val response = service.generate(request)

        assertEquals("", response.requirements[0].region)
        assertEquals("", response.requirements[0].license)
        assertEquals("", response.requirements[0].ipsCode)
    }

    @Test
    fun testGenerateHandlesMissingCustomFieldForRequirements() {
        val ipsRelease = createIssue(key = "IPS-750")
        val requirement = createIssue(key = "REQ-30", typeName = "IPS Requirement")
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))

        every { customFieldManager.getCustomFieldObjectsByName("IPS Requirement Region") } returns emptyList()

        val request = createRequest()
        val response = service.generate(request)

        assertEquals("", response.requirements[0].region)
    }

    // ==================== E. Dev Subtasks ====================

    @Test
    fun testGeneratePopulatesDevSubtasks() {
        val ipsRelease = createIssue(key = "IPS-800")
        val requirement = createIssue(key = "REQ-40")
        val devSubtask = createIssue(key = "DEV-1", typeName = "IPS Req Dev", summary = "Dev work", statusName = "Done")
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(devSubtask))

        val request = createRequest()
        val response = service.generate(request)

        val dev = response.requirements[0].development
        assertEquals(1, dev.size)
        assertEquals("DEV-1", dev[0].key)
        assertEquals("Dev work", dev[0].summary)
        assertEquals("Done", dev[0].status)
        assertTrue(dev[0].components.isEmpty())
    }

    @Test
    fun testGenerateIgnoresNonDevSubtaskTypes() {
        val ipsRelease = createIssue(key = "IPS-850")
        val requirement = createIssue(key = "REQ-50")
        val devSubtask = createIssue(key = "DEV-2", typeName = "IPS Req Dev")
        val taskSubtask = createIssue(key = "TASK-1", typeName = "Task")
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(devSubtask, taskSubtask))

        val request = createRequest()
        val response = service.generate(request)

        assertEquals(1, response.requirements[0].development.size)
        assertEquals("DEV-2", response.requirements[0].development[0].key)
    }

    // ==================== F. Mandatory Update Filtering ====================

    @Test
    fun testGenerateFiltersMandatoryUpdateIssuesWhenMandatoryIsTrue() {
        val ipsRelease = createIssue(key = "IPS-900")
        val requirement = createIssue(key = "REQ-60")
        val devSubtask = createIssue(key = "DEV-3", typeName = "IPS Req Dev")
        val mandatoryIssue = createIssue(key = "MU-1", typeName = "Mandatory Update", componentNames = listOf("core"))
        val bugIssue = createIssue(key = "BUG-3", typeName = "Bug", componentNames = listOf("core"))
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(devSubtask))
        stubInwardLinks(devSubtask, listOf(mandatoryIssue, bugIssue))

        val request = createRequest(mandatory = true)
        val response = service.generate(request)

        val dev = response.requirements[0].development[0]
        assertEquals(1, dev.components.size)
        assertEquals(1, dev.components[0].issues.size)
        assertEquals("MU-1", dev.components[0].issues[0].key)
    }

    @Test
    fun testGenerateIncludesAllLinkedIssueTypesWhenMandatoryIsFalse() {
        val ipsRelease = createIssue(key = "IPS-950")
        val requirement = createIssue(key = "REQ-70")
        val devSubtask = createIssue(key = "DEV-4", typeName = "IPS Req Dev")
        val mandatoryIssue = createIssue(key = "MU-2", typeName = "Mandatory Update", componentNames = listOf("core"), fixVersions = listOf("v1"))
        val bugIssue = createIssue(key = "BUG-4", typeName = "Bug", componentNames = listOf("core"), fixVersions = listOf("v2"))
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(devSubtask))
        stubInwardLinks(devSubtask, listOf(mandatoryIssue, bugIssue))

        val request = createRequest(mandatory = false)
        val response = service.generate(request)

        val dev = response.requirements[0].development[0]
        assertEquals(1, dev.components.size)
        assertEquals(2, dev.components[0].issues.size)
        assertEquals(setOf("MU-2", "BUG-4"), dev.components[0].issues.map { it.key }.toSet())
    }

    // ==================== G. Component Aggregation ====================

    @Test
    fun testGenerateAggregatesComponentsFromMultipleLinkedIssues() {
        val ipsRelease = createIssue(key = "IPS-1000")
        val requirement = createIssue(key = "REQ-80")
        val devSubtask = createIssue(key = "DEV-5", typeName = "IPS Req Dev")
        val issue1 = createIssue(key = "MU-3", typeName = "Mandatory Update", componentNames = listOf("core-module"), fixVersions = listOf("v1.0"))
        val issue2 = createIssue(key = "MU-4", typeName = "Mandatory Update", componentNames = listOf("core-module"), fixVersions = listOf("v2.0"))
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(devSubtask))
        stubInwardLinks(devSubtask, listOf(issue1, issue2))

        val request = createRequest()
        val response = service.generate(request)

        val components = response.requirements[0].development[0].components
        assertEquals(1, components.size)
        assertEquals("core-module", components[0].name)
        assertEquals(setOf("v1.0", "v2.0"), components[0].fixVersions.toSet())
        assertEquals(2, components[0].issues.size)
    }

    @Test
    fun testGenerateDeduplicatesFixVersionsPerComponent() {
        val ipsRelease = createIssue(key = "IPS-1050")
        val requirement = createIssue(key = "REQ-90")
        val devSubtask = createIssue(key = "DEV-6", typeName = "IPS Req Dev")
        val issue1 = createIssue(key = "MU-5", typeName = "Mandatory Update", componentNames = listOf("module-a"), fixVersions = listOf("v1.0"))
        val issue2 = createIssue(key = "MU-6", typeName = "Mandatory Update", componentNames = listOf("module-a"), fixVersions = listOf("v1.0"))
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(devSubtask))
        stubInwardLinks(devSubtask, listOf(issue1, issue2))

        val request = createRequest()
        val response = service.generate(request)

        val fixVersions = response.requirements[0].development[0].components[0].fixVersions
        assertEquals(1, fixVersions.size)
        assertEquals("v1.0", fixVersions[0])
    }

    @Test
    fun testGenerateSeparatesDifferentComponents() {
        val ipsRelease = createIssue(key = "IPS-1100")
        val requirement = createIssue(key = "REQ-100")
        val devSubtask = createIssue(key = "DEV-7", typeName = "IPS Req Dev")
        val issue1 = createIssue(key = "MU-7", typeName = "Mandatory Update", componentNames = listOf("module-x"), fixVersions = listOf("v1"))
        val issue2 = createIssue(key = "MU-8", typeName = "Mandatory Update", componentNames = listOf("module-y"), fixVersions = listOf("v2"))
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(devSubtask))
        stubInwardLinks(devSubtask, listOf(issue1, issue2))

        val request = createRequest()
        val response = service.generate(request)

        val components = response.requirements[0].development[0].components
        assertEquals(2, components.size)
        assertEquals("module-x", components[0].name)
        assertEquals("module-y", components[1].name)
    }

    @Test
    fun testGenerateLinkedIssueWithMultipleComponents() {
        val ipsRelease = createIssue(key = "IPS-1150")
        val requirement = createIssue(key = "REQ-110")
        val devSubtask = createIssue(key = "DEV-8", typeName = "IPS Req Dev")
        val linkedIssue = createIssue(key = "MU-9", typeName = "Mandatory Update", componentNames = listOf("comp-A", "comp-B"), fixVersions = listOf("v1"))
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(devSubtask))
        stubInwardLinks(devSubtask, listOf(linkedIssue))

        val request = createRequest()
        val response = service.generate(request)

        val components = response.requirements[0].development[0].components
        assertEquals(2, components.size)
        assertEquals("comp-A", components[0].name)
        assertEquals("comp-B", components[1].name)
        assertEquals(1, components[0].issues.size)
        assertEquals(1, components[1].issues.size)
    }

    @Test
    fun testGenerateLinkedIssueWithNoComponents() {
        val ipsRelease = createIssue(key = "IPS-1200")
        val requirement = createIssue(key = "REQ-120")
        val devSubtask = createIssue(key = "DEV-9", typeName = "IPS Req Dev")
        val linkedIssue = createIssue(key = "MU-10", typeName = "Mandatory Update", componentNames = emptyList())
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(devSubtask))
        stubInwardLinks(devSubtask, listOf(linkedIssue))

        val request = createRequest()
        val response = service.generate(request)

        assertTrue(response.requirements[0].development[0].components.isEmpty())
    }

    // ==================== H. QA Subtasks ====================

    @Test
    fun testGeneratePopulatesQaSubtasksWithTestDevelopmentIssues() {
        val ipsRelease = createIssue(key = "IPS-1300")
        val requirement = createIssue(key = "REQ-130")
        val qaSubtask = createIssue(key = "QA-1", typeName = "IPS Req QA", summary = "QA work", statusName = "To Do")
        val testDevIssue = createIssue(key = "TD-1", typeName = "Test Development")
        val bugIssue = createIssue(key = "BUG-5", typeName = "Bug")
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(qaSubtask))
        stubInwardLinks(qaSubtask, listOf(testDevIssue, bugIssue))

        val request = createRequest()
        val response = service.generate(request)

        val qa = response.requirements[0].testing
        assertEquals(1, qa.size)
        assertEquals("QA-1", qa[0].key)
        assertEquals("QA work", qa[0].summary)
        assertEquals("To Do", qa[0].status)
        assertEquals(1, qa[0].cases.size)
        assertEquals("TD-1", qa[0].cases[0].key)
    }

    @Test
    fun testGenerateQaSubtaskCasesIsIndependentOfMandatoryFlag() {
        val ipsRelease = createIssue(key = "IPS-1350")
        val requirement = createIssue(key = "REQ-140")
        val qaSubtask = createIssue(key = "QA-2", typeName = "IPS Req QA")
        val testDevIssue = createIssue(key = "TD-2", typeName = "Test Development")
        val bugIssue = createIssue(key = "BUG-6", typeName = "Bug")
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(qaSubtask))
        stubInwardLinks(qaSubtask, listOf(testDevIssue, bugIssue))

        val request = createRequest(mandatory = false)
        val response = service.generate(request)

        val cases = response.requirements[0].testing[0].cases
        assertEquals(1, cases.size)
        assertEquals("TD-2", cases[0].key)
    }

    @Test
    fun testGenerateIgnoresNonQaSubtaskTypes() {
        val ipsRelease = createIssue(key = "IPS-1400")
        val requirement = createIssue(key = "REQ-150")
        val qaSubtask = createIssue(key = "QA-3", typeName = "IPS Req QA")
        val taskSubtask = createIssue(key = "TASK-2", typeName = "Task")
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(qaSubtask, taskSubtask))

        val request = createRequest()
        val response = service.generate(request)

        assertEquals(1, response.requirements[0].testing.size)
        assertEquals("QA-3", response.requirements[0].testing[0].key)
    }

    // ==================== I. IssueBean Mapping ====================

    @Test
    fun testToIssueBeanMapsAllFieldsCorrectly() {
        val ipsRelease = createIssue(key = "IPS-1500")
        val requirement = createIssue(key = "REQ-160")
        val devSubtask = createIssue(key = "DEV-10", typeName = "IPS Req Dev")
        val linkedIssue = createIssue(
            key = "MU-11", typeName = "Mandatory Update", statusName = "Done",
            priorityName = "High", resolutionName = "Fixed",
            labels = listOf("l1"), fixVersions = listOf("v1"), componentNames = listOf("comp")
        )
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(devSubtask))
        stubInwardLinks(devSubtask, listOf(linkedIssue))

        val request = createRequest()
        val response = service.generate(request)

        val bean = response.requirements[0].development[0].components[0].issues[0]
        assertEquals("MU-11", bean.key)
        assertEquals("Done", bean.status)
        assertEquals("High", bean.priority)
        assertEquals("Fixed", bean.resolution)
        assertEquals(listOf("l1"), bean.labels)
        assertEquals(listOf("v1"), bean.fixVersions)
        assertEquals(listOf("comp"), bean.components)
    }

    @Test
    fun testToIssueBeanHandlesNullPriority() {
        val ipsRelease = createIssue(key = "IPS-1550")
        val requirement = createIssue(key = "REQ-170")
        val devSubtask = createIssue(key = "DEV-11", typeName = "IPS Req Dev")
        val linkedIssue = createIssue(key = "MU-12", typeName = "Mandatory Update", priorityName = null, componentNames = listOf("comp"))
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(devSubtask))
        stubInwardLinks(devSubtask, listOf(linkedIssue))

        val request = createRequest()
        val response = service.generate(request)

        assertNull(response.requirements[0].development[0].components[0].issues[0].priority)
    }

    @Test
    fun testToIssueBeanHandlesNullResolution() {
        val ipsRelease = createIssue(key = "IPS-1600")
        val requirement = createIssue(key = "REQ-180")
        val devSubtask = createIssue(key = "DEV-12", typeName = "IPS Req Dev")
        val linkedIssue = createIssue(key = "MU-13", typeName = "Mandatory Update", resolutionName = null, componentNames = listOf("comp"))
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(devSubtask))
        stubInwardLinks(devSubtask, listOf(linkedIssue))

        val request = createRequest()
        val response = service.generate(request)

        assertEquals("Unresolved", response.requirements[0].development[0].components[0].issues[0].resolution)
    }

    @Test
    fun testToIssueBeanHandlesNullIssueType() {
        val ipsRelease = createIssue(key = "IPS-1650")
        val requirement = createIssue(key = "REQ-190")
        val devSubtask = createIssue(key = "DEV-13", typeName = "IPS Req Dev")
        val linkedIssue = createIssue(key = "MU-14", typeName = "Bug", componentNames = listOf("comp"))

        // Override issueType to be null on the linked issue after creation
        every { linkedIssue.issueType } returns null

        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(devSubtask))
        stubInwardLinks(devSubtask, listOf(linkedIssue))

        // Use mandatory=false to bypass the type filter so toIssueBean is called
        val request = createRequest(mandatory = false)
        val response = service.generate(request)

        assertEquals("", response.requirements[0].development[0].components[0].issues[0].issueType)
    }

    @Test
    fun testToIssueBeanHandlesNullSummary() {
        val ipsRelease = createIssue(key = "IPS-1700")
        val requirement = createIssue(key = "REQ-200")
        val devSubtask = createIssue(key = "DEV-14", typeName = "IPS Req Dev")
        val linkedIssue = createIssue(key = "MU-15", typeName = "Mandatory Update", summary = "", componentNames = listOf("comp"))

        every { linkedIssue.summary } returns null

        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(devSubtask))
        stubInwardLinks(devSubtask, listOf(linkedIssue))

        val request = createRequest()
        val response = service.generate(request)

        assertEquals("", response.requirements[0].development[0].components[0].issues[0].summary)
    }

    // ==================== J. Complex Scenarios ====================

    @Test
    fun testGenerateFullScenarioWithMultipleRequirementsDevAndQaSubtasks() {
        val ipsRelease = createIssue(key = "IPS-2000")
        val req1 = createIssue(key = "REQ-300")
        val req2 = createIssue(key = "REQ-310")
        val dev1 = createIssue(key = "DEV-20", typeName = "IPS Req Dev")
        val qa1 = createIssue(key = "QA-10", typeName = "IPS Req QA")
        val dev2 = createIssue(key = "DEV-21", typeName = "IPS Req Dev")
        val mu1 = createIssue(key = "MU-20", typeName = "Mandatory Update", componentNames = listOf("core"), fixVersions = listOf("v1"))
        val mu2 = createIssue(key = "MU-21", typeName = "Mandatory Update", componentNames = listOf("core"), fixVersions = listOf("v2"))
        val testDev = createIssue(key = "TD-10", typeName = "Test Development")

        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(req1, req2))
        stubSubtasks(req1, listOf(dev1, qa1))
        stubSubtasks(req2, listOf(dev2))
        stubInwardLinks(dev1, listOf(mu1, mu2))
        stubInwardLinks(qa1, listOf(testDev))
        stubInwardLinks(dev2, emptyList())

        val request = createRequest()
        val response = service.generate(request)

        assertEquals(2, response.requirements.size)

        // REQ-1: 1 dev, 1 qa
        val r1 = response.requirements[0]
        assertEquals("REQ-300", r1.key)
        assertEquals(1, r1.development.size)
        assertEquals(1, r1.testing.size)
        assertEquals(1, r1.development[0].components.size)
        assertEquals("core", r1.development[0].components[0].name)
        assertEquals(setOf("v1", "v2"), r1.development[0].components[0].fixVersions.toSet())
        assertEquals(1, r1.testing[0].cases.size)
        assertEquals("TD-10", r1.testing[0].cases[0].key)

        // REQ-2: 1 dev, 0 qa
        val r2 = response.requirements[1]
        assertEquals("REQ-310", r2.key)
        assertEquals(1, r2.development.size)
        assertTrue(r2.testing.isEmpty())
    }

    @Test
    fun testGenerateMultipleRequirementsSomeFilteredByType() {
        val ipsRelease = createIssue(key = "IPS-2050")
        val req1 = createIssue(key = "REQ-400", typeName = "IPS Requirement")
        val bug = createIssue(key = "BUG-10", typeName = "Bug")
        val req2 = createIssue(key = "REQ-410", typeName = "IPS Requirement")
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(req1, bug, req2))

        val request = createRequest()
        val response = service.generate(request)

        assertEquals(2, response.requirements.size)
        assertEquals("REQ-400", response.requirements[0].key)
        assertEquals("REQ-410", response.requirements[1].key)
    }

    // ==================== K. Edge Cases ====================

    @Test
    fun testGenerateWithNullLabelsOnIssue() {
        val ipsRelease = createIssue(key = "IPS-2100", labels = emptyList())
        stubSearchReturns(ipsRelease)

        val request = createRequest()
        val response = service.generate(request)

        assertTrue(response.labels.isEmpty())
    }

    @Test
    fun testGenerateWithNullFixVersionsOnLinkedIssue() {
        val ipsRelease = createIssue(key = "IPS-2150")
        val requirement = createIssue(key = "REQ-500")
        val devSubtask = createIssue(key = "DEV-30", typeName = "IPS Req Dev")
        val linkedIssue = createIssue(key = "MU-30", typeName = "Mandatory Update", fixVersions = emptyList(), componentNames = listOf("comp"))
        stubSearchReturns(ipsRelease)
        stubInwardLinks(ipsRelease, listOf(requirement))
        stubSubtasks(requirement, listOf(devSubtask))
        stubInwardLinks(devSubtask, listOf(linkedIssue))

        val request = createRequest()
        val response = service.generate(request)

        assertTrue(response.requirements[0].development[0].components[0].fixVersions.isEmpty())
    }

    @Test
    fun testGenerateWithEmptyInwardIssuesCollection() {
        val ipsRelease = createIssue(key = "IPS-2200")
        stubSearchReturns(ipsRelease)

        val linkCollection = mockk<LinkCollection>()
        every { linkCollection.getInwardIssues("Implements") } returns null
        every { issueLinkManager.getLinkCollection(ipsRelease, mockServiceUser) } returns linkCollection

        val request = createRequest()
        val response = service.generate(request)

        assertTrue(response.requirements.isEmpty())
    }
}
