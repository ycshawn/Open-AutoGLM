# PhoneAgentSDK 使用指南

## 项目结构

```
iOS-SDK/
├── PhoneAgentSDK/           # SDK 源代码
│   └── Sources/PhoneAgentSDK/
│       ├── Agent/           # 核心引擎
│       │   ├── AgentEngine.swift      # 主引擎
│       │   ├── AgentConfig.swift      # 配置
│       │   └── AgentResult.swift      # 结果
│       ├── Model/           # 模型通信
│       │   ├── ModelClient.swift      # 客户端
│       │   ├── ModelConfig.swift      # 配置
│       │   ├── ModelResponse.swift    # 响应
│       │   └── MessageBuilder.swift   # 消息构建
│       ├── Capture/        # 屏幕捕获
│       │   ├── ScreenCapture.swift    # 截图
│       │   └── ScreenInfo.swift       # 屏幕信息
│       └── Action/         # 操作执行
│           └── ActionExecutor.swift   # 执行器
│
└── DemoApp/                # 示例 App
    └── DemoApp/
        ├── DemoApp.swift            # App 入口
        ├── SceneDelegate.swift       # 场景代理
        ├── Views/                   # 视图
        │   ├── MainViewController.swift      # 主界面
        │   ├── LoginViewController.swift     # 登录页
        │   └── ProductListViewController.swift # 商品列表
        └── Assets.xcassets/         # 资源
```

## 如何运行 Demo

### 1. 创建 Xcode 项目

由于纯代码无法直接创建完整的项目文件，你需要手动创建一个 Xcode 项目：

```bash
# 在 Xcode 中：
# 1. 创建新的 iOS App 项目 (iOS 15.0+)
# 2. 将 PhoneAgentSDK/Sources 下的文件添加到项目
# 3. 将 DemoApp/DemoApp 下的文件添加到项目
```

### 2. 配置项目

1. 确保项目部署目标为 iOS 15.0 或更高
2. 添加必要的权限（如果需要）
3. 确保网络可用（SDK 需要访问云端 API）

### 3. 配置模型服务

在 Demo App 的主界面中：
- **API 地址**: 你的 AutoGLM 模型服务地址，如 `http://localhost:8000/v1`
- **模型名称**: 使用的模型名称，如 `autoglm-phone-9b`

### 4. 运行测试

1. 启动 AutoGLM 模型服务（本地或云端）
2. 运行 Demo App
3. 在任务输入框中输入指令，例如：
   - "点击列表页面"
   - "点击登录页面"
   - "在登录页面输入用户名 admin，密码 123456，然后点击登录"

## 核心 API 说明

### AgentEngine

```swift
// 创建引擎
let engine = AgentEngine(
    modelConfig: ModelConfig(
        baseURL: "http://localhost:8000/v1",
        modelName: "autoglm-phone-9b"
    ),
    agentConfig: AgentConfig(maxSteps: 50)
)

// 执行任务
let result = await engine.run(instruction: "打开商品列表页面")

// 监听进度
engine.onStepProgress = { step, thinking, action in
    print("步骤 \(step): \(thinking)")
}

engine.onComplete = { result in
    print("完成: \(result.success)")
}
```

### ModelConfig

```swift
let config = ModelConfig(
    baseURL: String           // API 地址
    modelName: String         // 模型名称
    maxTokens: Int            // 最大 token 数 (默认 3000)
    temperature: Double       // 温度参数 (默认 0.0)
    language: Language        // 语言 .chinese / .english
)
```

### AgentConfig

```swift
let config = AgentConfig(
    maxSteps: Int                     // 最大执行步数 (默认 50)
    stepDelay: TimeInterval           // 每步延迟 (默认 0.5s)
    verbose: Bool                     // 是否打印日志 (默认 true)
    autoConfirmSensitiveActions: Bool // 自动确认敏感操作 (默认 false)
)
```

## 工作原理

```
用户输入任务
    ↓
1. AgentEngine 捕获当前屏幕截图
    ↓
2. 将截图 + 任务指令发送给云端模型
    ↓
3. 模型返回动作（如 "点击坐标 [500, 300]"）
    ↓
4. ActionExecutor 在 App 内执行动作
    ↓
5. 重复 1-4 直到任务完成
```

## 支持的动作

| 动作 | 说明 |
|------|------|
| Tap | 点击屏幕 |
| Type | 输入文本（需要先点击输入框） |
| Swipe | 滑动 |
| Long Press | 长按 |
| Double Tap | 双击 |
| Back | 返回上一页 |
| Wait | 等待指定时间 |

## 注意事项

1. **模型服务**：需要先启动 AutoGLM 模型服务，SDK 才能正常工作
2. **网络**：确保设备可以访问模型服务地址
3. **UI 响应**：每步操作后有 0.5 秒延迟，确保 UI 有时间响应
4. **坐标系统**：模型使用 0-999 的归一化坐标，SDK 会自动映射到实际屏幕

## 下一步

- 查看 `docs/ios_agent_integration.md` 了解完整的技术方案
- 如果需要更精确的控制，考虑实现混合方案（元素标识）
- 根据你的 App 需求自定义系统提示词
