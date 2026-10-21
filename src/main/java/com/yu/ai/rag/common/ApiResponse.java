package com.yu.ai.rag.common;

/**
 * 统一 API 响应模型。
 *
 * @param <T> 业务数据的类型
 */
public class ApiResponse<T> {

    /**
     * 业务处理是否成功
     */
    private boolean success;

    /**
     * 业务数据
     */
    private T data;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误消息
     */
    private String errorMessage;

    public ApiResponse() {
    }

    private ApiResponse(
            boolean success,
            T data,
            String errorCode,
            String errorMessage) {
        this.success = success;
        this.data = data;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * 创建成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    /**
     * 创建失败响应
     */
    public static <T> ApiResponse<T> failure(
            String errorCode,
            String errorMessage) {
        return new ApiResponse<>(
                false, null, errorCode, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
