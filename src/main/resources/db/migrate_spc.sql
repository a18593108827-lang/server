-- SPC-1：EDC 序列查询索引。已有库执行。
SET @db := DATABASE();
SET @exists := (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_edc_collection' AND INDEX_NAME = 'idx_edc_col_step_time'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_edc_collection ADD KEY idx_edc_col_step_time (step_id, collected_at)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
