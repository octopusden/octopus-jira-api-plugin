package org.octopusden.octopus.jira.api.service

import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.issue.CustomFieldManager
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.link.IssueLinkManager
import com.atlassian.jira.jql.builder.JqlQueryBuilder
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.web.bean.PagerFilter
import org.octopusden.octopus.jira.api.config.ApiSetting
import org.octopusden.octopus.jira.api.config.ApiSettingsProvider
import org.octopusden.octopus.jira.api.dto.DevComponent
import org.octopusden.octopus.jira.api.dto.IPSReqDev
import org.octopusden.octopus.jira.api.dto.IPSReqQA
import org.octopusden.octopus.jira.api.dto.IPSRequirement
import org.octopusden.octopus.jira.api.dto.IPSRequest
import org.octopusden.octopus.jira.api.dto.IPSResponse
import org.octopusden.octopus.jira.api.dto.IssueBean
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.fields.CustomField
import org.slf4j.LoggerFactory

class IPSService(
    private val issueLinkManager: IssueLinkManager,
    private val customFieldManager: CustomFieldManager,
    private val searchService: SearchService,
    private val settingsProvider: ApiSettingsProvider
) {
    companion object {
        private val logger = LoggerFactory.getLogger(IPSService::class.java)

        private const val IPS_REQ_DEV_TYPE = "IPS Req Dev"
        private const val IPS_REQ_QA_TYPE = "IPS Req QA"
        private const val IPS_RELEASE_TYPE = "IPS Release"
        private const val MANDATORY_UPDATE_TYPE = "Mandatory Update"

        private const val TEST_DEVELOPMENT_TYPE = "Test Development"

        private const val LINK_TYPE_IMPLEMENTS = "Implements"
        private const val FIELD_PRODUCT = "Product"
        private const val FIELD_IPS_RELEASE = "IPS Release"
        private const val FIELD_LICENSE = "License"
        private const val FIELD_IPS_REQUIREMENT_REGION = "IPS Requirement Region"
        private const val FIELD_IPS_CODE = "IPS Code"
        private const val FIELD_SYSTEM = "System"
    }

    private val fieldProduct = customFieldManager.getCustomFieldObjectsByName(FIELD_PRODUCT).firstOrNull()
        ?: throw IllegalStateException("Custom field '$FIELD_PRODUCT' not found")
    private val fieldIpsRelease = customFieldManager.getCustomFieldObjectsByName(FIELD_IPS_RELEASE).firstOrNull()
        ?: throw IllegalStateException("Custom field '$FIELD_IPS_RELEASE' not found")
    private val fieldLicense = customFieldManager.getCustomFieldObjectsByName(FIELD_LICENSE).firstOrNull()
    private val fieldIpsRequirementRegion = customFieldManager.getCustomFieldObjectsByName(FIELD_IPS_REQUIREMENT_REGION).firstOrNull()
    private val fieldIpsCode = customFieldManager.getCustomFieldObjectsByName(FIELD_IPS_CODE).firstOrNull()
    private val fieldSystem = customFieldManager.getCustomFieldObjectsByName(FIELD_SYSTEM).firstOrNull()

    fun generate(request: IPSRequest): IPSResponse {
        val serviceUser = getServiceUser()
        val ipsProject = settingsProvider.getString(ApiSetting.IPS_REPORTS_PROJECT)


        val ipsReleaseIssue =
            getIPSReleaseIssue(request.ips, request.release, request.startDate, ipsProject, serviceUser)
        logger.info("Found IPS Release issue ${ipsReleaseIssue.key} for ${request.ips}:${request.release}")

        val requirements = getInwardImplementsIssues(ipsReleaseIssue, serviceUser)
            .filter { it.issueType?.name == "IPS Requirement" }
            .map { requirement ->
                logger.debug("Processing IPS Requirement ${requirement.key}")

                val development = requirement.subTaskObjects
                    .filter { it.issueType?.name == IPS_REQ_DEV_TYPE }
                    .mapNotNull { devSubtask -> buildDevSubtask(devSubtask, request, serviceUser) }

                val testing = requirement.subTaskObjects
                    .filter { it.issueType?.name == IPS_REQ_QA_TYPE }
                    .mapNotNull { qaSubtask -> buildQASubtask(qaSubtask, request, serviceUser) }

                IPSRequirement(
                    key = requirement.key,
                    name = requirement.summary ?: "",
                    status = requirement.status.name,
                    labels = requirement.labels.map { it.label },
                    region = getCustomFieldStringValue(fieldIpsRequirementRegion, requirement),
                    license = getCustomFieldStringValue(fieldLicense, requirement),
                    ipsCode = getCustomFieldStringValue(fieldIpsCode, requirement),
                    development = development,
                    testing = testing
                )
            }

        return IPSResponse(
            key = ipsReleaseIssue.key,
            ips = request.ips,
            release = request.release ?: "",
            status = ipsReleaseIssue.status.name,
            labels = ipsReleaseIssue.labels.map { it.label },
            requirements = requirements
        )
    }

    private fun buildDevSubtask(devSubtask: Issue, request: IPSRequest, serviceUser: ApplicationUser): IPSReqDev? {
        logger.debug("Processing IPS Req Dev ${devSubtask.key}")
        val system = getCustomFieldValueAsStringList(fieldSystem, devSubtask)
        if (!system.emptyOrContains(request.system)) return null

        val versionToIssues = getInwardImplementsIssues(devSubtask, serviceUser).let { issues ->
            if (request.mandatory) issues.filter { it.issueType?.name == MANDATORY_UPDATE_TYPE } else issues
        }.flatMap { issue ->
            val issueBean = toIssueBean(issue)
            val versionNames = issue.fixVersions.map { it.name }
            val versions = if (versionNames.isEmpty()) listOf("") else versionNames
            issue.components.flatMap { jiraComponent ->
                versions.map { version -> Triple(jiraComponent.name, version, issueBean) }
            }
        }

        val components = versionToIssues
            .groupBy({ it.first }) { it.second to it.third }
            .map { (compName, pairs) ->
                DevComponent(
                    name = compName,
                    fixVersions = pairs.map { it.first }.filter { it.isNotEmpty() }.distinct(),
                    issues = pairs.map { it.second }
                )
            }

        return IPSReqDev(
            key = devSubtask.key,
            summary = devSubtask.summary ?: "",
            status = devSubtask.status.name,
            labels = devSubtask.labels.map { it.label },
            license = getCustomFieldStringValue(fieldLicense, devSubtask),
            system = system,
            components = components
        )
    }

    private fun buildQASubtask(qaSubtask: Issue, request: IPSRequest, serviceUser: ApplicationUser): IPSReqQA? {
        logger.debug("Processing IPS Req QA ${qaSubtask.key}")
        val system = getCustomFieldValueAsStringList(fieldSystem, qaSubtask)
        if (!system.emptyOrContains(request.system)) return null

        return IPSReqQA(
            key = qaSubtask.key,
            summary = qaSubtask.summary ?: "",
            status = qaSubtask.status.name,
            system = system,
            cases = getInwardImplementsIssues(qaSubtask, serviceUser)
                .filter { it.issueType?.name == TEST_DEVELOPMENT_TYPE }
                .map { testIssue -> toIssueBean(testIssue) }
        )
    }

    private fun List<String>.emptyOrContains(string: String) =
        string.isEmpty() || this.isEmpty() || this.any { it.equals(string, ignoreCase = true) }

    private fun getIPSReleaseIssue(
        ips: String,
        release: String?,
        startDate: java.util.Date?,
        ipsProject: String,
        user: ApplicationUser
    ): Issue {
        val queryBuilder = JqlQueryBuilder.newBuilder().where()
            .project(ipsProject).and()
            .issueType(IPS_RELEASE_TYPE).and()
            .customField(fieldProduct.idAsLong).eq(ips)

        if (release != null) {
            queryBuilder.and().customField(fieldIpsRelease.idAsLong).eq(release)
        } else if (startDate != null) {
            queryBuilder.and().createdAfter(startDate)
        }

        val query = queryBuilder.buildQuery()
        val issues = searchService.search(user, query, PagerFilter.getUnlimitedFilter()).results ?: emptyList()

        return when (issues.size) {
            0 -> throw IllegalArgumentException("No IPS Releases found for $ips:$release")
            1 -> issues.single()
            else -> throw IllegalArgumentException(
                "Found more than one IPS Release for $ips:$release (${issues.map { it.key }})"
            )
        }
    }

    private fun getInwardImplementsIssues(issue: Issue, user: ApplicationUser): List<Issue> {
        return issueLinkManager.getLinkCollection(issue, user)
            .getInwardIssues(LINK_TYPE_IMPLEMENTS)
            ?: emptyList()
    }

    private fun getCustomFieldStringValue(field: CustomField?, issue: Issue): String {
        return field?.getValue(issue)?.toString() ?: ""
    }

    private fun getCustomFieldValueAsStringList(field: CustomField?, issue: Issue): List<String> {
        if(field == null) return emptyList()
        return when (val value = issue.getCustomFieldValue(field)) {
            is List<*> -> value.map(Any?::toString)
            else -> emptyList()
        }
    }

    private fun toIssueBean(issue: Issue) = IssueBean(
        key = issue.key,
        summary = issue.summary ?: "",
        issueType = issue.issueType?.name ?: "",
        status = issue.status.name,
        priority = issue.priority?.name,
        labels = issue.labels.map { it.label },
        fixVersions = issue.fixVersions.map { it.name },
        components = issue.components.map { it.name },
        resolution = issue.resolution?.name ?: "Unresolved"
    )

    private fun getServiceUser(): ApplicationUser {
        val userName = settingsProvider.getString(ApiSetting.SERVICE_USER)
        return ComponentAccessor.getUserManager().getUserByName(userName)
            ?: throw IllegalStateException("Service user '$userName' not found")
    }
}
