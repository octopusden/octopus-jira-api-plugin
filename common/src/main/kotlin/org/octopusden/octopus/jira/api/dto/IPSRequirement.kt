package org.octopusden.octopus.jira.api.dto

data class IPSRequirement(
    val key: String,
    val name: String,
    val status: String,
    val labels: List<String> = emptyList(),
    val region: String,
    val license: String,
    val ipsCode: String,
    val development: List<IPSReqDev> = emptyList(),
    val testing: List<IPSReqQA> = emptyList()
)
