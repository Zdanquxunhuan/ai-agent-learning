package com.yu.ai.rag.document;

import com.yu.ai.rag.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档接口控制器
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentApplicationService documentApplicationService;

    public DocumentController(
            DocumentApplicationService documentApplicationService) {
        this.documentApplicationService = documentApplicationService;
    }

    /**
     * 上传文档
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentUploadResponse> upload(
            @RequestParam(value = "file", required = false)
            MultipartFile file) {

        DocumentUploadResponse response =
                documentApplicationService.upload(file);

        return ApiResponse.success(response);
    }
}