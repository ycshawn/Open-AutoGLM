//
//  AgentEngine.swift
//  PhoneAgentSDK
//
//  Created by AutoGLM on 2025-01-30.
//

import UIKit
import OSLog

/// AI Agent 引擎 - 核心协调器
let agentLogger = Logger(subsystem: "com.demo.PhoneAgentDemo", category: "AgentEngine")
@MainActor
public class AgentEngine: ObservableObject {

    // MARK: - Published Properties

    /// 当前步骤
    @Published public private(set) var currentStep = 0

    /// 是否正在执行
    @Published public private(set) var isRunning = false

    /// 当前思考内容
    @Published public private(set) var currentThinking = ""

    /// 当前动作
    @Published public private(set) var currentAction = ""

    /// 执行历史
    @Published public private(set) var history: [AgentResult.StepInfo] = []

    /// 最后的错误信息
    @Published public private(set) var lastError: Error?

    // MARK: - Properties

    private let modelClient: ModelClient
    private let actionExecutor: ActionExecutor
    private let config: AgentConfig

    private var messages: [MessageBuilder.Message] = []
    private var conversationStartTime: Date?
    private var unknownActionCount = 0  // 连续未知动作的计数

    // MARK: - Callbacks

    /// 步骤进度回调
    public var onStepProgress: ((Int, String, String) -> Void)?

    /// 敏感操作确认回调
    public var onSensitiveAction: ((String) async -> Bool)?

    /// 人工接管回调
    public var onTakeOver: ((String) -> Void)?

    /// 完成回调
    public var onComplete: ((AgentResult) -> Void)?

    // MARK: - Initialization

    public init(
        modelConfig: ModelConfig = .default,
        agentConfig: AgentConfig = .default
    ) {
        self.config = agentConfig
        self.modelClient = ModelClient(config: modelConfig)
        self.actionExecutor = ActionExecutor()

        setupCallbacks()
    }

    public init(
        modelClient: ModelClient,
        actionExecutor: ActionExecutor,
        config: AgentConfig = .default
    ) {
        self.modelClient = modelClient
        self.actionExecutor = actionExecutor
        self.config = config

        setupCallbacks()
    }

    private func setupCallbacks() {
        // 模型思考回调
        modelClient.onThinking = { [weak self] thinking in
            self?.currentThinking = thinking
            self?.log("思考: \(thinking)")
        }

        // 模型错误回调
        modelClient.onError = { [weak self] error in
            self?.lastError = error
            self?.log("模型错误: \(error.localizedDescription)")
        }

        // 敏感操作回调
        actionExecutor.setSensitiveActionCallback { [weak self] message in
            if let callback = self?.onSensitiveAction {
                return await callback(message)
            }
            // 默认自动确认
            return true
        }

        // 监听人工接管通知
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleTakeOver(_:)),
            name: .agentTakeOver,
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    // MARK: - Public Methods

    /// 运行任务
    /// - Parameter instruction: 用户指令
    /// - Returns: 执行结果
    public func run(instruction: String) async -> AgentResult {
        reset()
        isRunning = true
        conversationStartTime = Date()

        log("=== 开始执行任务 ===")
        log("指令: \(instruction)")

        do {
            // 首次步骤 - 添加用户指令
            try await executeFirstStep(instruction: instruction)

            // 循环执行直到完成或达到最大步数
            while currentStep < config.maxSteps {
                // 检查是否应该结束
                if let lastStep = history.last, lastStep.actionType.isFinished {
                    complete(success: true, message: lastStep.message)
                    return AgentResult.success(
                        message: lastStep.message ?? "任务完成",
                        totalSteps: currentStep,
                        history: history
                    )
                }

                // 执行下一步
                let shouldContinue = try await executeNextStep()
                if !shouldContinue {
                    break
                }
            }

            // 达到最大步数
            complete(success: false, message: "达到最大执行步数")
            return AgentResult.maxStepsReached(totalSteps: currentStep, history: history)

        } catch {
            log("执行失败: \(error.localizedDescription)")
            complete(success: false, message: error.localizedDescription)
            return AgentResult.failure(
                message: error.localizedDescription,
                totalSteps: currentStep,
                history: history
            )
        }
    }

    /// 单步执行（用于手动控制）
    /// - Parameter instruction: 用户指令（首次步骤需要）
    /// - Returns: 执行结果
    public func step(instruction: String? = nil) async throws -> AgentResult.StepInfo? {
        if currentStep == 0 {
            guard let instruction = instruction else {
                throw ModelError.parsingError
            }
            try await executeFirstStep(instruction: instruction)
        } else {
            _ = try await executeNextStep()
        }

        return history.last
    }

    /// 停止执行
    public func stop() {
        isRunning = false
        modelClient.cancel()
        log("=== 任务已停止 ===")
    }

    /// 重置状态
    public func reset() {
        currentStep = 0
        isRunning = false
        currentThinking = ""
        currentAction = ""
        history = []
        lastError = nil
        messages = []
        conversationStartTime = nil
        unknownActionCount = 0
    }

    // MARK: - Private Methods - 执行步骤

