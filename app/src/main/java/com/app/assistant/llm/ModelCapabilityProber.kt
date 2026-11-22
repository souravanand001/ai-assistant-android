package com.app.assistant.llm

import android.util.Log
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive


object ModelCapabilityProber {

    fun resolveCapabilitiesOffline(provider: LlmProvider, modelName: String): ModelCapabilities {
        val model = modelName.lowercase()
        return when (provider) {
            LlmProvider.GEMINI -> {
                val isMultimodal = model.contains("gemini-1.5") || model.contains("gemini-2.0") || model.contains("gemini-pro") || model.contains("gemini-2.5")
                ModelCapabilities(
                    hasImageInput = isMultimodal || model.contains("vision"),
                    hasAudioInput = isMultimodal,
                    hasVideoInput = isMultimodal,
                    hasDocumentInput = isMultimodal
                )
            }
            LlmProvider.OPENAI -> {
                val hasVision = model.contains("gpt-4o") || model.contains("vision") || model.contains("gpt-4-turbo")
                val hasAudio = model.contains("gpt-4o-audio")
                ModelCapabilities(
                    hasImageInput = hasVision,
                    hasAudioInput = hasAudio,
                    hasVideoInput = false,
                    hasDocumentInput = false
                )
            }
            LlmProvider.ANTHROPIC -> {
                val supportsVision = model.contains("claude-3")
                ModelCapabilities(
                    hasImageInput = supportsVision,
                    hasAudioInput = false,
                    hasVideoInput = false,
                    hasDocumentInput = supportsVision
                )
            }
            LlmProvider.GROQ -> {
                ModelCapabilities(
                    hasImageInput = model.contains("vision") || model.contains("scout") || model.contains("vl"),
                    hasAudioInput = false,
                    hasVideoInput = false,
                    hasDocumentInput = false
                )
            }
            LlmProvider.OLLAMA -> {
                val isMultimodal = model.contains("llava") || model.contains("bakllava") || model.contains("minicpm") || model.contains("vision")
                ModelCapabilities(
                    hasImageInput = isMultimodal,
                    hasAudioInput = false,
                    hasVideoInput = false,
                    hasDocumentInput = false
                )
            }
            LlmProvider.DEEPSEEK -> {
                ModelCapabilities(
                    hasImageInput = model.contains("vl"),
                    hasAudioInput = false,
                    hasVideoInput = false,
                    hasDocumentInput = false
                )
            }
            LlmProvider.OPEN_ROUTER -> {
                val hasVision = model.contains("vision") || model.contains("llava") || model.contains("gpt-4o") || model.contains("claude-3") || model.contains("gemini")
                val hasAudio = model.contains("gemini") || model.contains("audio")
                val hasVideo = model.contains("gemini")
                val hasDoc = model.contains("gemini") || model.contains("claude-3")
                ModelCapabilities(
                    hasImageInput = hasVision,
                    hasAudioInput = hasAudio,
                    hasVideoInput = hasVideo,
                    hasDocumentInput = hasDoc
                )
            }
            LlmProvider.CUSTOM -> {
                ModelCapabilities(
                    hasImageInput = false,
                    hasAudioInput = false,
                    hasVideoInput = false,
                    hasDocumentInput = false
                )
            }
        }
    }

