package com.autoglm.phoneagent.agent

import com.autoglm.phoneagent.action.ActionType

/**
 * Result of an agent execution.
 *
 * @property success Whether the task completed successfully
 * @property message Optional message describing the final result
 * @property totalSteps Total number of steps executed
 * @property reachedMaxSteps Whether execution stopped because max steps was reached
 * @property history List of step information for each executed step
 * @property metrics Optional performance metrics
 */
data class AgentResult(
    val success: Boolean,
    val message: String? = null,
    val totalSteps: Int = 0,
    val reachedMaxSteps: Boolean = false,
    val history: List<StepInfo> = emptyList(),
    val metrics: ExecutionMetrics? = null
) {
    /**
     * Information about a single execution step.
     *
     * @property step Step number (1-indexed)
     * @property thinking The model's thinking process for this step
     * @property action The action string that was executed
     * @property actionType The parsed action type
     * @property success Whether this step executed successfully
     * @property message Optional message from step execution
     * @property duration Time taken for this step in milliseconds
     * @property inferenceTime Time spent on model inference
     * @property executionTime Time spent executing the action
     */
    data class StepInfo(
        val step: Int,
        val thinking: String,
        val action: String,
        val actionType: ActionType,
        val success: Boolean,
        val message: String? = null,
        val duration: Long = 0,
        val inferenceTime: Long = 0,
        val executionTime: Long = 0
    )

    /**
     * Performance metrics for the entire execution.
     *
     * @property totalDuration Total execution time in milliseconds
     * @property averageStepDuration Average time per step in milliseconds
     * @property totalInferenceTime Total time spent on model inference
     * @property totalExecutionTime Total time spent executing actions
     */
    data class ExecutionMetrics(
        val totalDuration: Long,
        val averageStepDuration: Double,
        val totalInferenceTime: Long,
        val totalExecutionTime: Long
    )

    companion object {
        /**
         * Create a successful result.
         */
        fun success(
            message: String? = null,
            totalSteps: Int = 0,
            history: List<StepInfo> = emptyList(),
            metrics: ExecutionMetrics? = null
        ): AgentResult {
            return AgentResult(
                success = true,
                message = message,
                totalSteps = totalSteps,
                reachedMaxSteps = false,
                history = history,
                metrics = metrics
            )
        }

        /**
         * Create a failed result.
         */
        fun failure(
            message: String,
            totalSteps: Int = 0,
            history: List<StepInfo> = emptyList(),
            metrics: ExecutionMetrics? = null
        ): AgentResult {
            return AgentResult(
                success = false,
                message = message,
                totalSteps = totalSteps,
                reachedMaxSteps = false,
                history = history,
                metrics = metrics
            )
        }

        /**
         * Create a result indicating max steps was reached.
         */
        fun maxStepsReached(
            totalSteps: Int,
            history: List<StepInfo> = emptyList(),
            metrics: ExecutionMetrics? = null
        ): AgentResult {
            return AgentResult(
                success = false,
                message = "Reached maximum steps ($totalSteps) without completion",
                totalSteps = totalSteps,
                reachedMaxSteps = true,
                history = history,
                metrics = metrics
            )
        }

        /**
         * Create a result indicating too many unknown actions.
         */
        fun tooManyUnknownActions(
            totalSteps: Int,
            history: List<StepInfo> = emptyList(),
            metrics: ExecutionMetrics? = null
        ): AgentResult {
            return AgentResult(
                success = false,
                message = "Too many unknown actions detected",
                totalSteps = totalSteps,
                reachedMaxSteps = false,
                history = history,
                metrics = metrics
            )
        }

        /**
         * Create a result indicating user takeover.
         */
        fun userTakeover(
            message: String = "User took over the task",
            totalSteps: Int = 0,
            history: List<StepInfo> = emptyList(),
            metrics: ExecutionMetrics? = null
        ): AgentResult {
            return AgentResult(
                success = false,
                message = message,
                totalSteps = totalSteps,
                reachedMaxSteps = false,
                history = history,
                metrics = metrics
            )
        }
    }

    /**
     * Get a human-readable summary of the result.
     */
    fun getSummary(): String {
        return when {
            success -> "✓ 任务完成${if (!message.isNullOrBlank()) ": $message" else ""}"
            reachedMaxSteps -> "✗ 达到最大步数限制 ($totalSteps 步)"
            else -> "✗ 任务失败${if (!message.isNullOrBlank()) ": $message" else ""}"
        }
    }

    /**
     * Get detailed information about each step.
     */
    fun getDetailedHistory(): String {
        if (history.isEmpty()) return "无执行记录"

        return buildString {
            appendLine("=== 执行历史 ===")
            history.forEach { step ->
                appendLine("步骤 ${step.step}:")
                appendLine("  思考: ${step.thinking}")
                appendLine("  动作: ${step.action}")
                appendLine("  类型: ${step.actionType}")
                appendLine("  状态: ${if (step.success) "✓ 成功" else "✗ 失败"}")
                if (step.message != null) {
                    appendLine("  消息: ${step.message}")
                }
                appendLine("  耗时: ${step.duration}ms")
                appendLine()
            }
        }
    }
}
