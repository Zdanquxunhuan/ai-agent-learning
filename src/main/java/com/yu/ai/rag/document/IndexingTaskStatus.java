package com.yu.ai.rag.document;

/**
 * 文档索引任务状态。
 */
public enum IndexingTaskStatus {

    // 尚未提交索引任务
    PENDING,

    // 已提交，等待线程池执行
    QUEUED,

    // 正在执行
    RUNNING,

    // 索引任务执行成功
    SUCCEEDED,

    // 索引任务执行失败
    FAILED
}