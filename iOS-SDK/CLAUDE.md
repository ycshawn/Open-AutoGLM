# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 在此代码库中工作时提供指导。

## 项目概述

PhoneAgentSDK 是一个基于**纯视觉自动化**的 iOS 应用内 AI Agent SDK。SDK 通过截图、发送给云端 AutoGLM 模型、然后在应用内执行 UI 操作 - 全程无需修改现有 UI 代码。

**核心特性**：零代码侵入。Agent 完全通过截图和基于坐标的操作运行。

## 架构

```
┌─────────────────────────────────────────────────────────────┐
│                        iOS App                              │
│                                                             │
│  ┌──────────────┐          ┌──────────────────────────┐    │
│  │   UI 层      │          │   PhoneAgentSDK          │    │
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

### 核心组件

| 组件 | 位置 | 职责 |
|-----------|----------|----------------|
| **AgentEngine** | `SDK/Agent/AgentEngine.swift` | 主协调器，管理执行循环、对话历史 |
| **ModelClient** | `SDK/Model/ModelClient.swift` | AutoGLM API 的 HTTP 客户端，解析响应 |
| **ActionExecutor** | `SDK/Action/ActionExecutor.swift` | 通过 UIKit API 执行操作 |
| **ScreenCapture** | `SDK/Capture/ScreenCapture.swift` | 捕获截图，返回 base64 |
| **MessageBuilder** | `SDK/Model/MessageBuilder.swift` | 构建系统提示词和消息 |

## 构建命令

### 使用 Xcode（推荐）
```bash
open PhoneAgentDemo.xcodeproj
# 按 Cmd+R 构建并运行
```

### 使用命令行
```bash
# 列出可用的 schemes 和 destinations
xcodebuild -project PhoneAgentDemo.xcodeproj -list

# 为模拟器构建
xcodebuild -project PhoneAgentDemo.xcodeproj \
  -scheme PhoneAgentDemo \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' \
  build

# 查找可用模拟器
xcrun simctl list devices available
```

### 项目 Target
- **PhoneAgentDemo** - Demo 应用和嵌入的 SDK

## SDK 集成方式（无包管理器）

**重要**：SDK 作为源文件嵌入，不是外部包。SDK 文件位于 `PhoneAgentDemo/SDK/`，直接编译到应用中。

添加新 SDK 功能时：
1. 将源文件添加到 `PhoneAgentDemo/SDK/<模块>/`
2. 确保文件在 Xcode 中已添加到 PhoneAgentDemo target
3. 将公共 API 标记为 `public`

## 模型服务配置

SDK 需要 AutoGLM 模型服务。支持的选项：

| 提供商 | Base URL | 模型 |
|----------|-----------|-------|
| **智谱 AI BigModel** | `https://open.bigmodel.cn/api/paas/v4` | `autoglm-phone` |
| **ModelScope** | `https://api-inference.modelscope.cn/v1` | `ZhipuAI/AutoGLM-Phone-9b` |
| **自部署** | `http://localhost:8000/v1` | `autoglm-phone-9b` |

Demo 应用使用 UserDefaults 持久化存储配置。

## 坐标系统

模型使用**归一化坐标（0-999）**执行操作。ActionExecutor 会自动将其缩放到实际屏幕尺寸：

```
模型输出: [500, 500] (0-999 空间中的屏幕中心)
实际坐标: 缩放到 screen.width * 0.5, screen.height * 0.5
```

## 系统提示词格式

**关键**：系统提示词使用特定格式，与标准 LLM 提示词不同：

```
今天的日期是: {date}

你是一个智能体分析专家，可以根据操作历史和当前状态图执行一系列操作来完成任务。
你必须严格按照要求输出以下格式：

{think}
{answer}

其中：
- {think} 是对你为什么选择这个操作的简短推理说明。
- {answer} 是本次执行的具体操作指令，必须严格遵循下方定义的指令格式。
```

**不要使用 XML 风格标签**如 `<thinking>` 或 `<answer>` - 模型被训练为输出纯文本格式。

## 响应解析

模型响应按以下优先级解析：

1. `finish(message=...)` - 任务完成
2. `do(action=...)` - 操作执行
3. 遗留 XML 标签（仅用于向后兼容）

参见 `ModelClient.parseThinking()` 和 `ModelClient.parseAction()`。

## 关键实现细节

### 线程模型
- 所有 SDK 组件使用 `@MainActor` - UI 操作必须在主线程运行
- `AgentEngine` 通过 async/await 协调整个工作流程

### 动作解析优先级（关键）
解析模型响应时，顺序很重要（`ModelClient.parseAction()`）：
1. `finish(message=...)` - 任务完成
2. `do(action=...)` - 操作执行（中文提示词格式）
3. `<answer>...</answer>` - 遗留 XML 格式（英文提示词格式）

如果连续 3 次解析返回空字符串，引擎将终止执行以防止无限循环。

### UIScrollView + UIStackView 模式
在 UIScrollView 中嵌入 UIStackView 时，添加宽度约束：
```swift
contentStackView.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor, constant: -32)
```

### 日志策略
- 使用 `print()` 调试关键问题（会出现在 Xcode 控制台）
- `OSLog` (`logger.error()`) 用于结构化日志，但可能不会出现在控制台
- `config.verbose` 标志控制 AgentEngine 的详细输出

## 相关文档
- `README.md` - 项目概述
- `BUILD_GUIDE.md` - 详细构建说明
- `../phone_agent/` - Python 参考实现
