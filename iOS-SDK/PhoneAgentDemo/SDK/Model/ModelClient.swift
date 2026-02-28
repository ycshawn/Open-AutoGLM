//
//  ModelClient.swift
//  PhoneAgentSDK
//
//  Created by AutoGLM on 2025-01-30.
//

import Foundation
import OSLog

/// 模型客户端 - 负责与 AutoGLM 云端模型通信
let logger = Logger(subsystem: "com.demo.PhoneAgentDemo", category: "ModelClient")
@MainActor
public class ModelClient: ObservableObject {

    // MARK: - Properties

    private let config: ModelConfig
    private let session: URLSession
    private var streamTask: URLSessionDataTask?

    // MARK: - 回调

    /// 思考过程回调
    public var onThinking: ((String) -> Void)?

    /// 动作回调
    public var onAction: ((String) -> Void)?

    /// 错误回调
    public var onError: ((Error) -> Void)?

    // MARK: - Initialization

    public init(config: ModelConfig = .default) {
        self.config = config

        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 120
        configuration.timeoutIntervalForResource = 300
        self.session = URLSession(configuration: configuration)
    }

    // MARK: - Public Properties

    /// 配置（只读）
    public var configValue: ModelConfig {
        return config
    }

    // MARK: - Public Methods

    /// 发送请求到模型
    /// - Parameters:
    ///   - messages: 消息列表
    ///   - screenWidth: 屏幕宽度（用于解析坐标）
    ///   - screenHeight: 屏幕高度（用于解析坐标）
    /// - Returns: 模型响应
    public func request(
        messages: [MessageBuilder.Message],
        screenWidth: CGFloat,
        screenHeight: CGFloat
    ) async throws -> ModelResponse {
        // 取消之前的请求
        streamTask?.cancel()

        // 构建请求
        let request = try buildRequest(messages: messages)

        // 创建流式任务
        let startTime = Date()
        var rawContent = ""
        var thinkingContent = ""
        var buffer = ""
        var inActionPhase = false
        var firstTokenReceived = false
        var timeToFirstToken: Double?
        var timeToThinkingEnd: Double?

        // 动作标记
        let actionMarkers = ["finish(message=", "do(action="]

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<ModelResponse, Error>) in
            self.streamTask = self.session.dataTask(with: request) { data, response, error in
                if let error = error {
                    continuation.resume(throwing: error)
                    return
                }

                guard let httpResponse = response as? HTTPURLResponse else {
                    continuation.resume(throwing: ModelError.invalidResponse)
                    return
                }

                guard httpResponse.statusCode == 200 else {
                    continuation.resume(throwing: ModelError.httpError(httpResponse.statusCode))
                    return
                }

                guard let data = data else {
                    continuation.resume(throwing: ModelError.noData)
                    return
                }

                // 调试：记录原始响应（仅在解析失败时有用）
                if let jsonString = String(data: data, encoding: .utf8) {
                    logger.debug("\(jsonString.prefix(1000))")
                }

                // 解析非流式 JSON 响应
                do {
                    guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                          let choices = json["choices"] as? [[String: Any]],
                          let choice = choices.first,
                          let message = choice["message"] as? [String: Any],
                          let content = message["content"] as? String else {
                        logger.error("JSON 解析失败")
                        continuation.resume(throwing: ModelError.parsingError)
                        return
                    }

                    rawContent = content
                    timeToFirstToken = Date().timeIntervalSince(startTime)
                    timeToThinkingEnd = timeToFirstToken

                } catch {
                    logger.error("JSON 解析失败: \(error.localizedDescription)")
                    continuation.resume(throwing: ModelError.parsingError)
                    return
                }

                // 解析响应
                let parsedThinking = self.parseThinking(rawContent)
                let parsedAction = self.parseAction(rawContent)

                let totalTime = Date().timeIntervalSince(startTime)
                let metrics = ModelResponse.ResponseMetrics.make(
                    timeToFirstToken: timeToFirstToken ?? totalTime,
                    timeToThinkingEnd: timeToThinkingEnd,
                    totalTime: totalTime
                )

                let modelResponse = ModelResponse(
                    thinking: parsedThinking,
                    action: parsedAction,
                    rawContent: rawContent,
                    metrics: metrics
                )

                continuation.resume(returning: modelResponse)
            }

