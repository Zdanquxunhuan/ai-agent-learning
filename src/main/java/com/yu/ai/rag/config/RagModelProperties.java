package com.yu.ai.rag.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
@ConfigurationProperties(prefix = "rag.model")
public class RagModelProperties {

    private boolean enabled;
    private String baseUrl;
    private String name;
    private String apiKey;

    @PostConstruct
    public void validate() {
        if (!enabled) {
            return;
        }

        requireConfigured(baseUrl, "RAG_MODEL_BASE_URL");
        requireConfigured(name, "RAG_MODEL_NAME");
        requireConfigured(apiKey, "RAG_MODEL_API_KEY");
    }

    private void requireConfigured(String value, String envName) {
        String normalized = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);

        if (!StringUtils.hasText(value)
                || normalized.contains("example.com")
                || normalized.startsWith("your-")
                || normalized.startsWith("replace-with")) {

            throw new IllegalStateException(
                    "RAG 模型已启用，但配置 " + envName
                            + " 缺失或仍是占位值。"
                            + "请检查项目根目录的 .env 或环境变量。"
            );
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}

