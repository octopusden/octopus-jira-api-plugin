package org.octopusden.octopus.jira.api.client.impl

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.octopusden.octopus.jira.api.config.JacksonMapper
import kotlin.test.assertNull

class ClassicJiraApiClientTest {

    private lateinit var objectMapper: ObjectMapper
    private lateinit var parametersProvider: JiraApiClientParametersProvider

    @Before
    fun setup() {
        objectMapper = JacksonMapper.create()
        parametersProvider = mockk(relaxed = true)
        every { parametersProvider.getApiUrl() } returns "http://localhost:8080"
    }

    @Test
    fun `getAuthHeader returns Bearer token when token is present`() {
        every { parametersProvider.getBearerToken() } returns "my-token"
        val client = ClassicJiraApiClient(parametersProvider, objectMapper)

        val authHeader = invokeGetAuthHeader(client)

        assertEquals("Bearer my-token", authHeader)
    }

    @Test
    fun `getAuthHeader returns Basic credentials when token is null`() {
        every { parametersProvider.getBearerToken() } returns null
        every { parametersProvider.getBasicCredentials() } returns "user:pass"
        val client = ClassicJiraApiClient(parametersProvider, objectMapper)

        val authHeader = invokeGetAuthHeader(client)!!

        assertTrue(authHeader.startsWith("Basic "))
        val decoded = java.util.Base64.getDecoder().decode(authHeader.removePrefix("Basic "))
        assertEquals("user:pass", String(decoded))
    }

    @Test
    fun `Bearer token takes precedence over basic credentials`() {
        every { parametersProvider.getBearerToken() } returns "bearer-token"
        every { parametersProvider.getBasicCredentials() } returns "user:pass"
        val client = ClassicJiraApiClient(parametersProvider, objectMapper)

        val authHeader = invokeGetAuthHeader(client)

        assertEquals("Bearer bearer-token", authHeader)
    }

    @Test
    fun `getAuthHeader when both token and credentials are null`() {
        // Stub to allow constructor to succeed (Feign builder doesn't trigger interceptor until first request),
        // then set both to null before calling getAuthHeader via reflection.
        every { parametersProvider.getBearerToken() } returns "temp"
        val client = ClassicJiraApiClient(parametersProvider, objectMapper)
        // Now override to null — the Feign interceptor won't fire until an actual HTTP call
        every { parametersProvider.getBearerToken() } returns null
        every { parametersProvider.getBasicCredentials() } returns null
        assertNull(invokeGetAuthHeader(client))
    }

    @Test
    fun `getAuthHeader when both are blank`() {
        every { parametersProvider.getBearerToken() } returns "temp"
        val client = ClassicJiraApiClient(parametersProvider, objectMapper)
        every { parametersProvider.getBearerToken() } returns ""
        every { parametersProvider.getBasicCredentials() } returns ":"
        assertNull(invokeGetAuthHeader(client))
    }

    private fun invokeGetAuthHeader(client: ClassicJiraApiClient): String? {
        val method = ClassicJiraApiClient::class.java.getDeclaredMethod("getAuthHeader")
        method.isAccessible = true
        try {
            return method.invoke(client) as? String
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.cause ?: e
        }
    }
}
