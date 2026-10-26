-- 1. 增加文档所有者。
ALTER TABLE documents
    ADD COLUMN owner_user_id VARCHAR(100);

-- 2. 将历史文档统一分配给 user-a。
-- 这样已有测试数据不会因新增权限而全部不可访问。
UPDATE documents
SET owner_user_id = 'user-a'
WHERE owner_user_id IS NULL;

-- 3. 新文档必须有明确的所有者。
ALTER TABLE documents
    ALTER COLUMN owner_user_id SET NOT NULL;

-- 4. 将 Day 22 的全局内容哈希唯一索引，
-- 改为每个用户内部去重。
DROP INDEX IF EXISTS ux_documents_content_hash;

CREATE UNIQUE INDEX ux_documents_owner_content_hash
    ON documents (owner_user_id, content_hash)
    WHERE content_hash IS NOT NULL;

-- 5. 优化按用户查询文档状态。
CREATE INDEX idx_documents_owner_status
    ON documents (owner_user_id, status);