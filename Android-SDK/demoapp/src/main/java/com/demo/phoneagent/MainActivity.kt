package com.demo.phoneagent

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
 * Main activity for Phone Agent Demo.
 * Allows configuration of the API and execution of tasks.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private var agentEngine: AgentEngine? = null

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
            return
        }

        val apiKey = binding.etApiKey.text?.toString()?.trim()
        if (apiKey.isNullOrEmpty()) {
            logError("API Key 为空")
            Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        // Clear log
        clearLog()
        logInfo("=== 开始执行任务 ===")
        logInfo("指令: $task")
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
