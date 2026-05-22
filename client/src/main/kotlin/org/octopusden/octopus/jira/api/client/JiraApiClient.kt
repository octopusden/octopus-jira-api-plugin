package org.octopusden.octopus.jira.api.client

import feign.Headers
import feign.Param
import feign.RequestLine
import org.octopusden.octopus.jira.api.dto.IPSResponse

@Headers("Accept: application/json")
interface JiraApiClient {

    @RequestLine("GET /rest/octopus-jira-api/1/api/ips/{ips}?since={since}&sinceDate={sinceDate}&release={release}&system={system}&mandatory={mandatory}")
    fun getIps(
        @Param("ips") ips: String,
        @Param("since") sinceYear: Int?,
        @Param("sinceDate") sinceDate: String?,
        @Param("release") release: String?,
        @Param("system") system: String?,
        @Param("mandatory") mandatory: Boolean?
    ): IPSResponse

}
