package org.octopusden.octopus.jira.api.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.octopusden.octopus.jira.api.exception.BadRequestException;
import org.octopusden.octopus.jira.api.exception.FailedGenerateIPSException;
import org.octopusden.octopus.jira.api.exception.JiraApiException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.Map;

@Provider
public class JiraApiExceptionMapper implements ExceptionMapper<JiraApiException> {

    private final ObjectMapper objectMapper;

    public JiraApiExceptionMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Response toResponse(JiraApiException ex) {
        Response.Status status;
        if (ex instanceof BadRequestException) {
            status = Response.Status.BAD_REQUEST;
        } else if (ex instanceof FailedGenerateIPSException) {
            status = Response.Status.INTERNAL_SERVER_ERROR;
        } else {
            status = Response.Status.INTERNAL_SERVER_ERROR;
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", ex.getCode());
        body.put("message", ex.getMessage() != null ? ex.getMessage() : "Unknown error");

        try {
            return Response.status(status.getStatusCode())
                    .entity(objectMapper.writeValueAsString(body))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }
}
