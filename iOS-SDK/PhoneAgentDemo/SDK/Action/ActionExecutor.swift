//
//  ActionExecutor.swift
//  PhoneAgentSDK
//
//  Created by AutoGLM on 2025-01-30.
//

import UIKit
import OSLog

/// 动作执行器 - 负责在 App 内部执行模型输出的操作
let actionLogger = Logger(subsystem: "com.demo.PhoneAgentDemo", category: "ActionExecutor")
@MainActor
public class ActionExecutor {

    // MARK: - Types

    public enum ActionError: LocalizedError {
        case elementNotFound
        case noFirstResponder
        case noNavigationController
        case executionFailed(String)

        public var errorDescription: String? {
            switch self {
            case .elementNotFound:
                return "No element found at the specified location"
            case .noFirstResponder:
                return "No focused input field found"
            case .noNavigationController:
                return "No navigation controller available"
            case .executionFailed(let message):
                return "Action execution failed: \(message)"
            }
        }
    }

    public struct ExecutionResult {
        public let success: Bool
        public let message: String?
        public let shouldFinish: Bool

        public init(success: Bool, message: String? = nil, shouldFinish: Bool = false) {
            self.success = success
            self.message = message
            self.shouldFinish = shouldFinish
        }
    }

    // MARK: - Properties

    private let window: UIWindow
    private var sensitiveActionCallback: ((String) async -> Bool)?

    // MARK: - Initialization

    public init(window: UIWindow) {
        self.window = window
    }

    public init() {
        guard let window = ActionExecutor.getKeyWindowStatic() else {
            fatalError("No key window found")
        }
        self.window = window
    }

    private static func getKeyWindowStatic() -> UIWindow? {
        if let windowScene = UIApplication.shared.connectedScenes
            .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene {
            return windowScene.windows.first(where: { $0.isKeyWindow })
        }
        return UIApplication.shared.windows.first(where: { $0.isKeyWindow })
    }

    // MARK: - Configuration

    /// 设置敏感操作确认回调
    /// - Parameter callback: 返回 true 表示确认执行，false 表示取消
    public func setSensitiveActionCallback(_ callback: @escaping (String) async -> Bool) {
        self.sensitiveActionCallback = callback
    }

    // MARK: - Public Methods

    /// 执行动作
    /// - Parameter action: 动作类型
    /// - Returns: 执行结果
    public func execute(_ action: ModelResponse.ActionType) async throws -> ExecutionResult {
        actionLogger.error("执行动作: \(action.description)")

        switch action {
        case .launch(let app):
            throw ActionError.executionFailed("Launch operation is not supported in in-app mode")

        case .tap(let point):
            return try await performTap(at: point)

        case .tapWithMessage(let point, let message):
            // 敏感操作，需要确认
            if let callback = sensitiveActionCallback {
                let confirmed = await callback(message)
                guard confirmed else {
                    return ExecutionResult(success: false, message: "操作被用户取消", shouldFinish: false)
                }
            }
            return try await performTap(at: point)

        case .type(let text):
            return try await performType(text: text)

        case .swipe(let start, let end):
            return try await performSwipe(from: start, to: end)

        case .longPress(let point):
            return try await performLongPress(at: point)

        case .doubleTap(let point):
            return try await performDoubleTap(at: point)

        case .back:
            return try await performBack()

        case .home:
            return ExecutionResult(success: false, message: "Home operation is not supported in in-app mode", shouldFinish: false)

        case .wait(let duration):
            return try await performWait(duration: duration)

        case .takeOver(let message):
            // 请求人工接管
            NotificationCenter.default.post(
                name: .agentTakeOver,
                object: nil,
                userInfo: ["message": message]
            )
            return ExecutionResult(success: true, message: "等待用户手动操作", shouldFinish: false)

        case .finish(let message):
            return ExecutionResult(success: true, message: message, shouldFinish: true)

        case .unknown(let text):
            return ExecutionResult(success: false, message: "Unknown action: \(text)", shouldFinish: false)
        }
    }

    // MARK: - Private Methods - 具体操作实现

