# Android-SDK 日志排查指南

## 概述

PhoneAgent-Android SDK 使用 Android Logcat 进行日志记录。本文档介绍如何查看和排查 SDK 运行时问题。

## 测试模式功能

Demo 应用内置了**测试模式**，可以模拟各种模型响应格式，用于验证 SDK 的解析逻辑。

### 启用测试模式

1. 在主界面找到"测试模式"开关
2. 打开开关后，会出现"模拟响应类型"下拉选择器
3. 选择想要测试的响应类型
4. 点击"执行任务"查看解析结果

### 可用的测试类型

| 类型 | 说明 | 预期结果 |
|------|------|----------|
| 点击操作 (do) | 正确的 `do(action="tap(...)")` 格式 | ✅ 解析成功 |
| 滑动操作 (do) | 正确的 `do(action="swipe(...)")` 格式 | ✅ 解析成功 |
| 输入文字 (do) | 正确的 `do(action="text(...)")` 格式 | ✅ 解析成功 |
| 返回操作 (do) | 正确的 `do(action="back()")` 格式 | ✅ 解析成功 |
| 完成任务 (finish) | 正确的 `finish(message=...)` 格式 | ✅ 解析成功 |
| 空动作（错误） | 模型返回空内容 | ⚠️ 解析失败 |
| 未知动作格式 | 无法识别的格式 | ⚠️ 解析失败 |
| 点击操作 (XML) | 遗留的 `<answer>tap(...)</answer>` 格式 | ✅ 解析成功 |
| 完成任务 (XML) | 遗留的 `<answer>finish(...)</answer>` 格式 | ✅ 解析成功 |

### 使用测试模式的场景

1. **验证解析逻辑** - 确保 SDK 能正确处理不同的响应格式
2. **排查问题** - 当真实模型返回异常时，对比测试模式的结果
3. **开发调试** - 无需网络连接即可测试 UI 流程

## 日志配置

### 启用详细日志

在创建 `AgentConfig` 时设置 `verbose` 参数：

```kotlin
val agentConfig = AgentConfig(
    maxSteps = 30,
    stepDelay = 600,
    verbose = true  // 启用详细日志
)
```

**默认值**: `verbose = false`（仅输出关键信息）

## 日志标签

SDK 使用以下日志标签：

| 标签 | 用途 |
|------|------|
| `AgentEngine` | 任务执行流程、模型响应 |
| `ModelClient` | 网络请求、响应解析 |
| `ActionExecutor` | UI 操作执行 |
| `ScreenCapture` | 屏幕捕获 |

## 查看日志的方式

### 1. Android Studio Logcat（推荐）

在 Android Studio 底部的 Logcat 面板查看日志：

```
D/AgentEngine: === 开始执行任务 ===
D/AgentEngine: 指令: 打开设置
D/ModelClient: 发送请求到 https://open.bigmodel.cn/api/paas/v4
```

### 2. adb 命令行查看

```bash
# 实时查看所有日志
adb logcat

# 过滤 SDK 相关日志
adb logcat | grep -E "AgentEngine|ModelClient|ActionExecutor"

# 只查看错误日志
adb logcat *:E

# 清空日志
adb logcat -c
```

### 3. 按标签过滤

```bash
# 只查看 AgentEngine 日志
adb logcat AgentEngine:V *:S

# 查看 ModelClient 的详细日志
adb logcat ModelClient:V *:S

# 查看多个标签
adb logcat AgentEngine:V ModelClient:V ActionExecutor:V *:S
```

### 4. 保存日志到文件

```bash
# 保存日志到文件
adb logcat > sdk_logs.txt

# 过滤并保存
adb logcat -v time | grep -E "AgentEngine|ModelClient" > sdk_logs.txt
```

## 常见问题排查

### 问题 1：任务没有执行任何操作

**症状**：AgentEngine 启动后立即停止，没有执行任何 UI 操作

**排查步骤**：

1. 检查 `verbose` 日志是否启用
2. 查找 "解析失败" 相关日志
3. 检查模型响应格式是否正确

**相关日志**：
```
W/AgentEngine: 检测到未知动作 (1/3): xxx
W/ModelClient: 无法解析动作，原始内容: ...
```

