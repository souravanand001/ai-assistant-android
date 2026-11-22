package com.app.assistant.llm

data class LlmConfig(
    val url: String,
    val method: String = "POST",
    val headers: Map<String, String> = emptyMap(),
    val responsePath: String,
    val messageFormat: String,
    val systemRole: String? = null,
    val userRole: String = "user",
    val assistantRole: String = "assistant",
    val requestTemplate: String
) {
    fun resolveCompletionUrl(): String = resolveCompletionUrl(url)

    companion object {
        fun resolveCompletionUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            if (trimmed.isBlank()) return trimmed
            if (trimmed.endsWith("/chat/completions")) return trimmed

            val withoutSlash = trimmed.trimEnd('/')
            if (withoutSlash.endsWith("/v1")) {
                return "$withoutSlash/chat/completions"
            }
            if (withoutSlash.endsWith("/models")) {
                val base = withoutSlash.substringBeforeLast("/models")
                return if (base.endsWith("/v1")) "$base/chat/completions" else "$base/v1/chat/completions"
            }
            if (withoutSlash.matches(Regex("^https?://[^/]+$"))) {
                return "$withoutSlash/v1/chat/completions"
            }
            return trimmed
        }

        fun resolveModelsUrl(rawUrl: String): String? {
            val trimmed = rawUrl.trim()
            if (trimmed.isBlank()) return null

            val withoutSlash = trimmed.trimEnd('/')
            if (withoutSlash.endsWith("/models")) return withoutSlash
            if (withoutSlash.endsWith("/chat/completions")) {
                val base = withoutSlash.substringBeforeLast("/chat/completions")
                return "$base/models"
            }
            if (withoutSlash.endsWith("/v1")) {
                return "$withoutSlash/models"
            }
            if (withoutSlash.matches(Regex("^https?://[^/]+$"))) {
                return "$withoutSlash/v1/models"
            }
            if (withoutSlash.contains("/chat/completions")) {
                return withoutSlash.substringBeforeLast("/chat/completions") + "/models"
            }
            return "$withoutSlash/models"
        }

        fun escapeJsonString(input: String): String {
            val builder = StringBuilder()
            for (c in input) {
                when (c) {
                    '\"' -> builder.append("\\\"")
                    '\\' -> builder.append("\\\\")
                    '\b' -> builder.append("\\b")
                    '\u000C' -> builder.append("\\f")
                    '\n' -> builder.append("\\n")
                    '\r' -> builder.append("\\r")
                    '\t' -> builder.append("\\t")
                    else -> {
                        if (c.code in 0x00..0x1F) {
                            builder.append(String.format("\\u%04x", c.code))
                        } else {
                            builder.append(c)
                        }
                    }
                }
            }
            return builder.toString()
        }
    }
}
