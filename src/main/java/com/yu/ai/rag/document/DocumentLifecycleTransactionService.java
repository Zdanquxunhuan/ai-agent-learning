package com.yu.ai.rag.document;

import com.yu.ai.rag.chunking.DocumentChunkRepository;
import com.yu.ai.rag.common.BusinessException;
import com.yu.ai.rag.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DocumentLifecycleTransactionService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    public DocumentLifecycleTransactionService(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    /**
     * 开始删除：
     * 1. 标记 DELETING
     * 2. 物理删除全部切片及其向量
     *
     * 两步必须在同一数据库事务内。
     */
    @Transactional
    public DocumentRecord beginDeletion(UUID documentId) {

        DocumentRecord document = documentRepository
                .findById(documentId)
                .orElse(null);

        // 删除接口允许重复调用。
        if (document == null) {
            return null;
        }

        DocumentIndexingStatusResponse taskStatus =
                documentRepository.findIndexingStatus(documentId)
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.BUSINESS_ERROR,
                                "无法查询文档索引状态"
                        ));

        if (document.status() == DocumentStatus.PROCESSING
                || taskStatus.taskStatus() == IndexingTaskStatus.QUEUED
                || taskStatus.taskStatus() == IndexingTaskStatus.RUNNING) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "文档正在索引，请等待任务结束后再删除"
            );
        }

        boolean marked = documentRepository.markDeleting(documentId);

        if (!marked) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "文档状态已变化，无法删除，请重新查询状态"
            );
        }

        // document_chunks 同时保存文本和 embedding。
        // 物理删除这一表中的记录即可清理两者。
        chunkRepository.deleteByDocumentId(documentId);

        return document;
    }

    /**
     * 原文件清理成功后，删除文档元数据。
     */
    @Transactional
    public void finishDeletion(UUID documentId) {

        boolean deleted = documentRepository.deleteMetadata(documentId);

        if (!deleted
                && documentRepository.findById(documentId).isPresent()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "删除文档元数据失败，请重试删除操作"
            );
        }
    }

    /**
     * 重建索引前，在事务中重置任务状态并清理旧切片。
     */
    @Transactional
    public DocumentRecord prepareRebuild(UUID documentId) {

        DocumentRecord document = documentRepository
                .findById(documentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_PARAMETER,
                        "文档不存在"
                ));

        DocumentIndexingStatusResponse taskStatus =
                documentRepository.findIndexingStatus(documentId)
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.BUSINESS_ERROR,
                                "无法查询文档索引状态"
                        ));

        if (document.status() == DocumentStatus.DELETING) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "文档正在删除，不能重建索引"
            );
        }

        if (document.status() == DocumentStatus.PROCESSING
                || taskStatus.taskStatus() == IndexingTaskStatus.QUEUED
                || taskStatus.taskStatus() == IndexingTaskStatus.RUNNING) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "文档正在索引，不能重复重建"
            );
        }

        boolean prepared =
                documentRepository.prepareForRebuild(documentId);

        if (!prepared) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "文档状态已变化，无法重建索引"
            );
        }

        // 旧切片及向量先清理，再提交新任务。
        chunkRepository.deleteByDocumentId(documentId);

        return document;
    }
}