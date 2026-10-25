package com.yu.ai.rag.llm;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;

@Component
@ConfigurationProperties(prefix = "rag.llm")
public class LlmProperties {

    private String provider = "fake";
    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-flash";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(30);
    private int maxTokens = 1200;
    private double temperature = 0.2;
    private int maxContextChars = 12000;

    @PostConstruct
    public void validate() {
        provider = StringUtils.hasText(provider)
                ? provider.trim().toLowerCase(Locale.ROOT)
                : "fake";

        if (!provider.equals("fake") && !provider.equals("deepseek")) {
            throw new IllegalStateException(
                    "rag.llm.provider must be either fake or deepseek");
        }
        if (maxTokens < 1 || maxTokens > 32768) {
            throw new IllegalStateException(
                    "rag.llm.max-tokens must be between 1 and 32768");
        }
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalStateException(
                    "rag.llm.temperature must be between 0.0 and 2.0");
        }
        if (maxContextChars < 500 || maxContextChars > 100000) {
            throw new IllegalStateException(
                    "rag.llm.max-context-chars must be between 500 and 100000");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalStateException("rag.llm.connect-timeout must be positive");
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalStateException("rag.llm.read-timeout must be positive");
        }
        if (provider.equals("deepseek")) {
            requireText(baseUrl, "DEEPSEEK_BASE_URL");
            requireText(apiKey, "DEEPSEEK_API_KEY");
            requireText(model, "DEEPSEEK_MODEL");
        }
    }

    private void requireText(String value, String propertyName) {
        if (!StringUtils.hasText(value)
                || value.toLowerCase(Locale.ROOT).contains("replace-me")
                || value.toLowerCase(Locale.ROOT).contains("your-api-key")) {
            throw new IllegalStateException(
                    "RAG LLM provider is deepseek but " + propertyName
                            + " is missing or still a placeholder");
        }
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getMaxContextChars() { return maxContextChars; }
    public void setMaxContextChars(int maxContextChars) { this.maxContextChars = maxContextChars; }
}