    private func performTap(at point: CGPoint) async throws -> ExecutionResult {
        // 将归一化坐标映射到实际屏幕
        let actualPoint = CGPoint(
            x: point.x * window.bounds.width,
            y: point.y * window.bounds.height
        )

        // 使用 hitTest 找到响应的 View
        guard let view = window.hitTest(actualPoint, with: nil) else {
            throw ActionError.elementNotFound
        }

        // 检查是否可以交互
        guard view.isUserInteractionEnabled else {
            throw ActionError.executionFailed("View is not interactive")
        }

        // 执行点击
        // 方法1: 如果是按钮，直接触发
        if let button = view as? UIButton {
            button.sendActions(for: .touchUpInside)
        }
        // 方法2: 如果是 UIControl，尝试触发事件
        else if let control = view as? UIControl {
            control.sendActions(for: .touchUpInside)
        }
        // 方法3: 对于通用 view，尝试 accessibility 激活
        else {
            view.accessibilityActivate()
        }

        return ExecutionResult(success: true)
    }

    private func performType(text: String) async throws -> ExecutionResult {
        var textField: UITextField?

        // 首先尝试获取当前第一响应者
        if let firstResponder = findFirstResponder() as? UITextField {
            textField = firstResponder
        } else {
            // 如果没有焦点，尝试找到第一个可用的输入框
            textField = findFirstTextInputField()
        }

        guard let targetField = textField else {
            throw ActionError.noFirstResponder
        }

        // 如果输入框不是第一响应者，先点击激活它
        if targetField !== findFirstResponder() {
            targetField.becomeFirstResponder()
            try await Task.sleep(nanoseconds: 100_000_000) // 100ms
        }

        // 直接设置文本
        targetField.text = text
        targetField.sendActions(for: .editingChanged)
        targetField.sendActions(for: .editingDidEnd)
        targetField.resignFirstResponder()

        // 等待 UI 更新
        try await Task.sleep(nanoseconds: 100_000_000) // 100ms

        return ExecutionResult(success: true)
    }

    private func performSwipe(from start: CGPoint, to end: CGPoint) async throws -> ExecutionResult {
        actionLogger.error("Swipe 归一化坐标: start=(\(String(format: "%.3f", start.x)), \(String(format: "%.3f", start.y))), end=(\(String(format: "%.3f", end.x)), \(String(format: "%.3f", end.y)))")
        actionLogger.error("Window 尺寸: \(self.window.bounds.width) x \(self.window.bounds.height)")

        let startPoint = CGPoint(
            x: start.x * self.window.bounds.width,
            y: start.y * self.window.bounds.height
        )
        let endPoint = CGPoint(
            x: end.x * self.window.bounds.width,
            y: end.y * self.window.bounds.height
        )

        actionLogger.error("Swipe 实际坐标: startPoint=(\(String(format: "%.1f", startPoint.x)), \(String(format: "%.1f", startPoint.y))), endPoint=(\(String(format: "%.1f", endPoint.x)), \(String(format: "%.1f", endPoint.y)))")

        // 计算实际屏幕坐标的差值
        let deltaX = endPoint.x - startPoint.x
        let deltaY = endPoint.y - startPoint.y

        actionLogger.error("Swipe 偏移量: deltaX=\(deltaX), deltaY=\(deltaY)")

        // 优先使用主 UIScrollView（整屏滑动）
        var scrollView: UIScrollView?
        if let mainScrollView = findMainScrollView() {
            scrollView = mainScrollView
            actionLogger.error("使用主 ScrollView: \(mainScrollView)")
        } else if let hitScrollView = findScrollView(at: startPoint) {
            scrollView = hitScrollView
            actionLogger.error("使用点击点 ScrollView: \(hitScrollView)")
        }

        if let scrollView = scrollView {
            if abs(deltaX) > abs(deltaY) {
                // 水平滚动
                let offsetX = scrollView.contentOffset.x - deltaX
                scrollView.setContentOffset(CGPoint(x: offsetX, y: scrollView.contentOffset.y), animated: true)
            } else {
                // 垂直滚动
                let offsetY = scrollView.contentOffset.y - deltaY
                scrollView.setContentOffset(CGPoint(x: scrollView.contentOffset.x, y: offsetY), animated: true)
            }
        } else {
            actionLogger.error("没有找到 ScrollView")
        }

        // 等待动画完成
        try await Task.sleep(nanoseconds: 300_000_000) // 300ms

        return ExecutionResult(success: true)
    }

