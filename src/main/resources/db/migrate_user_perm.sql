-- 已有库升级：sys_user 扩展字段（新库直接跑 schema.sql 即可，无需执行本文件）
-- 若列已存在会报错，按需注释对应行

ALTER TABLE sys_user
    ADD COLUMN must_change_pwd TINYINT NOT NULL DEFAULT 0 COMMENT '1强制下次改密' AFTER status,
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'local' COMMENT '账号来源 local/sso' AFTER must_change_pwd,
    ADD COLUMN external_id VARCHAR(128) NULL COMMENT '外部身份ID' AFTER source,
    ADD KEY idx_external (source, external_id);

-- admin 用户绑定 admin 角色（DataInitializer 创建的用户 id 按实际替换）
-- INSERT INTO sys_user_role (id, user_id, role_id, create_time)
-- SELECT 1, id, 1, NOW() FROM sys_user WHERE username = 'admin' AND deleted = 0
-- ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