    private func executeFirstStep(instruction: String) async throws {
        // 添加系统提示词
        let systemMessage = MessageBuilder.createSystemMessage(
            content: MessageBuilder.getSystemPrompt(
                language: modelClient.configValue.language
            )
        )
        messages.append(systemMessage)

        // 捕获屏幕
        let screenshot = try ScreenCapture.capture()
        let screenName = ScreenCapture.getCurrentScreenName()

        // 构建屏幕信息
        let screenInfo = ScreenInfo(screenshot: screenshot, screenName: screenName)
        let screenText = MessageBuilder.buildScreenInfo(
            currentScreen: screenName,
            screenWidth: screenshot.width,
            screenHeight: screenshot.height
        )

        // 添加用户消息
        let userText = "\(instruction)\n\n** 屏幕信息 **\n\n\(screenText)"
        let userMessage = MessageBuilder.createUserMessage(
            text: userText,
            imageBase64: screenshot.base64Data
        )
        messages.append(userMessage)

        // 调用模型
        try await callModelAndExecute(
            screenshot: screenshot,
            isFirstStep: true
        )
    }

    private func executeNextStep() async throws -> Bool {
        currentStep += 1

        // 检查上一步是否完成
        if let lastStep = history.last, lastStep.actionType.isFinished {
            return false
        }

        // 短暂延迟
        try await Task.sleep(nanoseconds: UInt64(config.stepDelay * 1_000_000_000))

        // 捕获屏幕
        let screenshot = try ScreenCapture.capture()
        let screenName = ScreenCapture.getCurrentScreenName()

        // 构建屏幕信息
        let screenText = MessageBuilder.buildScreenInfo(
            currentScreen: screenName,
            screenWidth: screenshot.width,
            screenHeight: screenshot.height
        )

        // 添加用户消息
        let userText = "** 屏幕信息 **\n\n\(screenText)"
        let userMessage = MessageBuilder.createUserMessage(
            text: userText,
            imageBase64: screenshot.base64Data
        )
        messages.append(userMessage)

        // 调用模型
        try await callModelAndExecute(
            screenshot: screenshot,
            isFirstStep: false
        )

        return true
    }

    private func callModelAndExecute(
        screenshot: ScreenCapture.Screenshot,
        isFirstStep: Bool
    ) async throws {
        let startTime = Date()

        // 调用模型
        let response = try await modelClient.request(
            messages: messages,
            screenWidth: screenshot.width,
            screenHeight: screenshot.height
        )

        // 检查响应是否为空
        if response.action.isEmpty {
            log("⚠️ 模型返回空动作，停止执行")
            throw ModelError.parsingError
        }

        // 解析动作
        let actionType = response.parseAction(
            screenWidth: screenshot.width,
            screenHeight: screenshot.height
        )

        currentAction = response.action

        // 检查是否为未知动作
        if case .unknown(let text) = actionType {
            unknownActionCount += 1
            log("⚠️ 未知动作 (\(unknownActionCount)/3): \(text.prefix(100))")

            // 连续 3 次未知动作时终止
            if unknownActionCount >= 3 {
                log("❌ 连续 3 次解析失败，终止执行")
                lastError = ModelError.parsingError
                throw ModelError.parsingError
            }
        } else {
            // 成功解析，重置计数
            if unknownActionCount > 0 {
                unknownActionCount = 0
            }
        }

        // 移除上下文中的图片以节省空间
        messages[messages.count - 1] = MessageBuilder.removeImages(from: messages[messages.count - 1])

        // 添加助手响应到上下文
        let assistantMessage = MessageBuilder.createAssistantMessage(
            content: "<thinking>\(response.thinking)</thinking><answer>\(response.action)</answer>"
        )
        messages.append(assistantMessage)

        // 执行动作
        let executionResult = try await actionExecutor.execute(actionType)

        let duration = Date().timeIntervalSince(startTime)

        // 记录步骤
        let stepInfo = AgentResult.StepInfo(
            step: currentStep,
            thinking: response.thinking,
            action: response.action,
            actionType: actionType,
            success: executionResult.success,
            message: executionResult.message,
            duration: duration
        )
        history.append(stepInfo)

        // 通知进度
        onStepProgress?(currentStep, response.thinking, response.action)

        // 执行失败时记录
        if !executionResult.success {
            log("⚠️ 动作执行失败: \(executionResult.message ?? "未知错误")")
        }
    }

    // MARK: - Private Methods - 辅助

    private func complete(success: Bool, message: String?) {
        isRunning = false
        log("=== 任务\(success ? "完成" : "失败") ===")
        if let message = message {
            log("结果: \(message)")
        }

        let result = AgentResult(
            success: success,
            message: message,
            totalSteps: currentStep,
            history: history,
            metrics: calculateMetrics()
        )

        onComplete?(result)
    }

    private func calculateMetrics() -> AgentResult.ExecutionMetrics? {
        guard let startTime = conversationStartTime else { return nil }

        let totalDuration = Date().timeIntervalSince(startTime)
        let inferenceTime = history.reduce(0) { $0 + $1.duration }
        let executionTime = totalDuration - inferenceTime

        return AgentResult.ExecutionMetrics(
            totalDuration: totalDuration,
            averageStepDuration: history.isEmpty ? 0 : totalDuration / Double(history.count),
            totalInferenceTime: inferenceTime,
            totalExecutionTime: executionTime
        )
    }

    private func log(_ message: String) {
        if config.verbose {
            agentLogger.error("\(message)")
        }
    }

    // MARK: - Notification Handlers

    @objc private func handleTakeOver(_ notification: Notification) {
        guard let message = notification.userInfo?["message"] as? String else {
            return
        }
        onTakeOver?(message)
    }
}

// MARK: - ModelResponse.ActionType 扩展
extension ModelResponse.ActionType {
    var isFinished: Bool {
        if case .finish = self {
            return true
        }
        return false
    }
}
