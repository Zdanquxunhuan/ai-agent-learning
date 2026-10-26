-- status	文档生命周期：UPLOADED、PROCESSING、READY、FAILED
-- indexing_task_status	任务状态：PENDING、QUEUED、RUNNING、SUCCEEDED、FAILED

ALTER TABLE documents
    ADD COLUMN indexing_task_status VARCHAR(20)
        NOT NULL DEFAULT 'PENDING',
    ADD COLUMN indexing_attempt INTEGER
        NOT NULL DEFAULT 0,
    ADD COLUMN indexing_started_at TIMESTAMPTZ,
    ADD COLUMN indexing_finished_at TIMESTAMPTZ,
    ADD COLUMN indexing_failure_reason TEXT;


-- 将已有文档映射到对应的任务状态。
-- 迁移前仍处于 PROCESSING 的文档视为中断任务。
UPDATE documents
SET
    indexing_task_status = CASE
        WHEN status = 'READY' THEN 'SUCCEEDED'
        WHEN status IN ('FAILED', 'PROCESSING') THEN 'FAILED'
        ELSE 'PENDING'
    END,
    indexing_failure_reason = CASE
        WHEN status = 'PROCESSING'
            THEN '历史索引任务中断，请重新触发'
        ELSE NULL
    END,
    indexing_finished_at = CASE
        WHEN status = 'PROCESSING' THEN CURRENT_TIMESTAMP
        ELSE NULL
    END,
    status = CASE
        WHEN status = 'PROCESSING' THEN 'FAILED'
        ELSE status
    END;

ALTER TABLE documents
    ADD CONSTRAINT chk_documents_indexing_task_status
    CHECK (
        indexing_task_status IN (
            'PENDING',
            'QUEUED',
            'RUNNING',
            'SUCCEEDED',
            'FAILED'
        )
    );

ALTER TABLE documents
    ADD CONSTRAINT chk_documents_indexing_attempt
    CHECK (indexing_attempt >= 0);

CREATE INDEX idx_documents_indexing_task_status
    ON documents (indexing_task_status, updated_at);