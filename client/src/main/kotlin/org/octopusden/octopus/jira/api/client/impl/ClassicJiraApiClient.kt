package org.octopusden.octopus.jira.api.client.impl

import com.fasterxml.jackson.databind.ObjectMapper
import feign.Feign
import feign.Logger
import feign.Request
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder
import feign.slf4j.Slf4jLogger
import java.util.Base64
import org.octopusden.octopus.jira.api.client.JiraApiClient
import org.octopusden.octopus.jira.api.client.JiraApiClientErrorDecoder
import org.octopusden.octopus.jira.api.config.JacksonMapper
import java.util.concurrent.TimeUnit

class ClassicJiraApiClient(
    private val parametersProvider: JiraApiClientParametersProvider, private val objectMapper: ObjectMapper
) : JiraApiClient {
    private val client = createClient(parametersProvider)

    constructor(parametersProvider: JiraApiClientParametersProvider) : this(
        parametersProvider, JacksonMapper.create()
    )

    override fun getIps(
        ips: String, sinceYear: Int?, sinceDate: String?, release: String?, system: String?, mandatory: Boolean?
    ) = client.getIps(ips, sinceYear, sinceDate, release, system, mandatory)

    private fun createClient(parametersProvider: JiraApiClientParametersProvider): JiraApiClient {
        return Feign.builder()
            .options(Request.Options(1, TimeUnit.MINUTES, 5, TimeUnit.MINUTES, true))
            .encoder(JacksonEncoder(objectMapper))
            .decoder(JacksonDecoder(objectMapper))
            .errorDecoder(JiraApiClientErrorDecoder(objectMapper)).requestInterceptor { requestTemplate ->
                getAuthHeader()?.let { requestTemplate.header("Authorization", it) }
            }.logger(Slf4jLogger(JiraApiClient::class.java)).logLevel(Logger.Level.BASIC)
            .target(JiraApiClient::class.java, parametersProvider.getApiUrl())
    }

    private fun getAuthHeader(): String? {
        return parametersProvider.getBearerToken()?.let { token ->
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
        }
    }

    companion object {
        private val base64Encoder = Base64.getEncoder()
    }
}