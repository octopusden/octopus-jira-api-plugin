package org.octopusden.octopus.jira.api.dto

data class IPSReqQA(
    val key: String,
    val summary: String,
    val status: String,
    val system: List<String> = listOf(),
    val cases: MutableList<IssueBean> = mutableListOf()
)
