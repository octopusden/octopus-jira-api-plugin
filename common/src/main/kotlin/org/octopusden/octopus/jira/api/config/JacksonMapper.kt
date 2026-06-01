package org.octopusden.octopus.jira.api.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

class JacksonMapper : ObjectMapper() {
    init {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        registerModule(KotlinModule.Builder().build())
    }

    companion object {
        @JvmStatic
        fun create(): ObjectMapper = JacksonMapper()
    }
}
