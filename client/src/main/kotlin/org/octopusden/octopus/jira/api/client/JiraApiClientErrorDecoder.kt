package org.octopusden.octopus.jira.api.client

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import feign.Response
import feign.codec.ErrorDecoder
import org.octopusden.octopus.jira.api.exception.JiraApiException

class JiraApiClientErrorDecoder(private val objectMapper: ObjectMapper) : ErrorDecoder.Default() {
    override fun decode(methodKey: String?, response: Response?): Exception =
        response?.let {
            val responseBody = it.body()?.asInputStream()?.use { inputStream ->
                String(inputStream.readBytes())
            }
            if (responseBody != null && it.headers()["Content-Type"]?.contains("application/json") == true) {
                try {
                    val error = objectMapper.readValue(responseBody, ApplicationErrorResponse::class.java)
                    return@let JiraApiException.CODE_EXCEPTION_MAP[error.code]?.invoke(error.message)
                        ?: RuntimeException(error.message)
                } catch (_: JsonProcessingException) {
                }
            }
            return@let super.decode(methodKey, it)
        } ?: super.decode(methodKey, null)

}

data class ApplicationErrorResponse(val code: String, val message: String, val detail: String? = "")