//
//  ScreenInfo.swift
//  PhoneAgentSDK
//
//  Created by AutoGLM on 2025-01-30.
//

import Foundation
import UIKit

/// 屏幕信息 - 发送给模型的上下文信息
public struct ScreenInfo {

    /// 当前屏幕名称（ViewController 名称）
    public let screenName: String

    /// 屏幕宽度
    public let screenWidth: CGFloat

    /// 屏幕高度
    public let screenHeight: CGFloat

    /// 屏幕方向
    public let orientation: String

    /// 额外信息
    public let extraInfo: [String: Any]

    public init(
        screenName: String,
        screenWidth: CGFloat,
        screenHeight: CGFloat,
        orientation: UIInterfaceOrientation = .portrait,
        extraInfo: [String: Any] = [:]
    ) {
        self.screenName = screenName
        self.screenWidth = screenWidth
        self.screenHeight = screenHeight
        self.orientation = orientation.description
        self.extraInfo = extraInfo
    }

    /// 从截图创建屏幕信息
    public init(screenshot: ScreenCapture.Screenshot, screenName: String, extraInfo: [String: Any] = [:]) {
        self.screenName = screenName
        self.screenWidth = screenshot.width
        self.screenHeight = screenshot.height
        self.orientation = screenshot.orientation.description
        self.extraInfo = extraInfo
    }

    /// 转换为 JSON 字符串
    public func toJSONString() -> String {
        var info: [String: Any] = [
            "current_screen": screenName,
            "screen_width": screenWidth,
            "screen_height": screenHeight,
            "orientation": orientation
        ]
        info.merge(extraInfo) { _, new in new }

        guard let data = try? JSONSerialization.data(withJSONObject: info, options: [.prettyPrinted, .withoutEscapingSlashes]),
              let string = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return string
    }
}

extension UIInterfaceOrientation {
    var description: String {
        switch self {
        case .portrait:
            return "portrait"
        case .portraitUpsideDown:
            return "portraitUpsideDown"
        case .landscapeLeft:
            return "landscapeLeft"
        case .landscapeRight:
            return "landscapeRight"
        case .unknown:
            return "unknown"
        @unknown default:
            return "unknown"
        }
    }
}
