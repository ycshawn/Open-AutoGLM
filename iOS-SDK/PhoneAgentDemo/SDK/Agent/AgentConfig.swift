//
//  AgentConfig.swift
//  PhoneAgentSDK
//
//  Created by AutoGLM on 2025-01-30.
//

import Foundation

/// Agent 配置
public struct AgentConfig {

    /// 最大执行步数
    public var maxSteps: Int

    /// 每步操作后的等待时间（秒）
    public var stepDelay: TimeInterval

    /// 是否打印详细日志
    public var verbose: Bool

    /// 是否自动处理敏感操作（如果为 false，需要设置回调）
    public var autoConfirmSensitiveActions: Bool

    public init(
        maxSteps: Int = 50,
        stepDelay: TimeInterval = 0.5,
        verbose: Bool = true,
        autoConfirmSensitiveActions: Bool = false
    ) {
        self.maxSteps = maxSteps
        self.stepDelay = stepDelay
        self.verbose = verbose
        self.autoConfirmSensitiveActions = autoConfirmSensitiveActions
    }
}

// MARK: - 默认配置
extension AgentConfig {
    /// 默认配置
    public static let `default` = AgentConfig()

    /// 快速测试配置（较少步数，较短延迟）
    public static let fast = AgentConfig(maxSteps: 20, stepDelay: 0.3)

    /// 详细调试配置
    public static let debug = AgentConfig(verbose: true)
}
