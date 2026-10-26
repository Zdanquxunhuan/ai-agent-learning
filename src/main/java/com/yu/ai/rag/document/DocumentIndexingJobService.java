package com.yu.ai.rag.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DocumentIndexingJobService {

    private static final Logger log =
            LoggerFactory.getLogger(DocumentIndexingJobService.class);

    private final DocumentRepository documentRepository;
    private final DocumentIndexingService documentIndexingService;
    private final TaskExecutor taskExecutor;

    public DocumentIndexingJobService(
            DocumentRepository documentRepository,
            DocumentIndexingService documentIndexingService,
            @Qualifier("indexingTaskExecutor")
            TaskExecutor taskExecutor
    ) {
        this.documentRepository = documentRepository;
        this.documentIndexingService = documentIndexingService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 提交文档索引任务。
     *
     * queueIndexing 使用数据库条件更新保证幂等。
     */
    public void submit(
            UUID documentId,
            String originalFilename,
            String contentType,
            byte[] content
    ) {
        boolean queued = documentRepository.queueIndexing(documentId);

        if (!queued) {
            log.info(
                    "Indexing task skipped; document is not eligible "
                            + "or already has an active/completed task. "
                            + "documentId={}",
                    documentId
            );
            return;
        }

        try {
            taskExecutor.execute(() -> execute(
                    documentId,
                    originalFilename,
                    contentType,
                    content
            ));
        } catch (TaskRejectedException exception) {
            // 线程池或排队队列已满。
            documentRepository.markTaskFailed(
                    documentId,
                    "索引任务队列已满，任务未被执行，请稍后重试"
            );

            log.warn(
                    "Indexing task rejected; documentId={}",
                    documentId
            );
        }
    }

    private void execute(
            UUID documentId,
            String originalFilename,
            String contentType,
            byte[] content
    ) {
        // 只有 QUEUED 任务可以进入 RUNNING。
        boolean started = documentRepository.markTaskRunning(documentId);

        if (!started) {
            log.info(
                    "Indexing task did not start; documentId={}",
                    documentId
            );
            return;
        }

        try {
            documentIndexingService.index(
                    documentId,
                    originalFilename,
                    contentType,
                    content
            );
        } catch (RuntimeException exception) {
            try {
                documentRepository.markTaskFailed(
                        documentId,
                        buildFailureReason(exception)
                );
            } catch (RuntimeException statusException) {
                exception.addSuppressed(statusException);

                log.error(
                        "Failed to persist indexing failure status; "
                                + "documentId={}, failureType={}",
                        documentId,
                        statusException.getClass().getSimpleName()
                );
            }

            log.warn(
                    "Document indexing failed; documentId={}, failureType={}",
                    documentId,
                    exception.getClass().getSimpleName()
            );
            return;
        }

        try {
            boolean succeeded =
                    documentRepository.markTaskSucceeded(documentId);

            if (!succeeded) {
                log.warn(
                        "Indexing completed but task status was not updated; "
                                + "documentId={}",
                        documentId
                );
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Indexing completed but task status persistence failed; "
                            + "documentId={}, failureType={}",
                    documentId,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private String buildFailureReason(RuntimeException exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return exception.getClass().getSimpleName() + ": " + message;
    }
}