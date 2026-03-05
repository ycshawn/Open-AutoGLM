package com.autoglm.phoneagent.model

import com.autoglm.phoneagent.action.AgentAction
import com.autoglm.phoneagent.action.ActionType
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Client for communicating with the AutoGLM model API.
 * Uses OpenAI-compatible chat completion endpoint.
 *
 * Supports mock mode for testing without network requests.
 */
class ModelClient(val config: ModelConfig) {

    // Mock mode support
    companion object {
        /** Mock mode flag - when enabled, returns preset responses instead of making network requests */
        var mockModeEnabled = false

        /** Queue of preset mock responses to return in sequence */
        private val mockResponseQueue = ArrayDeque<String>()

        /** Current step index in mock mode */
        var mockStepIndex = 0

        /**
         * Set preset mock responses for testing.
         * Each response will be returned in sequence as requests are made.
         *
         * @param responses List of raw model response strings
         */
        fun setMockResponses(vararg responses: String) {
            mockResponseQueue.clear()
            mockResponseQueue.addAll(responses)
            mockStepIndex = 0
            mockModeEnabled = true
        }

        /**
         * Add a single mock response to the queue.
         */
        fun addMockResponse(response: String) {
            mockResponseQueue.add(response)
            mockModeEnabled = true
        }

        /**
         * Clear all mock responses and disable mock mode.
         */
        fun clearMockResponses() {
            mockResponseQueue.clear()
            mockStepIndex = 0
            mockModeEnabled = false
        }

        /**
         * Check if mock mode is enabled.
         */
        fun isMockMode(): Boolean = mockModeEnabled && mockResponseQueue.isNotEmpty()

        /**
         * Get predefined mock responses for common actions.
         * These use the exact format expected by AgentAction.parse()
         */
        fun getMockTAP(x: Int = 500, y: Int = 300): String {
            return """
            我需要点击屏幕上的目标元素

            do(action="Tap", element=[$x, $y])
            """.trimIndent()
        }

        fun getMockSWIPE(): String {
            return """
            向上滚动查看更多内容

            do(action="Swipe", start=[500, 700], end=[500, 300])
            """.trimIndent()
        }

        fun getMockTYPE(text: String = "搜索内容"): String {
            return """
            在输入框中输入文字

            do(action="Type", text="$text")
            """.trimIndent()
        }

        fun getMockBACK(): String {
            return """
            返回上一页

            do(action="Back")
            """.trimIndent()
        }

        fun getMockWAIT(seconds: Int = 1): String {
            return """
            等待一段时间

            do(action="Wait", duration="$seconds seconds")
            """.trimIndent()
        }

        fun getMockLONG_PRESS(x: Int = 500, y: Int = 300): String {
            return """
            长按目标元素

            do(action="Long Press", element=[$x, $y])
            """.trimIndent()
        }

        fun getMockDOUBLE_TAP(x: Int = 500, y: Int = 300): String {
            return """
            双击目标元素

            do(action="Double Tap", element=[$x, $y])
            """.trimIndent()
        }

        fun getMockFINISH(message: String = "任务已完成"): String {
            return """
            $message

            finish(message="$message")
            """.trimIndent()
        }
    }

