-- Report Rep-1：report:view API 权限（侧栏「复盘→报表」挂载见 Rep-4）

INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(311, 0, 3, 'report:view', '报表查看', NULL, NULL, 90, 1, NOW(), NOW(), 0)
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

-- admin
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1147, 1, 311, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1342, 3, 311, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- supervisor
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1427, 4, 311, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
