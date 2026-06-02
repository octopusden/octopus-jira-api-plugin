package org.octopusden.octopus.jira.api.client;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import org.octopusden.octopus.jira.api.dto.IPSResponse;

@Headers("Accept: application/json")
public interface JiraApiClient {

    @RequestLine("GET /rest/octopus-jira-api/1/api/ips/{ips}?since={since}&sinceDate={sinceDate}&release={release}&system={system}&mandatory={mandatory}")
    IPSResponse getIps(
            @Param("ips") String ips,
            @Param("since") Integer sinceYear,
            @Param("sinceDate") String sinceDate,
            @Param("release") String release,
            @Param("system") String system,
            @Param("mandatory") Boolean mandatory
    );
}
