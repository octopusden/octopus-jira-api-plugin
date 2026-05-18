package org.octopusden.octopus.jira.api.dto

data class IPSReqDev(
    val key: String,
    val summary: String,
    val status: String,
    val labels: MutableList<String> = mutableListOf(),
    val license: String,
    val system: String,
    val components: MutableList<DevComponent> = mutableListOf()
)
