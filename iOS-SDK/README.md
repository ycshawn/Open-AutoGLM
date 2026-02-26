# PhoneAgentSDK - iOS App 内嵌 AI Agent SDK

[![Swift](https://img.shields.io/badge/Swift-5.9-orange.svg)](https://swift.org)
[![iOS](https://img.shields.io/badge/iOS-15.0+-blue.svg)](https://apple.com/ios)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](LICENSE)

## 简介

PhoneAgentSDK 是一个纯视觉方案的 iOS AI Agent SDK，让 iOS App 能够通过自然语言指令自动执行 UI 操作。

## 特性

- **纯视觉识别**：模型通过截图理解界面，无需修改现有 UI 代码
- **零侵入性**：SDK 独立运行，不污染现有代码
- **云端模型**：调用 AutoGLM 云端模型，无需本地部署
- **实时反馈**：实时显示模型思考过程和执行进度
- **可扩展**：支持自定义回调，灵活集成

## 架构

```
┌─────────────────────────────────────────────────────────────┐
│                        iOS App                              │
│                                                             │
│  ┌──────────────┐          ┌──────────────────────────┐    │
│  │   UI Layer   │          │   PhoneAgentSDK          │    │
│  │              │◄─────────│                          │    │
│  └──────────────┘          │  ┌────────────────────┐  │    │
│                            │  │   AgentEngine      │  │    │
│                            │  │   (核心引擎)        │  │    │
│                            │  └────────────────────┘  │    │
│                            │                          │    │
│                            │  ┌────────────────────┐  │    │
│                            │  │   ScreenCapture    │  │    │
│                            │  │   (屏幕捕获)        │  │    │
│                            │  └────────────────────┘  │    │
│                            │                          │    │
│                            │  ┌────────────────────┐  │    │
│                            │  │   ActionExecutor   │  │    │
│                            │  │   (操作执行)        │  │    │
│                            │  └────────────────────┘  │    │
│                            │                          │    │
│                            │  ┌────────────────────┐  │    │
│                            │  │   ModelClient      │  │    │
│                            │  │   (模型通信)        │  │    │
│                            │  └────────────────────┘  │    │
│                            └──────────────────────────┘    │
│                                         │                   │
└─────────────────────────────────────────┼───────────────────┘
                                          ▼
                              ┌─────────────────────┐
                              │  AutoGLM 云端 API    │
                              └─────────────────────┘
```

## 快速开始

### 1. 基本使用

```swift
import PhoneAgentSDK

// 创建 Agent 引擎
let modelConfig = ModelConfig(
    baseURL: "http://localhost:8000/v1",
    modelName: "autoglm-phone-9b"
)
let agentConfig = AgentConfig(maxSteps: 50)
let engine = AgentEngine(modelConfig: modelConfig, agentConfig: agentConfig)

// 执行任务
Task {
    let result = await engine.run(instruction: "打开商品列表页面，搜索苹果")
    print("结果: \(result.message ?? "无")")
}
```

### 2. 监听执行进度

```swift
engine.onStepProgress = { step, thinking, action in
    print("步骤 \(step): \(thinking)")
    print("动作: \(action)")
}

engine.onComplete = { result in
    print("任务完成: \(result.success)")
    print("总步数: \(result.totalSteps)")
}
```

### 3. 敏感操作确认

```swift
engine.onSensitiveAction = { message in
    // 显示确认对话框
    return await showConfirmationDialog(message: message)
}
```

## 文档

详细文档请参考：
- [技术方案对比](./docs/ios_agent_integration.md)
- [API 文档](./docs/API.md)

## 示例

Demo App 包含以下示例：

1. **登录页面**：测试自动登录
2. **商品列表**：测试搜索、点击、导航
3. **实时日志**：显示模型思考和执行过程

## 要求

- iOS 15.0+
- Swift 5.9+
- Xcode 15.0+

## 许可证

Apache License 2.0
