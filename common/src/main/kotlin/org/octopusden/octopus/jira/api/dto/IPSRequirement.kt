package org.octopusden.octopus.jira.api.dto

data class IPSRequirement(
    val key: String,
    val name: String,
    val status: String,
    val labels: MutableList<String> = mutableListOf(),
    val region: String,
    val license: String,
    val ipsCode: String,
    val development: MutableList<IPSReqDev> = mutableListOf(),
    val testing: MutableList<IPSReqQA> = mutableListOf()
)
