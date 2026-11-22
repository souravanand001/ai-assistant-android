package com.app.assistant.llm

enum class LlmProvider(
    val displayName: String,
    val defaultUrl: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
    val config: LlmConfig
) {
    GROQ(
        displayName = "Groq",
        defaultUrl = "https://api.groq.com/openai/v1/chat/completions",
        defaultModel = "llama-3.3-70b-versatile",
        suggestedModels = listOf(
            "qwen/qwen3.8-27b",
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "mixtral-8x7b-32768",
            "gemma2-9b-it"
        ),
        config = LlmConfig(
            url = "https://api.groq.com/openai/v1/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer {{API_KEY}}",
                "Content-Type" to "application/json"
            ),
            responsePath = "choices[0].message.content",
            messageFormat = "{\"role\": \"{{ROLE}}\", \"content\": \"{{CONTENT}}\"}",
            systemRole = "system",
            userRole = "user",
            assistantRole = "assistant",
            requestTemplate = """
                {
                  "model": "{{MODEL}}",
                  "messages": {{MESSAGES}},
                  "temperature": 1.0,
                  "top_p": 1.0
                }
            """.trimIndent()
        )
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultUrl = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o",
        suggestedModels = listOf(
            "gpt-4o",
            "gpt-4o-mini",
            "gpt-4-turbo",
            "gpt-3.5-turbo"
        ),
        config = LlmConfig(
            url = "https://api.openai.com/v1/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer {{API_KEY}}",
                "Content-Type" to "application/json"
            ),
            responsePath = "choices[0].message.content",
            messageFormat = "{\"role\": \"{{ROLE}}\", \"content\": \"{{CONTENT}}\"}",
            systemRole = "system",
            userRole = "user",
            assistantRole = "assistant",
            requestTemplate = """
                {
                  "model": "{{MODEL}}",
                  "messages": {{MESSAGES}}
                }
            """.trimIndent()
        )
    ),
    OPEN_ROUTER(
        displayName = "OpenRouter",
        defaultUrl = "https://openrouter.ai/api/v1/chat/completions",
        defaultModel = "meta-llama/llama-3-8b-instruct:free",
        suggestedModels = listOf(
            "meta-llama/llama-3-8b-instruct:free",
            "google/gemini-2.0-flash-exp:free",
            "deepseek/deepseek-chat",
            "mistralai/mistral-7b-instruct:free"
        ),
        config = LlmConfig(
            url = "https://openrouter.ai/api/v1/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer {{API_KEY}}",
                "Content-Type" to "application/json"
            ),
            responsePath = "choices[0].message.content",
            messageFormat = "{\"role\": \"{{ROLE}}\", \"content\": \"{{CONTENT}}\"}",
            systemRole = "system",
            userRole = "user",
            assistantRole = "assistant",
            requestTemplate = """
                {
                  "model": "{{MODEL}}",
                  "messages": {{MESSAGES}}
                }
            """.trimIndent()
        )
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultUrl = "https://api.deepseek.com/chat/completions",
        defaultModel = "deepseek-chat",
        suggestedModels = listOf(
            "deepseek-chat",
            "deepseek-coder"
        ),
        config = LlmConfig(
            url = "https://api.deepseek.com/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer {{API_KEY}}",
                "Content-Type" to "application/json"
            ),
            responsePath = "choices[0].message.content",
            messageFormat = "{\"role\": \"{{ROLE}}\", \"content\": \"{{CONTENT}}\"}",
            systemRole = "system",
            userRole = "user",
            assistantRole = "assistant",
            requestTemplate = """
                {
                  "model": "{{MODEL}}",
                  "messages": {{MESSAGES}},
                  "temperature": 1.0
                }
            """.trimIndent()
        )
    ),
    OLLAMA(
        displayName = "Ollama (Local)",
        defaultUrl = "http://10.0.2.2:11434/api/chat",
        defaultModel = "llama3",
        suggestedModels = listOf(
            "llama3",
            "llama3.1",
            "mistral",
            "gemma2",
            "phi3"
        ),
        config = LlmConfig(
            url = "http://10.0.2.2:11434/api/chat",
            headers = mapOf(
                "Content-Type" to "application/json"
            ),
            responsePath = "message.content",
            messageFormat = "{\"role\": \"{{ROLE}}\", \"content\": \"{{CONTENT}}\"}",
            systemRole = "system",
            userRole = "user",
            assistantRole = "assistant",
            requestTemplate = """
                {
                  "model": "{{MODEL}}",
                  "messages": {{MESSAGES}},
                  "stream": false
                }
            """.trimIndent()
        )
    ),
    GEMINI(
        displayName = "Google Gemini",
        defaultUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent",
        defaultModel = "gemini-1.5-flash",
        suggestedModels = listOf(
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-1.5-flash-8b"
        ),
        config = LlmConfig(
            url = "https://generativelanguage.googleapis.com/v1beta/models/{{MODEL}}:generateContent?key={{API_KEY}}",
            headers = mapOf(
                "Content-Type" to "application/json"
            ),
            responsePath = "candidates[0].content.parts[0].text",
            messageFormat = "{\"role\": \"{{ROLE}}\", \"parts\": [{\"text\": \"{{CONTENT}}\"}]}",
            systemRole = null,
            userRole = "user",
            assistantRole = "model",
            requestTemplate = """
                {
                  "contents": {{MESSAGES}},
                  "systemInstruction": {
                    "parts": [
                      {
                        "text": "{{SYSTEM_CONTEXT}}"
                      }
                    ]
                  }
                }
            """.trimIndent()
        )
    ),
    ANTHROPIC(
        displayName = "Anthropic Claude",
        defaultUrl = "https://api.anthropic.com/v1/messages",
        defaultModel = "claude-3-5-sonnet-20240620",
        suggestedModels = listOf(
            "claude-3-5-sonnet-latest",
            "claude-3-5-haiku-latest",
            "claude-3-opus-latest",
            "claude-3-5-sonnet-20240620",
            "claude-3-5-haiku-20241022"
        ),
        config = LlmConfig(
            url = "https://api.anthropic.com/v1/messages",
            headers = mapOf(
                "x-api-key" to "{{API_KEY}}",
                "anthropic-version" to "2023-06-01",
                "content-type" to "application/json"
            ),
            responsePath = "content[0].text",
            messageFormat = "{\"role\": \"{{ROLE}}\", \"content\": \"{{CONTENT}}\"}",
            systemRole = null,
            userRole = "user",
            assistantRole = "assistant",
            requestTemplate = """
                {
                  "model": "{{MODEL}}",
                  "max_tokens": 1024,
                  "system": "{{SYSTEM_CONTEXT}}",
                  "messages": {{MESSAGES}}
                }
            """.trimIndent()
        )
    ),
    CUSTOM(
        displayName = "Custom Config",
        defaultUrl = "https://api.example.com/v1/chat/completions",
        defaultModel = "custom-model",
        suggestedModels = emptyList(),
        config = LlmConfig(
            url = "https://api.example.com/v1/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer {{API_KEY}}",
                "Content-Type" to "application/json"
            ),
            responsePath = "choices[0].message.content",
            messageFormat = "{\"role\": \"{{ROLE}}\", \"content\": \"{{CONTENT}}\"}",
            systemRole = "system",
            userRole = "user",
            assistantRole = "assistant",
            requestTemplate = """
                {
                  "model": "{{MODEL}}",
                  "messages": {{MESSAGES}}
                }
            """.trimIndent()
        )
    )
}
