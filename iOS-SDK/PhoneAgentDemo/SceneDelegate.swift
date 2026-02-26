//
//  SceneDelegate.swift
//  DemoApp
//
//  Created by AutoGLM on 2025-01-30.
//

import UIKit

class SceneDelegate: UIResponder, UIWindowSceneDelegate {

    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = (scene as? UIWindowScene) else { return }

        // 创建窗口
        window = UIWindow(windowScene: windowScene)
        window!.backgroundColor = .systemBackground

        // 创建主视图控制器
        let mainViewController = MainViewController()

        // 创建导航控制器
        let navigationController = UINavigationController(rootViewController: mainViewController)

        // 设置为根视图控制器
        window!.rootViewController = navigationController
        window!.makeKeyAndVisible()

        print("✅ SceneDelegate: 窗口已创建并显示")
    }

    func sceneDidDisconnect(_ scene: UIScene) {
        print("⚠️ SceneDelegate: 场景断开连接")
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        print("✅ SceneDelegate: 场景变为活跃")
    }

    func sceneWillResignActive(_ scene: UIScene) {
        print("⏸️ SceneDelegate: 场景即将变为非活跃")
    }

    func sceneWillEnterForeground(_ scene: UIScene) {
        print("⏸️ SceneDelegate: 场景即将进入前台")
    }

    func sceneDidEnterBackground(_ scene: UIScene) {
        print("⏸️ SceneDelegate: 场景已进入后台")
    }
}