**解决方案**：
- 确认使用的模型是 `autoglm-phone` 或兼容的变体
- 检查 `ModelConfig.language` 设置（中文/英文）
- 使用测试模式验证解析逻辑

### 问题 2：连续解析失败

**症状**：日志显示 "连续 3 次解析失败，终止执行"

**排查步骤**：

1. 查看 ModelClient 的响应格式
2. 检查 API 端点是否正确
3. 使用测试模式对比

**相关日志**：
```
W/AgentEngine: 检测到未知动作 (1/3): xxx
W/AgentEngine: 检测到未知动作 (2/3): xxx
W/AgentEngine: 检测到未知动作 (3/3): xxx
E/AgentEngine: 连续 3 次解析失败，终止执行
```

### 问题 3：网络请求失败

**症状**：任务启动后卡住，无响应

**排查步骤**：

1. 检查网络连接
2. 验证 API Key 是否有效
3. 检查 Base URL 是否可访问

**相关日志**：
```
E/ModelClient: HTTP error: 401
E/ModelClient: 网络错误: xxx
```

### 问题 4：坐标点击无效

**症状**：模型返回的 tap 操作没有点击到正确位置

**排查步骤**：

1. 查找坐标转换相关日志
2. 检查屏幕尺寸是否正确

**相关日志**（verbose 模式）：
```
D/ActionExecutor: Tap 归一化坐标: (0.5, 0.3)
D/ActionExecutor: 屏幕尺寸: 1080 x 2400
```

### 问题 5：动作执行失败

**症状**：模型返回正确动作，但执行失败

**排查步骤**：

1. 查找 "动作执行失败" 日志
2. 检查目标 UI 元素是否存在

**相关日志**：
```
W/ActionExecutor: 动作执行失败: View is not interactive
```

## 日志级别

| 级别 | Tag | 用途 | 示例 |
|------|-----|------|------|
| Verbose | V | 详细调试信息 | 坐标计算、详细响应 |
| Debug | D | 调试信息 | 步骤进度、请求发送 |
| Info | I | 一般信息 | 任务开始/结束 |
| Warn | W | 警告信息 | 未知动作、空响应 |
| Error | E | 错误信息 | 网络错误、解析失败 |

## 日志示例

### 正常执行流程

```
D/MainActivity: >>> executeTask() 被调用
I/MainActivity: === 开始执行任务 ===
I/MainActivity: 指令: 打开设置
D/MainActivity: 创建 AgentEngine...
D/AgentEngine: 开始执行任务，指令: 打开设置
D/ScreenCapture: 捕获屏幕截图
D/AgentEngine: 调用模型 API...
D/ModelClient: 发送请求
D/ModelClient: 收到响应，解析中...
D/AgentEngine: 模型响应: tap(point=(500, 300))
D/ActionExecutor: 执行动作: TAP
I/MainActivity: === 任务完成 ===
```

### 异常情况

```
D/MainActivity: >>> executeTask() 被调用
I/MainActivity: === 开始执行任务 ===
W/AgentEngine: 检测到未知动作 (1/3): press(100, 200)
W/AgentEngine: 检测到未知动作 (2/3): press(300, 400)
W/AgentEngine: 检测到未知动作 (3/3): press(500, 600)
E/AgentEngine: 连续 3 次解析失败，终止执行
I/MainActivity: === 任务完成 ===
E/MainActivity: 成功: false
```

## 系统提示词格式

SDK 支持两种提示词格式：

### 中文格式（推荐）

```
{思考过程}

do(action="具体操作")
```

### 英文格式（向后兼容）

```
<thinking>{思考过程}</thinking>
<answer>do(action="具体操作")</answer>
```

## 坐标系统

模型使用**归一化坐标（0-999）**执行操作。ActionExecutor 会自动将其缩放到实际屏幕尺寸。

## 联系支持

提供以下信息以获取帮助：

1. 完整的 logcat 日志（使用 `adb logcat -v time` 导出）
2. SDK 版本号
3. Android 版本和设备型号
4. 使用的模型配置 (Base URL, Model Name)
