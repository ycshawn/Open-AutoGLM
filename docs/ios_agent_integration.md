# iOS App 内嵌 AI Agent 技术方案对比

## 目录

- [1. 方案概述](#1-方案概述)
- [2. 纯视觉方案](#2-纯视觉方案)
- [3. 混合方案](#3-混合方案)
- [4. 方案对比](#4-方案对比)
- [5. 推荐建议](#5-推荐建议)

---

## 1. 方案概述

### 背景说明

原 Open-AutoGLM 项目通过外部工具（ADB/HDC/XCTest）控制手机，模型**仅通过屏幕截图**理解界面，输出**坐标**进行操作。

本项目希望将 AI Agent 能力嵌入到 iOS App 内部，在 App 内模拟用户操作，不依赖外部自动化工具。

### 核心挑战

1. **模型输入**：如何让模型理解 App 当前的 UI 状态？
2. **模型输出**：模型输出什么形式来指定操作目标？
3. **操作执行**：如何在 App 内部执行操作？

---

## 2. 纯视觉方案

### 2.1 方案描述

保持与原项目一致的逻辑：模型仅通过截图理解界面，输出坐标，在 App 内通过坐标找到对应 View 并执行操作。

```
┌─────────────────────────────────────────────────────────────────┐
│                        纯视觉方案架构                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐                                               │
│  │ iOS App      │                                               │
│  │              │   1. 截取当前屏幕                             │
│  │  ┌─────────┐ │   UIGraphicsImageRenderer                    │
│  │  │ Screen  │ │                      │                        │
│  │  │ Capture │ │                      ▼                        │
│  │  └────┬────┘ │   ┌─────────────────────────────────┐         │
│  │       │      │   │                                 │         │
│  │       ▼      │   │  发送给云端模型:                 │         │
│  │  ┌─────────┐ │   │  {                              │         │
│  │  │ Agent   │ │   │    "screenshot": "base64...",   │         │
│  │  │ SDK     │ │   │    "current_screen": "LoginVC"  │         │
│  │  └────┬────┘ │   │  }                              │         │
│  │       │      │   │                                 │         │
│  └───────┼──────┘   └─────────────────────────────────┘         │
│          │
│          │    2. 模型视觉理解，输出坐标                           │
│          ▼
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  云端 AutoGLM 模型                                           │  │
│  │                                                             │  │
│  │  输入: 截图 + 任务指令                                       │  │
│  │  处理: 视觉理解屏幕，识别可交互元素                           │  │
│  │  输出: do(action="Tap", element=[250, 330])                 │  │
│  └────────────────────────────────────────────────────────────┘  │
│          │
│          │    3. 接收坐标，在 App 内定位 View                     │
│          ▼
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  ActionExecutor                                             │  │
│  │                                                             │  │
│  │   // 通过坐标找到 View                                       │  │
│  │   let view = window.hitTest(point, with: nil)               │  │
│  │                                                             │  │
│  │   // 模拟触摸事件                                            │  │
│  │   view.touchDown(at: point)                                 │  │
│  │   view.touchUp(at: point)                                   │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 技术实现

#### 2.2.1 屏幕捕获

```swift
class ScreenCapture {
    static func capture() -> Data {
        // 获取当前窗口
        guard let window = UIApplication.shared.windows.first,
              let rootVC = window.rootViewController else {
            fatalError("无法获取当前窗口")
        }

        // 使用 UIGraphicsImageRenderer 截图
        let renderer = UIGraphicsImageRenderer(size: window.bounds.size)
        let image = renderer.image { context in
            window.drawHierarchy(in: window.bounds, afterScreenUpdates: false)
        }

        return image.pngData() ?? Data()
    }
}
```

#### 2.2.2 模型请求格式

```json
{
  "messages": [
    {
      "role": "system",
      "content": "你是手机操作助手... (原系统提示词)"
    },
    {
      "role": "user",
      "content": [
        {
          "type": "image_url",
          "image_url": {
            "url": "data:image/png;base64,iVBORw0KGgo..."
          }
        },
        {
          "type": "text",
          "text": "打开个人中心页面\n{\"current_screen\": \"MainViewController\"}"
        }
      ]
    }
  ]
}
```

#### 2.2.3 操作执行器

```swift
class ActionExecutor {
    private let window: UIWindow

    func execute(_ action: AgentAction) async throws {
        switch action {
        case .tap(let point):
            try await tap(at: point)

        case .swipe(let start, let end):
            try await swipe(from: start, to: end)

        case .type(let text):
            try await typeText(text)

        case .back:
            try await goBack()
        }
    }

    private func tap(at point: CGPoint) async throws {
        await MainActor.run {
            // 方法1: 使用 hitTest 找到 View 并触发事件
            guard let view = window.hitTest(point, with: nil) else {
                throw AgentError.elementNotFound
            }

            // 模拟触摸事件
            view.touchesBegan([UITouch(touchInView: view, at: point)], with: nil)
            view.touchesEnded([UITouch(touchInView: view, at: point)], with: nil)
        }
    }

    private func swipe(from start: CGPoint, to end: CGPoint) async throws {
        await MainActor.run {
            // 创建手势路径
            let path = UIBezierPath()
            path.move(to: start)
            path.addLine(to: end)

            // 模拟滑动手势
            let gesture = UIPanGestureRecognizer(target: self, action: nil)
            // ... 手势模拟逻辑
        }
    }
}
```

#### 2.2.4 坐标映射

```swift
struct CoordinateMapper {
    // 模型输出 0-999 的归一化坐标，需要映射到实际屏幕
    static func map(_ normalized: CGPoint, screenSize: CGSize) -> CGPoint {
        let x = normalized.x / 999.0 * screenSize.width
        let y = normalized.y / 999.0 * screenSize.height
        return CGPoint(x: x, y: y)
    }
}
```

### 2.3 优点

| 优点 | 说明 |
|------|------|
| **零侵入性** | 不需要修改现有 UI 代码，无需为 View 添加标识 |
| **开发成本低** | SDK 独立实现，与现有代码解耦 |
| **与原项目一致** | 可以复用原项目的模型和提示词 |
| **通用性强** | 理论上可以操作任何界面元素 |
| **快速上手** | 实现相对简单，核心逻辑清晰 |

### 2.4 缺点

| 缺点 | 说明 |
|------|------|
| **坐标不稳定** | 不同设备尺寸、动态内容、动画都会导致坐标变化 |
| **无法区分语义** | 两个相同的"确认"按钮，模型无法区分其语义差异 |
| **依赖视觉能力** | 完全依赖模型的视觉理解，对复杂 UI 可能误判 |
| **定位不精确** | hitTest 可能找到非预期的 View（如父容器） |
| **难以调试** | 当操作失败时，难以确定是坐标问题还是 View 问题 |
| **无法利用 App 结构** | App 已有的视图层级、命名规范等信息无法利用 |

### 2.5 改造工作量

| 任务 | 工作量 | 说明 |
|------|--------|------|
| **SDK 开发** | 2-3 天 | 包括 Agent 引擎、模型客户端、操作执行器 |
| **屏幕捕获** | 0.5 天 | 实现截图功能 |
| **操作执行** | 2-3 天 | hitTest、触摸事件模拟、手势模拟 |
| **模型集成** | 1 天 | API 调用、流式响应解析 |
| **测试调试** | 3-5 天 | 不同场景测试、边界情况处理 |
| **总计** | **8-12 天** | 约 2 周工作量 |

---

## 3. 混合方案

### 3.1 方案描述

模型同时接收截图和 UI 元素元数据，通过视觉理解界面外观，通过元数据理解语义结构，输出元素 ID 进行精确操作。

```
┌─────────────────────────────────────────────────────────────────────┐
│                        混合方案架构                                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────┐                                                   │
│  │ iOS App      │                                                   │
│  │              │   1. 截图 + 收集元素元数据                          │
│  │  ┌─────────┐ │                                                    │
│  │  │ Screen  │ │                    │                              │
│  │  │ Capture │ │                    ▼                              │
│  │  └────┬────┘ │   ┌─────────────────────────────────────┐         │
│  │       │      │   │   UIViewRegistry                    │         │
│  │       ▼      │   │                                     │         │
│  │  ┌─────────┐ │   │   收集当前屏幕的可交互元素:          │         │
│  │  │ Element │ │   │   [                                │         │
│  │  │ Registry│ │   │     {                              │         │
│  │  └────┬────┘ │   │       "id": "login.username",      │         │
│  │       │      │   │       "type": "textField",         │         │
│  │       ▼      │   │       "label": "用户名输入框",      │         │
│  │  ┌─────────┐ │   │       "frame": {x,y,width,height}, │         │
│  │  │ Agent   │ │   │       "hint": "请输入用户名"        │         │
│  │  │ SDK     │ │   │     },                             │         │
│  │  └─────────┘ │   │     ...                            │         │
│  │              │   │   ]                                │         │
│  └──────┬───────┘   └─────────────────────────────────────┘         │
│         │                                                            │
│         │    2. 发送截图 + 元数据给模型                              │
│         ▼                                                            │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  云端 AutoGLM 模型                                             │  │
│  │                                                              │  │
│  │  输入:                                                        │  │
│  │    - 截图 (视觉理解)                                           │  │
│  │    - 元素元数据 (语义理解)                                     │  │
│  │    - 任务指令                                                  │  │
│  │                                                              │  │
│  │  处理:                                                        │  │
│  │    - 视觉: 识别按钮位置、文本内容                              │  │
│  │    - 元数据: 知道每个元素的 ID 和语义                          │  │
│  │    - 决策: 选择合适的元素 ID                                   │  │
│  │                                                              │  │
│  │  输出: do(action="Tap", element="login.username")             │  │
│  └──────────────────────────────────────────────────────────────┘  │
│         │                                                            │
│         │    3. 根据 ID 精确定位并操作                              │
│         ▼                                                            │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  ActionExecutor                                               │  │
│  │                                                              │  │
│  │   // 根据 ID 找到 View                                        │  │
│  │   guard let view = UIViewRegistry.shared.find(id: "login.username") else { │
│  │       throw AgentError.elementNotFound                       │  │
│  │   }                                                          │  │
│  │                                                              │  │
│  │   // 直接调用操作                                             │  │
│  │   if let textField = view as? UITextField {                  │  │
│  │       textField.text = "user@example.com"                    │  │
│  │       textField.sendActions(for: .editingChanged)            │  │
│  │   }                                                          │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 技术实现

#### 3.2.1 元素标识系统

```swift
// 属性包装器 - 简化元素注册
@propertyWrapper
struct AIIdentifiable {
    let id: String
    let label: String?
    let metadata: [String: Any]?

    var wrappedValue: UIView {
        didSet {
            // 注册元素
            UIViewRegistry.shared.register(
                view: wrappedValue,
                id: id,
                label: label,
                metadata: metadata
            )
        }
    }
}

// 使用示例
class LoginViewController: UIViewController {

    @AIIdentifiable(id: "login.username", label: "用户名输入框")
    private lazy var usernameField: UITextField = {
        let field = UITextField()
        field.placeholder = "请输入用户名"
        return field
    }()

    @AIIdentifiable(id: "login.password", label: "密码输入框")
    private lazy var passwordField: UITextField = {
        let field = UITextField()
        field.placeholder = "请输入密码"
        field.isSecureTextEntry = true
        return field
    }()

    @AIIdentifiable(id: "login.submit", label: "登录按钮")
    private lazy var submitButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle("登录", for: .normal)
        return button
    }()
}
```

#### 3.2.2 SwiftUI 支持

```swift
struct IdentifiableViewModifier: ViewModifier {
    let id: String
    let label: String?
    let type: ElementType

    func body(content: Content) -> some View {
        content
            .background(
                GeometryReader { geometry in
                    Color.clear
                        .onAppear {
                            // 注册 SwiftUI 元素
                            SwiftUIRegistry.shared.register(
                                id: id,
                                label: label,
                                type: type,
                                frame: geometry.frame(in: .global)
                            )
                        }
                        .onChange(of: geometry.frame(in: .global)) { _, newFrame in
                            // 更新位置
                            SwiftUIRegistry.shared.updateFrame(id: id, frame: newFrame)
                        }
                }
            )
    }
}

extension View {
    func aiIdentifiable(id: String, label: String? = nil, type: ElementType = .generic) -> some View {
        self.modifier(IdentifiableViewModifier(id: id, label: label, type: type))
    }
}

// 使用示例
struct LoginView: View {
    @State private var username = ""
    @State private var password = ""

    var body: some View {
        VStack(spacing: 20) {
            TextField("用户名", text: $username)
                .aiIdentifiable(id: "login.username", label: "用户名输入框", type: .textField)

            SecureField("密码", text: $password)
                .aiIdentifiable(id: "login.password", label: "密码输入框", type: .textField)

            Button("登录") { }
                .aiIdentifiable(id: "login.submit", label: "登录按钮", type: .button)
        }
    }
}
```

#### 3.2.3 元素注册表

```swift
struct UIElement: Codable {
    let id: String
    let type: ElementType
    let label: String?
    let frame: ElementFrame
    let hint: String?
    let value: String?  // 当前值（如 TextField 的文本）
    let enabled: Bool
    let visible: Bool

    enum ElementType: String, Codable {
        case button, textField, textView, switch_, slider
        case tableViewCell, collectionViewCell
        case tabBar, navigationBar
        case generic
    }

    struct ElementFrame: Codable {
        let x: Double
        let y: Double
        let width: Double
        let height: Double
    }
}

class UIViewRegistry {
    static let shared = UIViewRegistry()

    private var elements: [String: UIElement] = [:]
    private var viewMap: [String: UIView] = [:]  // ID -> View 映射

    func register(view: UIView, id: String, label: String?, metadata: [String: Any]?) {
        let element = UIElement(
            id: id,
            type: inferType(from: view),
            label: label,
            frame: ElementFrame(from: view.frame),
            hint: (view as? UITextField)?.placeholder,
            value: (view as? UITextField)?.text,
            enabled: view.isUserInteractionEnabled,
            visible: !view.isHidden && view.alpha > 0
        )

        elements[id] = element
        viewMap[id] = view
    }

    func getElementsForCurrentScreen() -> [UIElement] {
        // 获取当前屏幕可见的元素
        let currentScreenVC = getCurrentViewController()
        return elements.values.filter { element in
            // 过滤出当前屏幕可见的元素
            isElementVisible(element, in: currentScreenVC)
        }
    }

    func findView(by id: String) -> UIView? {
        return viewMap[id]
    }

    private func inferType(from view: UIView) -> UIElement.ElementType {
        if view is UIButton { return .button }
        if view is UITextField { return .textField }
        if view is UITextView { return .textView }
        if view is UISwitch { return .switch_ }
        // ... 更多类型判断
        return .generic
    }
}
```

#### 3.2.4 模型请求格式

```json
{
  "messages": [
    {
      "role": "system",
      "content": "你是手机操作助手...\n\n操作指令:\n- do(action=\"Tap\", element=\"元素ID\")\n- do(action=\"Type\", element=\"元素ID\", text=\"输入内容\")\n..."
    },
    {
      "role": "user",
      "content": [
        {
          "type": "image_url",
          "image_url": {
            "url": "data:image/png;base64,iVBORw0KGgo..."
          }
        },
        {
          "type": "text",
          "text": "输入用户名和密码登录\n\n当前屏幕元素:\n{\n  \"current_screen\": \"LoginViewController\",\n  \"elements\": [\n    {\"id\": \"login.username\", \"type\": \"textField\", \"label\": \"用户名输入框\", \"frame\": {\"x\": 40, \"y\": 200, \"width\": 300, \"height\": 40}, \"hint\": \"请输入用户名\"},\n    {\"id\": \"login.password\", \"type\": \"textField\", \"label\": \"密码输入框\", \"frame\": {\"x\": 40, \"y\": 260, \"width\": 300, \"height\": 40}, \"hint\": \"请输入密码\"},\n    {\"id\": \"login.submit\", \"type\": \"button\", \"label\": \"登录按钮\", \"frame\": {\"x\": 40, \"y\": 320, \"width\": 300, \"height\": 50}, \"enabled\": true}\n  ]\n}"
        }
      ]
    }
  ]
}
```

#### 3.2.5 操作执行器

```swift
class ActionExecutor {
    func execute(_ action: AgentAction) async throws {
        switch action {
        case .tap(let elementId):
            try await tapElement(id: elementId)

        case .type(let elementId, let text):
            try await typeText(id: elementId, text: text)

        case .switch(let elementId, let isOn):
            try await setSwitch(id: elementId, isOn: isOn)

        case .scroll(let elementId, let direction):
            try await scrollElement(id: elementId, direction: direction)

        case .back:
            try await goBack()
        }
    }

    private func tapElement(id: String) async throws {
        guard let view = UIViewRegistry.shared.findView(by: id) else {
            throw AgentError.elementNotFound(id)
        }

        await MainActor.run {
            // 方法1: 直接发送 Action（推荐）
            if let button = view as? UIButton {
                button.sendActions(for: .touchUpInside)
            } else {
                // 方法2: 模拟触摸事件
                let center = CGPoint(x: view.bounds.midX, y: view.bounds.midY)
                view.touchesBegan(
                    [UITouch(touchInView: view, at: center)],
                    with: nil
                )
                view.touchesEnded(
                    [UITouch(touchInView: view, at: center)],
                    with: nil
                )
            }
        }
    }

    private func typeText(id: String, text: String) async throws {
        guard let textField = UIViewRegistry.shared.findView(by: id) as? UITextField else {
            throw AgentError.elementNotFound(id)
        }

        await MainActor.run {
            textField.text = text
            textField.sendActions(for: .editingChanged)
            // 触发 UITextFieldDelegate 回调
            textField.delegate?.textFieldDidEndEditing?(textField)
        }
    }
}
```

### 3.3 优点

| 优点 | 说明 |
|------|------|
| **精确可靠** | 通过 ID 定位元素，不会受屏幕尺寸、布局变化影响 |
| **语义清晰** | ID 本身包含语义信息，便于理解和维护 |
| **易于调试** | 操作失败时可以直接定位到具体元素 |
| **可扩展性强** | 元素元数据可以承载更多上下文信息 |
| **更好的提示** | 模型可以同时看到外观和结构，决策更准确 |
| **支持复杂场景** | 可以处理列表、动态内容等复杂场景 |
| **性能优化** | 可以预先过滤不可见/不可用的元素 |

### 3.4 缺点

| 缺点 | 说明 |
|------|------|
| **需要修改现有代码** | 需要为关键 UI 元素添加标识 |
| **开发工作量大** | 需要实现完整的元素注册和管理系统 |
| **维护成本** | 新增页面/元素时需要同步注册 |
| **学习曲线** | 团队需要了解新的属性包装器和 API |
| **可能的遗漏** | 如果忘记注册某个元素，该元素无法被 AI 操作 |
| **代码侵入性** | 添加标识会污染现有代码 |

### 3.5 改造工作量

| 任务 | 工作量 | 说明 |
|------|--------|------|
| **SDK 开发** | 3-4 天 | Agent 引擎、模型客户端、操作执行器 |
| **元素注册系统** | 2-3 天 | Registry、属性包装器、类型推断 |
| **UIKit 支持** | 2-3 天 | 基础控件支持、事件处理 |
| **SwiftUI 支持** | 2-3 天 | ViewModifier、动态更新 |
| **现有页面改造** | N × 0.5 天 | N 为需要改造的页面数量 |
| **测试调试** | 5-7 天 | 全面测试、边界情况处理 |
| **文档编写** | 1-2 天 | 使用指南、最佳实践 |
| **总计** | **15+N×0.5 天** | 假设 20 个页面，约 25 天 |

### 3.6 渐进式实施策略

为了降低改造风险，可以采用渐进式实施：

**阶段 1：核心 SDK（1 周）**
- 实现 Agent 引擎、模型客户端
- 实现基础的元素注册系统
- 先用纯视觉方案跑通流程

**阶段 2：关键页面改造（1-2 周）**
- 选择 3-5 个高频页面进行改造
- 添加元素标识
- 验证混合方案效果

**阶段 3：全面推广（持续）**
- 根据效果决定是否推广到所有页面
- 或者保持部分页面使用纯视觉方案

---

## 4. 方案对比

### 4.1 对比表

| 对比维度 | 纯视觉方案 | 混合方案 |
|----------|-----------|----------|
| **开发周期** | 2 周 | 3-4 周 |
| **代码侵入性** | 无侵入 | 需要添加标识 |
| **准确性** | 中等（依赖模型视觉能力） | 高（ID 精确定位） |
| **稳定性** | 低（坐标可能变化） | 高（ID 不变） |
| **调试难度** | 高（难以定位问题） | 低（直接定位元素） |
| **维护成本** | 低 | 中（需要维护注册表） |
| **设备兼容性** | 需考虑不同屏幕尺寸 | 无影响 |
| **复杂场景支持** | 弱（列表、动态内容） | 强（元数据提供结构信息） |
| **与原项目一致性** | 高（完全一致） | 中（需要修改 Prompt） |
| **未来扩展性** | 有限 | 良好 |

### 4.2 适用场景

#### 纯视觉方案适用于：
- 快速验证概念，不想修改现有代码
- App 结构简单，页面数量少
- 主要用于简单操作（点击、滑动）
- 可以容忍一定的失败率
- 作为过渡方案，后续考虑升级

#### 混合方案适用于：
- 需要高可靠性的生产环境
- App 结构复杂，页面数量多
- 需要操作列表、动态内容等复杂场景
- 希望有良好的可维护性和可调试性
- 长期项目，值得投入开发成本

### 4.3 技术风险评估

| 风险 | 纯视觉方案 | 混合方案 |
|------|-----------|----------|
| **模型输出格式变化** | 低风险（复用原项目） | 中风险（需要定制 Prompt） |
| **iOS 系统更新** | 中风险（触摸事件 API 可能变化） | 低风险（操作是 App 内部调用） |
| **现有代码冲突** | 无风险 | 低风险（标识是独立的） |
| **性能影响** | 低（仅截图） | 中（需要维护元素树） |
| **隐私合规** | 低（截图可能包含敏感信息） | 低（同样有截图问题） |

---

## 5. 推荐建议

### 5.1 推荐方案：混合方案（渐进式实施）

虽然纯视觉方案开发周期短，但从长期维护和可靠性考虑，**推荐混合方案**，但采用渐进式实施策略：

#### 第一阶段：纯视觉方案 MVP（2 周）

```
目标：快速验证可行性，跑通整个流程

工作内容：
1. 实现 Agent SDK 核心功能
2. 使用纯视觉方案（截图 + 坐标）
3. 支持基础操作（Tap、Type、Swipe、Back）
4. 选择 1-2 个简单页面测试

验收标准：
- 可以通过自然语言完成简单任务
- 基础操作成功率 > 70%
```

#### 第二阶段：混合方案验证（2 周）

```
目标：验证混合方案的可行性

工作内容：
1. 实现元素注册系统
2. 选择 3-5 个页面添加元素标识
3. 修改模型 Prompt，支持 ID 输出
4. 对比两种方案的效果

验收标准：
- 混合方案成功率 > 纯视觉方案 20%
- 操作更稳定，可重复性更好
```

#### 第三阶段：全面推广（根据效果决定）

```
如果第二阶段效果良好：
1. 推广到所有核心页面
2. 完善元素注册系统
3. 优化性能和用户体验

如果效果不理想：
1. 保持关键页面使用混合方案
2. 其他页面继续使用纯视觉方案
3. 或者全部回退到纯视觉方案
```

### 5.2 技术选型建议

#### 5.2.1 如果选择纯视觉方案

需要重点解决的问题：

1. **坐标稳定性**
   - 使用相对坐标而非绝对坐标
   - 考虑不同设备尺寸的适配

2. **View 定位准确性**
   - `hitTest` 可能找到非预期 View
   - 考虑遍历 View Tree 找到最合适的可交互 View

3. **失败重试机制**
   - 当操作失败时，尝试调整坐标重试
   - 或者请求模型重新分析

#### 5.2.2 如果选择混合方案

可以优化的点：

1. **降低侵入性**
   - 提供更简洁的 API
   - 使用代码生成工具自动添加标识

2. **自动元素发现**
   - 对于未注册的元素，自动推断基本信息
   - 提供"训练模式"，运行时自动收集元素信息

3. **分层注册策略**
   - 核心页面完整注册
   - 次要页面自动推断
   - 临时页面纯视觉方案

### 5.3 其他考虑因素

#### 5.3.1 模型 Prompt 定制

无论选择哪个方案，都需要根据实际场景定制 Prompt：

- 增加特定 App 的操作规则
- 添加常见问题的处理逻辑
- 优化错误处理和重试策略

#### 5.3.2 性能优化

- 截图优化：只截取必要区域，降低数据传输量
- 元素过滤：只传递可见、可交互的元素
- 缓存策略：缓存不变的元数据

#### 5.3.3 安全与隐私

- 敏感页面（支付、密码）特殊处理
- 截图数据脱敏
- 用户操作确认机制

#### 5.3.4 监控与分析

- 记录操作日志，便于分析问题
- 统计成功率、失败原因
- 用户反馈收集

---

## 6. 附录

### 6.1 关键代码示例

详见前文各章节的实现代码示例。

### 6.2 参考资料

- [Open-AutoGLM 项目](https://github.com/zai-org/Open-AutoGLM)
- [AutoGLM 论文](https://arxiv.org/abs/2411.00820)
- [UIView hitTest 文档](https://developer.apple.com/documentation/uikit/uiview/1622516-hittest)
- [SwiftUI ViewModifier](https://developer.apple.com/documentation/swiftui/viewmodifier)

### 6.3 联系方式

如有疑问或需要进一步讨论，请联系项目团队。



### 7 hitTest 方法
  核心方法：hitTest                                                                                                            
                                                                                                                               
  iOS UIKit 提供了一个内置方法 hitTest(_:with:)，它可以根据坐标找到那个位置的 UI 元素。                                        
                                                                                                                               
  ---                                                                                                                          
  1. hitTest 原理解释                                                                                                          
                                                                                                                               
  它是如何工作的？                                                                                                             
                                                                                                                               
  想象你在玩"打地鼠"游戏：                                                                                                     
                                                                                                                               
  屏幕 → 层层叠叠的很多 View                                                                                                   
          ┌────────────────────────────────────┐                                                                               
          │  UIWindow (最外层的窗口)           │                                                                               
          │   ┌──────────────────────────────┐ │                                                                               
          │   │  UIViewController            │ │                                                                               
          │   │   ┌──────────────┐           │ │                                                                               
          │   │   │  UIButton   │ ← 按钮   │ │                                                                                 
          │   │   │  [200,300]  │           │ │                                                                                
          │   │   └──────────────┘           │ │                                                                               
          │   └──────────────────────────────┘ │                                                                               
          └────────────────────────────────────┘                                                                               
                  ▲                                                                                                            
                  │                                                                                                            
          hitTest(200, 300)                                                                                                    
                  │                                                                                                            
                  ↓                                                                                                            
          "在这个位置的是什么？"                                                                                               
                  │                                                                                                            
                  ↓                                                                                                            
          找到了！是 UIButton                                                                                                  
                                                                                                                               
  代码实现                                                                                                                     
                                                                                                                               
  // 1. 模型给出的坐标是归一化的 (0-999)                                                                                       
  let normalizedPoint = CGPoint(x: 500, y: 500)                                                                                
                                                                                                                               
  // 2. 转换成真实屏幕坐标                                                                                                     
  // 假设屏幕是 393x852 (iPhone 14)                                                                                            
  let actualPoint = CGPoint(                                                                                                   
      x: 500 / 999.0 * 393,  // → 197                                                                                          
      y: 500 / 999.0 * 852   // → 426                                                                                          
  )                                                                                                                            
                                                                                                                               
  // 3. 用 hitTest 找到这个位置的 View                                                                                         
  let view = window.hitTest(actualPoint, with: nil)                                                                            
  // ↓ 返回：那个位置的 UIButton                                                                                               
                                                                                                                               
  ---                                                                                                                          
  2. hitTest 的查找过程                                                                                                        
                                                                                                                               
  UIKit 的 hitTest 会从最上层往下找：                                                                                          
                                                                
  点击位置 (197, 426)                                                                                                          
          │                                                                                                                    
          ▼                                                                                                                    
  ┌───────────────────────────────────────┐                                                                                    
  │  UIWindow                              │                                                                                   
  │  ┌─────────────────────────────────┐  │                                                                                    
  │  │  MainViewController             │  │                                                                                    
  │  │  ┌───────────────────────────┐  │  │                                                                                    
  │  │  │  ContainerView (背景)      │  │                                                                                      
  │  │  │  ┌─────────────────────┐  │  │  │                                                                                    
  │  │  │  │  UIButton (按钮)     │  │  │  │  ← hitTest 首先找到这个                                                           
  │  │  │  │  ✅ 点击位置在这里    │  │  │  │     （最上层、可交互）                                                           
  │  │  │  └─────────────────────┘  │  │  │                                                                                    
  │  │  └───────────────────────────┘  │  │                                                                                    
  │  └─────────────────────────────────┘  │                                                                                    
  └───────────────────────────────────────┘                                                                                    
                                                                                                                               
  查找顺序：                                                                                                                   
  1. 从 UIWindow 开始                                                                                                          
  2. 递归遍历所有子视图                                                                                                        
  3. 找到包含这个点的、最上层的、可交互的视图                                                                                  
  4. 返回那个视图                                                                                                              
                                                                                                                               
  ---                                                                                                                          
  3. 找到后的执行方式                                                                                                          
                                                                                                                               
  根据找到的 UI 类型，用不同的方式触发：                                                                                       
  ┌────────────┬──────────────┬──────────────────────────────────────────┐                                                     
  │ 找到的类型 │   触发方式   │                   代码                   │                                                     
  ├────────────┼──────────────┼──────────────────────────────────────────┤                                                     
  │ UIButton   │ 发送点击事件 │ button.sendActions(for: .touchUpInside)  │                                                     
  ├────────────┼──────────────┼──────────────────────────────────────────┤                                                     
  │ UIControl  │ 发送点击事件 │ control.sendActions(for: .touchUpInside) │                                                     
  ├────────────┼──────────────┼──────────────────────────────────────────┤                                                     
  │ 其他 View  │ 无障碍激活   │ view.accessibilityActivate()             │                                                     
  └────────────┴──────────────┴──────────────────────────────────────────┘                                                     
  // ActionExecutor 中的实际代码                                                                                               
  if let button = view as? UIButton {                                                                                          
      // 方式1：如果是按钮，直接触发                                                                                           
      button.sendActions(for: .touchUpInside)                                                                                  
  }                                                                                                                            
  else if let control = view as? UIControl {                                                                                   
      // 方式2：如果是 UIControl，触发事件                                                                                     
      control.sendActions(for: .touchUpInside)                                                                                 
  }                                                                                                                            
  else {                                                                                                                       
      // 方式3：其他情况，尝试无障碍激活                                                                                       
      view.accessibilityActivate()                                                                                             
  }                                                                                                                            
                                                                                                                               
  ---                                                                                                                          
  4. 为什么不需要任何特殊标识？                                                                                                
                                                                                                                               
  因为 iOS 的 UI 系统本身就"知道"每个位置的元素是什么：                                                                        
                                                                                                                               
  iOS 的视图树结构：                                                                                                           
                                                                                                                               
  UIWindow (根)                                                                                                                
    ├─ UINavigationBar                                                                                                         
    ├─ UITableView                                                                                                             
    │   ├─ UITableViewCell (第1行)                                                                                             
    │   │   └─ UILabel ("标题")                                                                                                
    │   └─ UITableViewCell (第2行)                                                                                             
    │       └─ UIButton ("点击我")                                                                                             
    └─ UITabBar                                                                                                                
                                                                                                                               
  每个 View 都知道：                                                                                                           
  - 自己的位置                                                                                                                 
  - 自己的大小                                                                                                                 
  - 自己的父视图是谁                                                                                                           
  - 自己的子视图有哪些                                                                                                         
                                                                                                                               
  所以 hitTest 只需要：                                                                                                        
  1. 给它一个坐标                                                                                                              
  2. 它自动沿着视图树查找                                                                                                      
  3. 返回那个位置的 View                                                                                                       
                                                                                                                               
  ---                                                                                                                          
  5. 完整流程图                                                                                                                
                                                                                                                               
  模型输出: do(action="Tap", element=[500, 500])                                                                               
                │                                                                                                              
                ▼                                                                                                              
      ┌─────────────────────┐                                                                                                  
      │ 1. 坐标转换         │                                                                                                  
      │ 500,500 → 197,426   │  (归一化 → 真实坐标)                                                                             
      └─────────────────────┘                                                                                                  
                │                                                                                                              
                ▼                                                                                                              
      ┌─────────────────────┐                                                                                                  
      │ 2. hitTest 查找     │                                                                                                  
      │ window.hitTest(    │                                                                                                   
      │   x:197, y:426     │                                                                                                   
      │ )                  │                                                                                                   
      └─────────────────────┘                                                                                                  
                │                                                                                                              
                ▼                                                                                                              
      ┌─────────────────────┐                                                                                                  
      │ 3. 返回找到的 View │                                                                                                   
      │ UIButton          │                                                                                                    
      └─────────────────────┘                                                                                                  
                │                                                                                                              
                ▼                                                                                                              
      ┌─────────────────────┐                                                                                                  
      │ 4. 执行操作         │                                                                                                  
      │ sendActions(        │                                                                                                  
      │   .touchUpInside   │                                                                                                   
      │ )                  │                                                                                                   
      └─────────────────────┘                                                                                                  
                │                                                                                                              
                ▼                                                                                                              
           ✅ 按钮被点击                                                                                                       
                                                                                                                               
  ---                                                                                                                          
  6. 其他操作的查找方式                                                                                                        
                                                                                                                               
  Type（输入文本）                                                                                                             
                                                                                                                               
  // 不是通过位置找，而是找"当前有焦点的输入框"                                                                                
  if let firstResponder = findFirstResponder() as? UITextField {                                                               
      firstResponder.text = "hello"  // 直接设置文本                                                                           
  }                                                                                                                            
                                                                                                                               
  Swipe（滑动）                                                                                                                
                                                                                                                               
  // 找到 UIScrollView 来滚动                                                                                                  
  if let scrollView = findMainScrollView() {                                                                                   
      scrollView.setContentOffset(新位置, animated: true)                                                                      
  }                                                                                                                            
                                                                                                                               
  Back（返回）                                                                                                                 
                                                                                                                               
  // 找到导航控制器                                                                                                            
  if let navVC = findNavigationController() {                                                                                  
      navVC.popViewController(animated: true)                                                                                  
  }                                                                                                                            
                                                                                                                               
  ---                                                                                                                          
  总结一句话                                                                                                                   
                                                                                                                               
  SDK 使用 iOS 原生的 hitTest 方法，就像一个"金属探测器"，给它一个坐标，它就能帮你找出那个位置是什么 UI                        
  元素，然后用对应的方法触发它。这是 UIKit 自带的能力，所以不需要任何代码改造。                                                
                                                                                                                               
