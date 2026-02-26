# PhoneAgent Android SDK 架构文档

## 目录

1. [项目概述](#项目概述)
2. [系统架构](#系统架构)
3. [模块结构](#模块结构)
4. [核心组件详解](#核心组件详解)
5. [数据流与工作流程](#数据流与工作流程)
6. [配置说明](#配置说明)
7. [使用指南](#使用指南)
8. [技术实现细节](#技术实现细节)
9. [局限性与改进方向](#局限性与改进方向)

---

## 项目概述

PhoneAgent Android SDK 是一个基于**纯视觉自动化**的 Android 应用内 AI Agent SDK。通过截图、发送给云端 AutoGLM 模型、然后在应用内执行 UI 操作，全程无需修改现有 UI 代码。

### 核心特性

- ✅ **零代码侵入**：Agent 完全通过截图和基于坐标的操作运行
- ✅ **同一 App 内操作**：无需 Root 或无障碍权限
- ✅ **视觉理解**：基于 LLM 的屏幕理解和操作规划
- ✅ **Kotlin 实现**：现代 Android 开发最佳实践
- ✅ **Coroutines + StateFlow**：响应式状态管理

### 系统要求

- Android 7.0 (API 24) 或更高版本
- Kotlin 1.9.0+
- Gradle 8.1.0+

---

## 系统架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android App                              │
│                                                                  │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │ 截图捕获      │───▶│ 云端 LLM      │───▶│ 操作执行      │      │
│  │ScreenCapture │   │ AutoGLM API  │   │ActionExecutor│      │
│  └──────────────┘    └──────────────┘    └──────────────┘      │
│         │                    │                    │             │
│         ▼                    ▼                    ▼             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │ScreenInfo    │    │MessageBuilder│    │ AgentAction  │      │
│  └──────────────┘    └──────────────┘    └──────────────┘      │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              AgentEngine (主引擎)                         │   │
│  │  • 状态管理 (StateFlow)                                  │   │
│  │  • 工作流协调                                             │   │
│  │  • 回调处理                                               │   │
│  │  • Activity 生命周期跟踪                                  │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 工作流程

```
1. 用户输入任务指令
        ↓
2. AgentEngine 启动执行循环
        ↓
3. ScreenCapture 捕获当前屏幕截图
        ↓
4. MessageBuilder 构建消息（系统提示 + 指令 + 截图）
        ↓
5. ModelClient 发送请求到 AutoGLM API
        ↓
6. 模型返回操作指令（思考过程 + 动作）
        ↓
7. AgentAction 解析操作指令
        ↓
8. ActionExecutor 执行操作（点击/滑动/输入等）
        ↓
9. 等待 UI 更新，重复步骤 3-8
        ↓
10. 模型返回 finish() 或达到最大步数
        ↓
11. 返回执行结果（成功/失败/达到步数限制）
```

---

## 模块结构

```
Android-SDK/
├── phoneagent-sdk/                    # SDK 核心库模块
│   └── src/main/java/com/autoglm/phoneagent/
│       ├── agent/                     # Agent 引擎
│       │   ├── AgentEngine.kt         # 主引擎，协调工作流程
│       │   ├── AgentConfig.kt         # Agent 配置
│       │   └── AgentResult.kt         # 执行结果数据类
│       ├── model/                     # 模型通信
│       │   ├── ModelClient.kt         # 与 AutoGLM API 通信
│       │   ├── ModelConfig.kt         # 模型 API 配置
│       │   └── MessageBuilder.kt      # 构建系统提示和用户消息
│       ├── action/                    # 操作执行
│       │   ├── ActionExecutor.kt      # 执行 UI 操作
│       │   └── AgentAction.kt         # 操作指令数据类
│       ├── capture/                   # 屏幕捕获
│       │   ├── ScreenCapture.kt       # 截图功能
│       │   └── ScreenInfo.kt          # 屏幕信息收集
│       └── utils/                     # 工具类（预留）
│
├── demoapp/                           # Demo 演示应用
│   └── src/main/java/com/demo/phoneagent/
│       ├── MainActivity.kt            # 主界面
│       ├── MainViewModel.kt           # ViewModel
│       ├── LoginActivity.kt           # 登录测试页
│       └── ProductListActivity.kt     # 商品列表测试页
│
├── build.gradle.kts                   # 项目构建配置
├── settings.gradle.kts                # 项目设置
└── README.md                          # 项目说明
```

---

## 核心组件详解

### 1. AgentEngine (`agent/AgentEngine.kt`)

主引擎，协调整个工作流程。

**核心职责：**
- 管理 Agent 执行状态（StateFlow）
- 协调截图、模型调用、操作执行
- 处理 Activity 切换（ActivityTracker）
- 提供回调接口

**核心方法：**

| 方法 | 说明 |
|------|------|
| `run(instruction: String)` | 启动任务执行 |
| `stop()` | 停止执行 |
| `cleanup()` | 清理资源 |
| `getCurrentActivity()` | 获取当前前台 Activity |

**StateFlow 状态：**
```kotlin
val currentStep: StateFlow<Int>           // 当前步数
val isRunning: StateFlow<Boolean>          // 是否运行中
val currentThinking: StateFlow<String>     // 当前思考
val currentAction: StateFlow<String>       // 当前动作
val history: StateFlow<List<StepInfo>>     // 执行历史
val error: StateFlow<String?>              // 错误信息
```

**回调接口：**
```kotlin
var onStepProgress: ((step, thinking, action) -> Unit)?
var onSensitiveAction: ((message) -> suspend () -> Boolean)?
var onTakeOver: ((message) -> Unit)?
var onComplete: ((AgentResult) -> Unit)?
```

**关键代码片段：**
```kotlin
// Activity 生命周期跟踪
private class ActivityTracker : Application.ActivityLifecycleCallbacks {
    private var currentActivityRef: WeakReference<Activity>? = null
    fun getCurrentActivity(): Activity? = currentActivityRef?.get()
}

// 执行循环
while (_currentStep.value < config.maxSteps && isActive) {
    val lastStep = historyList.lastOrNull()
    if (lastStep?.actionType == ActionType.FINISH) {
        break  // 任务完成
    }
    val stepResult = executeNextStep()
    // ...
}
```

---

### 2. ModelClient (`model/ModelClient.kt`)

与 AutoGLM 云端 API 通信。

**核心职责：**
- 发送 HTTP 请求到模型 API
- 解析模型响应
- 错误处理和重试

**核心方法：**

| 方法 | 说明 |
|------|------|
| `sendRequest(messages)` | 发送聊天完成请求 |

**响应解析：**
```kotlin
// 中文格式：思考 + 空行 + 操作
"用户要求点击登录按钮
do(action=\"Tap\", element=[500,500])"

// 英文格式：XML 标签
"<thinking>...</thinking>
<answer>do(action="Tap", element=[500,500])</answer>"
```

**错误类型：**
```kotlin
sealed class ModelError : Exception {
    data class HttpError(val statusCode: Int)
    data object NoDataError
    data class ParsingError(val detail: String)
    data class NetworkError(val detail: String)
}
```

**网络配置：**
```kotlin
private val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor())
        .build()
}
```

---

### 3. ActionExecutor (`action/ActionExecutor.kt`)

在同一 App 内执行 UI 操作。

**核心职责：**
- 执行各种 UI 操作
- 查找可交互视图
- 模拟触摸事件

**支持的操作类型：**

| 操作类型 | 说明 | 格式示例 |
|---------|------|---------|
| TAP | 点击 | `do(action="Tap", element=[x,y])` |
| TYPE | 输入文本 | `do(action="Type", text="xxx")` |
| SWIPE | 滑动 | `do(action="Swipe", start=[x1,y1], end=[x2,y2])` |
| LONG_PRESS | 长按 | `do(action="Long Press", element=[x,y])` |
| DOUBLE_TAP | 双击 | `do(action="Double Tap", element=[x,y])` |
| BACK | 返回 | `do(action="Back")` |
| WAIT | 等待 | `do(action="Wait", duration="x seconds")` |
| TAKE_OVER | 人工接管 | `do(action="Take_over", message="xxx")` |
| FINISH | 完成 | `finish(message="xxx")` |

**核心方法：**

| 方法 | 说明 |
|------|------|
| `execute(action)` | 执行操作 |
| `simulateSwipe()` | 模拟滑动手势 |
| `simulateTap()` | 模拟点击事件 |
| `findViewAt()` | 查找指定坐标的视图 |
| `findScrollableAt()` | 查找可滚动视图 |

**滑动放大机制：**
```kotlin
// 放大滑动距离以获得更明显的滚动效果
val swipeAmplification = 1.8f
val amplifiedDeltaX = (end[0] - start[0]) * swipeAmplification
val amplifiedDeltaY = (end[1] - start[1]) * swipeAmplification
```

**动态 Activity 获取：**
```kotlin
class ActionExecutor(private val activityProvider: () -> Activity) {
    private val rootView: ViewGroup
        get() = activityProvider().window.decorView as ViewGroup
}
```

---

### 4. ScreenCapture (`capture/ScreenCapture.kt`)

捕获应用屏幕截图。

**核心方法：**

| 方法 | 说明 |
|------|------|
| `captureActivity()` | 捕获整个 Activity 窗口 |
| `captureView()` | 捕获单个 View |
| `captureWithPixelCopy()` | 使用 PixelCopy API（API 26+） |
| `captureContentView()` | 仅捕获内容视图 |

**Screenshot 数据类：**
```kotlin
data class Screenshot(
    val bitmap: Bitmap,
    val width: Int,
    val height: Int
) {
    val base64Data: String  // Base64 编码（懒加载）
    fun toDataURL(): String  // 转换为 data URL
}
```

---

### 5. ScreenInfo (`capture/ScreenInfo.kt`)

收集屏幕信息，提供额外的 UI 上下文。

**UIElement 数据类：**
```kotlin
data class UIElement(
    val id: String,
    val type: String,
    val text: String?,
    val bounds: Rect,
    val clickable: Boolean,
    val enabled: Boolean,
    val focusable: Boolean,
    val focused: Boolean
)
```

**核心方法：**
```kotlin
suspend fun fromActivity(
    activity: Activity,
    includeElements: Boolean = false
): ScreenInfo
```

---

### 6. MessageBuilder (`model/MessageBuilder.kt`)

构建系统提示词和用户消息。

**核心方法：**

| 方法 | 说明 |
|------|------|
| `buildSystemMessage()` | 构建系统提示消息 |
| `buildUserMessage()` | 构建初始用户消息 |
| `buildFollowUpMessage()` | 构建后续用户消息 |

**消息结构：**
```kotlin
data class Message(
    val role: MessageRole,  // SYSTEM, USER, ASSISTANT
    val content: List<ContentItem>  // Text 或 Image
)
```

**系统提示词格式（中文）：**
```
今天的日期是: 2025年02月26日 星期三

你是一个智能体分析专家，可以根据操作历史和当前状态图执行一系列操作来完成...

## 输出格式要求（必须严格遵守）
你的输出必须包含两部分，用空行分隔：
**第一部分**：思考过程
**第二部分**：操作指令

## 操作指令格式
1. 点击: do(action="Tap", element=[x,y])
2. 输入: do(action="Type", text="xxx")
3. 滑动: do(action="Swipe", start=[x1,y1], end=[x2,y2])
...
```

---

### 7. AgentAction (`action/AgentAction.kt`)

操作指令的数据模型和解析。

**数据类：**
```kotlin
data class AgentAction(
    val type: ActionType,
    val coordinates: IntArray = intArrayOf(),
    val start: IntArray? = null,
    val end: IntArray? = null,
    val text: String? = null,
    val message: String? = null,
    val duration: Long? = null,
    val rawAction: String
)
```

**解析示例：**
```kotlin
// 输入: "do(action=\"Tap\", element=[500,500])"
// 输出: AgentAction(type=TAP, coordinates=[500,500], ...)

// 输入: "do(action=\"Type\", text=\"hello\")"
// 输出: AgentAction(type=TYPE, text="hello", ...)
```

---

### 8. 配置类

**ModelConfig** (`model/ModelConfig.kt`)
```kotlin
data class ModelConfig(
    val baseURL: String = "https://open.bigmodel.cn/api/paas/v4",
    val apiKey: String,
    val modelName: String = "autoglm-phone",
    val maxTokens: Int = 3000,
    val temperature: Double = 0.0,
    val topP: Double = 0.85,
    val frequencyPenalty: Double = 0.2,
    val language: Language = CHINESE
)
```

**AgentConfig** (`agent/AgentConfig.kt`)
```kotlin
data class AgentConfig(
    val maxSteps: Int = 50,              // 最大执行步数
    val stepDelay: Long = 500,           // 每步延迟(ms)
    val verbose: Boolean = true,         // 详细日志
    val autoConfirmSensitiveActions: Boolean = false,
    val unknownActionThreshold: Int = 3  // 未知动作阈值
)
```

**预置配置：**
```kotlin
companion object {
    val DEFAULT = AgentConfig()
    val FAST = AgentConfig(maxSteps = 20, stepDelay = 300, verbose = false)
    val DEBUG = AgentConfig(maxSteps = 100, stepDelay = 1000, verbose = true)
    val PRODUCTION = AgentConfig(maxSteps = 50, stepDelay = 500, verbose = false)
}
```

---

## 数据流与工作流程

### 完整执行流程

```
┌─────────────────────────────────────────────────────────────────┐
│                     1. 用户输入任务                             │
│  "点击登录页面，输入用户名'admin'，输入密码'123456'，点击登录"    │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                  2. AgentEngine.run() 启动                      │
│  - 重置状态                                                      │
│  - 构建系统提示词                                                │
│  - 开始执行循环                                                  │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                    3. 循环执行每一步                            │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 3.1 捕获屏幕截图                                        │   │
│  │     ScreenCapture.captureActivity()                     │   │
│  │     → Screenshot(bitmap, width, height, base64Data)     │   │
│  └─────────────────────────────────────────────────────────┘   │
│                            ↓                                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 3.2 构建消息                                            │   │
│  │     MessageBuilder.buildUserMessage()                   │   │
│  │     → Message(role=USER, content=[Text, Image])        │   │
│  └─────────────────────────────────────────────────────────┘   │
│                            ↓                                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 3.3 发送请求到模型                                      │   │
│  │     ModelClient.sendRequest(messages)                   │   │
│  │     → POST /chat/completions                            │   │
│  └─────────────────────────────────────────────────────────┘   │
│                            ↓                                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 3.4 解析模型响应                                        │   │
│  │     parseResponse(content)                              │   │
│  │     → (thinking: String, action: String)               │   │
│  └─────────────────────────────────────────────────────────┘   │
│                            ↓                                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 3.5 解析操作指令                                        │   │
│  │     AgentAction.parse(action)                           │   │
│  │     → AgentAction(type, coordinates, ...)              │   │
│  └─────────────────────────────────────────────────────────┘   │
│                            ↓                                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 3.6 执行操作                                            │   │
│  │     ActionExecutor.execute(action)                      │   │
│  │     → ExecutionResult (Success/Error/Finished/...)      │   │
│  └─────────────────────────────────────────────────────────┘   │
│                            ↓                                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 3.7 检查是否完成                                        │   │
│  │     if (type == FINISH) break                           │   │
│  │     if (step >= maxSteps) break                         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                            ↓                                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 3.8 等待 UI 更新                                        │   │
│  │     delay(stepDelay)                                     │   │
│  │     返回 3.1 继续下一步                                  │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                    4. 返回执行结果                              │
│  AgentResult(                                                   │
│    success: Boolean,                                            │
│    message: String?,                                            │
│    totalSteps: Int,                                             │
│    history: List<StepInfo>,                                     │
│    metrics: ExecutionMetrics                                   │
│  )                                                              │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                    5. 显示结果对话框                            │
│  - 成功: "✓ 任务完成"                                          │
│  - 失败: "✗ 任务失败"                                          │
│  - 达到步数: "达到最大步数"                                     │
│  - 用户接管: "用户接管"                                        │
└─────────────────────────────────────────────────────────────────┘
```

### 坐标系统

```
(0,0) ───────────────────────────► X (999)
  │
  │    [Tap 示例]
  │    do(action="Tap", element=[500,500])
  │           ↑
  │           └── 屏幕中心
  │
  ▼
Y (999)

归一化坐标 (0-999) → 实际屏幕坐标
actualX = (normalizedX / 999f * screenWidth).toInt()
actualY = (normalizedY / 999f * screenHeight).toInt()
```

---

## 配置说明

### ModelConfig 配置项

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| baseURL | String | `https://open.bigmodel.cn/api/paas/v4` | API 基础 URL |
| apiKey | String | 必填 | API 密钥 |
| modelName | String | `autoglm-phone` | 模型名称 |
| maxTokens | Int | 3000 | 最大生成 token 数 |
| temperature | Double | 0.0 | 采样温度 (0-1) |
| topP | Double | 0.85 | 核采样参数 |
| frequencyPenalty | Double | 0.2 | 频率惩罚 |
| language | Language | CHINESE | 提示词语言 (CHINESE/ENGLISH) |

### AgentConfig 配置项

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| maxSteps | Int | 50 | 最大执行步数 |
| stepDelay | Long | 500 | 步骤间延迟 (ms) |
| verbose | Boolean | true | 是否输出详细日志 |
| autoConfirmSensitiveActions | Boolean | false | 自动确认敏感操作 |
| unknownActionThreshold | Int | 3 | 连续未知动作阈值 |

### 推荐配置

**快速测试：**
```kotlin
AgentConfig(
    maxSteps = 20,
    stepDelay = 300,
    verbose = false
)
```

**调试模式：**
```kotlin
AgentConfig(
    maxSteps = 100,
    stepDelay = 1000,
    verbose = true
)
```

**生产环境：**
```kotlin
AgentConfig(
    maxSteps = 50,
    stepDelay = 500,
    verbose = false,
    autoConfirmSensitiveActions = false
)
```

---

## 使用指南

### 基本集成

**1. 添加 SDK 依赖：**

```kotlin
// settings.gradle.kts
include(":phoneagent-sdk")

// build.gradle.kts (app module)
dependencies {
    implementation(project(":phoneagent-sdk"))
}
```

**2. 创建 AgentEngine：**

```kotlin
val modelConfig = ModelConfig(
    baseURL = "https://open.bigmodel.cn/api/paas/v4",
    apiKey = "your-api-key",
    modelName = "autoglm-phone",
    language = ModelConfig.Language.CHINESE
)

val agentConfig = AgentConfig(
    maxSteps = 30,
    stepDelay = 600,
    verbose = true
)

val agentEngine = AgentEngineFactory.create(
    activity = this,
    modelConfig = modelConfig,
    agentConfig = agentConfig
)
```

**3. 设置回调：**

```kotlin
agentEngine?.apply {
    onStepProgress = { step, thinking, action ->
        log("步骤 $step: $thinking | $action")
    }

    onSensitiveAction = { message ->
        // 显示确认对话框
        suspend {
            showConfirmationDialog(message)
        }
    }

    onTakeOver = { message ->
        log("用户接管: $message")
    }

    onComplete = { result ->
        showResultDialog(result)
    }
}
```

**4. 执行任务：**

```kotlin
lifecycleScope.launch {
    val result = agentEngine?.run("点击登录，输入用户名'admin'，点击登录按钮")
    log("执行结果: ${result.success}")
}
```

### 在 ViewModel 中使用

```kotlin
class MainViewModel : ViewModel() {
    private val _executionResult = MutableLiveData<AgentResult?>()
    val executionResult: LiveData<AgentResult?> = _executionResult

    fun executeTask(activity: Activity, instruction: String) {
        viewModelScope.launch {
            val agentEngine = AgentEngineFactory.create(activity, modelConfig)
            val result = agentEngine.run(instruction)
            _executionResult.postValue(result)
        }
    }
}
```

---

## 技术实现细节

### 1. 异步处理

使用 Kotlin Coroutines 进行异步操作：

```kotlin
suspend fun run(instruction: String): AgentResult = withContext(Dispatchers.Main) {
    // 主线程执行
    val response = withContext(Dispatchers.IO) {
        // IO 线程执行网络请求
        modelClient.sendRequest(messages)
    }
    // 回到主线程执行 UI 操作
    actionExecutor.execute(action)
}
```

### 2. 状态管理

使用 StateFlow 进行响应式状态管理：

```kotlin
private val _isRunning = MutableStateFlow(false)
val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

// 在 Activity 中观察
viewModel.isRunning.observe(this) { isRunning ->
    binding.btnExecute.isEnabled = !isRunning
}
```

### 3. Activity 生命周期跟踪

动态获取当前前台 Activity：

```kotlin
private class ActivityTracker : Application.ActivityLifecycleCallbacks {
    private var currentActivityRef: WeakReference<Activity>? = null

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    fun getCurrentActivity(): Activity? = currentActivityRef?.get()
}
```

### 4. 触摸事件模拟

```kotlin
private suspend fun simulateTap(x: Int, y: Int) {
    val downTime = System.currentTimeMillis()

    val downEvent = MotionEvent.obtain(
        downTime, downTime,
        MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0
    )

    val upEvent = MotionEvent.obtain(
        downTime, downTime + 100,
        MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0
    )

    rootView.dispatchTouchEvent(downEvent)
    rootView.dispatchTouchEvent(upEvent)
}
```

### 5. 滑动优化

**滑动幅度放大：**
```kotlin
val swipeAmplification = 1.8f
val centerX = (start[0] + end[0]) / 2f
val centerY = (start[1] + end[1]) / 2f
val amplifiedDeltaX = (end[0] - start[0]) * swipeAmplification
val amplifiedDeltaY = (end[1] - start[1]) * swipeAmplification
```

**快速滑动参数：**
```kotlin
val actualDuration = 200L   // 持续时间
val steps = 20              // 事件步数
val stepDelay = actualDuration / steps
```

### 6. 智能视图查找

```kotlin
private fun findViewAt(x: Int, y: Int): View? {
    val views = rootView.touchables
    return views
        .filter { it.isEnabled && it.isClickable }
        .firstOrNull { view ->
            val rect = Rect()
            view.getGlobalVisibleRect(rect)
            rect.contains(x, y)
        }
}
```

### 7. 可滚动视图检测

```kotlin
private fun findScrollableAt(x: Int, y: Int): View? {
    // 优先查找 RecyclerView
    val recyclerView = findViewOfTypeInHierarchy(RecyclerView::class.java, x, y)
    if (recyclerView != null) return recyclerView

    // 其次查找 NestedScrollView
    val nestedScrollView = findViewOfTypeInHierarchy(NestedScrollView::class.java, x, y)
    if (nestedScrollView != null) return nestedScrollView

    // 再次查找 ScrollView
    val scrollView = findViewOfTypeInHierarchy(ScrollView::class.java, x, y)
    if (scrollView != null) return scrollView

    return null
}
```

---

## 局限性与改进方向

### 当前局限性

1. **应用内操作限制**
   - 仅可在同一 App 内操作
   - 无法跨应用执行任务
   - 无法操作系统级应用

2. **依赖模型理解**
   - 操作准确性完全依赖模型视觉理解能力
   - 复杂 UI 可能导致误判
   - 需要 Stable Diffusion 级别的视觉理解

3. **坐标系统限制**
   - 使用归一化坐标 (0-999)
   - 不同屏幕密度可能需要调整
   - 动态布局可能影响坐标准确性

4. **性能考虑**
   - 每步都需要截图和网络请求
   - 总执行时间 = 步数 × (网络延迟 + 处理时间)
   - 大型截图可能影响性能

5. **网络依赖**
   - 需要稳定的网络连接
   - API 调用产生费用
   - 网络延迟影响用户体验

### 改进方向

1. **本地模型支持**
   - 集成端侧视觉模型
   - 减少网络依赖
   - 提高执行速度

2. **智能元素识别**
   - 结合 View 层级信息
   - OCR 文字识别
   - 图标/图像识别

3. **操作优化**
   - 操作预测和缓存
   - 并行操作支持
   - 操作回滚机制

4. **增强功能**
   - 手势录制和回放
   - 条件判断和循环
   - 多任务并发执行

5. **调试工具**
   - 可视化执行过程
   - 操作步骤编辑器
   - 性能分析工具

---

## 与 iOS SDK 对比

| 特性 | iOS SDK | Android SDK |
|------|---------|-------------|
| 语言 | Swift | Kotlin |
| 并发 | @MainActor + async/await | Coroutines + StateFlow |
| 截图 | UIGraphicsImageRenderer | Canvas / PixelCopy |
| 网络 | URLSession | OkHttp |
| 坐标系统 | (0-999) | (0-999) |
| 操作权限 | 应用内 | 应用内 |

---

## 常见问题

### Q: 为什么不需要无障碍权限？

A: SDK 只在同一 App 内操作，可以直接调用 View 的方法（如 `performClick()`），无需注入事件到其他应用。

### Q: 支持跨应用操作吗？

A: 不支持。这是设计决策，专注于应用内的自动化场景。如需跨应用操作，请考虑使用 ADB 或无障碍服务方案。

### Q: 如何处理敏感操作？

A: SDK 提供 `onSensitiveAction` 回调，可以在执行敏感操作前请求用户确认：

```kotlin
agentEngine?.onSensitiveAction = { message ->
    suspend {
        // 显示确认对话框
        showConfirmationDialog(message)
    }
}
```

### Q: 模型返回未知动作怎么办？

A: SDK 会检测连续的未知动作，达到阈值（默认 3 次）后自动终止执行。可通过 `unknownActionThreshold` 配置。

### Q: 如何提高执行速度？

A: 可以调整以下参数：
- 减少 `stepDelay`：`AgentConfig(stepDelay = 300)`
- 减少 `maxSteps`：`AgentConfig(maxSteps = 20)`
- 使用快速配置：`AgentConfig.FAST`

---

## 参考资料

- [README.md](README.md) - 项目说明
- [iOS SDK](../iOS-SDK/) - Swift 实现版本
- [Python 参考实现](../phone_agent/) - Python ADB 实现
- [AutoGLM 文档](https://open.bigmodel.cn/) - 智谱 AI 模型文档
