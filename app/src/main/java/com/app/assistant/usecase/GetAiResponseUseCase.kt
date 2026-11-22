package com.app.assistant.usecase

import com.app.assistant.llm.FlexibleLlmAdapter
import com.app.assistant.llm.LlmConfig
import com.app.assistant.llm.LlmMessage
import com.app.assistant.llm.LlmProvider
import com.app.assistant.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient

class GetAiResponseUseCase(
    private val settingsRepository: SettingsRepository,
    private val client: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun execute(messages: List<LlmMessage>): String? {
        val providerName = settingsRepository.getLlmProvider()
        val provider = try {
            LlmProvider.valueOf(providerName)
        } catch (e: Exception) {
            LlmProvider.GROQ
        }

        var apiKey = settingsRepository.getChatApiKey(provider)
        if (apiKey.isNullOrBlank() && provider == LlmProvider.GROQ) {
            apiKey = com.app.assistant.BuildConfig.GROQ_API_KEY
        }

        val model = settingsRepository.getLlmModel()

        val config = if (provider == LlmProvider.CUSTOM) {
            LlmConfig(
                url = settingsRepository.getLlmCustomUrl(),
                headers = parseHeadersJson(settingsRepository.getLlmCustomHeaders()),
                responsePath = settingsRepository.getLlmCustomResponsePath(),
                requestTemplate = settingsRepository.getLlmCustomRequestTemplate(),
                messageFormat = settingsRepository.getLlmCustomMessageFormat(),
                systemRole = settingsRepository.getLlmCustomSystemRole().takeIf { it.isNotBlank() },
                userRole = settingsRepository.getLlmCustomUserRole(),
                assistantRole = settingsRepository.getLlmCustomAssistantRole()
            )
        } else {
            provider.config
        }

        val adapter = FlexibleLlmAdapter(
            client = client,
            apiKey = apiKey ?: "",
            model = model,
            config = config,
            provider = provider
        )

        val systemContext = messages.find { it.role == "system" }?.content ?: ""
        return adapter.generateResponse(systemContext, messages)
    }

    fun executeStream(messages: List<LlmMessage>): Flow<String> {
        val providerName = settingsRepository.getLlmProvider()
        val provider = try {
            LlmProvider.valueOf(providerName)
        } catch (e: Exception) {
            LlmProvider.GROQ
        }

        var apiKey = settingsRepository.getChatApiKey(provider)
        if (apiKey.isNullOrBlank() && provider == LlmProvider.GROQ) {
            apiKey = com.app.assistant.BuildConfig.GROQ_API_KEY
        }

        val model = settingsRepository.getLlmModel()

        val config = if (provider == LlmProvider.CUSTOM) {
            LlmConfig(
                url = settingsRepository.getLlmCustomUrl(),
                headers = parseHeadersJson(settingsRepository.getLlmCustomHeaders()),
                responsePath = settingsRepository.getLlmCustomResponsePath(),
                requestTemplate = settingsRepository.getLlmCustomRequestTemplate(),
                messageFormat = settingsRepository.getLlmCustomMessageFormat(),
                systemRole = settingsRepository.getLlmCustomSystemRole().takeIf { it.isNotBlank() },
                userRole = settingsRepository.getLlmCustomUserRole(),
                assistantRole = settingsRepository.getLlmCustomAssistantRole()
            )
        } else {
            provider.config
        }

        val adapter = FlexibleLlmAdapter(
            client = client,
            apiKey = apiKey ?: "",
            model = model,
            config = config,
            provider = provider
        )

        val systemContext = messages.find { it.role == "system" }?.content ?: ""
        return adapter.generateResponseStream(systemContext, messages)
    }

    private fun parseHeadersJson(jsonStr: String): Map<String, String> {
        return try {
            val element = json.parseToJsonElement(jsonStr)
            if (element is JsonObject) {
                element.mapValues { (_, value) ->
                    if (value is JsonPrimitive) value.content else value.toString()
                }
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
