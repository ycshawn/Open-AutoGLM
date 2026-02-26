//
//  ModelResponse.swift
//  PhoneAgentSDK
//
//  Created by AutoGLM on 2025-01-30.
//

import Foundation
import CoreGraphics

/// 模型响应
public struct ModelResponse {

    /// 推理过程（思维链）
    public let thinking: String

    /// 动作指令
    public let action: String

    /// 原始响应内容
    public let rawContent: String

    /// 性能指标
    public let metrics: ResponseMetrics?

    /// 是否完成
    public var isFinished: Bool {
        return action.contains("finish(message=")
    }

    public struct ResponseMetrics {
        /// 首字延迟（秒）
        public let timeToFirstToken: Double

        /// 思考结束时间（秒）
        public let timeToThinkingEnd: Double?

        /// 总耗时（秒）
        public let totalTime: Double

        public init(
            timeToFirstToken: Double,
            timeToThinkingEnd: Double? = nil,
            totalTime: Double
        ) {
            self.timeToFirstToken = timeToFirstToken
            self.timeToThinkingEnd = timeToThinkingEnd
            self.totalTime = totalTime
        }
    }

    public init(
        thinking: String,
        action: String,
        rawContent: String,
        metrics: ResponseMetrics? = nil
    ) {
        self.thinking = thinking
        self.action = action
        self.rawContent = rawContent
        self.metrics = metrics
    }
}

// MARK: - 动作解析
extension ModelResponse {

    /// 解析动作类型
    public enum ActionType: Equatable {
        case launch(app: String)
        case tap(point: CGPoint)
        case tapWithMessage(point: CGPoint, message: String)
        case type(text: String)
        case swipe(start: CGPoint, end: CGPoint)
        case longPress(point: CGPoint)
        case doubleTap(point: CGPoint)
        case back
        case home
        case wait(duration: TimeInterval)
        case takeOver(message: String)
        case finish(message: String)
        case unknown(text: String)
    }

    /// 解析动作
    public func parseAction(screenWidth: CGFloat, screenHeight: CGFloat) -> ActionType {
        let action = self.action.trimmingCharacters(in: .whitespacesAndNewlines)

        // finish(message="xxx")
        if action.contains("finish(message=") {
            if let range = action.range(of: "finish(message=") {
                let start = range.upperBound
                let remaining = String(action[start...])
                if let end = remaining.range(of: "\")", range: remaining.startIndex..<remaining.endIndex) {
                    let message = String(remaining[remaining.startIndex..<end.lowerBound])
                    return .finish(message: message)
                }
            }
        }

        // do(action="Launch", app="xxx")
        if action.contains("do(action=\"Launch\"") {
            if let range = action.range(of: "app=\"") {
                let start = range.upperBound
                if let end = action.range(of: "\"", range: start..<action.endIndex) {
                    let app = String(action[start..<end.lowerBound])
                    return .launch(app: app)
                }
            }
        }

        // do(action="Tap", element=[x,y])
        if action.contains("do(action=\"Tap\"") || action.contains("do(action=\"Long Press\"") || action.contains("do(action=\"Double Tap\"") {
            if let point = parsePoint(from: action) {
                if action.contains("message=\"") {
                    // 有敏感消息的 Tap
                    if let range = action.range(of: "message=\"") {
                        let start = range.upperBound
                        if let end = action.range(of: "\")", range: start..<action.endIndex) {
                            let message = String(action[start..<end.lowerBound])
                            return .tapWithMessage(point: point, message: message)
                        }
                    }
                }

                if action.contains("Long Press") {
                    return .longPress(point: point)
                } else if action.contains("Double Tap") {
                    return .doubleTap(point: point)
                } else {
                    return .tap(point: point)
                }
            }
        }

        // do(action="Type", text="xxx")
        if action.contains("do(action=\"Type\"") {
            if let range = action.range(of: "text=\"") {
                let start = range.upperBound
                if let end = action.range(of: "\"", range: start..<action.endIndex) {
                    let text = String(action[start..<end.lowerBound])
                    return .type(text: text)
                }
            }
        }

        // do(action="Swipe", start=[x1,y1], end=[x2,y2])
        if action.contains("do(action=\"Swipe\"") {
            let pattern = #"start=\[(\d+),(\d+)\].*?end=\[(\d+),(\d+)\]"#
            if let regex = try? NSRegularExpression(pattern: pattern),
               let match = regex.firstMatch(in: action, range: NSRange(action.startIndex..., in: action)) {

                let x1 = (action as NSString).substring(with: match.range(at: 1))
                let y1 = (action as NSString).substring(with: match.range(at: 2))
                let x2 = (action as NSString).substring(with: match.range(at: 3))
                let y2 = (action as NSString).substring(with: match.range(at: 4))

                let start = normalizedPoint(x: Double(x1) ?? 0, y: Double(y1) ?? 0, screenWidth: screenWidth, screenHeight: screenHeight)
                let end = normalizedPoint(x: Double(x2) ?? 0, y: Double(y2) ?? 0, screenWidth: screenWidth, screenHeight: screenHeight)

                return .swipe(start: start, end: end)
            }
        }

        // do(action="Back")
        if action.contains("do(action=\"Back\"") {
            return .back
        }

        // do(action="Home")
        if action.contains("do(action=\"Home\"") {
            return .home
        }

        // do(action="Wait", duration="x seconds")
        if action.contains("do(action=\"Wait\"") {
            if let range = action.range(of: "duration=\"") {
                let start = range.upperBound
                if let end = action.range(of: "\"", range: start..<action.endIndex) {
                    let durationStr = String(action[start..<end.lowerBound])
                        .replacingOccurrences(of: " seconds", with: "")
                        .replacingOccurrences(of: " second", with: "")
                    let duration = Double(durationStr) ?? 1.0
                    return .wait(duration: duration)
                }
            }
        }

        // do(action="Take_over", message="xxx")
        if action.contains("do(action=\"Take_over\"") {
            if let range = action.range(of: "message=\"") {
                let start = range.upperBound
                if let end = action.range(of: "\")", range: start..<action.endIndex) {
                    let message = String(action[start..<end.lowerBound])
                    return .takeOver(message: message)
                }
            }
        }

        return .unknown(text: action)
    }

