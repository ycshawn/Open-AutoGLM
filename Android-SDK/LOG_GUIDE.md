# PhoneAgent Android SDK 日志排查指南

## 目录

1. [日志概述](#日志概述)
2. [日志标签说明](#日志标签说明)
3. [常见问题排查](#常见问题排查)
4. [日志分析流程](#日志分析流程)
5. [错误代码参考](#错误代码参考)

---

## 日志概述

PhoneAgent SDK 使用统一的日志前缀方便过滤：

| 日志前缀 | 说明 | 示例 |
|---------|------|------|
| `[AgentEngine]` | 主引擎日志 | 任务执行、步骤切换、完成状态 |
| `[ModelClient]` | 模型通信日志 | API 请求、响应解析、网络错误 |
| `[ActionExecutor]` | UI 操作日志 | 点击、滑动、输入等操作执行 |
| `[MainActivity]` | Demo 应用日志 | 用户交互、配置更改 |

### 日志输出示例

```
[AgentEngine] 开始执行任务: 点击登录按钮
[AgentEngine] 步骤 1: TAP
[ActionExecutor] 点击了 btnLogin(Button)
[ModelClient] ERROR: 401 认证失败 - 请检查 API Key
```

---

## 日志标签说明

### 正常执行流程日志

#### 1. 任务开始
```
[AgentEngine] 开始执行任务: <用户指令>
```
- **含义**: Agent 开始执行新的任务
- **正常**: 后续应看到步骤日志

#### 2. 步骤执行
```
[AgentEngine] 步骤 1: TAP
[AgentEngine] 步骤 2: TYPE
[AgentEngine] 步骤 3: FINISH
```
- **含义**: 显示每一步的操作类型
- **操作类型**:
  - `TAP` - 点击
  - `TYPE` - 输入文本
  - `SWIPE` - 滑动
  - `BACK` - 返回
  - `WAIT` - 等待
  - `FINISH` - 任务完成
  - `TAKE_OVER` - 需要用户协助

#### 3. 任务完成
```
[AgentEngine] 任务完成
```
- **含义**: 模型返回完成指令，任务成功结束

### 错误日志

#### 1. 认证错误
```
[ModelClient] ERROR: 401 认证失败 - 请检查 API Key
[ModelClient] ERROR: API Key 为空
[ModelClient] ERROR: API Key 长度异常: 8
```
- **排查**: 检查 API Key 是否正确设置

#### 2. 网络错误
```
[ModelClient] ERROR: HTTP 请求失败: 404
[ModelClient] ERROR: 网络错误: Unknown host
[ModelClient] ERROR: Request timeout
```
- **排查**: 检查网络连接和 BaseURL

#### 3. 操作错误
```
[ActionExecutor] ERROR: 未找到输入框
[ActionExecutor] ERROR: 查找可滚动视图异常
```
- **排查**: 检查目标 UI 元素是否存在

#### 4. 模型错误
```
[AgentEngine] ERROR: 未知动作 #3: do(action="UnknownAction", ...)
[AgentEngine] ERROR: 模型请求异常
```
- **排查**: 检查模型返回格式是否正确

---

## 常见问题排查

### 问题 1: 任务执行后没有任何反应

**症状**:
```
[AgentEngine] 开始执行任务: 点击登录
(之后没有任何日志)
```

**可能原因**:
1. Activity 生命周期问题
2. 网络请求超时
3. 崩溃异常

**排查步骤**:
1. 检查 Logcat 中是否有崩溃日志
2. 查找 `ERROR` 或 `Exception` 关键字
3. 确认网络连接正常
4. 验证 API Key 有效性

---

### 问题 2: 点击操作没有效果

**症状**:
```
[AgentEngine] 步骤 1: TAP
[ActionExecutor] 点击坐标 (540, 1686)
(没有后续响应，UI 没有变化)
```

**可能原因**:
1. 坐标计算错误
2. 目标 View 不可点击
3. 需要更长延迟等待 UI 更新

**排查步骤**:
1. 检查坐标是否在屏幕范围内
2. 查看是否有 `performClick result: false` 日志
3. 增加 `stepDelay` 配置值
4. 检查目标 Activity 是否正确

---

### 问题 3: 滑动操作不生效

**症状**:
```
[AgentEngine] 步骤 1: SWIPE
(没有滑动效果)
```

**可能原因**:
1. 找不到可滚动视图
2. 滑动幅度太小
3. 使用了错误的滚动方法

**排查步骤**:
1. 检查是否有 `Found RecyclerView/ScrollView` 相关日志
2. 查看是否有 `no scrollable found` 警告
3. 确认视图层级结构
4. 尝试增加滑动放大系数

---

### 问题 4: 连续未知动作

**症状**:
```
[AgentEngine] ERROR: 未知动作 #1: do(action="Unknown", ...)
[AgentEngine] ERROR: 未知动作 #2: do(action="Unknown", ...)
[AgentEngine] ERROR: 未知动作 #3: do(action="Unknown", ...)
```

**可能原因**:
1. 模型返回格式错误
2. 提示词语言不匹配
3. 模型不支持该操作类型

**排查步骤**:
1. 检查 `ModelConfig.language` 是否正确
2. 查看模型原始响应内容
3. 确认系统提示词格式
4. 联系模型提供方确认支持

---

### 问题 5: 达到最大步数限制

**症状**:
```
[AgentEngine] 步骤 50: TAP
[AgentEngine] 步骤 51: SWIPE
任务结束，但未显示完成对话框
```

**可能原因**:
1. `maxSteps` 配置太小
2. 模型无法判断任务完成
3. 操作循环无法突破

**排查步骤**:
1. 增加 `maxSteps` 配置值
2. 检查最后一步的操作类型
3. 确认模型返回了 `finish()` 指令
4. 查看是否有错误日志被忽略

---

### 问题 6: API 认证失败

**症状**:
```
[ModelClient] ERROR: 401 认证失败 - 请检查 API Key
[ModelClient] ERROR: API Key: sk-xxx...
```

**可能原因**:
1. API Key 错误或过期
2. API Key 没有访问该模型的权限
3. BaseURL 配置错误

**排查步骤**:
1. 验证 API Key 是否正确复制
2. 确认 API Key 未过期
3. 检查 BaseURL 是否为 `https://open.bigmodel.cn/api/paas/v4`
4. 确认模型名称 `autoglm-phone` 正确

---

### 问题 7: 截图捕获失败

**症状**:
```
[AgentEngine] ERROR: 模型请求异常
```

**可能原因**:
1. Activity 已被销毁
2. 安全策略阻止截图
3. 某些设备不支持截图 API

**排查步骤**:
1. 检查 Activity 是否存在
2. 确认应用有截图权限
3. 尝试使用不同的截图方法

---

## 日志分析流程

### 调试新任务

#### 步骤 1: 过滤日志
```
adb logcat | grep -E "\[AgentEngine\]|\[ModelClient\]|\[ActionExecutor\]"
```

#### 步骤 2: 观察执行流程
正常流程应包含：
1. `[AgentEngine] 开始执行任务`
2. `[AgentEngine] 步骤 1: <操作类型>`
3. `[AgentEngine] 步骤 2: <操作类型>`
4. `[AgentEngine] 任务完成`

#### 步骤 3: 检查每步结果
- 点击操作: 应该有坐标信息
- 输入操作: 应该有文本内容
- 滑动操作: 应该有方向信息

### 调试网络问题

#### 步骤 1: 过滤网络日志
```
adb logcat | grep -E "\[ModelClient\]"
```

#### 步骤 2: 检查请求
- 应该看到 `开始执行任务` 后的网络请求
- 确认没有超时或连接错误

#### 步骤 3: 验证响应
- 检查响应状态码（应该是 200）
- 确认响应包含预期的内容

### 调试 UI 操作问题

#### 步骤 1: 过滤操作日志
```
adb logcat | grep -E "\[ActionExecutor\]"
```

#### 步骤 2: 检查操作执行
- 查看是否找到目标视图
- 确认操作是否成功执行

#### 步骤 3: 分析失败原因
- 如果 `No view found` - 坐标可能错误
- 如果 `performClick result: false` - 视图不可交互

---

## 错误代码参考

### HTTP 状态码

| 状态码 | 含义 | 排查方法 |
|--------|------|---------|
| 200 | 成功 | 正常 |
| 401 | 未授权 | 检查 API Key |
| 404 | 未找到 | 检查 BaseURL 和模型名 |
| 429 | 请求过多 | 降低请求频率 |
| 500 | 服务器错误 | 联系服务提供商 |
| 503 | 服务不可用 | 稍后重试 |

### 操作类型

| 类型 | 代码 | 说明 |
|------|------|------|
| TAP | TAP | 点击操作 |
| TYPE | TYPE | 输入文本 |
| SWIPE | SWIPE | 滑动操作 |
| BACK | BACK | 返回操作 |
| WAIT | WAIT | 等待 |
| FINISH | FINISH | 任务完成 |
| UNKNOWN | UNKNOWN | 无法解析 |

### 任务状态

| 状态 | 说明 | 日志标识 |
|------|------|---------|
| 运行中 | 正在执行 | `[AgentEngine] 步骤 X:` |
| 成功完成 | 任务完成 | `[AgentEngine] 任务完成` |
| 达到步数 | 达到最大步数限制 | 检查最终结果 |
| 用户接管 | 需要用户协助 | 检查对话框 |
| 失败 | 执行失败 | 检查错误日志 |

---

## 实用技巧

### 1. 导出日志到文件

```bash
adb logcat > debug.log
```

然后在文件中搜索关键字：
```
grep -E "\[AgentEngine\]|\[ModelClient\]|\[ActionExecutor\]" debug.log > filtered.log
```

### 2. 实时监控日志

```bash
adb logcat -s PhoneAgent
```

或使用多个标签：
```bash
adb logcat -s AgentEngine:* ModelClient:* ActionExecutor:*
```

### 3. 开启详细日志

在代码中设置 `verbose = true`：
```kotlin
AgentConfig(
    maxSteps = 30,
    stepDelay = 600,
    verbose = true  // 启用详细日志
)
```

### 4. 过滤特定类型的日志

只看错误：
```bash
adb logcat *:E
```

只看警告和错误：
```bash
adb logcat *:W *:E
```

只看 SDK 日志：
```bash
adb logcat | grep -E "\[AgentEngine\]|\[ModelClient\]|\[ActionExecutor\]"
```

---

## 配置调试

### 开发环境配置

```kotlin
val agentConfig = AgentConfig(
    maxSteps = 100,      // 增加最大步数方便调试
    stepDelay = 1000,    // 增加延迟方便观察
    verbose = true       // 启用详细日志
)
```

### 生产环境配置

```kotlin
val agentConfig = AgentConfig(
    maxSteps = 30,       // 限制步数提高效率
    stepDelay = 400,     // 减少延迟提高速度
    verbose = false      // 关闭详细日志
)
```

---

## 获取技术支持

如果以上方法无法解决问题，请收集以下信息：

1. **完整的日志输出**
   ```bash
   adb logcat > full_log.txt
   ```

2. **配置信息**
   - ModelConfig 配置
   - AgentConfig 配置
   - API Key（脱敏后）

3. **设备信息**
   - 设备型号
   - Android 版本
   - SDK 版本

4. **任务描述**
   - 输入的任务指令
   - 预期的行为
   - 实际的行为

5. **复现步骤**
   - 详细的操作步骤
   - 问题出现的时机

---

## 更新日志

### v1.1.0 (2025-02-26)
- 简化日志输出，移除冗余信息
- 添加统一日志前缀
- 创建排查指导文档
