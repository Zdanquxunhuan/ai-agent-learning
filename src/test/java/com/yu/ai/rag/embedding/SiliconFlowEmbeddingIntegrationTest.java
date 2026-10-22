
package com.yu.ai.rag.embedding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class SiliconFlowEmbeddingIntegrationTest {

    @Test
    @EnabledIfSystemProperty(
            named = "runEmbeddingIntegration",
            matches = "true"
    )
    void shouldCallRealEmbeddingService() throws IOException {
        Properties env = new Properties();

        Path envFile = Path.of(".env");

        assertTrue(
                Files.exists(envFile),
                "项目根目录下必须存在 .env 文件"
        );

        try (Reader reader = Files.newBufferedReader(
                envFile, StandardCharsets.UTF_8)) {
            env.load(reader);
        }

        String apiKey = env.getProperty(
                "SILICONFLOW_API_KEY", "").trim();

        String baseUrl = env.getProperty(
                "SILICONFLOW_BASE_URL",
                "https://api.siliconflow.cn/v1").trim();

        String model = env.getProperty(
                "SILICONFLOW_EMBEDDING_MODEL",
                "BAAI/bge-m3").trim();

        int dimensions = Integer.parseInt(
                env.getProperty(
                        "SILICONFLOW_EMBEDDING_DIMENSIONS",
                        "1024").trim());

        assertFalse(
                apiKey.isBlank(),
                "请先在 .env 中配置 SILICONFLOW_API_KEY"
        );

        EmbeddingService service =
                new SiliconFlowEmbeddingService(
                        baseUrl,
                        apiKey,
                        model,
                        dimensions,
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(30),
                        2
                );

        EmbeddingResult result =
                service.embed("如何重置密码？");

        assertNotNull(result);
        assertFalse(result.modelId().isBlank());
        assertEquals(dimensions, result.dimensions());
        assertEquals(dimensions, result.vector().size());

        assertTrue(
                result.vector().stream()
                        .allMatch(value ->
                                value != null
                                        && Double.isFinite(value)),
                "向量必须全部是有效数值"
        );

        System.out.println("Embedding API 调用成功");
        System.out.println("模型：" + result.modelId());
        System.out.println("向量维度：" + result.dimensions());
    }
}
