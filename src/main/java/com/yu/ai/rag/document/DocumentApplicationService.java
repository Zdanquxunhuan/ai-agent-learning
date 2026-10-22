package com.yu.ai.rag.document;

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
import java.util.UUID;

/**
 * 文档应用服务
 */
@Service
public class DocumentApplicationService {

    private final DocumentRepository documentRepository;

    private final DocumentUploadValidator documentUploadValidator;

    private final Path uploadDirectory;

    public DocumentApplicationService(
            DocumentRepository documentRepository,
            DocumentUploadValidator documentUploadValidator,
            @Value("${rag.storage.upload-dir:./data/uploads}")
            String uploadDir) {

        this.documentRepository = documentRepository;
        this.documentUploadValidator = documentUploadValidator;

        this.uploadDirectory = Path.of(uploadDir)
                .toAbsolutePath()
                .normalize();
    }

    /**
     * 上传文档，保存原文件和数据库元数据
     */
    public DocumentUploadResponse upload(MultipartFile file) {

        // 1. 校验文件
        documentUploadValidator.validate(file);

        String originalFilename = file.getOriginalFilename().trim();

        // 2. 读取文件内容
        byte[] content;

        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new DocumentStorageException(
                    "读取上传文件失败", e);
        }

        // 3. 计算 SHA-256
        String contentHash = calculateSha256(content);

        // 4. 生成文档唯一 ID
        UUID documentId = UUID.randomUUID();

        // 5. 根据 UUID 生成存储路径，不使用用户文件名
        Path target = uploadDirectory
                .resolve(documentId.toString())
                .normalize();

        if (!target.startsWith(uploadDirectory)) {
            throw new DocumentStorageException("非法文件存储路径");
        }

        // 6. 创建目录并保存原始文件
        try {
            Files.createDirectories(uploadDirectory);

            // CREATE_NEW 防止意外覆盖已有文件
            Files.write(
                    target,
                    content,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );

        } catch (IOException e) {
            throw new DocumentStorageException(
                    "保存原始文件失败", e);
        }

        // 7. 构造文档元数据
        DocumentRecord document = new DocumentRecord(
                documentId,
                originalFilename,
                contentHash,
                DocumentStatus.UPLOADED
        );

        // 8. 保存数据库；失败时清理刚保存的原文件
        try {
            documentRepository.save(document);
        } catch (RuntimeException e) {

            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupException) {
                e.addSuppressed(cleanupException);
            }

            throw e;
        }

        // 9. 返回上传结果
        return new DocumentUploadResponse(
                documentId,
                originalFilename,
                content.length,
                file.getContentType(),
                contentHash,
                DocumentStatus.UPLOADED
        );
    }

    /**
     * 计算文件 SHA-256 哈希值
     */
    private String calculateSha256(byte[] content) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(content);

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "当前 Java 环境不支持 SHA-256", e);
        }
    }

    /**
     * 保存文档元数据
     */
    public void saveMetadata(DocumentRecord document) {
        documentRepository.save(document);
    }
}