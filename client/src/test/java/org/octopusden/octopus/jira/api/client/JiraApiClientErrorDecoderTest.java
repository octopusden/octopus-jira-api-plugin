package org.octopusden.octopus.jira.api.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Response;
import org.junit.Before;
import org.junit.Test;
import org.octopusden.octopus.jira.api.config.JacksonMapper;
import org.octopusden.octopus.jira.api.exception.BadRequestException;
import org.octopusden.octopus.jira.api.exception.FailedGenerateIPSException;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JiraApiClientErrorDecoderTest {

    private ObjectMapper objectMapper;
    private JiraApiClientErrorDecoder decoder;

    @Before
    public void setup() {
        objectMapper = JacksonMapper.create();
        decoder = new JiraApiClientErrorDecoder(objectMapper);
    }

    @Test
    public void decodeWithKnownCodeAPI40000ShouldReturnBadRequestException() {
        String json = "{\"code\":\"API-40000\",\"detail\":\"detail\",\"message\":\"bad request\"}";
        Response response = createJsonResponse(json, 400);

        Exception exception = decoder.decode("test", response);

        assertTrue(exception instanceof BadRequestException);
        assertEquals("bad request", exception.getMessage());
    }

    @Test
    public void decodeWithKnownCodeAPI40001ShouldReturnFailedGenerateIPSException() {
        String json = "{\"code\":\"API-40001\",\"detail\":\"detail\",\"message\":\"failed to generate\"}";
        Response response = createJsonResponse(json, 500);

        Exception exception = decoder.decode("test", response);

        assertTrue(exception instanceof FailedGenerateIPSException);
        assertEquals("failed to generate", exception.getMessage());
    }

    @Test
    public void decodeWithUnknownCodeShouldReturnRuntimeException() {
        String json = "{\"code\":\"API-99999\",\"detail\":\"detail\",\"message\":\"unknown error\"}";
        Response response = createJsonResponse(json, 500);

        Exception exception = decoder.decode("test", response);

        assertTrue(exception instanceof RuntimeException);
        assertEquals("unknown error", exception.getMessage());
    }

    @Test
    public void decodeWithNonJsonContentTypeShouldFallBackToDefault() {
        Response response = createResponse(
                "<html><body>Internal Server Error</body></html>",
                500,
                "text/html"
        );

        Exception exception = decoder.decode("test", response);

        assertTrue(exception instanceof FeignException);
    }

    private Response createJsonResponse(String json, int status) {
        return createResponse(json, status, "application/json");
    }

    private Response createResponse(String body, int status, String contentType) {
        Map<String, java.util.Collection<String>> headers =
                Collections.singletonMap("Content-Type", Collections.singletonList(contentType));
        return Response.builder()
                .status(status)
                .headers(headers)
                .body(body.getBytes(StandardCharsets.UTF_8))
                .request(feign.Request.create(
                        feign.Request.HttpMethod.GET,
                        "http://localhost",
                        Collections.emptyMap(),
                        null,
                        StandardCharsets.UTF_8,
                        null
                ))
                .build();
    }
}
