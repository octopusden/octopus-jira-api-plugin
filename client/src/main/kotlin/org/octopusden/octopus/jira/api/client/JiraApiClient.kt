package org.octopusden.octopus.jira.api.client

import feign.Headers
import feign.Param
import feign.RequestLine
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder
import org.octopusden.octopus.jira.api.dto.IPSResponse

@Headers("Accept: application/json")
interface JiraApiClient {

    @RequestLine("GET /octopus-jira-api/api/ips/{ips}/type/{type}?release={release}&system={system}&mandatory={mandatory}")
    fun getIps(
        @Param("ips") ips: String,
        @Param("type") type: String,
        @Param("release") release: String,
        @Param("system") system: String,
        @Param("mandatory") mandatory: Boolean
    ): IPSResponse

}
