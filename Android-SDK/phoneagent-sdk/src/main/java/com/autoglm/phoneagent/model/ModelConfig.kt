package com.autoglm.phoneagent.model

/**
 * Configuration for the AutoGLM model client.
 *
 * @property baseURL Base URL for the API endpoint
 * @property apiKey API key for authentication
 * @property modelName Name of the model to use
 * @property maxTokens Maximum tokens in the response (default: 3000)
 * @property temperature Sampling temperature (default: 0.0)
 * @property topP Top-p sampling parameter (default: 0.85)
 * @property frequencyPenalty Frequency penalty (default: 0.2)
 * @property language Language for prompts (Chinese or English)
 */
data class ModelConfig(
    val baseURL: String = "https://open.bigmodel.cn/api/paas/v4",
    val apiKey: String = "",
    val modelName: String = "autoglm-phone",
    val maxTokens: Int = 3000,
    val temperature: Double = 0.0,
    val topP: Double = 0.85,
    val frequencyPenalty: Double = 0.2,
    val language: Language = Language.CHINESE
) {
    /**
     * Clean and normalize the base URL to fix common input errors.
     * - Removes accidental leading 'c' (e.g., 'chttps' -> 'https')
     * - Ensures proper URL format
     */
    private fun cleanURL(url: String): String {
        var cleaned = url.trim()

        // Fix common typo: chttps -> https
        if (cleaned.startsWith("chttps")) {
            cleaned = "https" + cleaned.removePrefix("chttps")
        } else if (cleaned.startsWith("chttp")) {
            cleaned = "http" + cleaned.removePrefix("chttp")
        }

        return cleaned
    }

    /**
     * Get the cleaned base URL.
     */
    val cleanBaseURL: String
        get() = cleanURL(baseURL)
    /**
     * Supported languages for system prompts.
     */
    enum class Language(val code: String) {
        CHINESE("cn"),
        ENGLISH("en");

        companion object {
            fun fromCode(code: String): Language {
                return values().find { it.code == code } ?: CHINESE
            }
        }
    }

    companion object {
        /**
         * Default configuration for Chinese language.
         */
        val DEFAULT = ModelConfig()

        /**
         * Default configuration for English language.
         */
        val ENGLISH = ModelConfig(language = Language.ENGLISH)

        /**
         * Local development configuration.
         */
        val LOCAL = ModelConfig(
            baseURL = "http://localhost:8000/v1",
            modelName = "autoglm-phone-9b"
        )

        /**
         * ModelScope configuration.
         */
        val MODELSCOPE = ModelConfig(
            baseURL = "https://api-inference.modelscope.cn/v1",
            modelName = "autoglm-phone-9b"
        )
    }

    /**
     * Validate the configuration.
     * @throws IllegalArgumentException if configuration is invalid
     */
    fun validate() {
        val url = cleanBaseURL
        require(url.isNotBlank()) { "baseURL must not be blank" }
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        require(modelName.isNotBlank()) { "modelName must not be blank" }
        require(maxTokens > 0) { "maxTokens must be greater than 0" }
        require(temperature in 0.0..2.0) { "temperature must be between 0.0 and 2.0" }
        require(topP in 0.0..1.0) { "topP must be between 0.0 and 1.0" }
        require(frequencyPenalty in -2.0..2.0) { "frequencyPenalty must be between -2.0 and 2.0" }
    }

    /**
     * Get the full chat completion URL.
     */
    fun getChatCompletionUrl(): String {
        val url = cleanBaseURL
        return when {
            url.endsWith("/chat/completions") -> url
            url.endsWith("/v1") -> "$url/chat/completions"
            url.endsWith("/paas/v4") -> "$url/chat/completions"
            else -> "$url/chat/completions"
        }
    }
}
