package org.octopusden.octopus.jira.api.client.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import feign.Feign
import feign.Logger
import feign.Request
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder
import feign.slf4j.Slf4jLogger
import org.apache.http.HttpHeaders
import java.util.Base64
import org.octopusden.octopus.jira.api.client.JiraApiClient
import org.octopusden.octopus.jira.api.client.JiraApiClientErrorDecoder
import java.util.concurrent.TimeUnit

class ClassicJiraApiClient(
    private val parametersProvider: JiraApiClientParametersProvider, private val objectMapper: ObjectMapper
) : JiraApiClient {
    private val client = createClient(parametersProvider)

    constructor(parametersProvider: JiraApiClientParametersProvider) : this(
        parametersProvider, getMapper()
    )

    override fun getIps(ips: String, type: String, release: String, system: String, mandatory: Boolean) =
        client.getIps(ips, type, release, system, mandatory)

    private fun createClient(parametersProvider: JiraApiClientParametersProvider): JiraApiClient {
        return Feign.builder()
            .options(Request.Options(1, TimeUnit.MINUTES, 5, TimeUnit.MINUTES, true))
            .encoder(JacksonEncoder(objectMapper))
            .decoder(JacksonDecoder(objectMapper))
            .errorDecoder(JiraApiClientErrorDecoder(objectMapper)).requestInterceptor { requestTemplate ->
                val authHeader = getAuthHeader()
                requestTemplate.header(HttpHeaders.AUTHORIZATION, authHeader)
            }.logger(Slf4jLogger(JiraApiClient::class.java)).logLevel(Logger.Level.BASIC)
            .target(JiraApiClient::class.java, parametersProvider.getApiUrl())
    }

    private fun getAuthHeader(): String {
        val authHeader = parametersProvider.getBearerToken()?.let { token ->
            if (token.isNotBlank()) {
                "Bearer $token"
            } else {
                null
            }
        } ?: parametersProvider.getBasicCredentials()?.let { basicCredentials ->
            if (basicCredentials.replace(":", "").isNotBlank()) {
                "Basic ${
                    base64Encoder.encodeToString(basicCredentials.toByteArray(Charsets.UTF_8))
                }"
            } else {
                null
            }
        } ?: throw IllegalArgumentException("Bearer token or basic credentials must be provided")
        return authHeader
    }

    companion object {
        private val base64Encoder = Base64.getEncoder()
        private fun getMapper(): ObjectMapper {
            val objectMapper = ObjectMapper()
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            objectMapper.registerModule(KotlinModule.Builder().build())
            return objectMapper
        }
    }
}