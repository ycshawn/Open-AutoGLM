//
//  MainViewController.swift
//  DemoApp
//
//  Created by AutoGLM on 2025-01-30.
//

import UIKit

/// 模拟响应类型 - 用于测试各种模型响应格式
enum MockResponseType: String, CaseIterable {
    // 正确格式（中文提示词）
    case tapAction = "点击操作 (do)"
    case swipeAction = "滑动操作 (do)"
    case typeAction = "输入文字 (do)"
    case backAction = "返回操作 (do)"
    case finishAction = "完成任务 (finish)"

    // 边缘情况
    case emptyAction = "空动作（错误）"
    case unknownAction = "未知动作格式"

    // 遗留格式（英文提示词 - XML）
    case tapXML = "点击操作 (XML)"
    case finishXML = "完成任务 (XML)"

    var description: String {
        return rawValue
    }

    func generateContent() -> String {
        switch self {
        case .tapAction:
            return """
            我需要点击列表页面的第一个商品

            do(action=tap(point=(500, 300)))
            """
        case .swipeAction:
            return """
            向上滚动查看更多内容

            do(action=swipe(start=(500, 700), end=(500, 300)))
            """
        case .typeAction:
            return """
            在搜索框中输入关键词

            do(action=text(text=苹果))
            """
        case .backAction:
            return """
            返回上一页

            do(action=back())
            """
        case .finishAction:
            return """
            已成功找到目标商品

            finish(message=已成功找到目标商品)
            """
        case .emptyAction:
            return ""
        case .unknownAction:
            return "这是一个未知格式的响应"
        case .tapXML:
            return """
            点击目标元素

            <thinking>我需要点击列表页面的第一个商品</thinking>
            <answer>tap(point=(500, 300))</answer>
            """
        case .finishXML:
            return """
            任务完成

            <thinking>已成功找到目标商品</thinking>
            <answer>finish(message=已成功找到目标商品)</answer>
            """
        }
    }
}

/// 主界面 - Agent 配置和任务执行
class MainViewController: UIViewController, UIPickerViewDelegate, UIPickerViewDataSource {

    // MARK: - UI Components

    private lazy var scrollView: UIScrollView = {
        let scroll = UIScrollView()
        scroll.translatesAutoresizingMaskIntoConstraints = false
        // 让scrollView自动处理safeArea
        scroll.contentInsetAdjustmentBehavior = .always
        // 启用滚动指示器
        scroll.showsVerticalScrollIndicator = true
        scroll.showsHorizontalScrollIndicator = false
        return scroll
    }()

    private lazy var contentStackView: UIStackView = {
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 20
        stack.translatesAutoresizingMaskIntoConstraints = false
        return stack
    }()

    // 配置区域
    private lazy var configSection: UIView = {
        let view = UIView()
        view.backgroundColor = .secondarySystemBackground
        view.layer.cornerRadius = 12
        view.translatesAutoresizingMaskIntoConstraints = false
        return view
    }()

