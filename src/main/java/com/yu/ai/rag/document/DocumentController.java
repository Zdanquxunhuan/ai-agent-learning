package com.yu.ai.rag.document;

import com.yu.ai.rag.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentApplicationService documentApplicationService;

    public DocumentController(
            DocumentApplicationService documentApplicationService
    ) {
        this.documentApplicationService = documentApplicationService;
    }

    /**
     * 上传文档并提交异步索引任务。
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentUploadResponse>> upload(
            @RequestParam(value = "file", required = false)
            MultipartFile file
    ) {
        DocumentUploadResponse response =
                documentApplicationService.upload(file);

        // 202 表示请求已被接受，后台任务可能尚未完成。
        return ResponseEntity.accepted()
                .body(ApiResponse.success(response));
    }

    /**
     * 查询文档索引状态。
     */
    @GetMapping("/{documentId}/status")
    public ApiResponse<DocumentIndexingStatusResponse> getStatus(
            @PathVariable("documentId") UUID documentId
    ) {
        return ApiResponse.success(
                documentApplicationService.getIndexingStatus(documentId)
        );
    }

    /**
     * 重新触发失败的索引任务。
     */
    @PostMapping("/{documentId}/retry")
    public ApiResponse<DocumentIndexingStatusResponse> retry(
            @PathVariable("documentId") UUID documentId
    ) {
        return ApiResponse.success(
                documentApplicationService.retryIndexing(documentId)
        );
    }
}