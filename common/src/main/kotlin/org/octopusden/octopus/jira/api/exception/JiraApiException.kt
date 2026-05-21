package org.octopusden.octopus.jira.api.exception

abstract class JiraApiException(message: String, val code: String) : RuntimeException(message) {
    companion object {
        val CODE_EXCEPTION_MAP = mapOf(
            "API-40000" to { message: String -> BadRequestException(message) },
            "API-40001" to { message: String -> FailedGenerateIPSException(message) },
        )
    }
}

class BadRequestException(message: String) : JiraApiException(message, "API-40000")
class FailedGenerateIPSException(message: String) : JiraApiException(message, "API-40001")
