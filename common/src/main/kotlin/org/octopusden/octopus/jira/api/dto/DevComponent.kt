package org.octopusden.octopus.jira.api.dto

data class DevComponent(
    val name: String,
    val fixVersions: MutableList<String> = mutableListOf(),
    val issues: MutableList<IssueBean> = mutableListOf()
)
