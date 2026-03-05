package com.demo.phoneagent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.autoglm.phoneagent.agent.AgentEngine
import com.autoglm.phoneagent.agent.AgentEngineFactory
import com.autoglm.phoneagent.agent.AgentResult
import com.autoglm.phoneagent.model.ModelConfig
import com.demo.phoneagent.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mock response types for testing
 */
enum class MockResponseType(val description: String) {
    TAP_ACTION("点击操作"),
    SWIPE_ACTION("滑动操作"),
    TYPE_ACTION("输入文字"),
    BACK_ACTION("返回操作"),
    WAIT_ACTION("等待操作"),
    LONG_PRESS_ACTION("长按操作"),
    DOUBLE_TAP_ACTION("双击操作"),
    FINISH_ACTION("完成任务"),
    EMPTY_ACTION("空动作（错误）"),
    UNKNOWN_ACTION("未知动作格式"),
    TAP_XML("点击操作 (XML)"),
    FINISH_XML("完成任务 (XML)");

    fun generateContent(): String {
        return when (this) {
            TAP_ACTION -> """
                我需要点击列表页面的第一个商品

                do(action="Tap", element=[500, 300])
            """.trimIndent()
            SWIPE_ACTION -> """
                向上滚动查看更多内容

                do(action="Swipe", start=[500, 700], end=[500, 300])
            """.trimIndent()
            TYPE_ACTION -> """
                在搜索框中输入关键词

                do(action="Type", text="苹果")
            """.trimIndent()
            BACK_ACTION -> """
                返回上一页

                do(action="Back")
            """.trimIndent()
            WAIT_ACTION -> """
                等待页面加载

                do(action="Wait", duration="1 seconds")
            """.trimIndent()
            LONG_PRESS_ACTION -> """
                长按目标元素

                do(action="Long Press", element=[500, 300])
            """.trimIndent()
            DOUBLE_TAP_ACTION -> """
                双击目标元素

                do(action="Double Tap", element=[500, 300])
            """.trimIndent()
            FINISH_ACTION -> """
                已成功找到目标商品

                finish(message="已成功找到目标商品")
            """.trimIndent()
            EMPTY_ACTION -> ""
            UNKNOWN_ACTION -> "这是一个未知格式的响应"
            TAP_XML -> """
                点击目标元素

                <thinking>我需要点击列表页面的第一个商品</thinking>
                <answer>tap(point=(500, 300))</answer>
            """.trimIndent()
            FINISH_XML -> """
                任务完成

                <thinking>已成功找到目标商品</thinking>
                <answer>finish(message=已成功找到目标商品)</answer>
            """.trimIndent()
        }
    }

    companion object {
        fun getAllDescriptions(): List<String> = entries.map { it.description }
        fun fromDescription(description: String): MockResponseType =
            entries.find { it.description == description } ?: TAP_ACTION
    }
}

