package com.yu.ai.day01;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class Day1LlmDemo {

    public static void main(String[] args) throws Exception {

        // 1. 读取环境变量
        String apiKey = "sk-027a3fb8a3764f00a432a7f913d4d6ee";
        String model = System.getenv()
                .getOrDefault("DEEPSEEK_MODEL", "deepseek-flash");

        // 2. 创建 JSON 序列化工具
        ObjectMapper mapper = new ObjectMapper();

        // 3. 构造 DeepSeek 请求参数
        Map<String, Object> requestData = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "你是一位Java技术导师，请使用简洁中文回答."
                        ),
                        Map.of(
                                "role", "user",
                                "content", "请用三句话解释Java的HashMap"
                        )
                ),
                "thinking", Map.of("type", "enabled"),
                "reasoning_effort", "high",
                "stream", false
        );

        String requestJson =
                mapper.writeValueAsString(requestData);

        // 打印请求参数，方便学习和调试
        System.out.println("请求JSON：");
        System.out.println(
                mapper.readTree(requestJson).toPrettyString()
        );

        // 4. 创建 HTTP 客户端
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // 5. 构造 HTTP 请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "https://api.deepseek.com/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(
                        requestJson, StandardCharsets.UTF_8))
                .build();

        // 6. 发送请求并等待响应
        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8)
        );

        // 7. 判断 HTTP 是否成功
        System.out.println("HTTP状态码：" + response.statusCode());

        if (response.statusCode() != 200) {
            System.err.println("API调用失败：");
            System.err.println(response.body());
            return;
        }

        // 8. 解析 DeepSeek JSON 响应
        JsonNode root = mapper.readTree(response.body());

        System.out.println("响应ID："
                + root.path("id").asText());

        System.out.println("模型名称："
                + root.path("model").asText());

        // 9. 从 choices[0].message 获取模型输出
        JsonNode choices = root.path("choices");

        if (!choices.isArray() || choices.size() == 0) {
            System.err.println("响应中没有choices数据：");
            System.err.println(response.body());
            return;
        }

        JsonNode firstChoice = choices.get(0);
        JsonNode message = firstChoice.path("message");

        // 模型最终回答
        String answer =
                message.path("content").asText("");

        // 模型思考内容（可能为空）
        String reasoning =
                message.path("reasoning_content").asText("");

        // 模型结束原因
        String finishReason =
                firstChoice.path("finish_reason").asText("");

        System.out.println("\n模型回答：");
        System.out.println(answer.isBlank()
                ? "未找到模型回答，请检查完整响应。"
                : answer);

        System.out.println("\n模型思考内容：");
        System.out.println(reasoning.isBlank()
                ? "无思考内容"
                : reasoning);

        System.out.println("\n结束原因：" + finishReason);

        // 10. 查看 Token 使用情况
        JsonNode usage = root.path("usage");

        System.out.println("\nToken 使用情况：");

        System.out.println("输入Token："
                + usage.path("prompt_tokens").asInt());

        System.out.println("输出Token："
                + usage.path("completion_tokens").asInt());

        System.out.println("总Token："
                + usage.path("total_tokens").asInt());

        // 推理Token包含在输出Token中
        int reasoningTokens = usage
                .path("completion_tokens_details")
                .path("reasoning_tokens")
                .asInt(0);

        System.out.println("推理Token："
                + reasoningTokens);

        System.out.println("\n完整Token统计：");
        System.out.println(usage.toPrettyString());
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "缺少环境变量：" + name);
        }

        return value;
    }
}


