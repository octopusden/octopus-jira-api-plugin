package org.octopusden.octopus.jira.api.client.impl

interface JiraApiClientParametersProvider {
    fun getApiUrl(): String
    fun getBearerToken(): String?
    fun getBasicCredentials(): String?
}