    suspend fun probeTextConnection(
        client: OkHttpClient,
        apiKey: String,
        model: String,
        config: LlmConfig
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val completionUrl = LlmConfig.resolveCompletionUrl(config.url)
            val escapedModel = LlmConfig.escapeJsonString(model)
            val url = completionUrl
                .replace("{{API_KEY}}", apiKey)
                .replace("{{MODEL}}", model)

            val requestBuilder = Request.Builder().url(url)
            for ((key, value) in config.headers) {
                val resolvedValue = value
                    .replace("{{API_KEY}}", apiKey)
                    .replace("{{MODEL}}", model)
                requestBuilder.addHeader(key, resolvedValue)
            }

            val hasSystemContextPlaceholder = config.requestTemplate.contains("{{SYSTEM_CONTEXT}}")
            
            val escapedContent = "say ok"
            val msgJson = config.messageFormat
                .replace("{{ROLE}}", config.userRole)
                .replace("{{CONTENT}}", escapedContent)
            
            val messagesJsonArray = "[$msgJson]"
            var requestBodyString = config.requestTemplate
                .replace("{{MODEL}}", escapedModel)
                .replace("{{MESSAGES}}", messagesJsonArray)

            if (hasSystemContextPlaceholder) {
                requestBodyString = requestBodyString.replace("{{SYSTEM_CONTEXT}}", "test connection")
            }

            val mediaType = config.headers["Content-Type"]?.toMediaTypeOrNull() ?: "application/json".toMediaTypeOrNull()
            val requestBody = requestBodyString.toRequestBody(mediaType)
            requestBuilder.post(requestBody)

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.w("ModelCapabilityProber", "Text test failed with code: ${response.code}, body: $errorBody")
                } else {
                    Log.d("ModelCapabilityProber", "Text test response code: ${response.code}")
                }
                response.isSuccessful
            }
        } catch (e: IOException) {
            Log.e("ModelCapabilityProber", "Text test failed with IOException: ${e.message}", e)
            if (e.message?.contains("Insecure HTTP") == true) {
                throw e
            }
            false
        } catch (e: Exception) {
            Log.e("ModelCapabilityProber", "Text test failed", e)
            false
        }
    }

    suspend fun probeVisionConnection(
        client: OkHttpClient,
        provider: LlmProvider,
        apiKey: String,
        model: String,
        config: LlmConfig
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val completionUrl = if (provider == LlmProvider.CUSTOM) LlmConfig.resolveCompletionUrl(config.url) else config.url
            val escapedModel = LlmConfig.escapeJsonString(model)
            val url = completionUrl
                .replace("{{API_KEY}}", apiKey)
                .replace("{{MODEL}}", model)

            val requestBuilder = Request.Builder().url(url)
            for ((key, value) in config.headers) {
                val resolvedValue = value
                    .replace("{{API_KEY}}", apiKey)
                    .replace("{{MODEL}}", model)
                requestBuilder.addHeader(key, resolvedValue)
            }

            // 32x32 PNG base64 (Groq and certain vision models enforce a minimum dimension of at least 32x32 pixels)
            val base64Image = "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAKUlEQVR4nO3NsQ0AAAzCMP5/mj5RNkuZ4zSZtr0DAAAAAAAAAACA/gEHMsP8LofXz44AAAAASUVORK5CYII="
            
            val requestBodyString = when (provider) {
                LlmProvider.GEMINI -> {
                    """
                    {
                      "contents": [{
                        "role": "user",
                        "parts": [
                          { "text": "is this image ok?" },
                          { "inlineData": { "mimeType": "image/png", "data": "$base64Image" } }
                        ]
                      }],
                      "generationConfig": { "maxOutputTokens": 1 }
                    }
                    """.trimIndent()
                }
                LlmProvider.ANTHROPIC -> {
                    """
                    {
                      "model": "$escapedModel",
                      "max_tokens": 10,
                      "messages": [{
                        "role": "user",
                        "content": [
                          { "type": "text", "text": "is this image ok?" },
                          { "type": "image", "source": { "type": "base64", "media_type": "image/png", "data": "$base64Image" } }
                        ]
                      }]
                    }
                    """.trimIndent()
                }
                else -> { // OpenAI-compatible schema (OpenAI, Groq, OpenRouter, Custom, Ollama, DeepSeek)
                    """
                    {
                      "model": "$escapedModel",
                      "max_tokens": 10,
                      "messages": [{
                        "role": "user",
                        "content": [
                          { "type": "text", "text": "is this image ok?" },
                          { "type": "image_url", "image_url": { "url": "data:image/png;base64,$base64Image" } }
                        ]
                      }]
                    }
                    """.trimIndent()
                }
            }

            val mediaType = "application/json".toMediaTypeOrNull()
            val requestBody = requestBodyString.toRequestBody(mediaType)
            requestBuilder.post(requestBody)

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.w("ModelCapabilityProber", "Vision test failed with code: ${response.code}, body: $errorBody")
                } else {
                    Log.d("ModelCapabilityProber", "Vision test response code: ${response.code}")
                }
                response.isSuccessful
            }
        } catch (e: IOException) {
            Log.e("ModelCapabilityProber", "Vision test failed with IOException: ${e.message}", e)
            if (e.message?.contains("Insecure HTTP") == true) {
                throw e
            }
            false
        } catch (e: Exception) {
            Log.e("ModelCapabilityProber", "Vision test failed", e)
            false
        }
    }

    suspend fun fetchAvailableModels(
        client: OkHttpClient,
        provider: LlmProvider,
        apiKey: String,
        customUrl: String = "",
        customHeaders: String = ""
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = when (provider) {
                LlmProvider.GROQ -> "https://api.groq.com/openai/v1/models"
                LlmProvider.OPENAI -> "https://api.openai.com/v1/models"
                LlmProvider.OPEN_ROUTER -> "https://openrouter.ai/api/v1/models"
                LlmProvider.DEEPSEEK -> "https://api.deepseek.com/v1/models"
                LlmProvider.OLLAMA -> {
                    val configUrl = provider.config.url
                    if (configUrl.endsWith("/api/chat")) {
                        configUrl.substringBeforeLast("/api/chat") + "/api/tags"
                    } else {
                        "http://10.0.2.2:11434/api/tags"
                    }
                }
                LlmProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
                LlmProvider.ANTHROPIC -> "https://api.anthropic.com/v1/models"
                LlmProvider.CUSTOM -> {
                    if (customUrl.isBlank()) return@withContext emptyList<String>()
                    LlmConfig.resolveModelsUrl(customUrl)
                }
            } ?: return@withContext emptyList<String>()

            val requestBuilder = Request.Builder().url(url).get()
            
            when (provider) {
                LlmProvider.GROQ, LlmProvider.OPENAI, LlmProvider.OPEN_ROUTER, LlmProvider.DEEPSEEK -> {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                    requestBuilder.addHeader("Content-Type", "application/json")
                }
                LlmProvider.ANTHROPIC -> {
                    requestBuilder.addHeader("x-api-key", apiKey)
                    requestBuilder.addHeader("anthropic-version", "2023-06-01")
                    requestBuilder.addHeader("content-type", "application/json")
                }
                LlmProvider.CUSTOM -> {
                    if (customHeaders.isNotBlank()) {
                        try {
                            val parsed = parseHeadersJson(customHeaders)
                            for ((k, v) in parsed) {
                                requestBuilder.addHeader(k, v.replace("{{API_KEY}}", apiKey))
                            }
                        } catch (e: Exception) {
                            Log.e("ModelCapabilityProber", "Failed to parse custom headers", e)
                        }
                    }
                }
                else -> {}
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ModelCapabilityProber", "Model fetch response not successful: ${response.code}")
                    return@withContext emptyList<String>()
                }
                val bodyString = response.body?.string() ?: return@withContext emptyList<String>()
                parseModelsResponse(provider, bodyString)
            }
        } catch (e: IOException) {
            Log.e("ModelCapabilityProber", "Failed to fetch models: ${e.message}", e)
            if (e.message?.contains("Insecure HTTP") == true) {
                throw e
            }
            emptyList()
        } catch (e: Exception) {
            Log.e("ModelCapabilityProber", "Failed to fetch models", e)
            emptyList()
        }
    }

    fun parseModelsResponse(provider: LlmProvider, bodyString: String): List<String> {
        return try {
            val element = Json.parseToJsonElement(bodyString)
            val modelsList = mutableListOf<String>()

            when (provider) {
                LlmProvider.OLLAMA -> {
                    val modelsArray = element.jsonObject["models"]?.jsonArray
                    modelsArray?.forEach { modelObj ->
                        modelObj.jsonObject["name"]?.jsonPrimitive?.content?.let { name ->
                            modelsList.add(name)
                        }
                    }
                }
                LlmProvider.GEMINI -> {
                    val modelsArray = element.jsonObject["models"]?.jsonArray
                    modelsArray?.forEach { modelObj ->
                        val supportedMethods = modelObj.jsonObject["supportedGenerationMethods"]?.jsonArray?.map {
                            it.jsonPrimitive.content
                        } ?: emptyList()
                        if (supportedMethods.contains("generateContent")) {
                            modelObj.jsonObject["name"]?.jsonPrimitive?.content?.let { name ->
                                val cleanedName = if (name.startsWith("models/")) name.substringAfter("models/") else name
                                modelsList.add(cleanedName)
                            }
                        }
                    }
                }
                LlmProvider.CUSTOM -> {
                    val dataArray = element.jsonObject["data"]?.jsonArray
                    dataArray?.forEach { modelObj ->
                        val obj = modelObj.jsonObject
                        val id = (obj["id"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
                            ?: (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
                            ?: (obj["model"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
                        if (!id.isNullOrBlank()) {
                            modelsList.add(id)
                        }
                    }
                    val modelsArray = element.jsonObject["models"]?.jsonArray
                    modelsArray?.forEach { modelObj ->
                        val obj = modelObj.jsonObject
                        val id = (obj["id"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
                            ?: (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
                            ?: (obj["model"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
                        if (!id.isNullOrBlank()) {
                            modelsList.add(id)
                        }
                    }
                }
                else -> {
                    val dataArray = element.jsonObject["data"]?.jsonArray
                    dataArray?.forEach { modelObj ->
                        modelObj.jsonObject["id"]?.jsonPrimitive?.content?.let { id ->
                            modelsList.add(id)
                        }
                    }
                }
            }
            modelsList.distinct().sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseHeadersJson(jsonStr: String): Map<String, String> {
        return try {
            val element = Json.parseToJsonElement(jsonStr)
            if (element is kotlinx.serialization.json.JsonObject) {
                element.mapValues { (_, value) ->
                    if (value is kotlinx.serialization.json.JsonPrimitive) value.content else value.toString()
                }
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun autoDetectCustomServer(
        client: OkHttpClient,
        baseUrl: String,
        apiKey: String = ""
    ): DetectedServerConfig? = withContext(Dispatchers.IO) {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) return@withContext null

        // 1. Try standard OpenAI /v1/models or /models endpoint
        val modelsUrl = LlmConfig.resolveModelsUrl(trimmed)
        if (modelsUrl != null) {
            try {
                val reqBuilder = Request.Builder().url(modelsUrl).get()
                if (apiKey.isNotBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer $apiKey")
                }
                client.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val models = parseModelsResponse(LlmProvider.CUSTOM, body)
                        val headersMap = if (apiKey.isNotBlank()) {
                            mapOf("Authorization" to "Bearer {{API_KEY}}", "Content-Type" to "application/json")
                        } else {
                            mapOf("Content-Type" to "application/json")
                        }
                        return@withContext DetectedServerConfig(
                            serverType = "OpenAI Compatible",
                            completionUrl = LlmConfig.resolveCompletionUrl(trimmed),
                            headers = headersMap,
                            responsePath = "choices[0].message.content",
                            requestTemplate = "{\n  \"model\": \"{{MODEL}}\",\n  \"messages\": {{MESSAGES}}\n}",
                            messageFormat = "{\"role\": \"{{ROLE}}\", \"content\": \"{{CONTENT}}\"}",
                            systemRole = "system",
                            userRole = "user",
                            assistantRole = "assistant",
                            availableModels = models
                        )
                    }
                }
            } catch (e: IOException) {
                Log.d("ModelCapabilityProber", "OpenAI endpoint probe failed: ${e.message}")
                if (e.message?.contains("Insecure HTTP") == true) {
                    throw e
                }
            } catch (e: Exception) {
                Log.d("ModelCapabilityProber", "OpenAI endpoint probe failed: ${e.message}")
            }
        }

        // 2. Try Ollama native /api/tags endpoint
        val withoutSlash = trimmed.trimEnd('/')
        val ollamaTagsUrl = if (withoutSlash.endsWith("/api/chat")) {
            withoutSlash.substringBeforeLast("/api/chat") + "/api/tags"
        } else if (withoutSlash.endsWith("/api/tags")) {
            withoutSlash
        } else {
            "$withoutSlash/api/tags"
        }

        try {
            val reqBuilder = Request.Builder().url(ollamaTagsUrl).get()
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val models = parseModelsResponse(LlmProvider.OLLAMA, body)
                    val chatUrl = if (withoutSlash.endsWith("/api/tags")) {
                        withoutSlash.substringBeforeLast("/api/tags") + "/api/chat"
                    } else if (withoutSlash.endsWith("/api/chat")) {
                        withoutSlash
                    } else {
                        "$withoutSlash/api/chat"
                    }
                    return@withContext DetectedServerConfig(
                        serverType = "Ollama Native",
                        completionUrl = chatUrl,
                        headers = mapOf("Content-Type" to "application/json"),
                        responsePath = "message.content",
                        requestTemplate = "{\n  \"model\": \"{{MODEL}}\",\n  \"messages\": {{MESSAGES}},\n  \"stream\": false\n}",
                        messageFormat = "{\"role\": \"{{ROLE}}\", \"content\": \"{{CONTENT}}\"}",
                        systemRole = "system",
                        userRole = "user",
                        assistantRole = "assistant",
                        availableModels = models
                    )
                }
            }
        } catch (e: IOException) {
            Log.d("ModelCapabilityProber", "Ollama endpoint probe failed: ${e.message}")
            if (e.message?.contains("Insecure HTTP") == true) {
                throw e
            }
        } catch (e: Exception) {
            Log.d("ModelCapabilityProber", "Ollama endpoint probe failed: ${e.message}")
        }

        null
    }
}

data class DetectedServerConfig(
    val serverType: String,
    val completionUrl: String,
    val headers: Map<String, String>,
    val responsePath: String,
    val requestTemplate: String,
    val messageFormat: String,
    val systemRole: String?,
    val userRole: String,
    val assistantRole: String,
    val availableModels: List<String>
)

enum class CustomServerPreset(val displayName: String, val description: String) {
    OPENAI_COMPATIBLE("OpenAI Compatible", "llama.cpp, vLLM, LM Studio, Ollama /v1, LocalAI"),
    OLLAMA_NATIVE("Ollama Native", "Ollama /api/chat endpoint"),
    ANTHROPIC_COMPATIBLE("Anthropic Compatible", "Claude /v1/messages proxy"),
    CUSTOM("Custom / Manual", "Full manual configuration")
}
