package org.octopusden.octopus.jira.api.client.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.Logger;
import feign.Request;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;
import org.octopusden.octopus.jira.api.client.JiraApiClient;
import org.octopusden.octopus.jira.api.client.JiraApiClientErrorDecoder;
import org.octopusden.octopus.jira.api.config.JacksonMapper;
import org.octopusden.octopus.jira.api.dto.IPSResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class ClassicJiraApiClient implements JiraApiClient {

    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();

    private final JiraApiClientParametersProvider parametersProvider;
    private final ObjectMapper objectMapper;
    private final JiraApiClient client;

    public ClassicJiraApiClient(JiraApiClientParametersProvider parametersProvider, ObjectMapper objectMapper) {
        this.parametersProvider = parametersProvider;
        this.objectMapper = objectMapper;
        this.client = createClient(parametersProvider);
    }

    public ClassicJiraApiClient(JiraApiClientParametersProvider parametersProvider) {
        this(parametersProvider, JacksonMapper.create());
    }

    @Override
    public IPSResponse getIps(String ips, Integer sinceYear, String sinceDate, String release, String system, Boolean mandatory) {
        return client.getIps(ips, sinceYear, sinceDate, release, system, mandatory);
    }

    private JiraApiClient createClient(JiraApiClientParametersProvider parametersProvider) {
        return Feign.builder()
                .options(new Request.Options(1, TimeUnit.MINUTES, 5, TimeUnit.MINUTES, true))
                .encoder(new JacksonEncoder(objectMapper))
                .decoder(new JacksonDecoder(objectMapper))
                .errorDecoder(new JiraApiClientErrorDecoder(objectMapper))
                .requestInterceptor(requestTemplate -> {
                    String authHeader = getAuthHeader();
                    if (authHeader != null) {
                        requestTemplate.header("Authorization", authHeader);
                    }
                })
                .logger(new Slf4jLogger(JiraApiClient.class))
                .logLevel(Logger.Level.BASIC)
                .target(JiraApiClient.class, parametersProvider.getApiUrl());
    }

    String getAuthHeader() {
        String bearerToken = parametersProvider.getBearerToken();
        if (bearerToken != null && !bearerToken.trim().isEmpty()) {
            return "Bearer " + bearerToken;
        }
        String basicCredentials = parametersProvider.getBasicCredentials();
        if (basicCredentials != null && !basicCredentials.replace(":", "").trim().isEmpty()) {
            String encoded = BASE64_ENCODER.encodeToString(basicCredentials.getBytes(StandardCharsets.UTF_8));
            return "Basic " + encoded;
        }
        return null;
    }
}
