package com.autoglm.phoneagent.model

import com.autoglm.phoneagent.capture.ScreenInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds messages for the AutoGLM API.
 * Handles system prompts and user messages with screenshots.
 */
object MessageBuilder {

    /**
     * Represents a message in the chat conversation.
     *
     * @property role The role of the message sender (system, user, or assistant)
     * @property content The content items (text and/or images)
     */
    data class Message(
        val role: MessageRole,
        val content: List<ContentItem>
    ) {
        /**
         * Convert message to JSON for API request.
         * Always use array format for content to ensure compatibility.
         */
        fun toJson(): Map<String, Any> {
            return mapOf(
                "role" to role.value,
                "content" to content.map { it.toJson() }
            )
        }
    }

    /**
     * Role of the message sender.
     */
    enum class MessageRole(val value: String) {
        SYSTEM("system"),
        USER("user"),
        ASSISTANT("assistant");

        companion object {
            fun fromValue(value: String): MessageRole {
                return values().find { it.value == value } ?: USER
            }
        }
    }

    /**
     * Content item in a message (text or image).
     */
    sealed class ContentItem {
        /**
         * Text content.
         */
        data class Text(val text: String) : ContentItem() {
            override val value: Any = text
            override fun toJson(): Any = mapOf(
                "type" to "text",
                "text" to text
            )
        }

        /**
         * Image content (base64 encoded).
         */
        data class Image(val dataUrl: String) : ContentItem() {
            override val value: Any = mapOf(
                "type" to "image_url",
                "image_url" to mapOf("url" to dataUrl)
            )

            override fun toJson(): Any = value
        }

        abstract val value: Any
        abstract fun toJson(): Any
    }

    /**
     * Build the system prompt message.
     *
     * @param language The language for the prompt (Chinese or English)
     * @return System message with the prompt
     */
    fun buildSystemMessage(language: ModelConfig.Language = ModelConfig.Language.CHINESE): Message {
        val prompt = when (language) {
            ModelConfig.Language.CHINESE -> getSystemPromptChinese()
            ModelConfig.Language.ENGLISH -> getSystemPromptEnglish()
        }

        return Message(
            role = MessageRole.SYSTEM,
            content = listOf(ContentItem.Text(prompt))
        )
    }

    /**
     * Build a user message with instruction and screenshot.
     *
     * @param instruction The user's instruction/task description
     * @param screenshotBase64 Base64 encoded screenshot (data URL format)
     * @param screenInfo Optional screen information
     * @return User message with instruction and screenshot
     */
    fun buildUserMessage(
        instruction: String,
        screenshotBase64: String,
        screenInfo: ScreenInfo? = null
    ): Message {
        val content = mutableListOf<ContentItem>()

        // Add instruction
        content.add(ContentItem.Text(buildInstructionText(instruction, screenInfo)))

        // Add screenshot
        content.add(ContentItem.Image(screenshotBase64))

        return Message(
            role = MessageRole.USER,
            content = content
        )
    }

    /**
     * Build a user message with only screenshot (for subsequent steps).
     *
     * @param screenshotBase64 Base64 encoded screenshot (data URL format)
     * @param screenInfo Optional screen information
     * @return User message with screenshot only
     */
    fun buildFollowUpMessage(
        screenshotBase64: String,
        screenInfo: ScreenInfo? = null
    ): Message {
        val content = mutableListOf<ContentItem>()

        // Add brief instruction
        val instruction = if (screenInfo != null) {
            "请继续执行任务。当前屏幕: ${screenInfo.screenName}"
        } else {
            "请继续执行任务。"
        }
        content.add(ContentItem.Text(instruction))

        // Add screenshot
        content.add(ContentItem.Image(screenshotBase64))

        return Message(
            role = MessageRole.USER,
            content = content
        )
    }

    /**
     * Build the instruction text with screen information.
     */
    private fun buildInstructionText(instruction: String, screenInfo: ScreenInfo?): String {
        return if (screenInfo != null) {
            """$instruction

${screenInfo.toJsonString()}"""
        } else {
            instruction
        }
    }

    /**
     * Get the Chinese system prompt.
     */
    private fun getSystemPromptChinese(): String {
        val dateFormatter = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA)
        val dateString = dateFormatter.format(Date())

