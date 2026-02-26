# PhoneAgent Android SDK

基于**纯视觉自动化**的 Android 应用内 AI Agent SDK。SDK 通过截图、发送给云端 AutoGLM 模型、然后在应用内执行 UI 操作 - 全程无需修改现有 UI 代码。

## 核心特性

- ✅ **零代码侵入**：Agent 完全通过截图和基于坐标的操作运行
- ✅ **同一 App 内操作**：无需 Root 或无障碍权限
- ✅ **视觉理解**：基于 LLM 的屏幕理解和操作规划
- ✅ **Kotlin 实现**：现代 Android 开发最佳实践
- ✅ **Coroutines + StateFlow**：响应式状态管理

## 项目结构

```
Android-SDK/
├── phoneagent-sdk/          # SDK 模块
│   └── src/main/java/com/autoglm/phoneagent/
│       ├── agent/           # 核心引擎
│       ├── model/           # 模型通信
│       ├── action/          # 操作执行
│       ├── capture/         # 屏幕捕获
│       └── utils/           # 工具类
├── demoapp/                 # Demo 应用
│   └── src/main/
│       ├── java/com/demo/phoneagent/
│       └── res/             # 资源文件
└── README.md
```

## 快速开始

### 系统要求

- Android 7.0 (API 24) 或更高版本
- Kotlin 1.9.0+
- Gradle 8.1.0+

### 集成 SDK

1. 将 SDK 模块添加到你的项目：

```kotlin
// settings.gradle.kts
include(":phoneagent-sdk")
```

2. 在应用模块中添加依赖：

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":phoneagent-sdk"))
}
```

### 基本使用

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var agentEngine: AgentEngine

    fun executeTask(instruction: String) {
        // 创建模型配置
        val modelConfig = ModelConfig(
            baseURL = "https://open.bigmodel.cn/api/paas/v4",
            apiKey = "your-api-key",
            modelName = "autoglm-phone",
            language = ModelConfig.Language.CHINESE
        )

        // 创建 Agent 引擎
        agentEngine = AgentEngine.create(
            activity = this,
            modelConfig = modelConfig,
            agentConfig = AgentConfig(
                maxSteps = 30,
                stepDelay = 500,
                verbose = true
            )
        )

        // 设置回调
        agentEngine?.apply {
            onStepProgress = { step, thinking, action ->
                println("步骤 $step: $thinking")
                println("动作: $action")
            }

            onComplete = { result ->
                println("任务完成: ${result.success}")
            }
        }

        // 执行任务
        lifecycleScope.launch {
            val result = agentEngine?.run(instruction)
        }
    }
}
```

## SDK 架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Android App                               │
│                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │ 截图捕获      │───▶│ 云端 LLM      │───▶│ 操作执行      │  │
│  │ ScreenCapture│   │ AutoGLM API  │   │ActionExecutor│  │
│  └──────────────┘    └──────────────┘    └──────────────┘  │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              AgentEngine (主引擎)                      │  │
│  │  • 状态管理 (StateFlow)                               │  │
│  │  • 工作流协调                                          │  │
│  │  • 回调处理                                            │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 核心组件

### AgentEngine

主引擎，协调整个工作流程：

```kotlin
class AgentEngine(
    activity: Activity,
    modelClient: ModelClient,
    config: AgentConfig
) {
    // StateFlow 用于观察状态
    val currentStep: StateFlow<Int>
    val isRunning: StateFlow<Boolean>
    val currentThinking: StateFlow<String>
    val history: StateFlow<List<StepInfo>>

    // 回调
    var onStepProgress: ((step, thinking, action) -> Unit)?
    var onSensitiveAction: ((message) -> suspend () -> Boolean)?
    var onComplete: ((AgentResult) -> Unit)?

    // 执行任务
    suspend fun run(instruction: String): AgentResult

    // 停止执行
    fun stop()
}
```

### ModelClient

与 AutoGLM 云端 API 通信：

```kotlin
class ModelClient(config: ModelConfig) {
    suspend fun sendRequest(
        messages: List<Message>
    ): ModelResponse

    // 回调
    var onThinking: ((String) -> Unit)?
    var onAction: ((String) -> Unit)?
}
```

### ActionExecutor

在同一 App 内执行 UI 操作：

```kotlin
class ActionExecutor(activity: Activity) {
    suspend fun execute(action: AgentAction): ExecutionResult
}
```

