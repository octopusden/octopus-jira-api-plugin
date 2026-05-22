package org.octopusden.octopus.jira.api.config

import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory
import org.octopusden.octopus.jira.exception.JiraApplicationException
import java.util.Properties

private const val PROPERTIES_FILE_NAME = "/octopus-jira-api-plugin.properties"

class ApiSettingsProvider(pluginSettingsFactory: PluginSettingsFactory) {

    private val pluginSettings = pluginSettingsFactory.createGlobalSettings()

    val properties = Properties().apply {
        ApiSettingsProvider::class.java.getResourceAsStream(PROPERTIES_FILE_NAME)
            .use { stream ->
                load(stream)
            }
    }

    operator fun get(setting: ApiSetting): Any =
        pluginSettings[setting.key] ?: getProperty(setting.key)

    operator fun set(setting: ApiSetting, value: String?) {
        pluginSettings.put(setting.key, value)
    }

    private fun getProperty(propertyName: String): String {
        return properties.getProperty(propertyName)
            ?: throw JiraApplicationException("Property [$propertyName] must be defined in the property file [$PROPERTIES_FILE_NAME]")
    }

    fun getString(key: ApiSetting): String =
        get(key) as? String
            ?: throw IllegalStateException("Setting '${key.key}' is not configured")
}

enum class ApiSetting(
    val key: String,
    val displayName: String,
    val type: SettingType,
    val values: List<String> = emptyList()
) {
    IPS_REPORTS_PROJECT("ips-reports.project", "IPS Reports Project", SettingType.TEXT),
    SERVICE_USER("service-user.username", "Service User", SettingType.TEXT);

    enum class SettingType { TEXT, PASSWORD, SELECT }
}
