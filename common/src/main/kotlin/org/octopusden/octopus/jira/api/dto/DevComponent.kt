package org.octopusden.octopus.jira.api.dto

data class DevComponent(
    val name: String,
    val fixVersions: List<String> = emptyList(),
    val issues: List<IssueBean> = emptyList()
)
