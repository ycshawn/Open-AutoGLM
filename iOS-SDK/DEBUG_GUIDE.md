# iOS-SDK 日志排查指南

## 概述

PhoneAgentSDK 使用 OSLog 框架进行日志记录。本文档介绍如何查看和排查 SDK 运行时问题。

## 日志配置

### 启用详细日志

在创建 `AgentEngine` 时，通过 `AgentConfig.verbose` 控制日志输出：

```swift
let config = AgentConfig(
    verbose: true  // 启用详细日志
)
let engine = AgentEngine(agentConfig: config)
```

**默认值**: `verbose = false`（仅输出错误和警告）

## 日志分类

SDK 的日志按子系统 (subsystem) 和类别 (category) 组织：

| Logger | Subsystem | Category | 用途 |
|--------|-----------|----------|------|
| `agentLogger` | `com.demo.PhoneAgentDemo` | `AgentEngine` | 任务执行流程、模型响应 |
| `logger` | `com.demo.PhoneAgentDemo` | `ModelClient` | 网络请求、响应解析 |
| `actionLogger` | `com.demo.PhoneAgentDemo` | `ActionExecutor` | UI 操作执行 |

## 查看日志的方式

### 1. Xcode Console（推荐开发环境使用）

运行应用后，在 Xcode 底部的 Console 面板查看日志：

```
AgentEngine: === 开始执行任务 ===
AgentEngine: 指令: 打开设置
AgentEngine: ⚠️ 未知动作 (1/3): click(500, 500)
```

### 2. macOS Console App（真机调试）

1. 连接 iPhone 到 Mac
2. 打开 **Console.app** (应用程序 > 实用工具 > 控制台)
3. 在左侧设备列表选择你的设备
4. 在搜索框输入 `com.demo.PhoneAgentDemo` 过滤日志
5. 点击"开始流式传输"按钮

### 3. 命令行查看（使用 log 命令）

```bash
# 实时查看 SDK 日志
log stream --predicate 'subsystem == "com.demo.PhoneAgentDemo"' --level debug

# 查看最近的日志
log show --predicate 'subsystem == "com.demo.PhoneAgentDemo"' --last 5m

# 仅查看 AgentEngine 日志
log stream --predicate 'subsystem == "com.demo.PhoneAgentDemo" AND category == "AgentEngine"'
```

### 4. iOS 设备日志（开发者模式）

1. **开启开发者模式**：设置 > 隐私与安全 > 开发者模式
2. **开启日志收集**：设置 > 隐私与安全 > 分析与改进 > 分析数据 > 共享 iPhone 分析
3. 使用 macOS Console App 查看

## 常见问题排查

### 问题 1：任务没有执行任何操作

**症状**：AgentEngine 启动后立即停止，没有执行任何 UI 操作

**排查步骤**：

1. 检查 `verbose` 日志是否启用
2. 查找 "⚠️ 未知动作" 日志
3. 检查模型响应格式是否正确

**相关日志**：
```
AgentEngine: ⚠️ 未知动作 (1/3): ...
ModelClient: 无法解析动作，原始内容: ...
```

**解决方案**：
- 确认使用的模型是 `autoglm-phone` 或兼容的变体
- 检查 `ModelConfig.language` 设置（中文/英文提示词）

### 问题 2：连续解析失败

**症状**：日志显示 "连续 3 次解析失败，终止执行"

**排查步骤**：

1. 查看 ModelClient 的响应格式
2. 检查 API 端点是否正确

**相关日志**：
```
AgentEngine: ⚠️ 未知动作 (1/3): xxx
AgentEngine: ⚠️ 未知动作 (2/3): xxx
AgentEngine: ⚠️ 未知动作 (3/3): xxx
AgentEngine: ❌ 连续 3 次解析失败，终止执行
```

### 问题 3：网络请求失败

**症状**：任务启动后卡住，无响应

**排查步骤**：

1. 检查网络连接
2. 验证 API Key 是否有效
3. 检查 Base URL 是否可访问

**相关日志**：
```
ModelClient: JSON 解析失败: ...
ModelClient: HTTP error: 401
```

### 问题 4：坐标点击无效

**症状**：模型返回的 tap 操作没有点击到正确位置

**排查步骤**：

1. 查找 Swipe 相关的坐标日志
2. 检查屏幕尺寸是否正确

**相关日志**（verbose 模式）：
```
ActionExecutor: Swipe: delta=(x, y)
AgentEngine: 屏幕尺寸: 390 x 844
```

### 问题 5：动作执行失败

**症状**：模型返回正确动作，但执行失败

**排查步骤**：

1. 查找 "动作执行失败" 日志
2. 检查目标 UI 元素是否存在

**相关日志**：
```
AgentEngine: ⚠️ 动作执行失败: View is not interactive
```

## 日志级别

| 级别 | 用途 | 示例 |
|------|------|------|
| `.error` | 错误，需要立即处理 | JSON 解析失败、网络错误 |
| `.warning` | 警告，可能影响功能 | 未知动作、空响应 |
| `.debug` | 调试信息，仅在 verbose 模式 | 坐标计算、详细响应 |

## 日志示例

### 正常执行流程

```
AgentEngine: === 开始执行任务 ===
AgentEngine: 指令: 打开设置
AgentEngine: ⚠️ 任务完成
AgentEngine: 结果: 已打开设置
```

### 异常情况

```
AgentEngine: === 开始执行任务 ===
AgentEngine: ⌟ 指令: 点击按钮
AgentEngine: ⚠️ 未知动作 (1/3): press(100, 200)
AgentEngine: ⚠️ 未知动作 (2/3): press(300, 400)
AgentEngine: ⚠️ 未知动作 (3/3): press(500, 600)
AgentEngine: ❌ 连续 3 次解析失败，终止执行
```

### 网络错误

```
ModelClient: HTTP error: 401
AgentEngine: 模型错误: HTTP error: 401
AgentEngine: 执行失败: HTTP error: 401
```

## 导出日志

### 导出最近的日志

```bash
# 导出最近 10 分钟的日志到文件
log show --predicate 'subsystem == "com.demo.PhoneAgentDemo"' --last 10m > sdk_logs.txt
```

### 导出完整系统日志

在 Xcode 中：**Window > Devices and Simulators** > 选择设备 > **View Device Logs**

## 联系支持

提供以下信息以获取帮助：

1. 完整的日志输出（使用 `log stream` 导出）
2. SDK 版本号
3. iOS 版本和设备型号
4. 使用的模型配置 (Base URL, Model Name)
