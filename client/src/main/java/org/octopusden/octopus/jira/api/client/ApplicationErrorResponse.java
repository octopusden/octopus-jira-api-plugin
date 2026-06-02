package org.octopusden.octopus.jira.api.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ApplicationErrorResponse {

    private final String code;
    private final String message;
    private final String detail;

    @JsonCreator
    public ApplicationErrorResponse(
            @JsonProperty("code") String code,
            @JsonProperty("message") String message,
            @JsonProperty("detail") String detail
    ) {
        this.code = code;
        this.message = message;
        this.detail = detail != null ? detail : "";
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getDetail() {
        return detail;
    }
}