支持的操作类型：
- `Tap` - 点击
- `Type` - 输入文本
- `Swipe` - 滑动
- `Back` - 返回
- `Wait` - 等待
- `LongPress` - 长按
- `DoubleTap` - 双击
- `TakeOver` - 人工接管
- `Finish` - 完成任务

### ScreenCapture

捕获应用屏幕截图：

```kotlin
object ScreenCapture {
    fun captureActivity(activity: Activity): Screenshot
    fun captureView(view: View): Screenshot
    suspend fun captureWithPixelCopy(activity: Activity): Screenshot
}

data class Screenshot(
    val bitmap: Bitmap,
    val width: Int,
    val height: Int
) {
    val base64Data: String
    fun toDataURL(): String
}
```

### MessageBuilder

构建系统提示词和用户消息：

```kotlin
object MessageBuilder {
    fun buildSystemMessage(language: Language): Message
    fun buildUserMessage(
        instruction: String,
        screenshotBase64: String,
        screenInfo: ScreenInfo?
    ): Message
}
```

## 配置说明

### ModelConfig

```kotlin
data class ModelConfig(
    val baseURL: String = "https://open.bigmodel.cn/api/paas/v4",
    val apiKey: String,
    val modelName: String = "autoglm-phone",
    val maxTokens: Int = 3000,
    val temperature: Double = 0.0,
    val topP: Double = 0.85,
    val frequencyPenalty: Double = 0.2,
    val language: Language = Language.CHINESE
)
```

### AgentConfig

```kotlin
data class AgentConfig(
    val maxSteps: Int = 50,              // 最大执行步数
    val stepDelay: Long = 500,           // 每步延迟(ms)
    val verbose: Boolean = true,         // 详细日志
    val autoConfirmSensitiveActions: Boolean = false,  // 自动确认敏感操作
    val unknownActionThreshold: Int = 3  // 未知动作阈值
)
```

## Demo 应用

Demo 应用包含三个测试页面：

1. **MainActivity** - 配置和执行界面
2. **LoginActivity** - 登录测试页面
3. **ProductListActivity** - 商品列表测试页面

### 构建 Demo

```bash
cd Android-SDK
./gradlew assembleDebug
```

### 运行 Demo

```bash
./gradlew installDebug
# 或者使用 Android Studio
```

### 测试任务示例

```
点击登录页面，输入用户名"admin"，输入密码"123456"，点击登录按钮
```

## 操作指令格式

模型返回的操作指令必须遵循以下格式：

```
思考: 用户要求点击登录按钮，它位于屏幕中央
do(action="Tap", element=[500,500])
```

支持的操作：
- `do(action="Tap", element=[x,y])` - 点击
- `do(action="Type", text="xxx")` - 输入
- `do(action="Swipe", start=[x1,y1], end=[x2,y2])` - 滑动
- `do(action="Back")` - 返回
- `do(action="Wait", duration="x seconds")` - 等待
- `finish(message="xxx")` - 完成

## 与 iOS SDK 对比

| 特性 | iOS SDK | Android SDK |
|------|---------|-------------|
| 语言 | Swift | Kotlin |
| 并发 | @MainActor + async/await | Coroutines + StateFlow |
| 截图 | UIGraphicsImageRenderer | Canvas / PixelCopy |
| 网络 | URLSession | OkHttp |
| 坐标系统 | (0-999) | (0-999) |
| 操作权限 | 应用内 | 应用内 |

## 常见问题

### Q: 为什么不需要无障碍权限？

A: SDK 只在**同一 App 内**操作，可以直接调用 View 的方法（如 `performClick()`），无需注入事件到其他应用。

### Q: 支持跨应用操作吗？

A: 不支持。这是设计决策，专注于应用内的自动化场景。

### Q: 如何处理敏感操作？

A: SDK 提供 `onSensitiveAction` 回调，可以在执行敏感操作前请求用户确认。

### Q: 模型返回未知动作怎么办？

A: SDK 会检测连续的未知动作，达到阈值（默认 3 次）后自动终止执行。

## 许可证

本项目采用 MIT 许可证。

## 相关链接

- [iOS SDK](../iOS-SDK/) - Swift 实现版本
- [Python 参考实现](../phone_agent/) - Python ADB 实现
- [AutoGLM 文档](https://open.bigmodel.cn/) - 智谱 AI 模型文档
