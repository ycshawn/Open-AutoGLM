//
//  LoginViewController.swift
//  DemoApp
//
//  Created by AutoGLM on 2025-01-30.
//

import UIKit

/// 登录页面 - 用于测试 Agent 的登录能力
class LoginViewController: UIViewController {

    // MARK: - UI Components

    private lazy var logoImageView: UIImageView = {
        let imageView = UIImageView()
        imageView.contentMode = .scaleAspectFit
        imageView.image = UIImage(systemName: "person.circle.fill")
        imageView.tintColor = .systemBlue
        imageView.translatesAutoresizingMaskIntoConstraints = false
        return imageView
    }()

    private lazy var titleLabel: UILabel = {
        let label = UILabel()
        label.text = "欢迎登录"
        label.font = .systemFont(ofSize: 28, weight: .bold)
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private lazy var subtitleLabel: UILabel = {
        let label = UILabel()
        label.text = "请输入您的账号信息"
        label.font = .systemFont(ofSize: 16)
        label.textColor = .secondaryLabel
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private lazy var usernameContainer: UIView = {
        let view = UIView()
        view.backgroundColor = .secondarySystemBackground
        view.layer.cornerRadius = 12
        view.translatesAutoresizingMaskIntoConstraints = false
        return view
    }()

    private lazy var usernameLabel: UILabel = {
        let label = UILabel()
        label.text = "用户名"
        label.font = .systemFont(ofSize: 14, weight: .medium)
        label.textColor = .secondaryLabel
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private lazy var usernameTextField: UITextField = {
        let field = UITextField()
        field.placeholder = "请输入用户名"
        field.font = .systemFont(ofSize: 16)
        field.borderStyle = .none
        field.translatesAutoresizingMaskIntoConstraints = false
        return field
    }()

    private lazy var passwordContainer: UIView = {
        let view = UIView()
        view.backgroundColor = .secondarySystemBackground
        view.layer.cornerRadius = 12
        view.translatesAutoresizingMaskIntoConstraints = false
        return view
    }()

    private lazy var passwordLabel: UILabel = {
        let label = UILabel()
        label.text = "密码"
        label.font = .systemFont(ofSize: 14, weight: .medium)
        label.textColor = .secondaryLabel
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private lazy var passwordTextField: UITextField = {
        let field = UITextField()
        field.placeholder = "请输入密码"
        field.font = .systemFont(ofSize: 16)
        field.borderStyle = .none
        field.isSecureTextEntry = true
        field.translatesAutoresizingMaskIntoConstraints = false
        return field
    }()

    private lazy var loginButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle("登录", for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 18, weight: .semibold)
        button.backgroundColor = .systemBlue
        button.setTitleColor(.white, for: .normal)
        button.layer.cornerRadius = 12
        button.translatesAutoresizingMaskIntoConstraints = false
        button.addTarget(self, action: #selector(loginButtonTapped), for: .touchUpInside)
        return button
    }()

    private lazy var forgotPasswordButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle("忘记密码？", for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 14)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.addTarget(self, action: #selector(forgotPasswordTapped), for: .touchUpInside)
        return button
    }()

    private lazy var registerButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle("还没有账号？立即注册", for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 14)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.addTarget(self, action: #selector(registerTapped), for: .touchUpInside)
        return button
    }()

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
    }

    // MARK: - Setup

    private func setupUI() {
        title = "登录"
        view.backgroundColor = .systemBackground

        view.addSubview(logoImageView)
        view.addSubview(titleLabel)
        view.addSubview(subtitleLabel)

        // 用户名输入
        usernameContainer.addSubview(usernameLabel)
        usernameContainer.addSubview(usernameTextField)
        view.addSubview(usernameContainer)

        // 密码输入
        passwordContainer.addSubview(passwordLabel)
        passwordContainer.addSubview(passwordTextField)
        view.addSubview(passwordContainer)

        view.addSubview(loginButton)
        view.addSubview(forgotPasswordButton)
        view.addSubview(registerButton)

        NSLayoutConstraint.activate([
            logoImageView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 40),
            logoImageView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            logoImageView.widthAnchor.constraint(equalToConstant: 80),
            logoImageView.heightAnchor.constraint(equalToConstant: 80),

            titleLabel.topAnchor.constraint(equalTo: logoImageView.bottomAnchor, constant: 24),
            titleLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 32),
            titleLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -32),

            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 8),
            subtitleLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 32),
            subtitleLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -32),

            usernameContainer.topAnchor.constraint(equalTo: subtitleLabel.bottomAnchor, constant: 40),
            usernameContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            usernameContainer.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            usernameContainer.heightAnchor.constraint(equalToConstant: 56),

            usernameLabel.leadingAnchor.constraint(equalTo: usernameContainer.leadingAnchor, constant: 16),
            usernameLabel.topAnchor.constraint(equalTo: usernameContainer.topAnchor, constant: 8),

            usernameTextField.leadingAnchor.constraint(equalTo: usernameContainer.leadingAnchor, constant: 16),
            usernameTextField.trailingAnchor.constraint(equalTo: usernameContainer.trailingAnchor, constant: -16),
            usernameTextField.bottomAnchor.constraint(equalTo: usernameContainer.bottomAnchor, constant: -8),

            passwordContainer.topAnchor.constraint(equalTo: usernameContainer.bottomAnchor, constant: 16),
            passwordContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            passwordContainer.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            passwordContainer.heightAnchor.constraint(equalToConstant: 56),

            passwordLabel.leadingAnchor.constraint(equalTo: passwordContainer.leadingAnchor, constant: 16),
            passwordLabel.topAnchor.constraint(equalTo: passwordContainer.topAnchor, constant: 8),

            passwordTextField.leadingAnchor.constraint(equalTo: passwordContainer.leadingAnchor, constant: 16),
            passwordTextField.trailingAnchor.constraint(equalTo: passwordContainer.trailingAnchor, constant: -16),
            passwordTextField.bottomAnchor.constraint(equalTo: passwordContainer.bottomAnchor, constant: -8),

            loginButton.topAnchor.constraint(equalTo: passwordContainer.bottomAnchor, constant: 32),
            loginButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            loginButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            loginButton.heightAnchor.constraint(equalToConstant: 56),

            forgotPasswordButton.topAnchor.constraint(equalTo: loginButton.bottomAnchor, constant: 16),
            forgotPasswordButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),

            registerButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -20),
            registerButton.centerXAnchor.constraint(equalTo: view.centerXAnchor)
        ])
    }

    // MARK: - Actions

    @objc private func loginButtonTapped() {
        guard let username = usernameTextField.text, !username.isEmpty else {
            showAlert(message: "请输入用户名")
            return
        }

        guard let password = passwordTextField.text, !password.isEmpty else {
            showAlert(message: "请输入密码")
            return
        }

        // 模拟登录
        let alert = UIAlertController(
            title: "登录成功",
            message: "欢迎回来，\(username)！",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "确定", style: .default) { [weak self] _ in
            self?.navigationController?.popViewController(animated: true)
        })
        present(alert, animated: true)
    }

    @objc private func forgotPasswordTapped() {
        showAlert(message: "忘记密码功能")
    }

    @objc private func registerTapped() {
        showAlert(message: "注册功能")
    }

    // MARK: - Helpers

    private func showAlert(message: String) {
        let alert = UIAlertController(title: "提示", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "确定", style: .default))
        present(alert, animated: true)
    }
}
