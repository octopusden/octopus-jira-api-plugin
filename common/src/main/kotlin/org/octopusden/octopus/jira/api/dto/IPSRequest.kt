package org.octopusden.octopus.jira.api.dto

import java.util.Date

data class IPSRequest(
    val ips: String,
    val release: String? = null,
    val startDate: Date? = null,
    val system: String,
    val mandatory: Boolean = true
)
