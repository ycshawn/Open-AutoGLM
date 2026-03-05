package com.autoglm.phoneagent.agent

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.autoglm.phoneagent.action.ActionExecutor
import com.autoglm.phoneagent.action.ActionType
import com.autoglm.phoneagent.action.ExecutionResult
import com.autoglm.phoneagent.capture.ScreenCapture
import com.autoglm.phoneagent.capture.ScreenInfo
import com.autoglm.phoneagent.model.MessageBuilder
import com.autoglm.phoneagent.model.ModelClient
import com.autoglm.phoneagent.model.ModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Activity lifecycle callbacks to track current foreground activity.
 */
private class ActivityTracker : Application.ActivityLifecycleCallbacks {
    private var currentActivityRef: WeakReference<Activity>? = null

    fun getCurrentActivity(): Activity? = currentActivityRef?.get()

    fun setCurrentActivity(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        setCurrentActivity(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        setCurrentActivity(activity)
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}
}

/**
 * Main engine for the Phone Agent SDK.
 * Coordinates screenshot capture, model communication, and action execution.
 *
 * @property activity The activity to operate on
 * @property modelClient Client for communicating with the model API
 * @property config Agent configuration
 */
class AgentEngine(
    private val activity: Activity,
    private val modelClient: ModelClient,
    private val config: AgentConfig = AgentConfig.DEFAULT
) {
    // Pass getCurrentActivity as a function reference to ActionExecutor
    // This allows the executor to dynamically get the current activity
    private val actionExecutor = ActionExecutor { getCurrentActivity() }

    // Track current foreground activity for correct screenshot capture
    private val activityTracker = ActivityTracker()

    init {
        // Set initial activity
        activityTracker.setCurrentActivity(activity)
        // Register lifecycle callbacks to track activity changes
        (activity.applicationContext as? Application)?.registerActivityLifecycleCallbacks(activityTracker)
    }

    /**
     * Get the current foreground activity.
     * Falls back to the original activity if tracking fails.
     */
    private fun getCurrentActivity(): Activity {
        return activityTracker.getCurrentActivity() ?: activity
    }

    /**
     * Clean up resources when engine is stopped.
     */
    fun cleanup() {
        (activity.applicationContext as? Application)?.unregisterActivityLifecycleCallbacks(activityTracker)
    }

    // State
    private val _currentStep = MutableStateFlow(0)
    private val _isRunning = MutableStateFlow(false)
    private val _currentThinking = MutableStateFlow("")
    private val _currentAction = MutableStateFlow("")
    private val _history = MutableStateFlow<List<AgentResult.StepInfo>>(emptyList())
    private val _error = MutableStateFlow<String?>(null)

    private var executionJob: Job? = null
    private var messages: MutableList<MessageBuilder.Message> = mutableListOf()
    private var unknownActionCount = 0

    // Helper method for conditional logging
    private fun logDebug(message: String) {
        if (config.verbose) {
            println("[AgentEngine] $message")
        }
    }

    private fun logInfo(message: String) {
        println("[AgentEngine] $message")
    }

    private fun logError(message: String) {
        println("[AgentEngine] ERROR: $message")
    }

    // Public state flows
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    val currentThinking: StateFlow<String> = _currentThinking.asStateFlow()
    val currentAction: StateFlow<String> = _currentAction.asStateFlow()
    val history: StateFlow<List<AgentResult.StepInfo>> = _history.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    // Callbacks
    var onStepProgress: ((step: Int, thinking: String, action: String) -> Unit)? = null
    var onSensitiveAction: ((message: String) -> suspend () -> Boolean)? = null
    var onTakeOver: ((message: String) -> Unit)? = null
    var onComplete: ((AgentResult) -> Unit)? = null

    /**
     * Run the agent with the given instruction.
     *
     * @param instruction The user's task instruction
     * @return AgentResult with the execution outcome
     */
    suspend fun run(instruction: String): AgentResult = withContext(Dispatchers.Main) {
        logInfo("开始执行任务: $instruction")

        if (_isRunning.value) {
            logError("Agent 已在运行中")
            return@withContext AgentResult.failure("Agent 已在运行中")
        }

        config.validate()

        // Reset state
        resetState()
        _isRunning.value = true

        val startTime = System.currentTimeMillis()
        val historyList = mutableListOf<AgentResult.StepInfo>()
        var totalInferenceTime = 0L
        var totalExecutionTime = 0L

        try {
            // Execute first step with instruction
            val firstStepResult = executeFirstStep(instruction)
            if (firstStepResult != null) {
                historyList.add(firstStepResult)
                totalInferenceTime += firstStepResult.inferenceTime
                totalExecutionTime += firstStepResult.executionTime
            }

            // Continue executing steps
            while (_currentStep.value < config.maxSteps && isActive) {
                // Check if previous step finished the task
                val lastStep = historyList.lastOrNull()
                if (lastStep?.actionType == ActionType.FINISH) {
                    logInfo("任务完成")
                    break
                }

                // Check if user takeover was requested
                if (lastStep?.actionType == ActionType.TAKE_OVER) {
                    onTakeOver?.invoke(lastStep.message ?: "需要用户协助")
                    break
                }

                // Execute next step
                val stepResult = executeNextStep()
                if (stepResult != null) {
                    historyList.add(stepResult)
                    totalInferenceTime += stepResult.inferenceTime
                    totalExecutionTime += stepResult.executionTime
                } else {
                    break
                }
            }

            val totalTime = System.currentTimeMillis() - startTime
            val lastStep = historyList.lastOrNull()
            val finalHistory = historyList.toList()

            // Build metrics
            val metrics = if (historyList.isNotEmpty()) {
                AgentResult.ExecutionMetrics(
                    totalDuration = totalTime,
                    averageStepDuration = if (historyList.isNotEmpty()) {
                        totalTime.toDouble() / historyList.size
                    } else 0.0,
                    totalInferenceTime = totalInferenceTime,
                    totalExecutionTime = totalExecutionTime
                )
            } else null

            // Determine final result
            val result = when {
                lastStep?.actionType == ActionType.FINISH -> AgentResult.success(
                    message = lastStep.message,
                    totalSteps = historyList.size,
                    history = finalHistory,
                    metrics = metrics
                )

                lastStep?.actionType == ActionType.TAKE_OVER -> AgentResult.userTakeover(
                    message = lastStep.message ?: "用户接管了任务",
                    totalSteps = historyList.size,
                    history = finalHistory,
                    metrics = metrics
                )

                _currentStep.value >= config.maxSteps -> AgentResult.maxStepsReached(
                    totalSteps = historyList.size,
                    history = finalHistory,
                    metrics = metrics
                )

                else -> AgentResult.failure(
                    message = "任务未完成",
                    totalSteps = historyList.size,
                    history = finalHistory,
                    metrics = metrics
                )
            }

            onComplete?.invoke(result)
            result

        } catch (e: Exception) {
            val errorResult = AgentResult.failure(
                message = "执行错误: ${e.message}",
                totalSteps = historyList.size,
                history = historyList.toList()
            )
            _error.value = e.message
            onComplete?.invoke(errorResult)
            errorResult
        } finally {
            _isRunning.value = false
        }
    }

    /**
     * Execute the first step with the instruction.
     */
    private suspend fun executeFirstStep(instruction: String): AgentResult.StepInfo? {
        // Build messages with system prompt and instruction
        messages.clear()
        messages.add(MessageBuilder.buildSystemMessage(modelClient.config.language))

        // Capture screenshot
        val currentActivity = getCurrentActivity()
        val screenshot = ScreenCapture.captureActivity(currentActivity)
        val screenInfo = ScreenInfo.fromActivity(currentActivity, includeElements = false)
        logDebug("截图: ${screenshot.width}x${screenshot.height}, Activity: ${currentActivity.javaClass.simpleName}")

        // Add user message with instruction and screenshot
        messages.add(
            MessageBuilder.buildUserMessage(
                instruction = instruction,
                screenshotBase64 = screenshot.toDataURL(),
                screenInfo = screenInfo
            )
        )

        return callModelAndExecute()
    }

    /**
     * Execute subsequent steps.
     */
    private suspend fun executeNextStep(): AgentResult.StepInfo? {
        // Wait for UI to update
        delay(config.stepDelay)

        // Capture new screenshot
        val currentActivity = getCurrentActivity()
        val screenshot = ScreenCapture.captureActivity(currentActivity)
        val screenInfo = ScreenInfo.fromActivity(currentActivity, includeElements = false)

        // Add follow-up message with screenshot only
        messages.add(
            MessageBuilder.buildFollowUpMessage(
                screenshotBase64 = screenshot.toDataURL(),
                screenInfo = screenInfo
            )
        )

        return callModelAndExecute()
    }

    /**
     * Call the model and execute the returned action.
     */
    private suspend fun callModelAndExecute(): AgentResult.StepInfo? {
        val stepStartTime = System.currentTimeMillis()

        val response = try {
            modelClient.sendRequest(messages.toList())
        } catch (e: Exception) {
            logError("模型请求异常: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            _error.value = "模型请求失败: ${e.message}"
            return AgentResult.StepInfo(
                step = _currentStep.value + 1,
                thinking = "错误",
                action = "无",
                actionType = ActionType.UNKNOWN,
                success = false,
                message = e.message,
                duration = System.currentTimeMillis() - stepStartTime
            )
        }

        logDebug("步骤 ${_currentStep.value + 1}: ${response.parsedAction.type}")

        val inferenceTime = response.totalTime
        _currentThinking.value = response.thinking
        _currentAction.value = response.action

        // Check for unknown action
        if (response.parsedAction.type == ActionType.UNKNOWN) {
            unknownActionCount++
            logError("未知动作 #$unknownActionCount: ${response.parsedAction.rawAction}")

            if (unknownActionCount >= config.unknownActionThreshold) {
                return AgentResult.StepInfo(
                    step = _currentStep.value + 1,
                    thinking = response.thinking,
                    action = response.action,
                    actionType = ActionType.UNKNOWN,
                    success = false,
                    message = "连续 $unknownActionCount 次未知动作，终止执行",
                    duration = System.currentTimeMillis() - stepStartTime
                )
            }
        } else {
            unknownActionCount = 0
        }

        // Handle sensitive actions (tap with message)
        if (response.parsedAction.type == ActionType.TAP_WITH_MESSAGE) {
            val message = response.parsedAction.message ?: "敏感操作"
            val shouldProceed = if (config.autoConfirmSensitiveActions) {
                true
            } else {
                onSensitiveAction?.invoke(message)?.invoke() ?: true
            }

            if (!shouldProceed) {
                return AgentResult.StepInfo(
                    step = _currentStep.value + 1,
                    thinking = response.thinking,
                    action = response.action,
                    actionType = ActionType.TAKE_OVER,
                    success = false,
                    message = "用户取消了敏感操作",
                    duration = System.currentTimeMillis() - stepStartTime
                )
            }
        }

        // Execute the action
        val executionStart = System.currentTimeMillis()
        val executionResult = actionExecutor.execute(response.parsedAction)
        val executionTime = System.currentTimeMillis() - executionStart

        // Add extra delay for navigation and scroll actions to allow UI to update
        val navigationActions = setOf(
            ActionType.TAP,
            ActionType.TAP_WITH_MESSAGE,
            ActionType.DOUBLE_TAP,
            ActionType.BACK,
            ActionType.SWIPE
        )
        if (response.parsedAction.type in navigationActions && executionResult.isSuccess) {
            val delayTime = if (response.parsedAction.type == ActionType.SWIPE) 300 else 400
            delay(delayTime.toLong())
        }

        val stepNumber = _currentStep.value + 1
        _currentStep.value = stepNumber

        val stepInfo = AgentResult.StepInfo(
            step = stepNumber,
            thinking = response.thinking,
            action = response.action,
            actionType = response.parsedAction.type,
            success = executionResult.isSuccess || executionResult.shouldFinish,
            message = executionResult.getMessage(),
            duration = System.currentTimeMillis() - stepStartTime
        )

        // Update history
        val currentHistory = _history.value.toMutableList()
        currentHistory.add(stepInfo)
        _history.value = currentHistory

        // Notify callback
        onStepProgress?.invoke(stepNumber, response.thinking, response.action)

        // Log if verbose
        if (config.verbose) {
            val result = when {
                executionResult.shouldFinish -> "✓ (完成)"
                executionResult.isSuccess -> "✓"
                else -> "✗"
            }
            logDebug("步骤 $stepNumber: ${response.parsedAction.type} $result")
        }

        return stepInfo.copy(
            inferenceTime = inferenceTime,
            executionTime = executionTime
        )
    }

    /**
     * Reset the engine state.
     */
    private fun resetState() {
        _currentStep.value = 0
        _currentThinking.value = ""
        _currentAction.value = ""
        _history.value = emptyList()
        _error.value = null
        unknownActionCount = 0
        messages.clear()
    }

    /**
     * Stop the agent execution.
     */
    fun stop() {
        executionJob?.cancel()
        _isRunning.value = false
    }

    /**
     * Get the current execution history as a formatted string.
     */
    fun getHistorySummary(): String {
        return _history.value.joinToString("\n\n") { step ->
            """
            步骤 ${step.step}:
            思考: ${step.thinking}
            动作: ${step.action}
            结果: ${if (step.success) "✓ 成功" else "✗ 失败"}
            ${if (step.message != null) "消息: ${step.message}" else ""}
            """.trimIndent()
        }
    }
}

/**
 * Factory for creating AgentEngine instances.
 */
object AgentEngineFactory {
    /**
     * Create an AgentEngine with the given configuration.
     *
     * @param activity The activity to operate on
     * @param modelConfig Model API configuration
     * @param agentConfig Agent execution configuration
     * @return Configured AgentEngine instance
     */
    fun create(
        activity: Activity,
        modelConfig: ModelConfig = ModelConfig.DEFAULT,
        agentConfig: AgentConfig = AgentConfig.DEFAULT
    ): AgentEngine {
        modelConfig.validate()
        agentConfig.validate()

        val modelClient = ModelClient(modelConfig)
        return AgentEngine(activity, modelClient, agentConfig)
    }
}
