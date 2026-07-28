-- =========================
-- 系统用户
-- =========================
CREATE TABLE IF NOT EXISTS sys_user (
    id               BIGINT       NOT NULL COMMENT '主键',
    user_code        VARCHAR(64)  NOT NULL COMMENT '用户编码（登录）',
    user_name        VARCHAR(64)  NOT NULL COMMENT '姓名',
    password         VARCHAR(128) NOT NULL COMMENT '密码',
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0禁用',
    must_change_pwd  TINYINT      NOT NULL DEFAULT 0 COMMENT '1强制下次改密',
    source           VARCHAR(16)  NOT NULL DEFAULT 'local' COMMENT '账号来源 local/sso',
    external_id      VARCHAR(128)          COMMENT '外部身份ID',
    create_time      DATETIME              COMMENT '创建时间',
    update_time      DATETIME              COMMENT '更新时间',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_code (user_code),
    KEY idx_user_name (user_name),
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
-- 种子：权限树
-- perm_type: 1目录 2菜单 3按钮
-- =========================
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
-- 生产执行
(200, 0,   1, NULL,              '生产执行', NULL,              'factory',          10, 1, NOW(), NOW(), 0),
(210, 200, 2, 'dashboard:view',  '看板',     '/app/dashboard',  'layout-dashboard', 11, 1, NOW(), NOW(), 0),
(220, 200, 2, 'lot:list',        '批次',     '/app/lots',       'package',          12, 1, NOW(), NOW(), 0),
(221, 220, 3, 'lot:add',         '批次新增', NULL,              NULL,               1,  1, NOW(), NOW(), 0),
(222, 220, 3, 'lot:edit',        '批次编辑', NULL,              NULL,               2,  1, NOW(), NOW(), 0),
(223, 220, 3, 'lot:release',     '批次放行', NULL,              NULL,               3,  1, NOW(), NOW(), 0),
(230, 200, 2, 'wip:list',        '在制',     '/app/wip',        'boxes',            13, 1, NOW(), NOW(), 0),
(240, 200, 2, 'eqp:list',        '设备',     '/app/equipment',  'factory',          14, 1, NOW(), NOW(), 0),
(250, 200, 2, 'route:list',      '路线',     '/app/route',      'map',              15, 1, NOW(), NOW(), 0),
(251, 250, 3, 'route:add',       '路线新增', NULL,              NULL,               1,  1, NOW(), NOW(), 0),
(252, 250, 3, 'route:edit',      '路线编辑', NULL,              NULL,               2,  1, NOW(), NOW(), 0),
(260, 200, 2, 'hold:list',       '锁批',     '/app/hold',       'pause-circle',     16, 1, NOW(), NOW(), 0),
(261, 260, 3, 'hold:create',     '发起锁批', NULL,              NULL,               1,  1, NOW(), NOW(), 0),
(262, 260, 3, 'hold:release',    '解锁',     NULL,              NULL,               2,  1, NOW(), NOW(), 0),
(270, 200, 2, 'alarm:list',      '报警',     '/app/alarm',      'bell',             17, 1, NOW(), NOW(), 0),
(280, 200, 2, 'history:list',    '追溯',     '/app/history',    'history',          18, 1, NOW(), NOW(), 0),
(290, 200, 2, 'track:view',      '现场台',   '/track',          'lock',             19, 1, NOW(), NOW(), 0),
(291, 290, 3, 'track:track-in',  'Track In', NULL,              NULL,               1,  1, NOW(), NOW(), 0),
(292, 290, 3, 'track:track-out', 'Track Out',NULL,              NULL,               2,  1, NOW(), NOW(), 0),
(293, 290, 3, 'track:release',   '放行',     NULL,              NULL,               3,  1, NOW(), NOW(), 0),
(294, 290, 3, 'track:move',      '移站',     NULL,              NULL,               4,  1, NOW(), NOW(), 0),
-- 系统管理
(100, 0,   1, 'system',              '系统管理', NULL,                   'settings', 100, 1, NOW(), NOW(), 0),
(110, 100, 2, 'system:user',         '用户管理', '/app/auth/users',      NULL,       10,  1, NOW(), NOW(), 0),
(111, 110, 3, 'user:list',           '用户查询', NULL,                   NULL,       1,   1, NOW(), NOW(), 0),
(112, 110, 3, 'user:add',            '用户新增', NULL,                   NULL,       2,   1, NOW(), NOW(), 0),
(113, 110, 3, 'user:edit',           '用户编辑', NULL,                   NULL,       3,   1, NOW(), NOW(), 0),
(114, 110, 3, 'user:reset-pwd',      '重置密码', NULL,                   NULL,       4,   1, NOW(), NOW(), 0),
(115, 110, 3, 'user:assign-role',    '分配角色', NULL,                   NULL,       5,   1, NOW(), NOW(), 0),
(116, 110, 3, 'user:kick',           '踢人下线', NULL,                   NULL,       6,   1, NOW(), NOW(), 0),
(120, 100, 2, 'system:role',         '角色管理', '/app/auth/roles',      NULL,       20,  1, NOW(), NOW(), 0),
(121, 120, 3, 'role:list',           '角色查询', NULL,                   NULL,       1,   1, NOW(), NOW(), 0),
(122, 120, 3, 'role:add',            '角色新增', NULL,                   NULL,       2,   1, NOW(), NOW(), 0),
(123, 120, 3, 'role:edit',           '角色编辑', NULL,                   NULL,       3,   1, NOW(), NOW(), 0),
(124, 120, 3, 'role:assign-perm',    '分配权限', NULL,                   NULL,       4,   1, NOW(), NOW(), 0),
(130, 100, 2, 'system:permission',   '权限管理', '/app/auth/perms',      NULL,       30,  1, NOW(), NOW(), 0),
(131, 130, 3, 'perm:list',           '权限查询', NULL,                   NULL,       1,   1, NOW(), NOW(), 0),
(132, 130, 3, 'perm:add',            '权限新增', NULL,                   NULL,       2,   1, NOW(), NOW(), 0),
(133, 130, 3, 'perm:edit',           '权限编辑', NULL,                   NULL,       3,   1, NOW(), NOW(), 0),
(140, 100, 2, 'system:perm-apply',   '权限申请', '/app/auth/requests',   NULL,       40,  1, NOW(), NOW(), 0),
(141, 140, 3, 'perm:apply',          '发起申请', NULL,                   NULL,       1,   1, NOW(), NOW(), 0),
(150, 100, 2, 'system:perm-approve', '权限审批', '/app/auth/approvals',  NULL,       50,  1, NOW(), NOW(), 0),
(151, 150, 3, 'perm:approve',        '审批权限', NULL,                   NULL,       1,   1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  perm_code = VALUES(perm_code),
  perm_name = VALUES(perm_name),
  path = VALUES(path),
  icon = VALUES(icon),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  update_time = NOW();

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
(1021, 1, 151, NOW()),
(1100, 1, 200, NOW()),
(1101, 1, 210, NOW()),
(1102, 1, 220, NOW()),
(1116, 1, 221, NOW()),
(1117, 1, 222, NOW()),
(1118, 1, 223, NOW()),
(1103, 1, 230, NOW()),
(1104, 1, 240, NOW()),
(1105, 1, 250, NOW()),
(1114, 1, 251, NOW()),
(1115, 1, 252, NOW()),
(1106, 1, 260, NOW()),
(1107, 1, 261, NOW()),
(1108, 1, 262, NOW()),
(1109, 1, 270, NOW()),
(1110, 1, 280, NOW()),
(1111, 1, 290, NOW()),
(1112, 1, 291, NOW()),
(1113, 1, 292, NOW()),
(1119, 1, 293, NOW()),
(1120, 1, 294, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- supervisor：生产部分 + 申请审批
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(2001, 4, 140, NOW()),
(2002, 4, 141, NOW()),
(2003, 4, 150, NOW()),
(2004, 4, 151, NOW()),
(1400, 4, 200, NOW()),
(1401, 4, 210, NOW()),
(1402, 4, 230, NOW()),
(1403, 4, 260, NOW()),
(1404, 4, 261, NOW()),
(1405, 4, 262, NOW()),
(1406, 4, 270, NOW()),
(1407, 4, 100, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- operator：申请 + 现场
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(3001, 2, 140, NOW()),
(3002, 2, 141, NOW()),
(1200, 2, 200, NOW()),
(1201, 2, 210, NOW()),
(1202, 2, 230, NOW()),
(1203, 2, 290, NOW()),
(1204, 2, 291, NOW()),
(1205, 2, 292, NOW()),
(1206, 2, 293, NOW()),
(1207, 2, 294, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng：申请 + 工艺相关
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(3003, 3, 140, NOW()),
(3004, 3, 141, NOW()),
(1300, 3, 200, NOW()),
(1301, 3, 210, NOW()),
(1302, 3, 220, NOW()),
(1311, 3, 221, NOW()),
(1312, 3, 222, NOW()),
(1313, 3, 223, NOW()),
(1303, 3, 230, NOW()),
(1304, 3, 240, NOW()),
(1305, 3, 250, NOW()),
(1309, 3, 251, NOW()),
(1310, 3, 252, NOW()),
(1314, 3, 293, NOW()),
(1306, 3, 260, NOW()),
(1307, 3, 270, NOW()),
(1308, 3, 280, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- =========================
-- Route 模块表（详见 migrate_route.sql）
-- =========================
CREATE TABLE IF NOT EXISTS mes_step (
    id          BIGINT        NOT NULL COMMENT '主键',
    step_code   VARCHAR(64)   NOT NULL COMMENT '工序编码',
    step_name   VARCHAR(128)  NOT NULL COMMENT '工序名称',
    step_type   TINYINT       NOT NULL DEFAULT 1 COMMENT '1加工 2量测 3其它',
    eqp_type    VARCHAR(64)            COMMENT '设备类型预留',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    remark      VARCHAR(255)           COMMENT '备注',
    create_time DATETIME               COMMENT '创建时间',
    update_time DATETIME               COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_step_code (step_code),
    KEY idx_step_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序定义';

CREATE TABLE IF NOT EXISTS mes_route (
    id           BIGINT        NOT NULL COMMENT '主键',
    route_code   VARCHAR(64)   NOT NULL COMMENT '路线编码',
    route_name   VARCHAR(128)  NOT NULL COMMENT '路线名称',
    product_code VARCHAR(64)            COMMENT '产品编码',
    status       TINYINT       NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
    remark       VARCHAR(255)           COMMENT '备注',
    create_time  DATETIME               COMMENT '创建时间',
    update_time  DATETIME               COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_route_code (route_code),
    KEY idx_route_product (product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工艺路线';

CREATE TABLE IF NOT EXISTS mes_route_version (
    id            BIGINT       NOT NULL COMMENT '主键',
    route_id      BIGINT       NOT NULL COMMENT '路线ID',
    version_no    INT          NOT NULL COMMENT '版本号',
    status        VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft/active/archived',
    published_at  DATETIME              COMMENT '发布时间',
    published_by  BIGINT                COMMENT '发布人',
    remark        VARCHAR(255)          COMMENT '备注',
    create_time   DATETIME              COMMENT '创建时间',
    update_time   DATETIME              COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_route_ver (route_id, version_no),
    KEY idx_route_ver_status (route_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工艺路线版本';

CREATE TABLE IF NOT EXISTS mes_route_step (
    id            BIGINT   NOT NULL COMMENT '主键',
    version_id    BIGINT   NOT NULL COMMENT '版本ID',
    step_id       BIGINT   NOT NULL COMMENT '工序ID',
    sort_no       INT      NOT NULL COMMENT '顺序号',
    next_sort_no  INT               COMMENT '下一站顺序号，空结束',
    create_time   DATETIME          COMMENT '创建时间',
    update_time   DATETIME          COMMENT '更新时间',
    deleted       TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ver_sort (version_id, sort_no),
    KEY idx_ver_step (version_id, step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路线版本步骤';

INSERT INTO mes_step (id, step_code, step_name, step_type, eqp_type, status, remark, create_time, update_time, deleted) VALUES
(5001, 'PHOTO-01', '光刻',         1, NULL, 1, NULL, NOW(), NOW(), 0),
(5002, 'ETCH-01',  '刻蚀一',       1, NULL, 1, NULL, NOW(), NOW(), 0),
(5003, 'ETCH-02',  '刻蚀二',       1, NULL, 1, NULL, NOW(), NOW(), 0),
(5004, 'CMP-01',   '化学机械抛光', 1, NULL, 1, NULL, NOW(), NOW(), 0),
(5005, 'DIFF-03',  '扩散',         1, NULL, 1, NULL, NOW(), NOW(), 0),
(5006, 'METRO-01', '量测',         2, NULL, 1, NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE step_name = VALUES(step_name), update_time = NOW();

INSERT INTO mes_route (id, route_code, route_name, product_code, status, remark, create_time, update_time, deleted) VALUES
(5100, 'WAFER-N7-MAIN', '晶圆主工艺', 'N7', 1, '演示路线', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE route_name = VALUES(route_name), update_time = NOW();

INSERT INTO mes_route_version (id, route_id, version_no, status, published_at, published_by, remark, create_time, update_time, deleted) VALUES
(5101, 5100, 1, 'active', NOW(), 1, '初始发布', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE status = VALUES(status), update_time = NOW();

INSERT INTO mes_route_step (id, version_id, step_id, sort_no, next_sort_no, create_time, update_time, deleted) VALUES
(5201, 5101, 5001, 10, 20,   NOW(), NOW(), 0),
(5202, 5101, 5002, 20, 30,   NOW(), NOW(), 0),
(5203, 5101, 5003, 30, 40,   NOW(), NOW(), 0),
(5204, 5101, 5004, 40, 50,   NOW(), NOW(), 0),
(5205, 5101, 5005, 50, 60,   NOW(), NOW(), 0),
(5206, 5101, 5006, 60, NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE step_id = VALUES(step_id), next_sort_no = VALUES(next_sort_no), update_time = NOW();

-- =========================
-- Lot 模块表（详见 migrate_lot.sql）
-- =========================
CREATE TABLE IF NOT EXISTS mes_lot (
    id                BIGINT        NOT NULL COMMENT '主键',
    lot_no            VARCHAR(64)   NOT NULL COMMENT '批次号',
    product_code      VARCHAR(64)            COMMENT '产品编码',
    qty               INT           NOT NULL DEFAULT 0 COMMENT '数量',
    priority          INT           NOT NULL DEFAULT 50 COMMENT '优先级1-100，越大越急，默认50',
    customer_lot      VARCHAR(64)            COMMENT '客户Lot',
    route_id          BIGINT                 COMMENT '路线ID',
    route_version_id  BIGINT                 COMMENT '放行快照版本ID，Release后锁定',
    current_sort_no   INT                    COMMENT '当前站顺序号（快照内）',
    current_step_id   BIGINT                 COMMENT '当前工序ID',
    current_eqp_id    BIGINT                 COMMENT '当前设备ID（TrackIn后）',
    status            VARCHAR(32)   NOT NULL DEFAULT 'created' COMMENT '状态: created已创建/released已放行/wait等待加工/processing加工中/held锁批/completed已完工/scrapped已报废',
    remark            VARCHAR(512)           COMMENT '备注',
    version           INT           NOT NULL DEFAULT 0 COMMENT '乐观锁',
    create_by         BIGINT                 COMMENT '创建人',
    create_time       DATETIME               COMMENT '创建时间',
    update_by         BIGINT                 COMMENT '更新人',
    update_time       DATETIME               COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lot_no (lot_no),
    KEY idx_lot_status (status),
    KEY idx_lot_product (product_code),
    KEY idx_lot_route (route_id),
    KEY idx_lot_route_ver (route_version_id),
    KEY idx_lot_current_step (current_step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次';

CREATE TABLE IF NOT EXISTS mes_lot_no_seq (
    seq_day  CHAR(8) NOT NULL COMMENT 'yyyyMMdd',
    next_no  INT     NOT NULL COMMENT '当日已分配流水（当前最大值）',
    PRIMARY KEY (seq_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次号按日流水';

CREATE TABLE IF NOT EXISTS mes_tx_log (
    id                BIGINT        NOT NULL COMMENT '主键',
    lot_id            BIGINT        NOT NULL COMMENT '批次ID',
    lot_no            VARCHAR(64)            COMMENT '批次号冗余',
    tx_type           VARCHAR(32)   NOT NULL COMMENT '事务类型: RELEASE放行/MOVE移站/TRACK_IN开工/TRACK_OUT完工/HOLD锁批/RELEASE_HOLD解锁/SKIP跳站/REWORK返工',
    from_status       VARCHAR(32)            COMMENT '变更前状态(同mes_lot.status枚举)',
    to_status         VARCHAR(32)            COMMENT '变更后状态(同mes_lot.status枚举)',
    from_sort_no      INT                    COMMENT '变更前站序',
    to_sort_no        INT                    COMMENT '变更后站序',
    step_id           BIGINT                 COMMENT '相关工序',
    eqp_id            BIGINT                 COMMENT '相关设备',
    route_version_id  BIGINT                 COMMENT '路线版本快照',
    remark            VARCHAR(512)           COMMENT '备注',
    oper_user_id      BIGINT                 COMMENT '操作人',
    oper_user_name    VARCHAR(64)            COMMENT '操作人名称冗余',
    create_time       DATETIME      NOT NULL COMMENT '事务时间',
    PRIMARY KEY (id),
    KEY idx_tx_lot_time (lot_id, create_time),
    KEY idx_tx_type_time (tx_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Track事务履历（只追加）';

-- =========================
-- WIP 在制投影（详见 migrate_wip.sql）
-- status: wait待加工 / processing加工中 / held锁批
-- =========================
CREATE TABLE IF NOT EXISTS mes_wip_lot (
    lot_id            BIGINT        NOT NULL COMMENT '批次ID，同mes_lot.id',
    lot_no            VARCHAR(64)   NOT NULL COMMENT '批次号冗余',
    product_code      VARCHAR(64)            COMMENT '产品编码',
    qty               INT           NOT NULL DEFAULT 0 COMMENT '数量',
    priority          INT           NOT NULL DEFAULT 50 COMMENT '优先级1-100，越大越急',
    customer_lot      VARCHAR(64)            COMMENT '客户Lot',
    status            VARCHAR(32)   NOT NULL COMMENT '在制状态: wait待加工/processing加工中/held锁批',
    current_sort_no   INT                    COMMENT '当前站顺序号（快照内）',
    current_step_id   BIGINT                 COMMENT '当前工序ID',
    current_eqp_id    BIGINT                 COMMENT '当前设备ID（TrackIn后可空）',
    route_id          BIGINT                 COMMENT '路线ID',
    route_version_id  BIGINT                 COMMENT '放行快照版本ID',
    update_time       DATETIME      NOT NULL COMMENT '投影更新时间',
    PRIMARY KEY (lot_id),
    UNIQUE KEY uk_wip_lot_no (lot_no),
    KEY idx_wip_status_sort (status, current_sort_no),
    KEY idx_wip_product (product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='在制投影（WIP只读；Track同事务同步）';

INSERT INTO mes_lot (
  id, lot_no, product_code, qty, priority, customer_lot,
  route_id, route_version_id, current_sort_no, current_step_id, current_eqp_id,
  status, remark, version,
  create_by, create_time, update_by, update_time, deleted
) VALUES
(6001, 'LOT-N7-DEMO-01', 'N7', 25, 50, NULL, 5100, NULL, NULL, NULL, NULL, 'created', '未放行演示', 0, 1, NOW(), 1, NOW(), 0),
(6002, 'LOT-N7-DEMO-02', 'N7', 25, 80, 'CUST-001', 5100, 5101, 10, 5001, NULL, 'wait', '已放行演示（首站 wait）', 0, 1, NOW(), 1, NOW(), 0)
ON DUPLICATE KEY UPDATE
  product_code = VALUES(product_code),
  qty = VALUES(qty),
  status = VALUES(status),
  route_version_id = VALUES(route_version_id),
  current_sort_no = VALUES(current_sort_no),
  current_step_id = VALUES(current_step_id),
  update_time = NOW();

INSERT INTO mes_tx_log (
  id, lot_id, lot_no, tx_type,
  from_status, to_status, from_sort_no, to_sort_no,
  step_id, eqp_id, route_version_id, remark,
  oper_user_id, oper_user_name, create_time
) VALUES
(7001, 6002, 'LOT-N7-DEMO-02', 'RELEASE',
 'created', 'wait', NULL, 10,
 5001, NULL, 5101, '演示放行进首站',
 1, 'admin', NOW())
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

INSERT INTO mes_wip_lot (
  lot_id, lot_no, product_code, qty, priority, customer_lot,
  status, current_sort_no, current_step_id, current_eqp_id,
  route_id, route_version_id, update_time
) VALUES
(6002, 'LOT-N7-DEMO-02', 'N7', 25, 80, 'CUST-001',
 'wait', 10, 5001, NULL,
 5100, 5101, NOW())
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  current_sort_no = VALUES(current_sort_no),
  current_step_id = VALUES(current_step_id),
  update_time = VALUES(update_time);

-- =========================
-- Hold 模块（详见 migrate_hold.sql）
-- mes_hold.status: active生效中 / released已解锁
-- mes_hold_reason.category: quality/eng/customer/other
-- =========================
CREATE TABLE IF NOT EXISTS mes_hold_reason (
    id            BIGINT       NOT NULL COMMENT '主键',
    reason_code   VARCHAR(32)  NOT NULL COMMENT '原因编码（唯一）',
    reason_name   VARCHAR(64)  NOT NULL COMMENT '原因名称',
    category      VARCHAR(32)           COMMENT '分类: quality质量/eng工程/customer客户/other其它',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0停用',
    remark        VARCHAR(256)          COMMENT '备注',
    create_time   DATETIME              COMMENT '创建时间',
    update_time   DATETIME              COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_hold_reason_code (reason_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='锁批原因码';

INSERT INTO mes_hold_reason (
  id, reason_code, reason_name, category, status, remark, create_time, update_time, deleted
) VALUES
(8001, 'Q_PENDING',  '待检/待判',     'quality',  1, '质量等待',   NOW(), NOW(), 0),
(8002, 'Q_ABNORMAL', '量测/良率异常', 'quality',  1, '质量调查',   NOW(), NOW(), 0),
(8003, 'E_REVIEW',   '工程评审',     'eng',      1, '工程',       NOW(), NOW(), 0),
(8004, 'M_MATERIAL', '缺料/物料问题', 'other',    1, '制造',       NOW(), NOW(), 0),
(8005, 'C_REQUEST',  '客户要求',     'customer', 1, '客规',       NOW(), NOW(), 0),
(8006, 'OTHER',      '其它',         'other',    1, '须填备注',   NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  reason_name = VALUES(reason_name),
  category = VALUES(category),
  status = VALUES(status),
  remark = VALUES(remark),
  update_time = NOW();

CREATE TABLE IF NOT EXISTS mes_hold (
    id                 BIGINT       NOT NULL COMMENT '主键',
    lot_id             BIGINT       NOT NULL COMMENT '批次ID',
    lot_no             VARCHAR(64)           COMMENT '批次号冗余',
    reason_id          BIGINT       NOT NULL COMMENT '原因码ID',
    reason_code        VARCHAR(32)  NOT NULL COMMENT '原因编码冗余',
    status             VARCHAR(16)  NOT NULL COMMENT '状态: active生效中/released已解锁',
    prev_status        VARCHAR(32)           COMMENT 'Hold前Lot状态(wait/processing)',
    remark             VARCHAR(512)          COMMENT '上锁备注',
    release_remark     VARCHAR(512)          COMMENT '解锁备注',
    hold_user_id       BIGINT                COMMENT '上锁人',
    hold_user_name     VARCHAR(64)           COMMENT '上锁人名称冗余',
    hold_time          DATETIME     NOT NULL COMMENT '上锁时间',
    release_user_id    BIGINT                COMMENT '解锁人',
    release_user_name  VARCHAR(64)           COMMENT '解锁人名称冗余',
    release_time       DATETIME              COMMENT '解锁时间',
    create_time        DATETIME              COMMENT '创建时间',
    update_time        DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_hold_lot_status (lot_id, status),
    KEY idx_hold_status_time (status, hold_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='锁批记录（最小集；一期同Lot最多一条active）';