    private lazy var titleLabel: UILabel = {
        let label = UILabel()
        label.text = "🤖 Phone Agent SDK"
        label.font = .systemFont(ofSize: 24, weight: .bold)
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private lazy var baseURLLabel: UILabel = {
        let label = UILabel()
        label.text = "API 地址:"
        label.font = .systemFont(ofSize: 14, weight: .medium)
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private lazy var baseURLTextField: UITextField = {
        let field = UITextField()
        field.placeholder = "https://open.bigmodel.cn/api/paas/v4"
        field.text = UserDefaults.standard.string(forKey: "baseURL") ?? "https://open.bigmodel.cn/api/paas/v4"
        field.borderStyle = .roundedRect
        field.translatesAutoresizingMaskIntoConstraints = false
        return field
    }()

    private lazy var modelLabel: UILabel = {
        let label = UILabel()
        label.text = "模型名称:"
        label.font = .systemFont(ofSize: 14, weight: .medium)
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private lazy var modelTextField: UITextField = {
        let field = UITextField()
        field.placeholder = "autoglm-phone"
        field.text = UserDefaults.standard.string(forKey: "modelName") ?? "autoglm-phone"
        field.borderStyle = .roundedRect
        field.translatesAutoresizingMaskIntoConstraints = false
        return field
    }()

    private lazy var apiKeyLabel: UILabel = {
        let label = UILabel()
        label.text = "API Key:"
        label.font = .systemFont(ofSize: 14, weight: .medium)
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private lazy var apiKeyTextField: UITextField = {
        let field = UITextField()
        field.placeholder = "请输入您的 API Key"
        field.text = UserDefaults.standard.string(forKey: "apiKey") ?? ""
        field.borderStyle = .roundedRect
        field.isSecureTextEntry = true
        field.translatesAutoresizingMaskIntoConstraints = false
        return field
    }()

    // 测试模式开关
    private lazy var testModeLabel: UILabel = {
        let label = UILabel()
        label.text = "测试模式:"
        label.font = .systemFont(ofSize: 14, weight: .medium)
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private lazy var testModeSwitch: UISwitch = {
        let switchControl = UISwitch()
        switchControl.isOn = false
        switchControl.addTarget(self, action: #selector(testModeChanged), for: .valueChanged)
        switchControl.translatesAutoresizingMaskIntoConstraints = false
        return switchControl
    }()

    // 模拟响应类型选择器
    private lazy var mockResponseTypeLabel: UILabel = {
        let label = UILabel()
        label.text = "模拟响应类型:"
        label.font = .systemFont(ofSize: 14, weight: .medium)
        label.translatesAutoresizingMaskIntoConstraints = false
        label.isHidden = true
        return label
    }()

    private lazy var mockResponseTypePicker: UIPickerView = {
        let picker = UIPickerView()
        picker.delegate = self
        picker.dataSource = self
        picker.translatesAutoresizingMaskIntoConstraints = false
        picker.isHidden = true
        return picker
    }()

    // 任务输入区域
    private lazy var taskSection: UIView = {
        let view = UIView()
        view.backgroundColor = .secondarySystemBackground
        view.layer.cornerRadius = 12
        view.translatesAutoresizingMaskIntoConstraints = false
        return view
    }()

    private lazy var taskLabel: UILabel = {
        let label = UILabel()
        label.text = "任务指令"
        label.font = .systemFont(ofSize: 18, weight: .semibold)
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private lazy var taskTextView: UITextView = {
        let view = UITextView()
        view.font = .systemFont(ofSize: 16)
        view.backgroundColor = .tertiarySystemBackground
        view.layer.cornerRadius = 8
        view.textContainerInset = UIEdgeInsets(top: 12, left: 12, bottom: 12, right: 12)
        view.translatesAutoresizingMaskIntoConstraints = false
        return view
    }()

    private lazy var executeButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle("执行任务", for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 18, weight: .semibold)
        button.backgroundColor = .systemBlue
        button.setTitleColor(.white, for: .normal)
        button.layer.cornerRadius = 12
        button.translatesAutoresizingMaskIntoConstraints = false
        button.addTarget(self, action: #selector(executeButtonTapped), for: .touchUpInside)
        return button
    }()

    private lazy var stopButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle("停止", for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 16, weight: .medium)
        button.backgroundColor = .systemRed
        button.setTitleColor(.white, for: .normal)
        button.layer.cornerRadius = 12
        button.translatesAutoresizingMaskIntoConstraints = false
        button.addTarget(self, action: #selector(stopButtonTapped), for: .touchUpInside)
        button.isEnabled = false
        return button
    }()

    // 执行状态区域
    private lazy var statusSection: UIView = {
        let view = UIView()
        view.backgroundColor = .secondarySystemBackground
        view.layer.cornerRadius = 12
        view.translatesAutoresizingMaskIntoConstraints = false
        return view
    }()

    private lazy var statusLabel: UILabel = {
        let label = UILabel()
        label.text = "执行状态"
        label.font = .systemFont(ofSize: 18, weight: .semibold)
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private lazy var thinkingTextView: UITextView = {
        let view = UITextView()
        view.font = .monospacedSystemFont(ofSize: 12, weight: .regular)
        view.backgroundColor = .tertiarySystemBackground
        view.layer.cornerRadius = 8
        view.textContainerInset = UIEdgeInsets(top: 12, left: 12, bottom: 12, right: 12)
        view.isEditable = false
        view.translatesAutoresizingMaskIntoConstraints = false
        return view
    }()

    private lazy var progressLabel: UILabel = {
        let label = UILabel()
        label.text = "步骤: 0"
        label.font = .systemFont(ofSize: 14, weight: .medium)
        label.textColor = .secondaryLabel
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    // 测试页面按钮
    private lazy var testPagesSection: UIView = {
        let view = UIView()
        view.backgroundColor = .secondarySystemBackground
        view.layer.cornerRadius = 12
        view.translatesAutoresizingMaskIntoConstraints = false
        return view
    }()

    private lazy var testPagesLabel: UILabel = {
        let label = UILabel()
        label.text = "测试页面"
        label.font = .systemFont(ofSize: 18, weight: .semibold)
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private lazy var loginPageButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle("登录页面", for: .normal)
        button.backgroundColor = .systemGreen
        button.setTitleColor(.white, for: .normal)
        button.layer.cornerRadius = 8
        button.translatesAutoresizingMaskIntoConstraints = false
        button.addTarget(self, action: #selector(showLoginPage), for: .touchUpInside)
        return button
    }()

    private lazy var listPageButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle("列表页面", for: .normal)
        button.backgroundColor = .systemOrange
        button.setTitleColor(.white, for: .normal)
        button.layer.cornerRadius = 8
        button.translatesAutoresizingMaskIntoConstraints = false
        button.addTarget(self, action: #selector(showListPage), for: .touchUpInside)
        return button
    }()

    // MARK: - Properties

    private var agentEngine: AgentEngine?
    private var statusLog: String = "" {
        didSet {
            thinkingTextView.text = statusLog
            let bottom = NSMakeRange(max(0, statusLog.count - 1), 1)
            thinkingTextView.scrollRangeToVisible(bottom)
        }
    }

    // 测试模式相关
    private let mockResponseTypes: [MockResponseType] = [
        .tapAction, .swipeAction, .typeAction, .backAction, .finishAction,
        .emptyAction, .unknownAction, .tapXML, .finishXML
    ]
    private var selectedMockResponseType = 0

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        print("✅ MainViewController: viewDidLoad 开始")

        setupUI()
        addKeyboardObservers()

        // 添加示例任务
        taskTextView.text = """
        示例任务:
        1. 点击"列表页面"
        2. 输入"苹果"搜索商品
        3. 点击第一个商品查看详情
        """

        print("✅ MainViewController: viewDidLoad 完成")
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        print("✅ MainViewController: viewWillAppear")
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        print("✅ MainViewController: viewDidAppear - 界面应该可见了")
    }

    // MARK: - Setup

    private func setupUI() {
        title = "Phone Agent Demo"
        view.backgroundColor = .white

        view.addSubview(scrollView)
        scrollView.addSubview(contentStackView)

        // 配置区域
        configSection.addSubview(titleLabel)
        configSection.addSubview(baseURLLabel)
        configSection.addSubview(baseURLTextField)
        configSection.addSubview(modelLabel)
        configSection.addSubview(modelTextField)
        configSection.addSubview(apiKeyLabel)
        configSection.addSubview(apiKeyTextField)
        configSection.addSubview(testModeLabel)
        configSection.addSubview(testModeSwitch)
        configSection.addSubview(mockResponseTypeLabel)
        configSection.addSubview(mockResponseTypePicker)

        // 任务输入区域
        taskSection.addSubview(taskLabel)
        taskSection.addSubview(taskTextView)

        // 按钮区域
        let buttonStack = UIStackView(arrangedSubviews: [executeButton, stopButton])
        buttonStack.axis = .horizontal
        buttonStack.spacing = 12
        buttonStack.distribution = .fillEqually
        buttonStack.translatesAutoresizingMaskIntoConstraints = false

        // 执行状态区域
        statusSection.addSubview(statusLabel)
        statusSection.addSubview(thinkingTextView)
        statusSection.addSubview(progressLabel)

        // 测试页面区域
        testPagesSection.addSubview(testPagesLabel)
        let pageButtonStack = UIStackView(arrangedSubviews: [loginPageButton, listPageButton])
        pageButtonStack.axis = .horizontal
        pageButtonStack.spacing = 12
        pageButtonStack.distribution = .fillEqually
        pageButtonStack.translatesAutoresizingMaskIntoConstraints = false
        testPagesSection.addSubview(pageButtonStack)

        // 添加到主栈
        contentStackView.addArrangedSubview(configSection)
        contentStackView.addArrangedSubview(taskSection)
        contentStackView.addArrangedSubview(buttonStack)
        contentStackView.addArrangedSubview(statusSection)
        contentStackView.addArrangedSubview(testPagesSection)

        // 布局 - 使用frameLayoutGuide和contentLayoutGuide
        NSLayoutConstraint.activate([
            // scrollView的frame连接到view的safeArea
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),

            // contentStackView连接到scrollView的contentLayoutGuide
            contentStackView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 20),
            contentStackView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor, constant: 16),
            contentStackView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor, constant: -16),
            contentStackView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -20),
            // 关键约束：让contentStackView的宽度等于scrollView的可见宽度
            contentStackView.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor, constant: -32),

            // 配置区域
            titleLabel.topAnchor.constraint(equalTo: configSection.topAnchor, constant: 16),
            titleLabel.leadingAnchor.constraint(equalTo: configSection.leadingAnchor, constant: 16),
            titleLabel.trailingAnchor.constraint(equalTo: configSection.trailingAnchor, constant: -16),

            baseURLLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 12),
            baseURLLabel.leadingAnchor.constraint(equalTo: configSection.leadingAnchor, constant: 16),
            baseURLLabel.widthAnchor.constraint(equalToConstant: 80),

            baseURLTextField.leadingAnchor.constraint(equalTo: baseURLLabel.trailingAnchor, constant: 8),
            baseURLTextField.trailingAnchor.constraint(equalTo: configSection.trailingAnchor, constant: -16),
            baseURLTextField.centerYAnchor.constraint(equalTo: baseURLLabel.centerYAnchor),
            baseURLTextField.heightAnchor.constraint(equalToConstant: 36),

            modelLabel.topAnchor.constraint(equalTo: baseURLTextField.bottomAnchor, constant: 12),
            modelLabel.leadingAnchor.constraint(equalTo: configSection.leadingAnchor, constant: 16),
            modelLabel.widthAnchor.constraint(equalToConstant: 80),

            modelTextField.leadingAnchor.constraint(equalTo: modelLabel.trailingAnchor, constant: 8),
            modelTextField.trailingAnchor.constraint(equalTo: configSection.trailingAnchor, constant: -16),
            modelTextField.centerYAnchor.constraint(equalTo: modelLabel.centerYAnchor),
            modelTextField.heightAnchor.constraint(equalToConstant: 36),

            apiKeyLabel.topAnchor.constraint(equalTo: modelTextField.bottomAnchor, constant: 12),
            apiKeyLabel.leadingAnchor.constraint(equalTo: configSection.leadingAnchor, constant: 16),
            apiKeyLabel.widthAnchor.constraint(equalToConstant: 80),

            apiKeyTextField.leadingAnchor.constraint(equalTo: apiKeyLabel.trailingAnchor, constant: 8),
            apiKeyTextField.trailingAnchor.constraint(equalTo: configSection.trailingAnchor, constant: -16),
            apiKeyTextField.centerYAnchor.constraint(equalTo: apiKeyLabel.centerYAnchor),
            apiKeyTextField.heightAnchor.constraint(equalToConstant: 36),

            testModeLabel.topAnchor.constraint(equalTo: apiKeyTextField.bottomAnchor, constant: 12),
            testModeLabel.leadingAnchor.constraint(equalTo: configSection.leadingAnchor, constant: 16),
            testModeLabel.widthAnchor.constraint(equalToConstant: 80),

            testModeSwitch.leadingAnchor.constraint(equalTo: testModeLabel.trailingAnchor, constant: 8),
            testModeSwitch.centerYAnchor.constraint(equalTo: testModeLabel.centerYAnchor),

            mockResponseTypeLabel.topAnchor.constraint(equalTo: testModeLabel.bottomAnchor, constant: 12),
            mockResponseTypeLabel.leadingAnchor.constraint(equalTo: configSection.leadingAnchor, constant: 16),

            mockResponseTypePicker.topAnchor.constraint(equalTo: mockResponseTypeLabel.bottomAnchor, constant: 8),
            mockResponseTypePicker.leadingAnchor.constraint(equalTo: configSection.leadingAnchor, constant: 16),
            mockResponseTypePicker.trailingAnchor.constraint(equalTo: configSection.trailingAnchor, constant: -16),
            mockResponseTypePicker.heightAnchor.constraint(equalToConstant: 120),
            mockResponseTypePicker.bottomAnchor.constraint(equalTo: configSection.bottomAnchor, constant: -16),

            // 任务区域
            taskLabel.topAnchor.constraint(equalTo: taskSection.topAnchor, constant: 16),
            taskLabel.leadingAnchor.constraint(equalTo: taskSection.leadingAnchor, constant: 16),

            taskTextView.topAnchor.constraint(equalTo: taskLabel.bottomAnchor, constant: 8),
            taskTextView.leadingAnchor.constraint(equalTo: taskSection.leadingAnchor, constant: 16),
            taskTextView.trailingAnchor.constraint(equalTo: taskSection.trailingAnchor, constant: -16),
            taskTextView.heightAnchor.constraint(equalToConstant: 150),
            taskTextView.bottomAnchor.constraint(equalTo: taskSection.bottomAnchor, constant: -16),

            // 按钮区域
            executeButton.heightAnchor.constraint(equalToConstant: 50),

            // 执行状态区域
            statusLabel.topAnchor.constraint(equalTo: statusSection.topAnchor, constant: 16),
            statusLabel.leadingAnchor.constraint(equalTo: statusSection.leadingAnchor, constant: 16),

            progressLabel.centerYAnchor.constraint(equalTo: statusLabel.centerYAnchor),
            progressLabel.trailingAnchor.constraint(equalTo: statusSection.trailingAnchor, constant: -16),

            thinkingTextView.topAnchor.constraint(equalTo: statusLabel.bottomAnchor, constant: 8),
            thinkingTextView.leadingAnchor.constraint(equalTo: statusSection.leadingAnchor, constant: 16),
            thinkingTextView.trailingAnchor.constraint(equalTo: statusSection.trailingAnchor, constant: -16),
            thinkingTextView.heightAnchor.constraint(equalToConstant: 150),
            thinkingTextView.bottomAnchor.constraint(equalTo: statusSection.bottomAnchor, constant: -16),

            // 测试页面区域
            testPagesLabel.topAnchor.constraint(equalTo: testPagesSection.topAnchor, constant: 16),
            testPagesLabel.leadingAnchor.constraint(equalTo: testPagesSection.leadingAnchor, constant: 16),

            pageButtonStack.topAnchor.constraint(equalTo: testPagesLabel.bottomAnchor, constant: 12),
            pageButtonStack.leadingAnchor.constraint(equalTo: testPagesSection.leadingAnchor, constant: 16),
            pageButtonStack.trailingAnchor.constraint(equalTo: testPagesSection.trailingAnchor, constant: -16),
            pageButtonStack.heightAnchor.constraint(equalToConstant: 50),
            pageButtonStack.bottomAnchor.constraint(equalTo: testPagesSection.bottomAnchor, constant: -16)
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
    }

    private func addKeyboardObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(keyboardWillShow(_:)),
            name: UIResponder.keyboardWillShowNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(keyboardWillHide(_:)),
            name: UIResponder.keyboardWillHideNotification,
            object: nil
        )
    }

    // MARK: - Actions

    @objc private func executeButtonTapped() {
        saveConfig()
        guard let task = taskTextView.text, !task.isEmpty else {
            showAlert(message: "请输入任务指令")
            return
        }

        executeButton.isEnabled = false
        stopButton.isEnabled = true
        taskTextView.isEditable = false

        statusLog = "=== 开始执行任务 ===\n"
        statusLog += "指令: \(task)\n"

        // 检查是否是测试模式
        if testModeSwitch.isOn {
            statusLog += "🧪 测试模式: \(mockResponseTypes[selectedMockResponseType].description)\n\n"
            runMockMode(instruction: task)
        } else {
            statusLog += "\n"
            // 创建 Agent
            let modelConfig = ModelConfig(
                baseURL: baseURLTextField.text ?? "",
                apiKey: apiKeyTextField.text ?? "",
                modelName: modelTextField.text ?? ""
            )
            let agentConfig = AgentConfig(maxSteps: 30, verbose: true)

            let engine = AgentEngine(modelConfig: modelConfig, agentConfig: agentConfig)
            self.agentEngine = engine

            setupCallbacks(engine: engine)
            executeEngine(engine: engine, instruction: task)
        }
    }

    /// 运行测试模式
    private func runMockMode(instruction: String) {
        let responseType = mockResponseTypes[selectedMockResponseType]
        let content = responseType.generateContent()

        // 简单解析（用于演示）
        var thinking = ""
        var action = ""

        // 解析思考过程
        if content.contains("finish(message=") {
            let parts = content.components(separatedBy: "finish(message=")
            thinking = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
        } else if content.contains("do(action=") {
            let parts = content.components(separatedBy: "do(action=")
            thinking = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
        } else if let range = content.range(of: "<thinking>"),
                  let endRange = content.range(of: "</thinking>", range: range.upperBound..<content.endIndex) {
            thinking = String(content[range.upperBound..<endRange.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
        }

        // 解析动作
        if let range = content.range(of: "finish(message=") {
            action = "finish(message=" + String(content[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
        } else if let range = content.range(of: "do(action=") {
            action = "do(action=" + String(content[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
        } else if let range = content.range(of: "<answer>"),
                  let endRange = content.range(of: "</answer>", range: range.upperBound..<content.endIndex) {
            action = String(content[range.upperBound..<endRange.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
        }

        // 更新 UI
        statusLog += "\n[模拟响应]\n"
        statusLog += "类型: \(responseType.description)\n"
        statusLog += "原始内容: \(content.prefix(150))...\n\n"
        statusLog += "思考: \(thinking)\n"
        statusLog += "动作: \(action)\n"

        // 检查解析结果
        if action.isEmpty {
            statusLog += "\n⚠️ 解析失败: 无法识别的动作格式\n"
        } else if action.contains("finish") {
            statusLog += "\n✅ 解析成功: 任务完成动作\n"
        } else {
            statusLog += "\n✅ 解析成功\n"
        }

        executeButton.isEnabled = true
        stopButton.isEnabled = false
        taskTextView.isEditable = true

        showAlert(
            title: "测试完成",
            message: "类型: \(responseType.description)\n解析: \(action.isEmpty ? "失败" : "成功")"
        )
    }

    /// 设置回调
    private func setupCallbacks(engine: AgentEngine) {
        engine.onStepProgress = { [weak self] (step: Int, thinking: String, action: String) in
            DispatchQueue.main.async {
                self?.statusLog += "\n[步骤 \(step)]\n"
                self?.statusLog += "思考: \(thinking)\n"
                self?.statusLog += "动作: \(action)\n"
                self?.progressLabel.text = "步骤: \(step)"
            }
        }

        engine.onComplete = { [weak self] (result: AgentResult) in
            guard let self = self else { return }
            DispatchQueue.main.async {
                self.executeButton.isEnabled = true
                self.stopButton.isEnabled = false
                self.taskTextView.isEditable = true

                self.statusLog += "\n=== 任务完成 ===\n"
                self.statusLog += "成功: \(result.success)\n"
                self.statusLog += "结果: \(result.message ?? "无")\n"
                self.statusLog += "总步数: \(result.totalSteps)\n"

                if let metrics = result.metrics {
                    self.statusLog += "\n性能统计:\n"
                    self.statusLog += "总耗时: \(String(format: "%.2f", metrics.totalDuration))秒\n"
                    self.statusLog += "平均每步: \(String(format: "%.2f", metrics.averageStepDuration))秒\n"
                }

                self.showAlert(
                    title: result.success ? "任务完成" : "任务失败",
                    message: result.message ?? "无"
                )
            }
        }

        engine.onSensitiveAction = { [weak self] (message: String) async -> Bool in
            // 显示确认对话框
            return await withCheckedContinuation { continuation in
                DispatchQueue.main.async {
                    let alert = UIAlertController(
                        title: "确认操作",
                        message: message,
                        preferredStyle: .alert
                    )
                    alert.addAction(UIAlertAction(title: "取消", style: .cancel) { _ in
                        continuation.resume(returning: false)
                    })
                    alert.addAction(UIAlertAction(title: "确认", style: .default) { _ in
                        continuation.resume(returning: true)
                    })
                    self?.present(alert, animated: true)
                }
            }
        }
    }

    /// 执行引擎
    private func executeEngine(engine: AgentEngine, instruction: String) {
        Task {
            let _ = await engine.run(instruction: instruction)
        }
    }

    @objc private func stopButtonTapped() {
        agentEngine?.stop()
        executeButton.isEnabled = true
        stopButton.isEnabled = false
        taskTextView.isEditable = true
        statusLog += "\n=== 任务已停止 ===\n"
    }

    @objc private func showLoginPage() {
        let loginVC = LoginViewController()
        navigationController?.pushViewController(loginVC, animated: true)
    }

    @objc private func showListPage() {
        let listVC = ProductListViewController()
        navigationController?.pushViewController(listVC, animated: true)
    }

    // MARK: - Keyboard

    @objc private func keyboardWillShow(_ notification: Notification) {
        guard let keyboardFrame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else {
            return
        }
        // 获取键盘在scrollView坐标系中的frame
        let keyboardFrameInView = view.convert(keyboardFrame, from: view.window)

        // 计算需要滚动的距离
        let keyboardHeight = keyboardFrameInView.height - view.safeAreaInsets.bottom
        let contentInset = UIEdgeInsets(top: 0, left: 0, bottom: keyboardHeight, right: 0)

        scrollView.contentInset = contentInset
        scrollView.scrollIndicatorInsets = contentInset
    }

    @objc private func keyboardWillHide(_ notification: Notification) {
        // 键盘隐藏时，恢复contentInset
        // 由于我们使用了contentLayoutGuide，scrollView会自动处理safeArea的inset
        scrollView.contentInset = .zero
        scrollView.scrollIndicatorInsets = .zero
    }

    // MARK: - Helpers

    private func saveConfig() {
        UserDefaults.standard.set(baseURLTextField.text, forKey: "baseURL")
        UserDefaults.standard.set(modelTextField.text, forKey: "modelName")
        UserDefaults.standard.set(apiKeyTextField.text, forKey: "apiKey")
    }

    private func showAlert(title: String = "提示", message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "确定", style: .default))
        present(alert, animated: true)
    }

    // MARK: - Test Mode

    @objc private func testModeChanged() {
        let isOn = testModeSwitch.isOn
        mockResponseTypeLabel.isHidden = !isOn
        mockResponseTypePicker.isHidden = !isOn

        // 测试模式下不需要 API 配置
        baseURLTextField.isEnabled = !isOn
        modelTextField.isEnabled = !isOn
        apiKeyTextField.isEnabled = !isOn

        if isOn {
            statusLog = "🧪 测试模式已启用\n"
        }
    }

    // MARK: - UIPickerView DataSource

    public func numberOfComponents(in pickerView: UIPickerView) -> Int {
        return 1
    }

    public func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int {
        return mockResponseTypes.count
    }

    // MARK: - UIPickerView Delegate

    public func pickerView(_ pickerView: UIPickerView, titleForRow row: Int, forComponent component: Int) -> String? {
        return mockResponseTypes[row].description
    }

    public func pickerView(_ pickerView: UIPickerView, didSelectRow row: Int, inComponent component: Int) {
        selectedMockResponseType = row
        statusLog = "🧪 已选择: \(mockResponseTypes[row].description)\n"
    }
}
