package com.yu.ai.rag.document;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;

/**
 * 文档上传校验器
 */
@Component
public class DocumentUploadValidator {

    private static final long MAX_FILE_SIZE =
            10L * 1024 * 1024;

    /**
     * 支持的扩展名及对应 MIME 类型
     */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            ".txt", "text/plain",
            ".pdf", "application/pdf",
            ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    /**
     * 校验上传文件
     */
    public void validate(MultipartFile file) {

        // 1. 校验文件是否存在
        if (file == null) {
            throw new DocumentValidationException("请选择要上传的文件");
        }

        // 2. 校验空文件
        if (file.isEmpty()) {
            throw new DocumentValidationException("上传文件不能为空");
        }

        // 3. 校验文件名
        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null
                || originalFilename.isBlank()) {
            throw new DocumentValidationException("文件名不能为空");
        }

        String filename = originalFilename.trim();

        // 不允许客户端通过文件名指定服务器路径
        if (filename.contains("/")
                || filename.contains("\\")
                || filename.contains(":")
                || filename.indexOf('\0') >= 0
                || filename.equals(".")
                || filename.equals("..")) {
            throw new DocumentValidationException(
                    "文件名不能包含路径信息"
            );
        }

        // 数据库 original_filename 字段最大长度为 255
        if (filename.length() > 255) {
            throw new DocumentValidationException(
                    "文件名长度不能超过 255 个字符"
            );
        }

        // 4. 校验文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new DocumentValidationException(
                    "上传文件不能超过 10MB"
            );
        }

        // 5. 校验文件扩展名
        int dotIndex = filename.lastIndexOf('.');

        if (dotIndex <= 0 || dotIndex == filename.length() - 1) {
            throw new DocumentValidationException(
                    "文件必须具有合法扩展名"
            );
        }

        String extension = filename
                .substring(dotIndex)
                .toLowerCase(Locale.ROOT);

        String expectedMimeType = ALLOWED_TYPES.get(extension);

        if (expectedMimeType == null) {
            throw new DocumentValidationException(
                    "不支持该文件类型，仅支持 TXT、PDF、DOCX"
            );
        }

        // 6. 校验 MIME 类型
        String contentType = file.getContentType();

        if (contentType == null || contentType.isBlank()) {
            throw new DocumentValidationException(
                    "无法识别文件 MIME 类型"
            );
        }

        // 兼容带参数的 Content-Type，例如 text/plain;charset=UTF-8
        String actualMimeType = contentType
                .split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!expectedMimeType.equals(actualMimeType)) {
            throw new DocumentValidationException(
                    "文件扩展名与 MIME 类型不匹配"
            );
        }
    }
}