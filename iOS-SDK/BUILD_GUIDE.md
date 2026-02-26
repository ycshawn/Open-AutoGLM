# PhoneAgentSDK - 构建步骤指南

## 目录

- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [使用 Xcode 构建](#使用-xcode-构建)
- [使用命令行构建](#使用命令行构建)
- [常见问题](#常见问题)

---

## 环境要求

- **macOS**: Ventura (13.0) 或更高版本
- **Xcode**: 15.0 或更高版本
- **iOS SDK**: iOS 15.0 或更高版本
- **Swift**: 5.9 或更高版本

---

## 快速开始

### 1. 打开项目

```bash
cd /Users/yangchao/devProjects/autoglm/Open-AutoGLM/iOS-SDK
open PhoneAgentDemo.xcodeproj
```

### 2. 选择模拟器或设备

在 Xcode 顶部工具栏中，选择：
- **模拟器**: iPhone 15 Pro (或其他 iOS 15+ 模拟器)
- **真机**: 连接你的 iOS 设备

### 3. 运行项目

点击 Xcode 左上角的 ▶️ 按钮或按 `Cmd + R`

---

## 使用 Xcode 构建

### 步骤 1: 打开项目

```bash
cd /Users/yangchao/devProjects/autoglm/Open-AutoGLM/iOS-SDK
open PhoneAgentDemo.xcodeproj
```

### 步骤 2: 检查项目配置

1. 点击左侧项目导航器中的 `PhoneAgentDemo` 项目
2. 选择 `PhoneAgentDemo` 目标
3. 确认以下设置：
   - **Deployment Target**: iOS 15.0
   - **Bundle Identifier**: `com.demo.PhoneAgentDemo`
   - **Team**: 选择你的开发团队（真机运行需要）

### 步骤 3: 配置代码签名（真机运行）

如果你想在真机上运行：

1. 在项目设置中，选择 **Signing & Capabilities**
2. 勾选 **Automatically manage signing**
3. 选择你的 **Team**（Apple Developer 账号）
4. 等待 Xcode 自动生成证书和配置文件

### 步骤 4: 构建并运行

- 点击 ▶️ 按钮或按 `Cmd + R`
- 等待编译完成
- App 将自动在模拟器或设备上启动

---

## 使用命令行构建

### 构建

```bash
cd /Users/yangchao/devProjects/autoglm/Open-AutoGLM/iOS-SDK

# 列出可用方案
xcodebuild -project PhoneAgentDemo.xcodeproj -list

# 构建（模拟器）
xcodebuild -project PhoneAgentDemo.xcodeproj \
  -scheme PhoneAgentDemo \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' \
  clean build

# 构建（真机）
xcodebuild -project PhoneAgentDemo.xcodeproj \
  -scheme PhoneAgentDemo \
  -sdk iphoneos \
  -configuration Release \
  clean build
```

### 运行

```bash
# 使用模拟器运行
xcrun simctl boot "iPhone 15 Pro"
xcrun simctl install booted ~/Library/Developer/Xcode/DerivedData/PhoneAgentDemo-*/Build/Products/Debug-iphonesimulator/PhoneAgentDemo.app
xcrun simctl launch --console booted com.demo.PhoneAgentDemo
```

---

## 项目结构

```
iOS-SDK/
├── PhoneAgentDemo.xcodeproj/    # Xcode 项目文件
│   └── project.pbxproj
│
├── PhoneAgentDemo/               # App 源代码
│   ├── AppDelegate.swift         # 应用入口
│   ├── SceneDelegate.swift       # 场景代理
│   ├── Info.plist               # 配置文件
│   ├── Views/                   # 视图控制器
│   │   ├── MainViewController.swift
│   │   ├── LoginViewController.swift
│   │   └── ProductListViewController.swift
│   ├── SDK/                     # PhoneAgentSDK 源代码
│   │   ├── Agent/               # 核心引擎
│   │   ├── Model/               # 模型通信
│   │   ├── Capture/             # 屏幕捕获
│   │   └── Action/              # 操作执行
│   └── Assets.xcassets/         # 资源文件
│
├── DemoApp/                     # 原始 Demo 文件（备份）
├── PhoneAgentSDK/               # SDK 源文件（备份）
├── README.md                    # 项目说明
└── USAGE_GUIDE.md              # 使用指南
```

---

## 配置模型服务

### 1. 启动 AutoGLM 模型服务

在运行 App 之前，你需要先启动 AutoGLM 模型服务：

```bash
# 使用 vLLM 启动服务（示例）
python -m vllm.entrypoints.openai.api_server \
  --model autoglm-phone-9b \
  --host 0.0.0.0 \
  --port 8000
```

### 2. 配置 App

在 App 的主界面中：
- **API 地址**: 输入 `http://localhost:8000/v1`
- **模型名称**: 输入 `autoglm-phone-9b`

---

## 常见问题

### Q1: 编译错误 - "Cannot find type 'XXX' in scope"

**原因**: SDK 文件没有正确添加到项目

**解决**:
1. 在 Xcode 中，选择项目导航器中的 `PhoneAgentDemo`
2. 检查 `SDK` 文件夹下的所有 `.swift` 文件是否已添加到目标
3. 如果没有，选中文件，在右侧 File Inspector 中勾选 `PhoneAgentDemo` 目标

### Q2: 运行时崩溃 - "Could not find a storyboard named 'LaunchScreen'"

**原因**: 缺少 LaunchScreen.storyboard 文件

**解决**: 项目中已经包含该文件，确保它在 `Copy Bundle Resources` 中

### Q3: 模型请求失败

**原因**:
- 模型服务未启动
- API 地址配置错误
- 网络连接问题

**解决**:
1. 确认模型服务已启动：`curl http://localhost:8000/v1/models`
2. 检查 App 中的 API 地址配置
3. 如果使用真机，确保设备和电脑在同一网络，并使用电脑的 IP 地址

### Q4: 真机无法安装

**原因**: 代码签名问题

**解决**:
1. 登录你的 Apple Developer 账号
2. 在项目设置中配置 Signing & Capabilities
3. 确保选择了正确的 Team

### Q5: iOS 版本过低

**原因**: 设备或模拟器 iOS 版本低于 15.0

**解决**: 使用 iOS 15.0 或更高版本的模拟器/设备

---

## 下一步

1. ✅ 打开并运行项目
2. ✅ 启动 AutoGLM 模型服务
3. ✅ 在 App 中配置 API 地址
4. ✅ 输入测试任务，例如："点击列表页面"

查看 [USAGE_GUIDE.md](./USAGE_GUIDE.md) 了解更多使用说明。
