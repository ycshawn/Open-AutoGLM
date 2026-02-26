//
//  ModelConfig.swift
//  PhoneAgentSDK
//
//  Created by AutoGLM on 2025-01-30.
//

import Foundation

/// 模型配置
public struct ModelConfig {

    /// API 基础地址
    public var baseURL: String

    /// API 密钥
    public var apiKey: String

    /// 模型名称
    public var modelName: String

    /// 最大生成 token 数
    public var maxTokens: Int

    /// 温度参数
    public var temperature: Double

    /// Top P 采样
    public var topP: Double

    /// 频率惩罚
    public var frequencyPenalty: Double

    /// 语言（用于提示词和消息）
    public var language: Language

    public enum Language: String {
        case chinese = "cn"
        case english = "en"

        var localeCode: String {
            switch self {
            case .chinese: return "zh-CN"
            case .english: return "en-US"
            }
        }
    }

    public init(
        baseURL: String = "http://localhost:8000/v1",
        apiKey: String = "EMPTY",
        modelName: String = "autoglm-phone-9b",
        maxTokens: Int = 3000,
        temperature: Double = 0.0,
        topP: Double = 0.85,
        frequencyPenalty: Double = 0.2,
        language: Language = .chinese
    ) {
        self.baseURL = baseURL
        self.apiKey = apiKey
        self.modelName = modelName
        self.maxTokens = maxTokens
        self.temperature = temperature
        self.topP = topP
        self.frequencyPenalty = frequencyPenalty
        self.language = language
    }
}

// MARK: - 默认配置
extension ModelConfig {
    /// 默认配置（中文）
    public static let `default` = ModelConfig()

    /// 英文配置
    public static let english = ModelConfig(language: .english)
}
