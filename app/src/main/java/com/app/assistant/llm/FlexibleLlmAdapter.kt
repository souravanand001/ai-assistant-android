package com.app.assistant.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.UnknownHostException

class FlexibleLlmAdapter(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val model: String,
    private val config: LlmConfig,
    private val provider: LlmProvider
) : LlmAdapter {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun generateResponse(
        systemContext: String,
        messages: List<LlmMessage>
    ): String? {
        val completionUrl = if (provider == LlmProvider.CUSTOM) LlmConfig.resolveCompletionUrl(config.url) else config.url
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

        val requestBodyString = buildRequestBodyString(systemContext, messages, isStream = false)
        val mediaType = config.headers["Content-Type"]?.toMediaTypeOrNull() ?: "application/json".toMediaTypeOrNull()
        val requestBody = requestBodyString.toRequestBody(mediaType)

        requestBuilder.post(requestBody)
        val request = requestBuilder.build()

        val rawResponse = withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: ""
                        Log.e("FlexibleLlmAdapter", "API request failed with code ${response.code}: $errorBody")
                        if (response.code == 401) {
                            return@withContext """{"error_code": 401, "msg": "API key is missing or invalid."}"""
                        }
                        val errMsg = extractErrorMessage(errorBody) ?: "API error (HTTP ${response.code})."
                        return@withContext """{"error_code": ${response.code}, "msg": "$errMsg"}"""
                    }
                    response.body!!.string()
                }
            } catch (e: UnknownHostException) {
                """{"error_code": 503, "msg": "Seems this device is offline. Maybe try checking data connection."}"""
            } catch (e: Exception) {
                Log.e("FlexibleLlmAdapter", "Error during LLM API call", e)
                """{"error_code": 500, "msg": "Seems some issue in LLM server. Please try again."}"""
            }
        }

        return extractResponseContent(rawResponse, config.responsePath)
    }

    override fun generateResponseStream(
        systemContext: String,
        messages: List<LlmMessage>
    ): Flow<String> = flow {
        val completionUrl = if (provider == LlmProvider.CUSTOM) LlmConfig.resolveCompletionUrl(config.url) else config.url
        var url = completionUrl
            .replace("{{API_KEY}}", apiKey)
            .replace("{{MODEL}}", model)

        if (url.contains(":generateContent")) {
            url = url.replace(":generateContent", ":streamGenerateContent")
            if (!url.contains("alt=sse")) {
                url = if (url.contains("?")) {
                    url.replace("?", "?alt=sse&")
                } else {
                    "$url?alt=sse"
                }
            }
        }

        val requestBuilder = Request.Builder().url(url)
        for ((key, value) in config.headers) {
            val resolvedValue = value
                .replace("{{API_KEY}}", apiKey)
                .replace("{{MODEL}}", model)
            requestBuilder.addHeader(key, resolvedValue)
        }

        val requestBodyString = buildRequestBodyString(systemContext, messages, isStream = true)
        val mediaType = config.headers["Content-Type"]?.toMediaTypeOrNull() ?: "application/json".toMediaTypeOrNull()
        val requestBody = requestBodyString.toRequestBody(mediaType)

        requestBuilder.post(requestBody)
        val request = requestBuilder.build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: UnknownHostException) {
            null
        } catch (e: Exception) {
            null
        }

        if (response == null) {
            emit("Seems this device is offline or LLM server is unreachable.")
            return@flow
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            Log.e("FlexibleLlmAdapter", "API streaming request failed with code ${response.code}: $errorBody")
            val errMsg = extractErrorMessage(errorBody) ?: "API error (HTTP ${response.code})."
            emit("Error: $errMsg")
            response.close()
            return@flow
        }

        val responseBody = response.body
        if (responseBody == null) {
            emit("Error: Empty response body")
            response.close()
            return@flow
        }

        responseBody.byteStream().bufferedReader().use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: break
                if (currentLine.startsWith("data: ")) {
                    val dataContent = currentLine.substring(6).trim()
                    if (dataContent == "[DONE]") {
                        break
                    }
                    if (dataContent.isNotEmpty()) {
                        val chunkText = extractChunkContent(dataContent, config.responsePath)
                        if (!chunkText.isNullOrEmpty()) {
                            emit(chunkText)
                        }
                    }
                } else {
                    var cleanedLine = currentLine.trim()
                    if (cleanedLine.endsWith(",")) {
                        cleanedLine = cleanedLine.substring(0, cleanedLine.length - 1).trim()
                    }
                    if (cleanedLine.startsWith("{") && cleanedLine.endsWith("}")) {
                        val chunkText = extractChunkContent(cleanedLine, config.responsePath)
                        if (!chunkText.isNullOrEmpty()) {
                            emit(chunkText)
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestBodyString(systemContext: String, messages: List<LlmMessage>, isStream: Boolean): String {
        val hasSystemContextPlaceholder = config.requestTemplate.contains("{{SYSTEM_CONTEXT}}")
        val messagesToSerialize = if (hasSystemContextPlaceholder) {
            messages.filter { it.role != "system" }
        } else {
            messages
        }

        val serializedMessagesBuilder = StringBuilder()
        serializedMessagesBuilder.append("[")
        messagesToSerialize.forEachIndexed { index, msg ->
            val roleValue = when (msg.role) {
                "system" -> config.systemRole ?: "system"
                "user" -> config.userRole
                "assistant" -> config.assistantRole
                else -> msg.role
            }
            val msgJson = if (msg.attachments.isNullOrEmpty()) {
                val escapedContent = escapeJsonString(msg.content)
                config.messageFormat
                    .replace("{{ROLE}}", roleValue)
                    .replace("{{CONTENT}}", escapedContent)
            } else {
                buildMultimodalMessageJson(roleValue, msg.content, msg.attachments, provider)
            }
            
            serializedMessagesBuilder.append(msgJson)
            if (index < messagesToSerialize.size - 1) {
                serializedMessagesBuilder.append(",")
            }
        }
        serializedMessagesBuilder.append("]")
        val messagesJsonArray = serializedMessagesBuilder.toString()

        var requestBodyString = config.requestTemplate
            .replace("{{MODEL}}", LlmConfig.escapeJsonString(model))
            .replace("{{MESSAGES}}", messagesJsonArray)

        if (hasSystemContextPlaceholder) {
            val resolvedSystemContext = systemContext.ifBlank {
                if (provider == LlmProvider.GEMINI) "You are a helpful AI assistant." else ""
            }
            requestBodyString = requestBodyString.replace("{{SYSTEM_CONTEXT}}", escapeJsonString(resolvedSystemContext))
        }

        if (isStream && provider != LlmProvider.GEMINI) {
            if (requestBodyString.contains("\"stream\": false")) {
                requestBodyString = requestBodyString.replace("\"stream\": false", "\"stream\": true")
            } else if (!requestBodyString.contains("\"stream\"")) {
                requestBodyString = requestBodyString.replaceFirst("{", "{\n  \"stream\": true,\n")
            }
        }
        return requestBodyString
    }

    private fun buildMultimodalMessageJson(
        role: String,
        content: String,
        attachments: List<LlmAttachment>,
        provider: LlmProvider
    ): String {
        return when (provider) {
            LlmProvider.GEMINI -> {
                val parts = mutableListOf<String>()
                parts.add("{\"text\": \"${escapeJsonString(content)}\"}")
                attachments.forEach { att ->
                    parts.add("{\"inlineData\": {\"mimeType\": \"${att.mimeType}\", \"data\": \"${att.base64Data}\"}}")
                }
                "{\"role\": \"$role\", \"parts\": [${parts.joinToString(",")}]}"
            }
            LlmProvider.ANTHROPIC -> {
                val contentArray = mutableListOf<String>()
                contentArray.add("{\"type\": \"text\", \"text\": \"${escapeJsonString(content)}\"}")
                attachments.forEach { att ->
                    if (att.mimeType.startsWith("image/")) {
                        contentArray.add("{\"type\": \"image\", \"source\": {\"type\": \"base64\", \"media_type\": \"${att.mimeType}\", \"data\": \"${att.base64Data}\"}}")
                    } else if (att.mimeType == "application/pdf") {
                        contentArray.add("{\"type\": \"document\", \"source\": {\"type\": \"base64\", \"media_type\": \"${att.mimeType}\", \"data\": \"${att.base64Data}\"}}")
                    }
                }
                "{\"role\": \"$role\", \"content\": [${contentArray.joinToString(",")}]}"
            }
            else -> { // OpenAI, Groq, Ollama, DeepSeek, OpenRouter, Custom
                val contentArray = mutableListOf<String>()
                contentArray.add("{\"type\": \"text\", \"text\": \"${escapeJsonString(content)}\"}")
                attachments.forEach { att ->
                    if (att.mimeType.startsWith("image/")) {
                        contentArray.add("{\"type\": \"image_url\", \"image_url\": {\"url\": \"data:${att.mimeType};base64,${att.base64Data}\"}}")
                    }
                }
                "{\"role\": \"$role\", \"content\": [${contentArray.joinToString(",")}]}"
            }
        }
    }

    private fun extractChunkContent(jsonStr: String, path: String): String? {
        var extracted = extractValueFromJson(jsonStr, path)
        if (!extracted.isNullOrEmpty()) return extracted

        if (path == "choices[0].message.content") {
            extracted = extractValueFromJson(jsonStr, "choices[0].delta.content")
            if (!extracted.isNullOrEmpty()) return extracted
        }

        if (path == "content[0].text") {
            extracted = extractValueFromJson(jsonStr, "delta.text")
            if (!extracted.isNullOrEmpty()) return extracted
        }
        
        for (fallbackPath in listOf(
            "choices[0].delta.content",
            "delta.text",
            "message.content",
            "candidates[0].content.parts[0].text"
        )) {
            extracted = extractValueFromJson(jsonStr, fallbackPath)
            if (!extracted.isNullOrEmpty()) return extracted
        }
        return null
    }

    private fun extractErrorMessage(errorBody: String): String? {
        return try {
            val element = json.parseToJsonElement(errorBody)
            if (element is kotlinx.serialization.json.JsonObject) {
                val errorObj = element["error"]
                if (errorObj is kotlinx.serialization.json.JsonObject) {
                    val msg = errorObj["message"]
                    if (msg is kotlinx.serialization.json.JsonPrimitive && msg !is kotlinx.serialization.json.JsonNull) return msg.content
                }
                val msg = element["message"] ?: element["error_description"] ?: element["msg"]
                if (msg is kotlinx.serialization.json.JsonPrimitive && msg !is kotlinx.serialization.json.JsonNull) return msg.content
            }
            null
        } catch (e: Exception) {
            errorBody.take(150)
        }
    }

    private fun extractResponseContent(rawResponse: String, path: String): String? {
        try {
            if (rawResponse.contains("error_code")) {
                val errExtract = extractValueFromJson(rawResponse, "msg")
                if (errExtract != null) return errExtract
            }

            val extracted = extractValueFromJson(rawResponse, path) ?: return null
            return extracted.trim()
        } catch (e: Exception) {
            Log.e("FlexibleLlmAdapter", "Error extracting LLM response", e)
            return null
        }
    }

    private fun extractValueFromJson(jsonStr: String, path: String): String? {
        return try {
            val jsonElement = json.parseToJsonElement(jsonStr)
            val tokens = path.split(Regex("[\\.\\[\\]]+")).filter { it.isNotEmpty() }
            var currentElement = jsonElement
            for (token in tokens) {
                currentElement = when (currentElement) {
                    is kotlinx.serialization.json.JsonObject -> {
                        currentElement[token] ?: return null
                    }
                    is kotlinx.serialization.json.JsonArray -> {
                        val index = token.toIntOrNull() ?: return null
                        if (index in 0 until currentElement.size) {
                            currentElement[index]
                        } else {
                            return null
                        }
                    }
                    else -> return null
                }
            }
            if (currentElement is kotlinx.serialization.json.JsonPrimitive && currentElement !is kotlinx.serialization.json.JsonNull) {
                currentElement.content
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun escapeJsonString(input: String): String {
        val builder = StringBuilder()
        for (ch in input) {
            when (ch) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        builder.append(String.format("\\u%04x", ch.code))
                    } else {
                        builder.append(ch)
                    }
                }
            }
        }
        return builder.toString()
    }
}
