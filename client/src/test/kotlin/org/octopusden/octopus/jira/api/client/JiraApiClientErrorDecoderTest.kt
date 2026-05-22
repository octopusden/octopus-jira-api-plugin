package org.octopusden.octopus.jira.api.client

import com.fasterxml.jackson.databind.ObjectMapper
import feign.FeignException
import feign.Response
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.octopusden.octopus.jira.api.config.JacksonMapper
import org.octopusden.octopus.jira.api.exception.BadRequestException
import org.octopusden.octopus.jira.api.exception.FailedGenerateIPSException
import java.nio.charset.StandardCharsets

class JiraApiClientErrorDecoderTest {

    private lateinit var objectMapper: ObjectMapper
    private lateinit var decoder: JiraApiClientErrorDecoder

    @Before
    fun setup() {
        objectMapper = JacksonMapper.create()
        decoder = JiraApiClientErrorDecoder(objectMapper)
    }

    @Test
    fun `decode with known code API-40000 should return BadRequestException`() {
        val json = """{"code":"API-40000","detail":"detail","message":"bad request"}"""
        val response = createJsonResponse(json, 400)

        val exception = decoder.decode("test", response)

        assertTrue(exception is BadRequestException)
        assertEquals("bad request", exception.message)
    }

    @Test
    fun `decode with known code API-40001 should return FailedGenerateIPSException`() {
        val json = """{"code":"API-40001","detail":"detail","message":"failed to generate"}"""
        val response = createJsonResponse(json, 500)

        val exception = decoder.decode("test", response)

        assertTrue(exception is FailedGenerateIPSException)
        assertEquals("failed to generate", exception.message)
    }

    @Test
    fun `decode with unknown code should return RuntimeException`() {
        val json = """{"code":"API-99999","detail":"detail","message":"unknown error"}"""
        val response = createJsonResponse(json, 500)

        val exception = decoder.decode("test", response)

        assertTrue(exception is RuntimeException)
        assertEquals("unknown error", exception.message)
    }

    @Test
    fun `decode with non-JSON content type should fall back to default`() {
        val response = createResponse(
            "<html><body>Internal Server Error</body></html>",
            500,
            "text/html"
        )

        val exception = decoder.decode("test", response)

        assertTrue(exception is FeignException)
    }

    private fun createJsonResponse(json: String, status: Int): Response {
        return createResponse(json, status, "application/json")
    }

    private fun createResponse(body: String, status: Int, contentType: String): Response {
        return Response.builder()
            .status(status)
            .headers(mapOf("Content-Type" to listOf(contentType)))
            .body(body.toByteArray(StandardCharsets.UTF_8))
            .request(mockk(relaxed = true))
            .build()
    }
}
