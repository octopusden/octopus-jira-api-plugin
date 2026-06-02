package org.octopusden.octopus.jira.api.config;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import org.octopusden.octopus.jira.exception.JiraApplicationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ApiSettingsProvider {

    private final PluginSettings pluginSettings;
    private final Properties properties;

    public ApiSettingsProvider(PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettings = pluginSettingsFactory.createGlobalSettings();
        this.properties = loadProperties();
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream stream = ApiSettingsProvider.class.getResourceAsStream("/octopus-jira-api-plugin.properties")) {
            props.load(stream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load properties file", e);
        }
        return props;
    }

    public Object get(ApiSetting setting) {
        Object value = pluginSettings.get(setting.getKey());
        if (value != null) {
            return value;
        }
        return getProperty(setting.getKey());
    }

    public void set(ApiSetting setting, String value) {
        pluginSettings.put(setting.getKey(), value);
    }

    public String getString(ApiSetting setting) {
        Object value = get(setting);
        if (value instanceof String) {
            return (String) value;
        }
        throw new IllegalStateException("Setting '" + setting.getKey() + "' is not configured");
    }

    private String getProperty(String propertyName) {
        String value = properties.getProperty(propertyName);
        if (value == null) {
            throw new JiraApplicationException(
                    "Property [" + propertyName + "] must be defined in the property file [/octopus-jira-api-plugin.properties]"
            );
        }
        return value;
    }

    public Properties getProperties() {
        return properties;
    }
}
