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
import org.octopusden.octopus.jira.api.dto.IPSResponse
import org.octopusden.octopus.jira.api.dto.IPSReqDev
import org.octopusden.octopus.jira.api.dto.IPSReqQA
import org.octopusden.octopus.jira.api.dto.IPSRequirement
import org.octopusden.octopus.jira.api.dto.IPSRequest
import org.octopusden.octopus.jira.api.dto.IssueBean
import com.atlassian.jira.component.ComponentAccessor
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

    fun generate(request: IPSRequest): IPSResponse {
        val serviceUser = getServiceUser()
        val ipsProject = settingsProvider.getString(ApiSetting.IPS_REPORTS_PROJECT)

        val ipsReleaseIssue =
            getIPSReleaseIssue(request.ips, request.release, request.startDate, ipsProject, serviceUser)
        logger.info("Found IPS Release issue ${ipsReleaseIssue.key} for ${request.ips}:${request.release}")

        val response = IPSResponse(
            key = ipsReleaseIssue.key,
            ips = request.ips,
            release = request.release ?: "",
            status = ipsReleaseIssue.status.name,
            labels = ipsReleaseIssue.labels.map { it.label }.toMutableList()
        )

        getInwardImplementsIssues(ipsReleaseIssue, serviceUser)
            .filter { it.issueType?.name == "IPS Requirement" }
            .forEach { requirement ->
                logger.debug("Processing IPS Requirement ${requirement.key}")

                val ipsRequirement = IPSRequirement(
                    key = requirement.key,
                    name = requirement.summary ?: "",
                    status = requirement.status.name,
                    labels = requirement.labels.map { it.label }.toMutableList(),
                    region = getCustomFieldStringValue(FIELD_IPS_REQUIREMENT_REGION, requirement),
                    license = getCustomFieldStringValue(FIELD_LICENSE, requirement),
                    ipsCode = getCustomFieldStringValue(FIELD_IPS_CODE, requirement)
                )
                response.requirements.add(ipsRequirement)

                // Development subtasks: IPS Req Dev
                requirement.subTaskObjects.filter { it.issueType?.name == IPS_REQ_DEV_TYPE }
                    .forEach { devSubtask ->
                        logger.debug("Processing IPS Req Dev ${devSubtask.key}")
                        val ipsReqDev = IPSReqDev(
                            key = devSubtask.key,
                            summary = devSubtask.summary ?: "",
                            status = devSubtask.status.name,
                            labels = devSubtask.labels.map { it.label }.toList(),
                            license = getCustomFieldStringValue(FIELD_LICENSE, devSubtask),
                            system = getCustomFieldValueAsStringList(FIELD_SYSTEM, devSubtask)
                        )
                        if (ipsReqDev.system.emptyOrContains(request.system)) {
                            ipsRequirement.development.add(ipsReqDev)
                            // Mandatory Update issues linked to the Dev subtask
                            getInwardImplementsIssues(devSubtask, serviceUser).let {
                                if (request.mandatory) it.filter { issue ->
                                    issue.issueType?.name == MANDATORY_UPDATE_TYPE
                                } else it
                            }.forEach { issue ->
                                val issueBean = toIssueBean(issue)
                                val versionNames = issue.fixVersions.map { it.name }
                                issue.components.forEach { jiraComponent ->
                                    val comp = ipsReqDev.components.find { it.name == jiraComponent.name }
                                        ?: DevComponent(name = jiraComponent.name).also {
                                            ipsReqDev.components.add(it)
                                        }
                                    versionNames.forEach { v -> if (v !in comp.fixVersions) comp.fixVersions.add(v) }
                                    comp.issues.add(issueBean)
                                }
                            }
                        }
                    }

                // Testing subtasks: IPS Req QA
                requirement.subTaskObjects.filter { it.issueType?.name == IPS_REQ_QA_TYPE }
                    .forEach { qaSubtask ->
                        logger.debug("Processing IPS Req QA ${qaSubtask.key}")
                        val ipsReqQA = IPSReqQA(
                            key = qaSubtask.key,
                            summary = qaSubtask.summary ?: "",
                            status = qaSubtask.status.name,
                            system = getCustomFieldValueAsStringList(FIELD_SYSTEM, qaSubtask)
                        )
                        if (ipsReqQA.system.emptyOrContains(request.system)) {
                            ipsRequirement.testing.add(ipsReqQA)
                            // Test Development issues linked to the QA subtask
                            getInwardImplementsIssues(qaSubtask, serviceUser)
                                .filter { it.issueType?.name == TEST_DEVELOPMENT_TYPE }
                                .forEach { testIssue -> ipsReqQA.cases.add(toIssueBean(testIssue)) }
                        }
                    }
            }

        return response
    }

    private fun List<String>.emptyOrContains(string: String) =
        string.isEmpty() || this.isEmpty() || this.any { it == string }

    private fun getIPSReleaseIssue(
        ips: String,
        release: String?,
        startDate: java.util.Date?,
        ipsProject: String,
        user: ApplicationUser
    ): Issue {
        val productField = customFieldManager.getCustomFieldObjectsByName(FIELD_PRODUCT).firstOrNull()
            ?: throw IllegalStateException("Custom field '$FIELD_PRODUCT' not found")
        val ipsReleaseField = customFieldManager.getCustomFieldObjectsByName(FIELD_IPS_RELEASE).firstOrNull()
            ?: throw IllegalStateException("Custom field '$FIELD_IPS_RELEASE' not found")

        val queryBuilder = JqlQueryBuilder.newBuilder().where()
            .project(ipsProject).and()
            .issueType(IPS_RELEASE_TYPE).and()
            .customField(productField.idAsLong).eq(ips)

        if (release != null) {
            queryBuilder.and().customField(ipsReleaseField.idAsLong).eq(release)
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

    private fun getCustomFieldStringValue(fieldName: String, issue: Issue): String {
        val field = customFieldManager.getCustomFieldObjectsByName(fieldName).firstOrNull()
            ?: return ""
        return field.getValue(issue)?.toString() ?: ""
    }

    private fun getCustomFieldValueAsStringList(fieldName: String, issue: Issue): List<String> {
        val field = customFieldManager.getCustomFieldObjectsByName(fieldName).firstOrNull()
            ?: return emptyList()
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
