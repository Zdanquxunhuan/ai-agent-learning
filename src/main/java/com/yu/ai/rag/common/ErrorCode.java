package com.yu.ai.rag.common;

/**
 * 系统统一错误码枚举。
 */
public enum ErrorCode {

    INVALID_PARAMETER(
            "INVALID_PARAMETER",
            "请求参数不合法"
    ),

    BUSINESS_ERROR(
            "BUSINESS_ERROR",
            "业务处理失败"
    ),

    INTERNAL_ERROR(
            "INTERNAL_ERROR",
            "服务器内部错误"
    );

    /**
     * 对外返回的错误码
     */
    private final String code;

    /**
     * 默认错误消息
     */
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}