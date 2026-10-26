package com.yu.ai.rag.document;

import com.yu.ai.rag.common.BusinessException;
import com.yu.ai.rag.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentLifecycleService {

    private final DocumentRepository documentRepository;
    private final DocumentLifecycleTransactionService transactionService;
    private final DocumentIndexingJobService indexingJobService;
    private final Path uploadDirectory;

    public DocumentLifecycleService(
            DocumentRepository documentRepository,
            DocumentLifecycleTransactionService transactionService,
            DocumentIndexingJobService indexingJobService,
            @Value("${rag.storage.upload-dir:./data/uploads}")
            String uploadDir
    ) {
        this.documentRepository = documentRepository;
        this.transactionService = transactionService;
        this.indexingJobService = indexingJobService;

        this.uploadDirectory = Path.of(uploadDir)
                .toAbsolutePath()
                .normalize();
    }

    /**
     * 删除文档原文件、切片、向量和元数据。
     */
    public void delete(UUID documentId) {

        // 事务提交后，文档已标记为 DELETING，切片已清理。
        DocumentRecord document =
                transactionService.beginDeletion(documentId);

        if (document == null) {
            // 重复 DELETE 请求按幂等方式处理。
            return;
        }

        Path target = resolveStoredFile(documentId);

        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            // 元数据保留为 DELETING，后续可重试。
            throw new DocumentStorageException(
                    "原文件删除失败，文档已进入 DELETING 状态，请重试删除",
                    exception
            );
        }

        // 只有文件清理成功后才删除元数据。
        transactionService.finishDeletion(documentId);
    }

    /**
     * 清理旧索引后重新提交异步索引任务。
     */
    public DocumentIndexingStatusResponse rebuild(UUID documentId) {

        DocumentRecord existing = documentRepository
                .findById(documentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_PARAMETER,
                        "文档不存在"
                ));

        if (existing.status() == DocumentStatus.DELETING) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "文档正在删除，不能重建索引"
            );
        }

        // 先读取原文件，避免原文件不存在时先清理旧索引。
        byte[] content = readOriginalFile(documentId);

        // 数据库事务：重置状态并清理旧切片和向量。
        DocumentRecord document =
                transactionService.prepareRebuild(documentId);

        // 重新提交到 Day 21 的受控线程池。
        indexingJobService.submit(
                documentId,
                document.originalFilename(),
                inferContentType(document.originalFilename()),
                content
        );

        return documentRepository.findIndexingStatus(documentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "重建任务已提交，但无法查询任务状态"
                ));
    }

    private byte[] readOriginalFile(UUID documentId) {

        Path target = resolveStoredFile(documentId);

        if (!Files.isRegularFile(target)) {
            throw new DocumentStorageException("原始文档文件不存在");
        }

        try {
            return Files.readAllBytes(target);
        } catch (IOException exception) {
            throw new DocumentStorageException(
                    "读取原始文档失败",
                    exception
            );
        }
    }

    private Path resolveStoredFile(UUID documentId) {

        Path target = uploadDirectory
                .resolve(documentId.toString())
                .normalize();

        if (!target.startsWith(uploadDirectory)) {
            throw new DocumentStorageException("非法文件存储路径");
        }

        return target;
    }

    private String inferContentType(String filename) {

        String normalized = filename.toLowerCase(Locale.ROOT);

        if (normalized.endsWith(".txt")) {
            return "text/plain";
        }

        if (normalized.endsWith(".pdf")) {
            return "application/pdf";
        }

        if (normalized.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }

        return null;
    }
}