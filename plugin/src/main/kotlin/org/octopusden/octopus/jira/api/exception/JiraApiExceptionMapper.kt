package org.octopusden.octopus.jira.api.exception

import com.fasterxml.jackson.databind.ObjectMapper

import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

@Provider
class JiraApiExceptionMapper(private val objectMapper: ObjectMapper) : ExceptionMapper<JiraApiException> {

    override fun toResponse(ex: JiraApiException): Response {
        val status = when (ex) {
            is BadRequestException -> Response.Status.BAD_REQUEST
            is FailedGenerateIPSException -> Response.Status.INTERNAL_SERVER_ERROR
            else -> Response.Status.INTERNAL_SERVER_ERROR
        }
        val body = mapOf("code" to ex.code, "message" to (ex.message ?: "Unknown error"))
        return Response.status(status.statusCode)
            .entity(objectMapper.writeValueAsString(body))
            .type(MediaType.APPLICATION_JSON)
            .build()
    }
}
