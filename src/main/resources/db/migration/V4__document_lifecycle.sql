-- 1. 允许文档进入 DELETING 状态。
-- 如果原表存在旧的 status 检查约束，则替换为新约束。
DO $$
DECLARE
    constraint_row RECORD;
BEGIN
    FOR constraint_row IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'documents'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid)
              ~* '(^|[^a-z_])status([^a-z_]|$)'
    LOOP
        EXECUTE format(
            'ALTER TABLE documents DROP CONSTRAINT %I',
            constraint_row.conname
        );
    END LOOP;
END $$;

ALTER TABLE documents
    ADD CONSTRAINT chk_documents_status_day22
    CHECK (
        status IN (
            'UPLOADED',
            'PROCESSING',
            'READY',
            'FAILED',
            'DELETING'
        )
    );

-- 2. 同一文件内容只允许有一条文档元数据。
CREATE UNIQUE INDEX IF NOT EXISTS ux_documents_content_hash
    ON documents(content_hash);