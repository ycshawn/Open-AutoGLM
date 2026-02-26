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
 */
class ModelClient(val config: ModelConfig) {

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
            println("🌐 ModelClient.sendRequest() 开始")
            println("   URL: ${config.getChatCompletionUrl()}")
            println("   模型: ${config.modelName}")
            println("   消息数: ${messages.size}")

            try {
                // Build request body
                val requestBody = buildRequestBody(messages)
                println("   请求体已构建")

                // Debug: Print the actual JSON being sent (without image data)
                println("   === 实际发送的 JSON ===")
                val jsonToSend = gson.toJson(requestBody)
                // Truncate image data for logging
                val truncatedJson = jsonToSend.replace(Regex("\"image_url\":\\s*\\{[^}]*\"url\":\\s*\"data:image/[^\"]+\"\\}"), "\"image_url\": {\"url\": \"data:image/png;base64,<截取>\"}")
                println("   $truncatedJson")
                println("   ====================")

                // Build HTTP request
                val request = Request.Builder()
                    .url(config.getChatCompletionUrl())
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(gson.toJson(requestBody).toRequestBody(jsonMediaType))
                    .build()

                println("   HTTP 请求已构建")
                println("   === HTTP 请求头 ===")
                println("   URL: ${config.getChatCompletionUrl()}")
                println("   Method: POST")
                println("   Authorization: Bearer ${config.apiKey.take(20)}...")
                println("   Content-Type: application/json")
                println("   === 请求头结束 ===")

                // Execute request
                println("   发送 HTTP 请求...")
                val response = client.newCall(request).execute()
                println("   HTTP 响应收到: code=${response.code}")

                if (!response.isSuccessful) {
                    println("   ❌ HTTP 请求失败: ${response.code}")

                    // For 401 errors, provide helpful debugging info
                    if (response.code == 401) {
                        println("   🔑 401 认证失败 - 请检查:")
                        println("      1. API Key 是否正确设置")
                        println("      2. API Key 是否有效（未过期）")
                        println("      3. API Key 是否有访问该模型的权限")
                        println("      4. BaseURL 是否正确: ${config.baseURL}")
                        println("      5. 模型名称是否正确: ${config.modelName}")

                        // Log API Key info (safely)
                        val apiKey = config.apiKey
                        when {
                            apiKey.isBlank() -> println("      ❌ API Key 为空!")
                            apiKey.length < 10 -> println("      ⚠️ API Key 长度异常 (${apiKey.length} 字符)")
                            !apiKey.startsWith("sk-") && !apiKey.startsWith("eyJ") -> println("      ⚠️ API Key 格式可能不正确")
                            else -> println("      ✅ API Key 格式看起来正常 (${apiKey.take(10)}...)")
                        }
                    }

                    // Try to read error response body
                    val errorBody = response.body?.string()
                    if (!errorBody.isNullOrBlank()) {
                        println("   错误响应: ${errorBody.take(500)}")
                    }

                    throw ModelError.HttpError(response.code)
                }

                val responseBody = response.body?.string()
                    ?: throw ModelError.NoDataError
                println("   响应体长度: ${responseBody.length}")

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
                        println("   ❌ content 为 null")
                        throw ModelError.ParsingError("content is null")
                    }
                    contentElement.isJsonPrimitive -> {
                        // Content is a string
                        val str = contentElement.asString
                        println("   ✅ content 是字符串，长度: ${str.length}")
                        str
                    }
                    contentElement.isJsonArray -> {
                        // Content is an array - concatenate text parts
                        val contentArray = contentElement.asJsonArray
                        println("   ✅ content 是数组，元素数量: ${contentArray.size()}")
                        val result = contentArray.mapNotNull {
                            if (it.isJsonPrimitive) it.asString else null
                        }.joinToString("")
                        println("   合并后长度: ${result.length}")
                        result
                    }
                    else -> {
                        println("   ❌ content 类型不支持: ${contentElement.javaClass}")
                        println("   content 原始值: $contentElement")
                        throw ModelError.ParsingError("Unsupported content type: ${contentElement.javaClass.simpleName}")
                    }
                }
                println("   响应内容: ${content.take(200)}...")
                println("   === 响应消息详情结束 ===")
                println("   ========== 完整响应内容（用于调试）==========")
                println(content)
                println("   ========== 完整响应内容结束 ==========")

                val timeToFirstToken = System.currentTimeMillis() - startTime
                val totalTime = System.currentTimeMillis() - startTime

                // Parse thinking and action from content
                val (thinking, action) = parseResponse(content)
                println("   解析结果:")
                println("     Thinking 长度: ${thinking.length}")
                println("     Thinking 内容: ${thinking.take(100)}...")
                println("     Action 长度: ${action.length}")
                println("     Action 内容: ${action.take(100)}...")

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
        val requestBody = mapOf(
            "model" to config.modelName,
            "messages" to messages.map { it.toJson() },
            "max_tokens" to config.maxTokens,
            "temperature" to config.temperature,
            "top_p" to config.topP,
            "frequency_penalty" to config.frequencyPenalty,
            "stream" to false
        )

        // Debug: Print request body summary (without full image data)
        println("   === 请求体详情 ===")
        println("   model: ${requestBody["model"]}")
        println("   max_tokens: ${requestBody["max_tokens"]}")
        println("   temperature: ${requestBody["temperature"]}")
        println("   top_p: ${requestBody["top_p"]}")
        println("   frequency_penalty: ${requestBody["frequency_penalty"]}")
        println("   stream: ${requestBody["stream"]}")

        val msgs = requestBody["messages"] as List<*>
        println("   messages 数量: ${msgs.size}")
        msgs.forEachIndexed { index, msg ->
            val msgMap = msg as Map<*, *>
            val role = msgMap["role"]
            val content = msgMap["content"]

            when {
                content is String -> {
                    println("   消息 $index: role=$role, type=text, length=${(content as String).length}")
                    println("              内容预览: ${(content as String).take(100)}...")
                }
                content is List<*> -> {
                    @Suppress("USELESS_CAST")
                    val contentList = content as List<*>
                    println("   消息 $index: role=$role, type=multipart, items=${contentList.size}")
                    contentList.forEach { item ->
                        if (item is Map<*, *>) {
                            val itemMap = item as Map<*, *>
                            val type = itemMap["type"]
                            if (type == "image_url") {
                                val imageUrl = (itemMap["image_url"] as Map<*, *>)["url"] as String
                                val dataLength = imageUrl.length
                                println("              - 图片: data URL, 长度=$dataLength 字符")
                            } else {
                                println("              - $type")
                            }
                        }
                    }
                }
                else -> {
                    println("   消息 $index: role=$role, type=unknown, content class=${content?.javaClass?.simpleName}")
                }
            }
        }
        println("   === 请求体结束 ===")

        return requestBody
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
