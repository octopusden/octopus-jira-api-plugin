package org.octopusden.octopus.jira.api.dto

data class IPSReqDev(
    val key: String,
    val summary: String,
    val status: String,
    val labels: List<String> = listOf(),
    val license: String,
    val system: List<String> = listOf(),
    val components: List<DevComponent> = emptyList()
)