    private val gson = Gson()

    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        // Add logging interceptor for debug mode (HEADERS only, to avoid logging base64 images)
        if (config.language == ModelConfig.Language.CHINESE) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addInterceptor(loggingInterceptor)
        }

        builder.build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Callbacks
    var onThinking: ((String) -> Unit)? = null
    var onAction: ((String) -> Unit)? = null
    var onError: ((ModelError) -> Unit)? = null

    // Helper method for conditional logging
    private fun logDebug(message: String) {
        // Only log in debug builds or when explicitly needed
        println("[ModelClient] $message")
    }

    private fun logError(message: String) {
        println("[ModelClient] ERROR: $message")
    }

    /**
     * Send a chat completion request to the model.
     *
     * @param messages List of messages in the conversation
     * @return ModelResponse containing the parsed action and thinking
     * @throws ModelError if the request fails
     */
    suspend fun sendRequest(messages: List<MessageBuilder.Message>): ModelResponse =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            // Check if mock mode is enabled
            if (isMockMode()) {
                logDebug("=== MOCK MODE ENABLED ===")
                logDebug("使用预设响应，跳过网络请求")

                val mockContent = if (mockResponseQueue.isNotEmpty()) {
                    mockResponseQueue.removeFirst()
                } else {
                    // Fallback to a default TAP action
                    getMockTAP()
                }

                mockStepIndex++
                logDebug("Mock 步骤: $mockStepIndex")
                logDebug("Mock 响应: ${mockContent.take(100)}...")

                // Simulate network delay
                kotlinx.coroutines.delay(300)

                val timeToFirstToken = 300L
                val totalTime = System.currentTimeMillis() - startTime

                // Parse thinking and action from mock content
                val (thinking, action) = parseResponse(mockContent)

                onThinking?.invoke(thinking)
                onAction?.invoke(action)

                return@withContext ModelResponse(
                    thinking = thinking,
                    action = action,
                    rawContent = mockContent,
                    parsedAction = AgentAction.parse(action),
                    timeToFirstToken = timeToFirstToken,
                    totalTime = totalTime
                )
            }

            try {
                // Build request body
                val requestBody = buildRequestBody(messages)

                // Build HTTP request
                val request = Request.Builder()
                    .url(config.getChatCompletionUrl())
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(gson.toJson(requestBody).toRequestBody(jsonMediaType))
                    .build()

                // Execute request
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    // For 401 errors, provide helpful debugging info
                    if (response.code == 401) {
                        logError("401 认证失败 - 请检查 API Key 是否正确")
                        val apiKey = config.apiKey
                        when {
                            apiKey.isBlank() -> logError("API Key 为空")
                            apiKey.length < 10 -> logError("API Key 长度异常: ${apiKey.length}")
                            else -> logError("API Key: ${apiKey.take(10)}...")
                        }
                    } else {
                        logError("HTTP 请求失败: ${response.code}")
                    }

                    // Try to read error response body
                    val errorBody = response.body?.string()
                    if (!errorBody.isNullOrBlank()) {
                        logError("错误响应: ${errorBody.take(200)}")
                    }

                    throw ModelError.HttpError(response.code)
                }

                val responseBody = response.body?.string()
                    ?: throw ModelError.NoDataError

                // Parse response
                val responseJson = JsonParser.parseString(responseBody).asJsonObject
                val choices = responseJson.getAsJsonArray("choices")

                if (choices == null || choices.size() == 0) {
                    println("   ❌ 响应中没有 choices")
                    throw ModelError.ParsingError("No choices in response")
                }

                val firstChoice = choices[0].asJsonObject
                val message = firstChoice.getAsJsonObject("message")

                println("   === 响应消息详情 ===")
                println("   message keys: ${message.keySet()}")
                println("   has content: ${message.has("content")}")

                // Check content type
                val contentElement = message.get("content")
                println("   content type: ${contentElement?.javaClass?.simpleName}")

                // Content can be either a string or an array
                val content: String = when {
                    contentElement == null -> {
                        logError("响应 content 为 null")
                        throw ModelError.ParsingError("content is null")
                    }
                    contentElement.isJsonPrimitive -> {
                        contentElement.asString
                    }
                    contentElement.isJsonArray -> {
                        // Content is an array - concatenate text parts
                        val contentArray = contentElement.asJsonArray
                        contentArray.mapNotNull {
                            if (it.isJsonPrimitive) it.asString else null
                        }.joinToString("")
                    }
                    else -> {
                        logError("不支持的 content 类型: ${contentElement.javaClass}")
                        throw ModelError.ParsingError("Unsupported content type: ${contentElement.javaClass.simpleName}")
                    }
                }

                val timeToFirstToken = System.currentTimeMillis() - startTime
                val totalTime = System.currentTimeMillis() - startTime

                // Parse thinking and action from content
                val (thinking, action) = parseResponse(content)

                onThinking?.invoke(thinking)
                onAction?.invoke(action)

                ModelResponse(
                    thinking = thinking,
                    action = action,
                    rawContent = content,
                    parsedAction = AgentAction.parse(action),
                    timeToFirstToken = timeToFirstToken,
                    totalTime = totalTime
                )

            } catch (e: IOException) {
                val error = when (e) {
                    is java.net.UnknownHostException -> ModelError.NetworkError("Unknown host: ${config.baseURL}")
                    is java.net.SocketTimeoutException -> ModelError.NetworkError("Request timeout")
                    else -> ModelError.NetworkError(e.message ?: "Network error")
                }
                onError?.invoke(error)
                throw error
            } catch (e: Exception) {
                if (e is ModelError) {
                    onError?.invoke(e)
                    throw e
                } else {
                    val error = ModelError.ParsingError(e.message ?: "Unknown error")
                    onError?.invoke(error)
                    throw error
                }
            }
        }

    /**
     * Build the request body for the API call.
     */
    private fun buildRequestBody(messages: List<MessageBuilder.Message>): Map<String, Any> {
        return mapOf(
            "model" to config.modelName,
            "messages" to messages.map { it.toJson() },
            "max_tokens" to config.maxTokens,
            "temperature" to config.temperature,
            "top_p" to config.topP,
            "frequency_penalty" to config.frequencyPenalty,
            "stream" to false
        )
    }

    /**
     * Parse the model response to extract thinking and action.
     *
     * Expected formats:
     * - Chinese: Two lines separated by blank line - first line is thinking, second is action
     * - English: XML tags <thinking>...</thinking> and <answer>...</answer>
     */
    private fun parseResponse(content: String): Pair<String, String> {
        val trimmed = content.trim()

        return when (config.language) {
            ModelConfig.Language.CHINESE -> parseChineseResponse(trimmed)
            ModelConfig.Language.ENGLISH -> parseEnglishResponse(trimmed)
        }
    }

    /**
     * Parse Chinese format response (two lines separated by blank line).
     */
    private fun parseChineseResponse(content: String): Pair<String, String> {
        // Try to extract thinking before action keywords
        val actionPatterns = listOf(
            "do(action=\"",
            "do(action='",
            "finish(message=\"",
            "finish(message='"
        )

        for (pattern in actionPatterns) {
            val index = content.indexOf(pattern)
            if (index != -1) {
                val thinking = content.substring(0, index).trim()
                val actionStart = index
                val action = content.substring(actionStart).trim()
                return thinking to action
            }
        }

        // Split by double newline as fallback
        val parts = content.split("\n\n", "\r\n\r\n")
        return when {
            parts.size >= 2 -> parts[0].trim() to parts[1].trim()
            content.contains("\n") -> {
                val lines = content.split("\n", limit = 2)
                lines[0].trim() to (lines.getOrNull(1)?.trim() ?: "")
            }
            else -> "" to content
        }
    }

    /**
     * Parse English format response (XML tags).
     */
    private fun parseEnglishResponse(content: String): Pair<String, String> {
        val thinkingRegex = """<thinking>(.*?)</thinking>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val answerRegex = """<answer>(.*?)</answer>""".toRegex(RegexOption.DOT_MATCHES_ALL)

        val thinking = thinkingRegex.find(content)?.groupValues?.get(1)?.trim() ?: ""
        val answer = answerRegex.find(content)?.groupValues?.get(1)?.trim() ?: ""

        return if (thinking.isNotEmpty() && answer.isNotEmpty()) {
            thinking to answer
        } else {
            // Fallback to Chinese format parsing
            parseChineseResponse(content)
        }
    }

    /**
     * Error types for model operations.
     */
    sealed class ModelError(message: String) : Exception(message) {
        data class HttpError(val statusCode: Int) : ModelError("HTTP error: $statusCode")

        data object NoDataError : ModelError("No data in response")

        data class ParsingError(val detail: String) : ModelError("Parsing error: $detail")

        data class NetworkError(val detail: String) : ModelError("Network error: $detail")
    }
}

/**
 * Response from the model API.
 *
 * @property thinking The model's thinking process
 * @property action The action command to execute
 * @property rawContent The raw response content
 * @property parsedAction The parsed action object
 * @property timeToFirstToken Time to first token in milliseconds
 * @property totalTime Total request time in milliseconds
 */
data class ModelResponse(
    val thinking: String,
    val action: String,
    val rawContent: String,
    val parsedAction: AgentAction,
    val timeToFirstToken: Long,
    val totalTime: Long
) {
    /**
     * Check if the response indicates completion.
     */
    val isFinish: Boolean
        get() = parsedAction.type == ActionType.FINISH

    /**
     * Check if the response indicates user takeover.
     */
    val isTakeOver: Boolean
        get() = parsedAction.type == ActionType.TAKE_OVER

    /**
     * Check if the action is unknown/unparseable.
     */
    val isUnknown: Boolean
        get() = parsedAction.type == ActionType.UNKNOWN

    /**
     * Get the finish message if this is a finish action.
     */
    val finishMessage: String?
        get() = if (isFinish) parsedAction.message else null

    /**
     * Get the takeover message if this is a takeover action.
     */
    val takeoverMessage: String?
        get() = if (isTakeOver) parsedAction.message else null
}
