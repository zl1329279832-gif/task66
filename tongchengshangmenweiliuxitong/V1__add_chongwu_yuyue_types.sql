-- =============================================================
-- 宠物预约表新增「预约进度类型」字段
-- 执行前请先备份数据库
-- =============================================================

-- 1. 新增字段
ALTER TABLE `chongwu_yuyue`
    ADD COLUMN `chongwu_yuyue_types` INT(11) DEFAULT 1
    COMMENT '预约进度类型: 1=待审核 2=已通过/待进行 3=进行中 4=已完成'
    AFTER `chongwu_yuyue_shenhe_time`;

-- 2. 回填历史数据：
--    审核通过(yesno=2)  → types=2 (已通过/待进行)
--    审核拒绝(yesno=3)  → types 保持 NULL 或 1（不再进入流程）
--    待审核(yesno=1)    → types=1 (待审核)
UPDATE `chongwu_yuyue` SET `chongwu_yuyue_types` = 1 WHERE `chongwu_yuyue_yesno_types` = 1;
UPDATE `chongwu_yuyue` SET `chongwu_yuyue_types` = 2 WHERE `chongwu_yuyue_yesno_types` = 2;

-- 3. 添加索引（可选，优化冲突查询性能）
ALTER TABLE `chongwu_yuyue`
    ADD INDEX `idx_chongwu_time_types` (`chongwu_id`, `chongwu_yuyue_time`, `chongwu_yuyue_types`);
