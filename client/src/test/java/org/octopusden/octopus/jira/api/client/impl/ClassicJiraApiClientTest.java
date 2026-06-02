package org.octopusden.octopus.jira.api.client.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.octopusden.octopus.jira.api.config.JacksonMapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClassicJiraApiClientTest {

    private ObjectMapper objectMapper;
    private JiraApiClientParametersProvider parametersProvider;

    @Before
    public void setup() {
        objectMapper = JacksonMapper.create();
        parametersProvider = mock(JiraApiClientParametersProvider.class);
        when(parametersProvider.getApiUrl()).thenReturn("http://localhost:8080");
    }

    @Test
    public void getAuthHeaderReturnsBearerTokenWhenTokenIsPresent() {
        when(parametersProvider.getBearerToken()).thenReturn("my-token");
        ClassicJiraApiClient client = new ClassicJiraApiClient(parametersProvider, objectMapper);

        String authHeader = invokeGetAuthHeader(client);

        assertEquals("Bearer my-token", authHeader);
    }

    @Test
    public void getAuthHeaderReturnsBasicCredentialsWhenTokenIsNull() {
        when(parametersProvider.getBearerToken()).thenReturn(null);
        when(parametersProvider.getBasicCredentials()).thenReturn("user:pass");
        ClassicJiraApiClient client = new ClassicJiraApiClient(parametersProvider, objectMapper);

        String authHeader = invokeGetAuthHeader(client);

        assertTrue(authHeader != null && authHeader.startsWith("Basic "));
        byte[] decoded = Base64.getDecoder().decode(authHeader.substring("Basic ".length()));
        assertEquals("user:pass", new String(decoded));
    }

    @Test
    public void bearerTokenTakesPrecedenceOverBasicCredentials() {
        when(parametersProvider.getBearerToken()).thenReturn("bearer-token");
        when(parametersProvider.getBasicCredentials()).thenReturn("user:pass");
        ClassicJiraApiClient client = new ClassicJiraApiClient(parametersProvider, objectMapper);

        String authHeader = invokeGetAuthHeader(client);

        assertEquals("Bearer bearer-token", authHeader);
    }

    @Test
    public void getAuthHeaderWhenBothTokenAndCredentialsAreNull() {
        when(parametersProvider.getBearerToken()).thenReturn(null);
        when(parametersProvider.getBasicCredentials()).thenReturn(null);
        ClassicJiraApiClient client = new ClassicJiraApiClient(parametersProvider, objectMapper);

        assertNull(invokeGetAuthHeader(client));
    }

    @Test
    public void getAuthHeaderWhenBothAreBlank() {
        when(parametersProvider.getBearerToken()).thenReturn("");
        when(parametersProvider.getBasicCredentials()).thenReturn(":");
        ClassicJiraApiClient client = new ClassicJiraApiClient(parametersProvider, objectMapper);

        assertNull(invokeGetAuthHeader(client));
    }

    private String invokeGetAuthHeader(ClassicJiraApiClient client) {
        try {
            Method method = ClassicJiraApiClient.class.getDeclaredMethod("getAuthHeader");
            method.setAccessible(true);
            return (String) method.invoke(client);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
