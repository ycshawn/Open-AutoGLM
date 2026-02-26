//
//  ScreenCapture.swift
//  PhoneAgentSDK
//
//  Created by AutoGLM on 2025-01-30.
//

import UIKit

/// 屏幕捕获器 - 负责捕获 App 的屏幕截图
public struct ScreenCapture {

    public enum ScreenError: LocalizedError {
        case noWindow
        case noRootViewController
        case captureFailed

        public var errorDescription: String? {
            switch self {
            case .noWindow:
                return "No active window found"
            case .noRootViewController:
                return "No root view controller found"
            case .captureFailed:
                return "Failed to capture screen"
            }
        }
    }

    /// 屏幕截图信息
    public struct Screenshot {
        /// PNG 格式的图片数据
        public let imageData: Data

        /// Base64 编码的图片数据
        public var base64Data: String {
            return imageData.base64EncodedString()
        }

        /// 图片
        public var image: UIImage? {
            return UIImage(data: imageData)
        }

        /// 屏幕宽度
        public let width: CGFloat

        /// 屏幕高度
        public let height: CGFloat

        /// 屏幕方向
        public let orientation: UIInterfaceOrientation

        public init(imageData: Data, width: CGFloat, height: CGFloat, orientation: UIInterfaceOrientation) {
            self.imageData = imageData
            self.width = width
            self.height = height
            self.orientation = orientation
        }
    }

    // MARK: - Public Methods

    /// 捕获当前屏幕截图
    /// - Parameter window: 指定窗口，nil 则使用主窗口
    /// - Returns: 屏幕截图信息
    public static func capture(window: UIWindow? = nil) throws -> Screenshot {
        // 获取窗口
        let targetWindow = window ?? getKeyWindow()

        guard let window = targetWindow else {
            throw ScreenError.noWindow
        }

        guard let rootViewController = window.rootViewController else {
            throw ScreenError.noRootViewController
        }

        // 获取屏幕尺寸
        let screen = UIScreen.main
        let screenWidth = screen.bounds.width
        let screenHeight = screen.bounds.height
        let orientation = window.windowScene?.interfaceOrientation ?? .portrait

        // 使用 UIGraphicsImageRenderer 截图
        let renderer = UIGraphicsImageRenderer(size: window.bounds.size)

        guard let imageData = renderer.image { context in
            window.drawHierarchy(in: window.bounds, afterScreenUpdates: false)
        }.pngData() else {
            throw ScreenError.captureFailed
        }

        return Screenshot(
            imageData: imageData,
            width: screenWidth,
            height: screenHeight,
            orientation: orientation
        )
    }

    /// 捕获指定 View 的截图
    /// - Parameter view: 要截图的 View
    /// - Returns: 屏幕截图信息
    public static func capture(view: UIView) throws -> Screenshot {
        let renderer = UIGraphicsImageRenderer(size: view.bounds.size)

        guard let imageData = renderer.image { context in
            view.drawHierarchy(in: view.bounds, afterScreenUpdates: false)
        }.pngData() else {
            throw ScreenError.captureFailed
        }

        return Screenshot(
            imageData: imageData,
            width: view.bounds.width,
            height: view.bounds.height,
            orientation: .portrait
        )
    }

    // MARK: - Private Methods

    /// 获取关键窗口
    private static func getKeyWindow() -> UIWindow? {
        // iOS 13+
        if let windowScene = UIApplication.shared.connectedScenes
            .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene {
            return windowScene.windows.first(where: { $0.isKeyWindow })
        }

        // 降级方案
        return UIApplication.shared.windows.first(where: { $0.isKeyWindow })
    }

    /// 获取当前屏幕名称
    public static func getCurrentScreenName() -> String {
        guard let window = getKeyWindow(),
              let rootVC = window.rootViewController else {
            return "Unknown"
        }

        return getScreenName(for: rootVC)
    }

    /// 递归获取当前显示的 ViewController 名称
    private static func getScreenName(for viewController: UIViewController) -> String {
        // 检查是否有 presented 的 ViewController
        if let presented = viewController.presentedViewController {
            return getScreenName(for: presented)
        }

        // 检查是否是导航控制器
        if let nav = viewController as? UINavigationController,
           let topVC = nav.topViewController {
            return getScreenName(for: topVC)
        }

        // 检查是否是 TabBar 控制器
        if let tab = viewController as? UITabBarController,
           let selected = tab.selectedViewController {
            return getScreenName(for: selected)
        }

        // 返回当前 ViewController 的名称
        return String(describing: type(of: viewController))
            .replacingOccurrences(of: "<", with: "")
            .replacingOccurrences(of: ">", with: "")
    }
}

// MARK: - 便捷方法
extension ScreenCapture {

    /// 捕获并返回 base64 编码的截图
    public static func captureBase64() throws -> String {
        let screenshot = try capture()
        return screenshot.base64Data
    }

    /// 捕获并返回 UIImage
    public static func captureImage() throws -> UIImage {
        let screenshot = try capture()
        guard let image = screenshot.image else {
            throw ScreenError.captureFailed
        }
        return image
    }
}
