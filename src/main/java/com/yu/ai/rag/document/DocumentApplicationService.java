package com.yu.ai.rag.document;

import com.yu.ai.rag.common.BusinessException;
import com.yu.ai.rag.common.ErrorCode;
import com.yu.ai.rag.security.CurrentUserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import com.yu.ai.rag.common.BusinessException;
import com.yu.ai.rag.common.ErrorCode;
import org.springframework.dao.DuplicateKeyException;

@Service
public class DocumentApplicationService {

    private final DocumentRepository documentRepository;
    private final DocumentUploadValidator documentUploadValidator;
    private final DocumentIndexingJobService documentIndexingJobService;
    private final Path uploadDirectory;
    private final CurrentUserContext currentUserContext;

    public DocumentApplicationService(
            DocumentRepository documentRepository,
            DocumentUploadValidator documentUploadValidator,
            DocumentIndexingJobService documentIndexingJobService,
            @Value("${rag.storage.upload-dir:./data/uploads}")
            String uploadDir,
            CurrentUserContext currentUserContext
    ) {
        this.documentRepository = documentRepository;
        this.documentUploadValidator = documentUploadValidator;
        this.documentIndexingJobService = documentIndexingJobService;

        this.uploadDirectory = Path.of(uploadDir)
                .toAbsolutePath()
                .normalize();
        this.currentUserContext = currentUserContext;
    }

    /**
     * 上传文档，保存文件和元数据后提交异步索引任务。
     */
    public DocumentUploadResponse upload(MultipartFile file) {
        String ownerUserId = currentUserContext.current().userId();
        // 1. 校验上传文件。
        documentUploadValidator.validate(file);

        String originalFilename = file.getOriginalFilename().trim();

        // 2. 读取上传内容。
        byte[] content;

        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new DocumentStorageException(
                    "读取上传文件失败",
                    exception
            );
        }

        // 3. 计算文件 SHA-256。
        String contentHash = calculateSha256(content);

//        DocumentRecord existing = documentRepository
//                .findByContentHash(contentHash, ownerUserId)
//                .orElse(null);

        // 相同内容已存在：返回原文档，不保存新文件、不重复提交索引任务。
        DocumentRecord existing =
                documentRepository.findByContentHash(contentHash)
                        .orElse(null);

        if (existing != null) {
            return buildDuplicateResponse(
                    existing,
                    content.length,
                    file.getContentType()
            );
        }

        // 4. 生成文档 ID。
        UUID documentId = UUID.randomUUID();

        // 5. 使用 UUID 构建安全的存储路径。
        Path target = uploadDirectory
                .resolve(documentId.toString())
                .normalize();

        if (!target.startsWith(uploadDirectory)) {
            throw new DocumentStorageException("非法文件存储路径");
        }

        // 6. 保存原始文件。
        try {
            Files.createDirectories(uploadDirectory);

            Files.write(
                    target,
                    content,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            throw new DocumentStorageException(
                    "保存原始文件失败",
                    exception
            );
        }

        // 7. 保存元数据，初始文档状态为 UPLOADED。
        DocumentRecord document = new DocumentRecord(
                documentId,
                originalFilename,
                contentHash,
                DocumentStatus.UPLOADED
        );

        try {
            documentRepository.save(document, ownerUserId);

        } catch (DuplicateKeyException exception) {

            // 另一个并发请求可能已经保存了相同内容。
            cleanupFailedUpload(target, exception);

            DocumentRecord duplicate =
                    documentRepository.findByContentHash(contentHash)
                            .orElse(null);

            if (duplicate != null) {
                return buildDuplicateResponse(
                        duplicate,
                        content.length,
                        file.getContentType()
                );
            }

            throw exception;

        } catch (RuntimeException exception) {

            // 元数据保存失败，清理未登记的原文件。
            cleanupFailedUpload(target, exception);
            throw exception;
        }

        // 8. 只提交任务，不在当前请求中等待索引完成。
        documentIndexingJobService.submit(
                documentId,
                originalFilename,
                file.getContentType(),
                content
        );

        // 9. 返回提交后的当前文档状态。
        DocumentStatus currentStatus = documentRepository
                .findById(documentId)
                .map(DocumentRecord::status)
                .orElse(DocumentStatus.UPLOADED);

        return new DocumentUploadResponse(
                documentId,
                originalFilename,
                content.length,
                file.getContentType(),
                contentHash,
                currentStatus
        );
    }

    private DocumentUploadResponse buildDuplicateResponse(
            DocumentRecord existing,
            long sizeBytes,
            String contentType
    ) {
        if (existing.status() == DocumentStatus.DELETING) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "相同内容的文档正在删除，请稍后重试"
            );
        }

        return new DocumentUploadResponse(
                existing.id(),
                existing.originalFilename(),
                sizeBytes,
                contentType,
                existing.contentHash(),
                existing.status()
        );
    }

    private void cleanupFailedUpload(
            Path target,
            RuntimeException originalException
    ) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException cleanupException) {
            originalException.addSuppressed(cleanupException);

            throw new DocumentStorageException(
                    "清理未登记的上传文件失败",
                    originalException
            );
        }
    }

    /**
     * 查询索引任务状态。
     */
    public DocumentIndexingStatusResponse getIndexingStatus(
            UUID documentId
    ) {
        return documentRepository.findIndexingStatus(documentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_PARAMETER,
                        "文档不存在"
                ));
    }

    /**
     * 重新提交失败任务。
     *
     * 已排队、执行中或成功的任务不会重复提交。
     */
    public DocumentIndexingStatusResponse retryIndexing(
            UUID documentId
    ) {
        DocumentRecord document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_PARAMETER,
                        "文档不存在"
                ));

        DocumentIndexingStatusResponse current =
                getIndexingStatus(documentId);

        if (current.documentStatus() == DocumentStatus.READY
                || current.documentStatus() == DocumentStatus.PROCESSING
                || current.taskStatus() == IndexingTaskStatus.QUEUED
                || current.taskStatus() == IndexingTaskStatus.RUNNING
                || current.taskStatus() == IndexingTaskStatus.SUCCEEDED) {
            return current;
        }

        // 读取之前保存的原文件。
        byte[] content = readOriginalFile(documentId);

        // 上传时的 MIME 类型没有单独持久化，因此根据已校验的扩展名恢复。
        String contentType = inferContentType(document.originalFilename());

        documentIndexingJobService.submit(
                documentId,
                document.originalFilename(),
                contentType,
                content
        );

        return getIndexingStatus(documentId);
    }

    private byte[] readOriginalFile(UUID documentId) {
        Path target = uploadDirectory
                .resolve(documentId.toString())
                .normalize();

        if (!target.startsWith(uploadDirectory)
                || !Files.isRegularFile(target)) {
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

    private String calculateSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前 Java 环境不支持 SHA-256",
                    exception
            );
        }
    }

    /**
     * 保留原有元数据保存方法。
     */
    public void saveMetadata(DocumentRecord document, String ownerUserId) {
        documentRepository.save(document,ownerUserId);
    }
}