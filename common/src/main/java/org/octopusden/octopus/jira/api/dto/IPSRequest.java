package org.octopusden.octopus.jira.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

public class IPSRequest {

    private final String ips;
    private final String release;
    private final Date startDate;
    private final String system;
    private final boolean mandatory;

    @JsonCreator
    public IPSRequest(
            @JsonProperty("ips") String ips,
            @JsonProperty("release") String release,
            @JsonProperty("startDate") Date startDate,
            @JsonProperty("system") String system,
            @JsonProperty("mandatory") boolean mandatory
    ) {
        this.ips = ips;
        this.release = release;
        this.startDate = startDate;
        this.system = system;
        this.mandatory = mandatory;
    }

    public String getIps() {
        return ips;
    }

    public String getRelease() {
        return release;
    }

    public Date getStartDate() {
        return startDate;
    }

    public String getSystem() {
        return system;
    }

    public boolean isMandatory() {
        return mandatory;
    }
}