/**
 * Main activity for Phone Agent Demo.
 * Allows configuration of the API and execution of tasks.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private var agentEngine: AgentEngine? = null

    // Test mode
    private var isTestMode = false
    private var selectedMockResponseType = MockResponseType.TAP_ACTION

    // Logger for demo app - uses StringBuilder for better performance
    private val logBuffer = StringBuilder()
    private val logLines = mutableListOf<String>()
    private val maxLogLines = 500 // Limit to prevent memory issues

    // Date formatter for timestamps
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    companion object {
        private const val PREFS_NAME = "phone_agent_prefs"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_API_KEY = "api_key"

        // Log level constants
        private const val LOG_DEBUG = 0
        private const val LOG_INFO = 1
        private const val LOG_WARN = 2
        private const val LOG_ERROR = 3

        // Current log level - set to LOG_DEBUG for verbose output
        private const val CURRENT_LOG_LEVEL = LOG_DEBUG
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadConfig()
        observeViewModel()
    }

    override fun onDestroy() {
        super.onDestroy()
        agentEngine?.stop()
        agentEngine?.cleanup()
        agentEngine = null
    }

    private fun setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar)

        // Execute button
        binding.btnExecute.setOnClickListener {
            executeTask()
        }

        // Stop button
        binding.btnStop.setOnClickListener {
            stopExecution()
        }

        // Navigation buttons
        binding.btnLoginPage.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        binding.btnListPage.setOnClickListener {
            startActivity(Intent(this, ProductListActivity::class.java))
        }

        // Auto-save config on change
        binding.etBaseUrl.doAfterTextChanged { saveConfig() }
        binding.etModelName.doAfterTextChanged { saveConfig() }
        binding.etApiKey.doAfterTextChanged { saveConfig() }

        // Test mode setup
        setupTestMode()
    }

    private fun setupTestMode() {
        // Test mode switch
        binding.swTestMode.setOnCheckedChangeListener { _, isChecked ->
            isTestMode = isChecked
            binding.layoutMockResponseType.visibility = if (isChecked) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

            // Disable API config in test mode
            binding.etBaseUrl.isEnabled = !isChecked
            binding.etModelName.isEnabled = !isChecked
            binding.etApiKey.isEnabled = !isChecked

            if (isChecked) {
                logInfo("🧪 测试模式已启用")
            } else {
                logInfo("测试模式已关闭")
            }
        }

        // Mock response type spinner
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            MockResponseType.getAllDescriptions()
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMockResponseType.adapter = adapter

        binding.spinnerMockResponseType.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    selectedMockResponseType = MockResponseType.fromDescription(
                        parent?.getItemAtPosition(position) as String
                    )
                    logDebug("已选择: ${selectedMockResponseType.description}")
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    private fun loadConfig() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        binding.etBaseUrl.setText(prefs.getString(KEY_BASE_URL, getString(R.string.base_url_hint)))
        binding.etModelName.setText(prefs.getString(KEY_MODEL_NAME, getString(R.string.model_name_hint)))

        // Load API key, use default if empty or not set
        val apiKey = prefs.getString(KEY_API_KEY, null)
        if (apiKey.isNullOrEmpty()) {
            binding.etApiKey.setText("e51d6924329a44058e71543ff94a4a12.X9VdUWI5rMHqCacH")
        } else {
            binding.etApiKey.setText(apiKey)
        }
    }

    private fun saveConfig() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_BASE_URL, binding.etBaseUrl.text?.toString())
            .putString(KEY_MODEL_NAME, binding.etModelName.text?.toString())
            .putString(KEY_API_KEY, binding.etApiKey.text?.toString())
            .apply()
    }

    private fun observeViewModel() {
        viewModel.isRunning.observe(this) { isRunning ->
            binding.btnExecute.isEnabled = !isRunning
            binding.btnStop.isEnabled = isRunning
            binding.etTask.isEnabled = !isRunning
        }

        viewModel.currentStep.observe(this) { step ->
            binding.tvStepCount.text = getString(R.string.step_count, step)
        }

        viewModel.currentThinking.observe(this) { thinking ->
            if (thinking.isNotEmpty()) {
                logDebug("思考: $thinking")
            }
        }

        viewModel.currentAction.observe(this) { action ->
            if (action.isNotEmpty()) {
                logDebug("动作: $action")
            }
        }

        viewModel.executionResult.observe(this) { result ->
            result?.let { onExecutionComplete(it) }
        }
    }

    private fun executeTask() {
        // Set running state to hide UI elements
        viewModel.setRunning(true)

        logDebug(">>> executeTask() 被调用")

        val task = binding.etTask.text?.toString()?.trim()
        if (task.isNullOrEmpty()) {
            logError("任务指令为空")
            Toast.makeText(this, "请输入任务指令", Toast.LENGTH_SHORT).show()
            viewModel.setRunning(false)
            return
        }

        // Clear log
        clearLog()
        logInfo("=== 开始执行任务 ===")
        logInfo("指令: $task")

        // Check if test mode is enabled
        if (isTestMode) {
            logInfo("🧪 测试模式: ${selectedMockResponseType.description}")
            runMockMode(task)
            return
        }

        val apiKey = binding.etApiKey.text?.toString()?.trim()
        if (apiKey.isNullOrEmpty()) {
            logError("API Key 为空")
            Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
            viewModel.setRunning(false)
            return
        }
        logDebug("API Key: ${apiKey.take(10)}...")

        // Create model config
        val baseURL = binding.etBaseUrl.text?.toString() ?: ModelConfig.DEFAULT.baseURL
        val modelName = binding.etModelName.text?.toString() ?: ModelConfig.DEFAULT.modelName

        logDebug("创建 ModelConfig: BaseURL=$baseURL, ModelName=$modelName")

        val modelConfig = ModelConfig(
            baseURL = baseURL,
            apiKey = apiKey,
            modelName = modelName,
            language = ModelConfig.Language.CHINESE
        )

        logDebug("创建 AgentEngine...")
        // Create agent engine
        agentEngine = AgentEngineFactory.create(
            activity = this,
            modelConfig = modelConfig,
            agentConfig = com.autoglm.phoneagent.agent.AgentConfig(
                maxSteps = 30,
                stepDelay = 600,  // Reduced for faster execution
                verbose = true
            )
        )
        logDebug("AgentEngine 创建成功")

        // Setup callbacks
        agentEngine?.apply {
            onStepProgress = { step, thinking, action ->
                logDebug("步骤 $step: $thinking | $action")
                viewModel.updateStep(step, thinking, action)
            }

            onSensitiveAction = { message ->
                logWarn("敏感操作: $message")
                // Show confirmation dialog for sensitive actions
                // For simplicity, auto-confirm in this demo
                lifecycleScope.launch {
                    val confirmed = showSensitiveActionConfirmation(message)
                    if (!confirmed) {
                        // User cancelled
                    }
                }
                // Return a suspend function that resolves to true
                suspend {
                    true
                }
            }

            onTakeOver = { message ->
                logWarn("用户接管: $message")
                viewModel.setResult(AgentResult.userTakeover(message = message))
            }

            onComplete = { result ->
                logDebug("执行完成: success=${result.success}")
                viewModel.setResult(result)
            }
        }

        // Execute task
        logDebug("启动执行协程...")
        lifecycleScope.launch {
            try {
                // Wait for UI to hide before taking screenshots
                kotlinx.coroutines.delay(300)
                logDebug("调用 agentEngine.run()...")
                val result = agentEngine?.run(task)
                logDebug("agentEngine.run() 返回: ${result?.success}")
                result?.let {
                    viewModel.setResult(it)
                }
            } catch (e: Exception) {
                logError("执行错误: ${e.message}")
                logDebug("异常类型: ${e.javaClass.simpleName}")
                viewModel.setResult(AgentResult.failure(e.message ?: "未知错误"))
            }
        }
    }

    /**
     * Run in mock mode - uses real AgentEngine with preset mock responses
     */
    private fun runMockMode(task: String) {
        logInfo("🧪 初始化 Mock 模式")

        // Clear any previous mock responses
        com.autoglm.phoneagent.model.ModelClient.clearMockResponses()

        // Setup mock responses based on selected type
        when (selectedMockResponseType) {
            MockResponseType.TAP_ACTION -> {
                // Sequence: TAP -> TAP -> FINISH
                com.autoglm.phoneagent.model.ModelClient.setMockResponses(
                    com.autoglm.phoneagent.model.ModelClient.getMockTAP(500, 300),
                    com.autoglm.phoneagent.model.ModelClient.getMockTAP(500, 500),
                    com.autoglm.phoneagent.model.ModelClient.getMockFINISH("点击操作测试完成")
                )
            }
            MockResponseType.SWIPE_ACTION -> {
                com.autoglm.phoneagent.model.ModelClient.setMockResponses(
                    com.autoglm.phoneagent.model.ModelClient.getMockSWIPE(),
                    com.autoglm.phoneagent.model.ModelClient.getMockFINISH("滑动操作测试完成")
                )
            }
            MockResponseType.TYPE_ACTION -> {
                // TAP first to focus an input area, then TYPE
                com.autoglm.phoneagent.model.ModelClient.setMockResponses(
                    com.autoglm.phoneagent.model.ModelClient.getMockTAP(500, 600),  // Try to tap near center-bottom
                    com.autoglm.phoneagent.model.ModelClient.getMockTYPE("测试文字"),
                    com.autoglm.phoneagent.model.ModelClient.getMockFINISH("输入操作测试完成")
                )
            }
            MockResponseType.BACK_ACTION -> {
                com.autoglm.phoneagent.model.ModelClient.setMockResponses(
                    com.autoglm.phoneagent.model.ModelClient.getMockBACK(),
                    com.autoglm.phoneagent.model.ModelClient.getMockFINISH("返回操作测试完成")
                )
            }
            MockResponseType.WAIT_ACTION -> {
                com.autoglm.phoneagent.model.ModelClient.setMockResponses(
                    com.autoglm.phoneagent.model.ModelClient.getMockWAIT(1),
                    com.autoglm.phoneagent.model.ModelClient.getMockFINISH("等待操作测试完成")
                )
            }
            MockResponseType.LONG_PRESS_ACTION -> {
                com.autoglm.phoneagent.model.ModelClient.setMockResponses(
                    com.autoglm.phoneagent.model.ModelClient.getMockLONG_PRESS(500, 300),
                    com.autoglm.phoneagent.model.ModelClient.getMockFINISH("长按操作测试完成")
                )
            }
            MockResponseType.DOUBLE_TAP_ACTION -> {
                com.autoglm.phoneagent.model.ModelClient.setMockResponses(
                    com.autoglm.phoneagent.model.ModelClient.getMockDOUBLE_TAP(500, 300),
                    com.autoglm.phoneagent.model.ModelClient.getMockFINISH("双击操作测试完成")
                )
            }
            MockResponseType.FINISH_ACTION -> {
                com.autoglm.phoneagent.model.ModelClient.setMockResponses(
                    com.autoglm.phoneagent.model.ModelClient.getMockFINISH("任务直接完成")
                )
            }
            MockResponseType.EMPTY_ACTION -> {
                com.autoglm.phoneagent.model.ModelClient.setMockResponses("")
            }
            MockResponseType.UNKNOWN_ACTION -> {
                com.autoglm.phoneagent.model.ModelClient.setMockResponses("这是一个未知的响应格式")
            }
            MockResponseType.TAP_XML -> {
                // XML format not supported by current parser, just for testing
                com.autoglm.phoneagent.model.ModelClient.setMockResponses(
                    """
                    点击目标元素

                    <thinking>我需要点击屏幕上的按钮</thinking>
                    <answer>tap(point=(500, 300))</answer>
                    """.trimIndent()
                )
            }
            MockResponseType.FINISH_XML -> {
                com.autoglm.phoneagent.model.ModelClient.setMockResponses(
                    """
                    任务完成

                    <thinking>测试已完成</thinking>
                    <answer>finish(message=XML格式测试完成)</answer>
                    """.trimIndent()
                )
            }
        }

        logInfo("已设置预设响应序列")

        // Create ModelConfig (URL won't be used in mock mode)
        val modelConfig = ModelConfig(
            baseURL = "mock://localhost",
            apiKey = "mock-key",
            modelName = "mock-model",
            language = ModelConfig.Language.CHINESE
        )

        // Create AgentEngine
        val agentConfig = com.autoglm.phoneagent.agent.AgentConfig(
            maxSteps = 10,
            stepDelay = 500,
            verbose = true
        )

        agentEngine = AgentEngineFactory.create(
            activity = this,
            modelConfig = modelConfig,
            agentConfig = agentConfig
        )

        // Setup callbacks
        agentEngine?.apply {
            onStepProgress = { step, thinking, action ->
                logDebug("步骤 $step: $thinking | $action")
                viewModel.updateStep(step, thinking, action)
            }

            onSensitiveAction = { message ->
                logWarn("敏感操作: $message")
                suspend { true }
            }

            onTakeOver = { message ->
                logWarn("用户接管: $message")
                viewModel.setResult(AgentResult.userTakeover(message = message))
            }

            onComplete = { result ->
                logDebug("执行完成: success=${result.success}")
                viewModel.setResult(result)
            }
        }

        // Execute task (will use mock responses)
        lifecycleScope.launch {
            try {
                kotlinx.coroutines.delay(300)
                logDebug("调用 agentEngine.run() [Mock 模式]...")
                val result = agentEngine?.run(task)

                // Clear mock mode after execution
                com.autoglm.phoneagent.model.ModelClient.clearMockResponses()

                result?.let {
                    viewModel.setResult(it)
                }
            } catch (e: Exception) {
                logError("执行错误: ${e.message}")
                viewModel.setResult(AgentResult.failure(e.message ?: "未知错误"))
                com.autoglm.phoneagent.model.ModelClient.clearMockResponses()
            }
        }
    }

    private fun stopExecution() {
        agentEngine?.stop()
        logWarn("任务已停止")
        viewModel.setResult(AgentResult.failure("用户停止了任务"))
    }

    private fun onExecutionComplete(result: AgentResult) {
        logInfo("=== 任务完成 ===")
        logInfo("成功: ${result.success}")
        result.message?.let { logInfo("结果: $it") }
        logInfo("总步数: ${result.totalSteps}")

        // Show result dialog
        showResultDialog(result)
    }

    /**
     * Show a dialog with the execution result.
     */
    private fun showResultDialog(result: AgentResult) {
        // Check if last step was user takeover
        val isUserTakeover = result.history.lastOrNull()?.actionType == com.autoglm.phoneagent.action.ActionType.TAKE_OVER

        val iconRes = when {
            result.success -> android.R.drawable.ic_dialog_info
            result.reachedMaxSteps -> android.R.drawable.ic_dialog_alert
            isUserTakeover -> android.R.drawable.ic_dialog_info
            else -> android.R.drawable.ic_dialog_alert
        }

        val title = when {
            result.success -> "任务完成"
            result.reachedMaxSteps -> "达到最大步数"
            isUserTakeover -> "用户接管"
            else -> "任务失败"
        }

        val message = buildString {
            if (result.message != null) {
                appendLine(result.message)
                appendLine()
            }
            appendLine("总步数: ${result.totalSteps}")
            result.metrics?.let { metrics ->
                appendLine("总耗时: ${metrics.totalDuration}ms")
                appendLine("平均每步: ${metrics.averageStepDuration.toInt()}ms")
                appendLine("推理耗时: ${metrics.totalInferenceTime}ms")
                appendLine("执行耗时: ${metrics.totalExecutionTime}ms")
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .setIcon(iconRes)
            .show()
    }

    /**
     * Append log with INFO level.
     */
    private fun logInfo(message: String) {
        log(LOG_INFO, message)
    }

    /**
     * Append log with DEBUG level.
     */
    private fun logDebug(message: String) {
        log(LOG_DEBUG, message)
    }

    /**
     * Append log with WARN level.
     */
    private fun logWarn(message: String) {
        log(LOG_WARN, message)
    }

    /**
     * Append log with ERROR level.
     */
    private fun logError(message: String) {
        log(LOG_ERROR, message)
    }

    /**
     * Core logging method with level control and size limiting.
     */
    private fun log(level: Int, message: String) {
        // Filter by log level
        if (level < CURRENT_LOG_LEVEL) return

        val timestamp = dateFormat.format(Date())
        val levelTag = when (level) {
            LOG_DEBUG -> "D"
            LOG_INFO -> "I"
            LOG_WARN -> "W"
            LOG_ERROR -> "E"
            else -> "?"
        }
        val logLine = "[$timestamp] [$levelTag] $message"

        // Add to lines list with size limit
        logLines.add(logLine)
        if (logLines.size > maxLogLines) {
            logLines.removeAt(0) // Remove oldest line
        }

        // Update TextView
        updateLogDisplay()
    }

    /**
     * Update the log display TextView.
     */
    private fun updateLogDisplay() {
        logBuffer.clear()
        logLines.forEach { logBuffer.append(it).append("\n") }
        binding.tvLog.text = logBuffer.toString()

        // Scroll to bottom
        binding.tvLog.post {
            val scrollAmount = binding.tvLog.layout?.getLineTop(binding.tvLog.lineCount) ?: 0
            val container = binding.tvLog.parent as? androidx.core.widget.NestedScrollView
            container?.scrollTo(0, scrollAmount)
        }
    }

    /**
     * Clear all logs.
     */
    private fun clearLog() {
        logLines.clear()
        logBuffer.clear()
        binding.tvLog.text = ""
    }

    private suspend fun showSensitiveActionConfirmation(message: String): Boolean {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            // For simplicity, auto-confirm sensitive actions
            // In a real app, you would show a dialog
            continuation.resume(true) {}
        }
    }

}
