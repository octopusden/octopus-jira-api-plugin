package org.octopusden.octopus.jira.api.dto

data class IPSResponse(
    val key: String,
    val ips: String,
    val release: String,
    val status: String,
    val labels: List<String> = emptyList(),
    val requirements: List<IPSRequirement> = emptyList()
)
