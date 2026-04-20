ALTER TABLE `operation_log`
    ADD COLUMN IF NOT EXISTS `operator_id` bigint(20) NULL COMMENT '操作人ID',
    ADD COLUMN IF NOT EXISTS `operator_name` varchar(50) NULL COMMENT '操作人姓名',
    ADD COLUMN IF NOT EXISTS `ip_address` varchar(50) NULL COMMENT '操作IP地址';

ALTER TABLE `operation_log`
    DROP COLUMN IF EXISTS `target_type`;
