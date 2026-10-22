package com.yu.ai.rag.document;

/**
 * 文档处理状态
 */
public enum DocumentStatus {

    // 已上传，等待处理
    UPLOADED,

    // 正在处理
    PROCESSING,

    // 处理完成
    READY,

    // 处理失败
    FAILED
}