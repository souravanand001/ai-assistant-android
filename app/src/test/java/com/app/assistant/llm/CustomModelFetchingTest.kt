package com.app.assistant.llm

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CustomModelFetchingTest {

    @Test
    fun parseModelsResponse_realLlamaServerJson_extractsModelId() {
        val llamaServerJson = """
            {"models":[{"name":"bartowski/SmolLM2-135M-Instruct-GGUF:Q4_K_M","model":"bartowski/SmolLM2-135M-Instruct-GGUF:Q4_K_M","modified_at":"","size":"","digest":"","type":"model","description":"","tags":[""],"capabilities":["completion"],"parameters":"","details":{"parent_model":"","format":"gguf","family":"","families":[""],"parameter_size":"","quantization_level":""}}],"object":"list","data":[{"id":"bartowski/SmolLM2-135M-Instruct-GGUF:Q4_K_M","aliases":["bartowski/SmolLM2-135M-Instruct-GGUF:Q4_K_M"],"tags":[],"object":"model","architecture":{"input_modalities":["text"],"output_modalities":["text"]},"created":1795319873,"owned_by":"llamacpp","meta":{"vocab_type":2,"n_vocab":49152,"n_ctx":8192,"n_ctx_train":8192,"n_embd":576,"n_params":134515008,"size":103668480,"ftype":"Q4_K - Medium"}}]}
        """.trimIndent()

        val result = ModelCapabilityProber.parseModelsResponse(LlmProvider.CUSTOM, llamaServerJson)

        assertEquals(1, result.size)
        assertEquals("bartowski/SmolLM2-135M-Instruct-GGUF:Q4_K_M", result[0])
    }

    @Test
    fun parseModelsResponse_filePathModelId_extractsFilePath() {
        val jsonWithFilePath = """
            {
              "object": "list",
              "data": [
                {
                  "id": "/path/to/model.gguf",
                  "object": "model",
                  "created": 123456789,
                  "owned_by": "llamacpp"
                }
              ]
            }
        """.trimIndent()

        val result = ModelCapabilityProber.parseModelsResponse(LlmProvider.CUSTOM, jsonWithFilePath)

        assertEquals(1, result.size)
        assertEquals("/path/to/model.gguf", result[0])
    }

    @Test
    fun testUrlResolution_variousEndpoints() {
        // Base IP without path
        assertEquals(
            "http://192.168.1.3:11434/v1/models",
            LlmConfig.resolveModelsUrl("http://192.168.1.3:11434")
        )
        assertEquals(
            "http://192.168.1.3:11434/v1/chat/completions",
            LlmConfig.resolveCompletionUrl("http://192.168.1.3:11434")
        )

        // Base IP with trailing slash
        assertEquals(
            "http://192.168.1.3:11434/v1/models",
            LlmConfig.resolveModelsUrl("http://192.168.1.3:11434/")
        )
        assertEquals(
            "http://192.168.1.3:11434/v1/chat/completions",
            LlmConfig.resolveCompletionUrl("http://192.168.1.3:11434/")
        )

        // URL with /v1
        assertEquals(
            "http://192.168.1.3:11434/v1/models",
            LlmConfig.resolveModelsUrl("http://192.168.1.3:11434/v1")
        )
        assertEquals(
            "http://192.168.1.3:11434/v1/chat/completions",
            LlmConfig.resolveCompletionUrl("http://192.168.1.3:11434/v1")
        )

        // URL with /v1/
        assertEquals(
            "http://192.168.1.3:11434/v1/models",
            LlmConfig.resolveModelsUrl("http://192.168.1.3:11434/v1/")
        )
        assertEquals(
            "http://192.168.1.3:11434/v1/chat/completions",
            LlmConfig.resolveCompletionUrl("http://192.168.1.3:11434/v1/")
        )

        // URL already pointing to /models
        assertEquals(
            "http://192.168.1.3:11434/models",
            LlmConfig.resolveModelsUrl("http://192.168.1.3:11434/models")
        )
        assertEquals(
            "http://192.168.1.3:11434/v1/chat/completions",
            LlmConfig.resolveCompletionUrl("http://192.168.1.3:11434/models")
        )

        // URL already pointing to /chat/completions
        assertEquals(
            "http://192.168.1.3:11434/v1/models",
            LlmConfig.resolveModelsUrl("http://192.168.1.3:11434/v1/chat/completions")
        )
        assertEquals(
            "http://192.168.1.3:11434/v1/chat/completions",
            LlmConfig.resolveCompletionUrl("http://192.168.1.3:11434/v1/chat/completions")
        )
    }

    @Test
    fun autoDetectCustomServer_openAiEndpoint_detectsOpenAiCompatible() = runBlocking {
        val llamaServerJson = """
            {"models":[{"name":"bartowski/SmolLM2-135M-Instruct-GGUF:Q4_K_M","model":"bartowski/SmolLM2-135M-Instruct-GGUF:Q4_K_M","modified_at":"","size":"","digest":"","type":"model","description":"","tags":[""],"capabilities":["completion"],"parameters":"","details":{"parent_model":"","format":"gguf","family":"","families":[""],"parameter_size":"","quantization_level":""}}],"object":"list","data":[{"id":"bartowski/SmolLM2-135M-Instruct-GGUF:Q4_K_M","aliases":["bartowski/SmolLM2-135M-Instruct-GGUF:Q4_K_M"],"tags":[],"object":"model","architecture":{"input_modalities":["text"],"output_modalities":["text"]},"created":1795319873,"owned_by":"llamacpp","meta":{"vocab_type":2,"n_vocab":49152,"n_ctx":8192,"n_ctx_train":8192,"n_embd":576,"n_params":134515008,"size":103668480,"ftype":"Q4_K - Medium"}}]}
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                if (request.url.encodedPath.endsWith("/models")) {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(llamaServerJson.toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            })
            .build()

        val detected = ModelCapabilityProber.autoDetectCustomServer(
            client = mockClient,
            baseUrl = "http://192.168.1.3:11434",
            apiKey = ""
        )

        assertNotNull(detected)
        assertEquals("OpenAI Compatible", detected!!.serverType)
        assertEquals("http://192.168.1.3:11434/v1/chat/completions", detected.completionUrl)
        assertEquals("choices[0].message.content", detected.responsePath)
        assertEquals(listOf("bartowski/SmolLM2-135M-Instruct-GGUF:Q4_K_M"), detected.availableModels)
    }

    @Test
    fun autoDetectCustomServer_ollamaEndpoint_detectsOllamaNative() = runBlocking {
        val ollamaTagsJson = """
            {"models":[{"name":"llama3:latest","model":"llama3:latest","modified_at":"2024-05-01T00:00:00Z","size":4000000000}]}
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                if (request.url.encodedPath.endsWith("/api/tags")) {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ollamaTagsJson.toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            })
            .build()

        val detected = ModelCapabilityProber.autoDetectCustomServer(
            client = mockClient,
            baseUrl = "http://192.168.1.3:11434",
            apiKey = ""
        )

        assertNotNull(detected)
        assertEquals("Ollama Native", detected!!.serverType)
        assertEquals("http://192.168.1.3:11434/api/chat", detected.completionUrl)
        assertEquals("message.content", detected.responsePath)
        assertEquals(listOf("llama3:latest"), detected.availableModels)
    }
}