        return """
        今天的日期是: $dateString

        你是一个智能体分析专家，可以根据操作历史和当前状态图执行一系列操作来完成任务。

        ## 输出格式要求（必须严格遵守）

        你的输出必须包含两部分，用空行分隔：

        **第一部分**：思考过程 - 简要说明你为什么选择这个操作
        **第二部分**：操作指令 - 必须严格遵循下方定义的格式

        ⚠️ 重要：你必须输出可以直接执行的操作指令，不能只描述需要做什么！

        ### 输出示例：

        示例1 - 点击操作：
        用户要求点击登录按钮，它位于屏幕中央
        do(action="Tap", element=[500,500])

        示例2 - 输入操作：
        需要在用户名输入框中输入"ADMIN"
        do(action="Type", text="ADMIN")

        示例3 - 完成任务：
        已经成功完成了搜索任务
        finish(message="已完成搜索，找到相关商品")

        ## 操作指令格式

        所有操作指令必须严格遵循以下格式，**不要使用其他格式**：

        1. **点击**: do(action="Tap", element=[x,y])
           - x,y 是0-999之间的坐标值

        2. **输入**: do(action="Type", text="xxx")
           - text 是要输入的文本内容

        3. **滑动**: do(action="Swipe", start=[x1,y1], end=[x2,y2])
           - 从起始坐标滑动到结束坐标

        4. **长按**: do(action="Long Press", element=[x,y])

        5. **双击**: do(action="Double Tap", element=[x,y])

        6. **返回**: do(action="Back")

        7. **等待**: do(action="Wait", duration="x seconds")

        8. **接管**: do(action="Take_over", message="xxx")

        9. **完成**: finish(message="xxx")

        ## 重要规则

        1. 屏幕坐标系统从左上角 (0,0) 到右下角 (999,999)
        2. 所有坐标值必须在 0-999 范围内
        3. 操作指令中的文本必须用双引号包裹
        4. 不要使用JSON、Python字典或其他格式
        5. 只输出一条指令，不要输出多条
        6. 必须输出完整的 do(action=...) 或 finish(message=...) 格式

        ## ⚠️ 屏幕信息识别规则（重要）

        你是一个自动化助手，屏幕上可能会显示以下信息，请**忽略**这些信息：

        1. **忽略操作历史显示**：
           - 任何显示"步骤"、"Step"、"操作"、"Action"等字样的内容
           - 任何显示"思考"、"Thinking"、"任务执行"等字样的内容
           - 这些是自动化系统自身的调试信息，不是应用内容

        2. **忽略配置和状态显示**：
           - 任何显示"API Key"、"BaseURL"、"模型"等配置信息
           - 任何显示"执行中"、"Running"、"等待中"等状态信息
           - 任何显示"错误"、"Error"、"警告"等系统提示

        3. **专注于应用实际内容**：
           - 只关注应用本身的 UI 元素（按钮、输入框、列表、文本等）
           - 根据应用的实际功能状态做出判断
           - 不要被屏幕上显示的自动化系统信息干扰

        4. **操作验证规则**：
           - 通过观察应用 UI 的实际变化来判断操作是否生效
           - 例如：点击按钮后，如果有弹窗或页面跳转，说明操作成功
           - 如果点击后界面无变化，可能需要重试或调整操作

        ## 执行规则

        1. 在执行下一步操作前请一定要检查上一步的操作是否生效，如果点击没生效，可能因为app反应较慢，请先稍微等待一下，如果还是不生效请调整一下点击位置重试。
        2. 如果当前页面找不到目标信息，可以尝试滑动查找。
        3. 请严格遵循用户意图执行任务，用户的特殊要求可以执行多次搜索、滑动查找。
        4. 在结束任务前请一定要仔细检查任务是否完整准确的完成。
        5. 如果遇到需要验证码、指纹识别等情况，使用 Take_over 操作请求用户协助。

        ## 错误示例（不要这样输出）

        ❌ 错误1：我需要点击用户名输入框，然后输入"ADMIN"
        ❌ 错误2：1. 点击用户名框 2. 输入ADMIN
        ❌ 错误3：在用户名框中输入：ADMIN

        ## 正确示例（必须这样输出）

        ✅ 正确1：do(action="Tap", element=[500,300])
        ✅ 正确2：do(action="Type", text="ADMIN")
        ✅ 正确3：do(action="Tap", element=[500,300])
        do(action="Type", text="ADMIN")
        """.trimIndent()
    }

    /**
     * Get the English system prompt.
     */
    private fun getSystemPromptEnglish(): String {
        val dateFormatter = SimpleDateFormat("MMMM dd, yyyy EEEE", Locale.US)
        val dateString = dateFormatter.format(Date())

        return """
        Today's date is: $dateString

        You are an intelligent agent expert that can execute a series of operations to complete tasks based on operation history and current screen state.
        You must strictly output in the following format:

        <thinking>{think}</thinking>
        <answer>{action}</answer>

        Where:
        - {think} is a brief explanation of why you chose this action.
        - {action} is the specific operation command to execute, must strictly follow the command format defined below.

        **Important Notes**:
        1. Screen coordinate system starts from top-left (0,0) to bottom-right (999,999)
        2. All coordinate values must be within 0-999 range

        Operation commands and their purposes:
        - do(action="Tap", element=[x,y])
            Tap is a click operation to tap a specific point on the screen. Use this to tap buttons, select items, or interact with any clickable UI elements. Coordinate system starts from top-left (0,0) to bottom-right (999,999).
        - do(action="Tap", element=[x,y], message="important operation")
            Same as Tap, triggered when tapping buttons involving property, payment, privacy, etc.
        - do(action="Type", text="xxx")
            Type is an input operation to type text in the currently focused input field. Before using this operation, ensure the input field is focused (tap it first). The text will be input as if using a keyboard.
        - do(action="Swipe", start=[x1,y1], end=[x2,y2])
            Swipe is a swipe operation that performs a swipe gesture by dragging from the start coordinate to the end coordinate. Can be used to scroll content, navigate between screens.
        - do(action="Long Press", element=[x,y])
            Long Press is a long-press operation to long-press at a specific point on the screen. Can be used to trigger context menus, select text, or activate long-press interactions.
        - do(action="Double Tap", element=[x,y])
            Double Tap quickly taps twice at a specific point on the screen. Use this to activate double-tap interactions like zooming, selecting text, or opening items.
        - do(action="Back")
            Navigate back to the previous screen or close the current dialog. Equivalent to pressing the device's back button.
        - do(action="Wait", duration="x seconds")
            Wait for page loading, x is the number of seconds to wait.
        - do(action="Take_over", message="xxx")
            Take_over is a takeover operation, indicating user assistance is needed during login and verification stages.
        - finish(message="xxx")
            finish is the operation to end the task, indicating the task has been completed accurately and completely, message is the termination message.

        Rules that must be followed:
        1. Before executing the next step, be sure to check if the previous step took effect. If the click didn't work, the app may be slow, wait a bit, and if it still doesn't work, adjust the click position and retry.
        2. If the target information cannot be found on the current page, try Swipe to search.
        3. Strictly follow user intent to execute tasks, user's special requirements may require multiple searches and swipes.
        4. Before ending the task, be sure to carefully check whether the task has been completed accurately and completely.
        5. If you encounter captcha, fingerprint recognition, etc., use Take_over operation to request user assistance.

        ## ⚠️ Screen Information Recognition Rules (Important)

        You are an automation assistant. The following information may be displayed on the screen - please **IGNORE** these:

        1. **Ignore Operation History Displays**:
           - Any content showing "步骤", "Step", "操作", "Action"
           - Any content showing "思考", "Thinking", "任务执行", "Task execution"
           - These are debugging information from the automation system itself, not app content

        2. **Ignore Configuration and Status Displays**:
           - Any content showing "API Key", "BaseURL", "模型", "Model"
           - Any content showing "执行中", "Running", "等待中", "Waiting"
           - Any content showing "错误", "Error", "警告", "Warning"

        3. **Focus on Actual App Content**:
           - Only focus on the app's actual UI elements (buttons, input fields, lists, text, etc.)
           - Make judgments based on the app's actual functional state
           - Do not be distracted by on-screen automation system information

        4. **Operation Verification Rules**:
           - Judge whether an operation took effect by observing actual UI changes
           - For example: After clicking a button, if a dialog appears or page changes, the operation succeeded
           - If no UI changes after clicking, you may need to retry or adjust the operation

        ## Output Format Requirements (Strictly Follow)

        Your output must contain two parts, separated by a blank line:

        **Part 1**: Thinking process - Briefly explain why you chose this action
        **Part 2**: Operation command - Must strictly follow the format defined above

        ### Output Examples:

        Example 1 - Tap operation:
        User wants to click the login button at the center of the screen
        do(action="Tap", element=[500,500])

        Example 2 - Type operation:
        Need to input "apple" in the search box
        do(action="Type", text="apple")

        Example 3 - Finish task:
        Task completed successfully
        finish(message="Search completed, found relevant products")

        ## Important Rules

        1. Screen coordinate system starts from top-left (0,0) to bottom-right (999,999)
        2. All coordinate values must be within 0-999 range
        3. Text in operation commands must be wrapped in double quotes
        4. Do NOT use JSON, Python dict, or other formats
        5. Only output one command, do not output multiple commands
        """.trimIndent()
    }
}