            self.streamTask?.resume()
        }

        // 返回解析后的响应
        let parsedThinking = parseThinking(rawContent)
        let parsedAction = parseAction(rawContent)

        let totalTime = Date().timeIntervalSince(startTime)
        let metrics = ModelResponse.ResponseMetrics.make(
            timeToFirstToken: timeToFirstToken ?? totalTime,
            timeToThinkingEnd: timeToThinkingEnd,
            totalTime: totalTime
        )

        return ModelResponse(
            thinking: parsedThinking,
            action: parsedAction,
            rawContent: rawContent,
            metrics: metrics
        )
    }

    // MARK: - Private Methods

    private func buildRequest(messages: [MessageBuilder.Message]) throws -> URLRequest {
        guard let url = URL(string: "\(config.baseURL)/chat/completions") else {
            throw ModelError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(config.apiKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let requestBody: [String: Any] = [
            "model": config.modelName,
            "messages": messages.map { message -> [String: Any] in
                return [
                    "role": message.role.rawValue,
                    "content": message.content.compactMap { item -> [String: Any]? in
                        switch item {
                        case .text(let text):
                            return ["type": "text", "text": text]
                        case .image(let imageURL):
                            return ["type": "image_url", "image_url": ["url": imageURL.url]]
                        }
                    }
                ]
            },
            "max_tokens": config.maxTokens,
            "temperature": config.temperature,
            "top_p": config.topP,
            "frequency_penalty": config.frequencyPenalty,
            "stream": false
        ]

        request.httpBody = try JSONSerialization.data(withJSONObject: requestBody)
        return request
    }

    private func processStream(
        lines: String,
        startTime: Date,
        rawContent: inout String,
        thinkingContent: inout String,
        buffer: inout String,
        inActionPhase: inout Bool,
        firstTokenReceived: inout Bool,
        timeToFirstToken: inout Double?,
        timeToThinkingEnd: inout Double?,
        actionMarkers: [String]
    ) {
        let lines = lines.split(separator: "\n")

        for line in lines {
            let trimmedLine = line.trimmingCharacters(in: .whitespacesAndNewlines)

            // SSE 格式: data: {...}
            guard trimmedLine.hasPrefix("data: ") else {
                continue
            }

            let jsonStr = String(trimmedLine.dropFirst(6))

            // 结束标记
            if jsonStr == "[DONE]" {
                break
            }

            guard let data = jsonStr.data(using: .utf8),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let choices = json["choices"] as? [[String: Any]],
                  let choice = choices.first,
                  let delta = choice["delta"] as? [String: Any],
                  let content = delta["content"] as? String else {
                continue
            }

            rawContent += content

            // 记录首字延迟
            if !firstTokenReceived {
                firstTokenReceived = true
                timeToFirstToken = Date().timeIntervalSince(startTime)
            }

            if inActionPhase {
                continue
            }

            buffer += content

            // 检查是否有动作标记
            var markerFound = false
            for marker in actionMarkers {
                if buffer.contains(marker) {
                    let thinkingPart = buffer.components(separatedBy: marker)[0]
                    thinkingContent = thinkingPart
                    onThinking?(thinkingPart)
                    inActionPhase = true
                    markerFound = true

                    if timeToThinkingEnd == nil {
                        timeToThinkingEnd = Date().timeIntervalSince(startTime)
                    }
                    break
                }
            }

            if markerFound {
                continue
            }

            // 检查 buffer 是否以某个标记的前缀结尾
            var isPotentialMarker = false
            for marker in actionMarkers {
                for i in 1..<marker.count {
                    if buffer.hasSuffix(String(marker.prefix(i))) {
                        isPotentialMarker = true
                        break
                    }
                }
                if isPotentialMarker {
                    break
                }
            }

            if !isPotentialMarker {
                onThinking?(buffer)
                thinkingContent += buffer
                buffer = ""
            }
        }
    }

    private func parseThinking(_ content: String) -> String {
        // Rule 1: 检查 finish(message=
        if content.contains("finish(message=") {
            let parts = content.components(separatedBy: "finish(message=")
            return parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
        }

        // Rule 2: 检查 do(action=
        if content.contains("do(action=") {
            let parts = content.components(separatedBy: "do(action=")
            return parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
        }

        // Rule 3: 遗留的 XML 标签解析（向后兼容）
        if let range = content.range(of: "<thinking>", options: .caseInsensitive),
           let endRange = content.range(of: "</thinking>", options: .caseInsensitive, range: range.upperBound..<content.endIndex) {
            return String(content[range.upperBound..<endRange.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
        }

        // Rule 4: 没有找到标记，返回空字符串
        logger.warning("无法解析思考过程，原始内容: \(content.prefix(200))")
        return ""
    }

    private func parseAction(_ content: String) -> String {
        // 优先检查 finish(message= - 这是中文提示词的格式
        if let range = content.range(of: "finish(message=") {
            return "finish(message=" + String(content[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
        }

        // 优先检查 do(action= - 这是中文提示词的格式
        if let range = content.range(of: "do(action=") {
            return "do(action=" + String(content[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
        }

        // 向后兼容：提取 <answer> 标签内容（英文提示词的格式）
        if let range = content.range(of: "<answer>", options: .caseInsensitive),
           let endRange = content.range(of: "</answer>", options: .caseInsensitive, range: range.upperBound..<content.endIndex) {
            return String(content[range.upperBound..<endRange.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
        }

        // 都没找到，返回空字符串（将在 AgentEngine 中触发解析错误）
        logger.warning("无法解析动作，原始内容: \(content.prefix(200))")
        return ""
    }
}

// MARK: - Error Types

public enum ModelError: LocalizedError {
    case invalidURL
    case invalidResponse
    case httpError(Int)
    case noData
    case parsingError
    case networkError(Error)

    public var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid API URL"
        case .invalidResponse:
            return "Invalid response from server"
        case .httpError(let code):
            return "HTTP error: \(code)"
        case .noData:
            return "No data received"
        case .parsingError:
            return "Failed to parse response"
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        }
    }
}

// MARK: - 取消操作
extension ModelClient {
    /// 取消当前请求
    public func cancel() {
        streamTask?.cancel()
        streamTask = nil
    }
}