    /// 从动作字符串中解析坐标点
    private func parsePoint(from action: String) -> CGPoint? {
        // 匹配 element=[x,y] 格式
        let pattern = #"element=\[(\d+),(\d+)\]"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: action, range: NSRange(action.startIndex..., in: action)) else {
            return nil
        }

        let xStr = (action as NSString).substring(with: match.range(at: 1))
        let yStr = (action as NSString).substring(with: match.range(at: 2))

        // 模型输出的是 0-999 的归一化坐标
        let normalizedX = Double(xStr) ?? 0
        let normalizedY = Double(yStr) ?? 0

        // 返回归一化坐标（0-1），调用时再映射到实际屏幕
        return CGPoint(x: normalizedX / 999.0, y: normalizedY / 999.0)
    }

    /// 将归一化坐标映射到实际屏幕尺寸
    private func normalizedPoint(x: Double, y: Double, screenWidth: CGFloat, screenHeight: CGFloat) -> CGPoint {
        return CGPoint(
            x: x / 999.0,  // 返回归一化坐标（0-1）
            y: y / 999.0
        )
    }
}

// MARK: - 便捷工厂方法
extension ModelResponse.ResponseMetrics {
    /// 创建性能指标
    public static func make(
        timeToFirstToken: Double,
        timeToThinkingEnd: Double? = nil,
        totalTime: Double
    ) -> ModelResponse.ResponseMetrics {
        return ModelResponse.ResponseMetrics(
            timeToFirstToken: timeToFirstToken,
            timeToThinkingEnd: timeToThinkingEnd,
            totalTime: totalTime
        )
    }
}

// MARK: - ActionType Debug Description
extension ModelResponse.ActionType: CustomStringConvertible {
    public var description: String {
        switch self {
        case .launch(let app):
            return "Launch(app: \(app))"
        case .tap(let point):
            return "Tap(point: \(String(format: "%.2f,%.2f", point.x, point.y)))"
        case .tapWithMessage(let point, let message):
            return "TapWithMessage(point: \(String(format: "%.2f,%.2f", point.x, point.y)), message: \(message))"
        case .type(let text):
            return "Type(text: \(text))"
        case .swipe(let start, let end):
            return "Swipe(start: \(String(format: "%.2f,%.2f", start.x, start.y)), end: \(String(format: "%.2f,%.2f", end.x, end.y)))"
        case .longPress(let point):
            return "LongPress(point: \(String(format: "%.2f,%.2f", point.x, point.y)))"
        case .doubleTap(let point):
            return "DoubleTap(point: \(String(format: "%.2f,%.2f", point.x, point.y)))"
        case .back:
            return "Back"
        case .home:
            return "Home"
        case .wait(let duration):
            return "Wait(duration: \(duration)s)"
        case .takeOver(let message):
            return "TakeOver(message: \(message))"
        case .finish(let message):
            return "Finish(message: \(message))"
        case .unknown(let text):
            return "Unknown(text: \(text))"
        }
    }
}
