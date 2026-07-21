-- =========================
-- 系统用户
-- =========================
CREATE TABLE IF NOT EXISTS sys_user (
    id               BIGINT       NOT NULL COMMENT '主键',
    username         VARCHAR(64)  NOT NULL COMMENT '工号',
    password         VARCHAR(128) NOT NULL COMMENT '密码',
    nickname         VARCHAR(64)           COMMENT '昵称',
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0禁用',
    must_change_pwd  TINYINT      NOT NULL DEFAULT 0 COMMENT '1强制下次改密',
    source           VARCHAR(16)  NOT NULL DEFAULT 'local' COMMENT '账号来源 local/sso',
    external_id      VARCHAR(128)          COMMENT '外部身份ID',
    create_time      DATETIME              COMMENT '创建时间',
    update_time      DATETIME              COMMENT '更新时间',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_external (source, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

-- =========================
-- 操作审计日志
-- =========================
CREATE TABLE IF NOT EXISTS sys_oper_log (
    id             BIGINT        NOT NULL COMMENT '主键',
    user_id        BIGINT                 COMMENT '操作人ID',
    username       VARCHAR(64)            COMMENT '操作人工号',
    module         VARCHAR(64)            COMMENT '模块',
    action         VARCHAR(64)            COMMENT '动作',
    lot_no         VARCHAR(64)            COMMENT '批次号',
    request_uri    VARCHAR(255)           COMMENT '请求URI',
    request_method VARCHAR(16)            COMMENT '请求方法',
    request_param  TEXT                   COMMENT '请求参数摘要',
    status         TINYINT       NOT NULL DEFAULT 1 COMMENT '结果 1成功 0失败',
    error_msg      VARCHAR(512)           COMMENT '错误信息',
    ip             VARCHAR(64)            COMMENT 'IP',
    cost_time      BIGINT                 COMMENT '耗时ms',
    create_time    DATETIME               COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_create_time (create_time),
    KEY idx_username (username),
    KEY idx_lot_no (lot_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';

-- =========================
-- 角色
-- =========================
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT       NOT NULL COMMENT '主键',
    role_code   VARCHAR(64)  NOT NULL COMMENT '角色编码',
    role_name   VARCHAR(64)  NOT NULL COMMENT '角色名称',
    remark      VARCHAR(255)          COMMENT '备注',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    create_time DATETIME              COMMENT '创建时间',
    update_time DATETIME              COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

-- =========================
-- 权限/菜单
-- =========================
CREATE TABLE IF NOT EXISTS sys_permission (
    id          BIGINT       NOT NULL COMMENT '主键',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父ID',
    perm_type   TINYINT      NOT NULL COMMENT '1目录 2菜单 3按钮',
    perm_code   VARCHAR(128)          COMMENT '权限码',
    perm_name   VARCHAR(64)  NOT NULL COMMENT '名称',
    path        VARCHAR(255)          COMMENT '路由',
    icon        VARCHAR(64)           COMMENT '图标',
    sort_no     INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    create_time DATETIME              COMMENT '创建时间',
    update_time DATETIME              COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_parent (parent_id),
    UNIQUE KEY uk_perm_code (perm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限/菜单';

-- =========================
-- 用户角色
-- =========================
CREATE TABLE IF NOT EXISTS sys_user_role (
    id          BIGINT   NOT NULL COMMENT '主键',
    user_id     BIGINT   NOT NULL COMMENT '用户ID',
    role_id     BIGINT   NOT NULL COMMENT '角色ID',
    create_time DATETIME          COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色';

-- =========================
-- 角色权限
-- =========================
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id            BIGINT   NOT NULL COMMENT '主键',
    role_id       BIGINT   NOT NULL COMMENT '角色ID',
    permission_id BIGINT   NOT NULL COMMENT '权限ID',
    create_time   DATETIME          COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_id, permission_id),
    KEY idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限';

-- =========================
-- 权限申请单
-- =========================
CREATE TABLE IF NOT EXISTS sys_perm_request (
    id              BIGINT       NOT NULL COMMENT '主键',
    request_no      VARCHAR(32)  NOT NULL COMMENT '申请单号',
    applicant_id    BIGINT       NOT NULL COMMENT '申请人',
    role_id         BIGINT       NOT NULL COMMENT '目标角色',
    reason          VARCHAR(512) NOT NULL COMMENT '申请原因',
    status          VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected/cancelled',
    approver_id     BIGINT                COMMENT '审批人',
    approve_opinion VARCHAR(512)          COMMENT '审批意见',
    approve_time    DATETIME              COMMENT '审批时间',
    oa_instance_id  VARCHAR(64)           COMMENT '预留OA实例ID',
    create_time     DATETIME              COMMENT '创建时间',
    update_time     DATETIME              COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_no (request_no),
    KEY idx_applicant (applicant_id),
    KEY idx_status (status),
    KEY idx_applicant_role_status (applicant_id, role_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限申请单';

-- =========================
-- 权限申请流转日志
-- =========================
CREATE TABLE IF NOT EXISTS sys_perm_request_log (
    id           BIGINT       NOT NULL COMMENT '主键',
    request_id   BIGINT       NOT NULL COMMENT '申请单ID',
    from_status  VARCHAR(16)           COMMENT '原状态',
    to_status    VARCHAR(16)  NOT NULL COMMENT '新状态',
    operator_id  BIGINT       NOT NULL COMMENT '操作人',
    opinion      VARCHAR(512)          COMMENT '意见',
    create_time  DATETIME              COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限申请流转日志';

-- =========================
-- 种子：角色
-- =========================
INSERT INTO sys_role (id, role_code, role_name, remark, status, create_time, update_time, deleted) VALUES
(1, 'admin',       '超级管理员', '全部权限', 1, NOW(), NOW(), 0),
(2, 'operator',    '现场操作员', '现场 Track 操作', 1, NOW(), NOW(), 0),
(3, 'process_eng', '工艺工程师', 'Route/Recipe 配置', 1, NOW(), NOW(), 0),
(4, 'supervisor',  '班组长',     '看板与权限审批', 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- =========================
-- 种子：权限树（系统管理）
-- perm_type: 1目录 2菜单 3按钮
-- =========================
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(100, 0,   1, 'system',              '系统管理', NULL,                   'settings', 100, 1, NOW(), NOW(), 0),
(110, 100, 2, 'system:user',         '用户管理', '/system/user',         NULL,       10,  1, NOW(), NOW(), 0),
(111, 110, 3, 'user:list',           '用户查询', NULL,                   NULL,       1,   1, NOW(), NOW(), 0),
(112, 110, 3, 'user:add',            '用户新增', NULL,                   NULL,       2,   1, NOW(), NOW(), 0),
(113, 110, 3, 'user:edit',           '用户编辑', NULL,                   NULL,       3,   1, NOW(), NOW(), 0),
(114, 110, 3, 'user:reset-pwd',      '重置密码', NULL,                   NULL,       4,   1, NOW(), NOW(), 0),
(115, 110, 3, 'user:assign-role',    '分配角色', NULL,                   NULL,       5,   1, NOW(), NOW(), 0),
(116, 110, 3, 'user:kick',           '踢人下线', NULL,                   NULL,       6,   1, NOW(), NOW(), 0),
(120, 100, 2, 'system:role',         '角色管理', '/system/role',         NULL,       20,  1, NOW(), NOW(), 0),
(121, 120, 3, 'role:list',           '角色查询', NULL,                   NULL,       1,   1, NOW(), NOW(), 0),
(122, 120, 3, 'role:add',            '角色新增', NULL,                   NULL,       2,   1, NOW(), NOW(), 0),
(123, 120, 3, 'role:edit',           '角色编辑', NULL,                   NULL,       3,   1, NOW(), NOW(), 0),
(124, 120, 3, 'role:assign-perm',    '分配权限', NULL,                   NULL,       4,   1, NOW(), NOW(), 0),
(130, 100, 2, 'system:permission',   '权限管理', '/system/permission',   NULL,       30,  1, NOW(), NOW(), 0),
(131, 130, 3, 'perm:list',           '权限查询', NULL,                   NULL,       1,   1, NOW(), NOW(), 0),
(132, 130, 3, 'perm:add',            '权限新增', NULL,                   NULL,       2,   1, NOW(), NOW(), 0),
(133, 130, 3, 'perm:edit',           '权限编辑', NULL,                   NULL,       3,   1, NOW(), NOW(), 0),
(140, 100, 2, 'system:perm-apply',   '权限申请', '/system/perm-apply',   NULL,       40,  1, NOW(), NOW(), 0),
(141, 140, 3, 'perm:apply',          '发起申请', NULL,                   NULL,       1,   1, NOW(), NOW(), 0),
(150, 100, 2, 'system:perm-approve', '权限审批', '/system/perm-approve', NULL,       50,  1, NOW(), NOW(), 0),
(151, 150, 3, 'perm:approve',        '审批权限', NULL,                   NULL,       1,   1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE perm_name = VALUES(perm_name);

-- =========================
-- 种子：admin 绑定全部权限
-- =========================
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1001, 1, 100, NOW()),
(1002, 1, 110, NOW()),
(1003, 1, 111, NOW()),
(1004, 1, 112, NOW()),
(1005, 1, 113, NOW()),
(1006, 1, 114, NOW()),
(1007, 1, 115, NOW()),
(1008, 1, 116, NOW()),
(1009, 1, 120, NOW()),
(1010, 1, 121, NOW()),
(1011, 1, 122, NOW()),
(1012, 1, 123, NOW()),
(1013, 1, 124, NOW()),
(1014, 1, 130, NOW()),
(1015, 1, 131, NOW()),
(1016, 1, 132, NOW()),
(1017, 1, 133, NOW()),
(1018, 1, 140, NOW()),
(1019, 1, 141, NOW()),
(1020, 1, 150, NOW()),
(1021, 1, 151, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- supervisor：申请 + 审批
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(2001, 4, 140, NOW()),
(2002, 4, 141, NOW()),
(2003, 4, 150, NOW()),
(2004, 4, 151, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- operator / process_eng：可发起申请
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(3001, 2, 140, NOW()),
(3002, 2, 141, NOW()),
(3003, 3, 140, NOW()),
(3004, 3, 141, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
