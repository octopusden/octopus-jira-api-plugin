package org.octopusden.octopus.jira.api.dto

data class IssueBean(
    val key: String,
    val summary: String,
    val issueType: String,
    val status: String,
    val priority: String?,
    val labels: List<String>,
    val fixVersions: List<String>,
    val components: List<String>,
    val resolution: String
)
