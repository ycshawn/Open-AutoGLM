# PhoneAgentSDK - 快速开始

## 一行命令打开项目

```bash
cd /Users/yangchao/devProjects/autoglm/Open-AutoGLM/iOS-SDK && open PhoneAgentDemo.xcodeproj
```

## 三步运行

### 1️⃣ 打开项目

```bash
open /Users/yangchao/devProjects/autoglm/Open-AutoGLM/iOS-SDK/PhoneAgentDemo.xcodeproj
```

### 2️⃣ 选择模拟器并点击 ▶️

在 Xcode 顶部选择 "iPhone 15 Pro" 模拟器，然后点击左上角 ▶️ 按钮

### 3️⃣ 测试

在 App 中配置：
- **API 地址**: `http://localhost:8000/v1`
- **模型名称**: `autoglm-phone-9b`

输入测试任务：
```
点击列表页面
```

---

## 项目文件位置

```
/Users/yangchao/devProjects/autoglm/Open-AutoGLM/iOS-SDK/
├── PhoneAgentDemo.xcodeproj    ← Xcode 项目（双击打开）
├── PhoneAgentDemo/              ← App 源代码
├── BUILD_GUIDE.md               ← 详细构建指南
└── USAGE_GUIDE.md               ← 使用说明
```

---

## 环境要求

- macOS 13.0+
- Xcode 15.0+
- iOS 15.0+ 模拟器或设备

---

## 遇到问题？

查看 [BUILD_GUIDE.md](./BUILD_GUIDE.md) 获取详细的故障排除指南。