    private func performLongPress(at point: CGPoint) async throws -> ExecutionResult {
        let actualPoint = CGPoint(
            x: point.x * window.bounds.width,
            y: point.y * window.bounds.height
        )

        guard let view = window.hitTest(actualPoint, with: nil) else {
            throw ActionError.elementNotFound
        }

        // 使用 accessibility 激活
        view.accessibilityActivate()

        // 等待长按延迟
        try await Task.sleep(nanoseconds: 500_000_000) // 500ms

        return ExecutionResult(success: true)
    }

    private func performDoubleTap(at point: CGPoint) async throws -> ExecutionResult {
        // 双击 = 两次点击
        try await performTap(at: point)

        // 短暂延迟
        try await Task.sleep(nanoseconds: 100_000_000) // 100ms

        try await performTap(at: point)

        return ExecutionResult(success: true)
    }

    private func performBack() async throws -> ExecutionResult {
        guard let navVC = findNavigationController() else {
            throw ActionError.noNavigationController
        }

        navVC.popViewController(animated: true)

        // 等待导航动画
        try await Task.sleep(nanoseconds: 300_000_000) // 300ms

        return ExecutionResult(success: true)
    }

    private func performWait(duration: TimeInterval) async throws -> ExecutionResult {
        let seconds = UInt64(duration * 1_000_000_000)
        try await Task.sleep(nanoseconds: seconds)
        return ExecutionResult(success: true)
    }

    // MARK: - Helper Methods

    private func getKeyWindow() -> UIWindow? {
        if let windowScene = UIApplication.shared.connectedScenes
            .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene {
            return windowScene.windows.first(where: { $0.isKeyWindow })
        }
        return UIApplication.shared.windows.first(where: { $0.isKeyWindow })
    }

    private func findFirstResponder() -> UIView? {
        return findFirstResponder(in: window)
    }

    private func findFirstResponder(in view: UIView) -> UIView? {
        if view.isFirstResponder {
            return view
        }

        for subview in view.subviews {
            if let found = findFirstResponder(in: subview) {
                return found
            }
        }

        return nil
    }

    private func findFirstTextInputField() -> UITextField? {
        return findFirstTextInputField(in: window)
    }

    private func findFirstTextInputField(in view: UIView) -> UITextField? {
        // 检查当前 view 是否是 UITextField
        if let textField = view as? UITextField, textField.isEnabled, !textField.isHidden, textField.isUserInteractionEnabled {
            return textField
        }

        // 递归检查子视图
        for subview in view.subviews {
            if let found = findFirstTextInputField(in: subview) {
                return found
            }
        }

        return nil
    }

    private func findNavigationController() -> UINavigationController? {
        return findNavigationController(in: window.rootViewController)
    }

    private func findNavigationController(in viewController: UIViewController?) -> UINavigationController? {
        guard let vc = viewController else { return nil }

        if let nav = vc as? UINavigationController {
            return nav
        }

        if let nav = vc.navigationController {
            return nav
        }

        if let presented = vc.presentedViewController {
            return findNavigationController(in: presented)
        }

        return nil
    }

    private func findScrollView(at point: CGPoint) -> UIScrollView? {
        guard let view = window.hitTest(point, with: nil) else {
            return nil
        }

        // 向上遍历查找 UIScrollView（但跳过 UITextView）
        var currentView: UIView? = view
        var textViewScrollView: UIScrollView?

        while currentView != nil {
            // 保存找到的 UITextView（作为备选）
            if currentView is UITextView {
                if let tv = currentView as? UIScrollView {
                    textViewScrollView = tv
                }
            }
            // 返回真正的 UIScrollView（非 UITextView）
            else if let scrollView = currentView as? UIScrollView {
                return scrollView
            }
            currentView = currentView?.superview
        }

        // 如果没有找到真正的 UIScrollView，返回 UITextView（备选）
        return textViewScrollView
    }

    /// 查找主界面的 UIScrollView（用于整屏滑动）
    private func findMainScrollView() -> UIScrollView? {
        return findMainScrollView(in: window)
    }

    private func findMainScrollView(in view: UIView) -> UIScrollView? {
        // 检查当前 view
        if let scrollView = view as? UIScrollView, !(view is UITextView) {
            return scrollView
        }

        // 递归检查子视图
        for subview in view.subviews {
            if let found = findMainScrollView(in: subview) {
                return found
            }
        }

        return nil
    }
}

// MARK: - Notification Names
extension Notification.Name {
    static let agentTakeOver = Notification.Name("AgentTakeOver")
}
