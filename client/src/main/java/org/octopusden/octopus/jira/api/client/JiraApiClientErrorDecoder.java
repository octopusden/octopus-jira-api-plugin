package org.octopusden.octopus.jira.api.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.octopusden.octopus.jira.api.exception.JiraApiException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

public class JiraApiClientErrorDecoder extends ErrorDecoder.Default {

    private final ObjectMapper objectMapper;

    public JiraApiClientErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response == null) {
            return super.decode(methodKey, null);
        }

        String responseBody = null;
        if (response.body() != null) {
            try (InputStream inputStream = response.body().asInputStream()) {
                responseBody = new String(readAllBytes(inputStream), StandardCharsets.UTF_8);
            } catch (IOException e) {
                // fall through to default
            }
        }

        if (responseBody != null && isJsonContentType(response.headers())) {
            try {
                ApplicationErrorResponse error = objectMapper.readValue(responseBody, ApplicationErrorResponse.class);
                Map<String, Function<String, JiraApiException>> map = JiraApiException.CODE_EXCEPTION_MAP;
                Function<String, JiraApiException> factory = map.get(error.getCode());
                if (factory != null) {
                    return factory.apply(error.getMessage());
                } else {
                    return new RuntimeException(error.getMessage());
                }
            } catch (IOException e) {
                // fall through to default
            }
        }

        return super.decode(methodKey, response);
    }

    private boolean isJsonContentType(Map<String, Collection<String>> headers) {
        Collection<String> contentType = headers.get("Content-Type");
        if (contentType == null) {
            return false;
        }
        return contentType.stream().anyMatch(v -> v.contains("application/json"));
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }
}
