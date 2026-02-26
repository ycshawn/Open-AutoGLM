//
//  AgentResult.swift
//  PhoneAgentSDK
//
//  Created by AutoGLM on 2025-01-30.
//

import Foundation

/// Agent 执行结果
public struct AgentResult {

    /// 是否成功完成
    public let success: Bool

    /// 最终消息
    public let message: String?

    /// 执行的总步数
    public let totalSteps: Int

    /// 是否因达到最大步数而停止
    public let reachedMaxSteps: Bool

    /// 执行历史（每一步的详情）
    public let history: [StepInfo]

    /// 性能统计
    public let metrics: ExecutionMetrics?

    public struct StepInfo {
        /// 步骤编号
        public let step: Int

        /// 模型的思考过程
        public let thinking: String

        /// 执行的动作
        public let action: String

        /// 动作类型
        public let actionType: ModelResponse.ActionType

        /// 是否成功
        public let success: Bool

        /// 步骤消息
        public let message: String?

        /// 该步的耗时
        public let duration: TimeInterval
    }

    public struct ExecutionMetrics {
        /// 总耗时
        public let totalDuration: TimeInterval

        /// 平均每步耗时
        public let averageStepDuration: TimeInterval

        /// 模型推理总耗时
        public let totalInferenceTime: TimeInterval

        /// 操作执行总耗时
        public let totalExecutionTime: TimeInterval
    }

    public init(
        success: Bool,
        message: String? = nil,
        totalSteps: Int,
        reachedMaxSteps: Bool = false,
        history: [StepInfo] = [],
        metrics: ExecutionMetrics? = nil
    ) {
        self.success = success
        self.message = message
        self.totalSteps = totalSteps
        self.reachedMaxSteps = reachedMaxSteps
        self.history = history
        self.metrics = metrics
    }
}

// MARK: - 便捷初始化
extension AgentResult {

    /// 成功结果
    public static func success(message: String, totalSteps: Int, history: [StepInfo] = []) -> AgentResult {
        return AgentResult(
            success: true,
            message: message,
            totalSteps: totalSteps,
            history: history
        )
    }

    /// 失败结果
    public static func failure(message: String, totalSteps: Int, history: [StepInfo] = []) -> AgentResult {
        return AgentResult(
            success: false,
            message: message,
            totalSteps: totalSteps,
            history: history
        )
    }

    /// 达到最大步数
    public static func maxStepsReached(totalSteps: Int, history: [StepInfo] = []) -> AgentResult {
        return AgentResult(
            success: false,
            message: "达到最大执行步数",
            totalSteps: totalSteps,
            reachedMaxSteps: true,
            history: history
        )
    }
}
