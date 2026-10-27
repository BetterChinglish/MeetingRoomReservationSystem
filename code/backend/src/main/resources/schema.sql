SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 部门表 (department)
CREATE TABLE IF NOT EXISTS `department` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '部门唯一主键',
  `name` VARCHAR(50) NOT NULL COMMENT '部门名称（如研发部、行政部）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `deleted_at` DATETIME NULL DEFAULT NULL COMMENT '软删除时间戳（NULL表示正常活跃）',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_name` (`name`, `deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门主数据表';

-- 2. 用户表 (user)
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户唯一主键',
  `username` VARCHAR(50) NOT NULL COMMENT '登录账号（强制小写存储，唯一）',
  `password` VARCHAR(100) NOT NULL COMMENT 'BCrypt 加盐哈希密文（禁止明文）',
  `name` VARCHAR(64) NOT NULL COMMENT '用户真实姓名 / 显示名',
  `dept_id` BIGINT NOT NULL COMMENT '所属部门ID',
  `role` VARCHAR(20) NOT NULL DEFAULT 'EMPLOYEE' COMMENT '系统角色：EMPLOYEE(普通员工), ADMIN(管理员)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  KEY `idx_user_dept` (`dept_id`),
  CONSTRAINT `fk_user_dept` FOREIGN KEY (`dept_id`) REFERENCES `department` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 3. 会议室表 (meeting_room)
CREATE TABLE IF NOT EXISTS `meeting_room` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会议室唯一主键',
  `name` VARCHAR(50) NOT NULL COMMENT '会议室名称（如第一会议室）',
  `location` VARCHAR(100) NOT NULL COMMENT '物理位置（如 A座 3F）',
  `capacity` INT NOT NULL COMMENT '容纳人数上限（人）',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '可用状态：ENABLED(启用), DISABLED(停用)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `deleted_at` DATETIME NULL DEFAULT NULL COMMENT '软删除时间戳（NULL表示正常活跃）',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_room_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议室主数据表';

-- 4. 预约记录表 (reservation)
CREATE TABLE IF NOT EXISTS `reservation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '预约记录唯一主键',
  `room_id` BIGINT NOT NULL COMMENT '会议室ID',
  `user_id` BIGINT NOT NULL COMMENT '预约人ID（创建者）',
  `reserve_date` DATE NOT NULL COMMENT '预约日期（YYYY-MM-DD）',
  `start_time` TIME NOT NULL COMMENT '预约起始时间（HH:mm:ss）',
  `end_time` TIME NOT NULL COMMENT '预约结束时间（HH:mm:ss）',
  `dept_id` BIGINT NOT NULL COMMENT '预约归属部门ID',
  `title` VARCHAR(100) NOT NULL COMMENT '会议主题',
  `room_name` VARCHAR(50) NOT NULL COMMENT '会议室名称快照（防止主数据变更影响历史）',
  `dept_name` VARCHAR(50) NOT NULL COMMENT '部门名称快照',
  `user_name` VARCHAR(64) NOT NULL COMMENT '预约人姓名快照',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '存储状态：PENDING(未开始), ACTIVE(进行中), ENDED(已结束), CANCELLED(已取消)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后流转/更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_res_room_date_time` (`room_id`, `reserve_date`, `start_time`, `end_time`),
  KEY `idx_res_user` (`user_id`),
  KEY `idx_res_dept` (`dept_id`),
  KEY `idx_res_status` (`status`),
  CONSTRAINT `fk_res_room` FOREIGN KEY (`room_id`) REFERENCES `meeting_room` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_res_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_res_dept` FOREIGN KEY (`dept_id`) REFERENCES `department` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议室预约表';

-- 5.1 部门初始数据
INSERT INTO `department` (`id`, `name`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
(1, '研发部', NOW(), NOW(), NULL, 0),
(2, '行政部', NOW(), NOW(), NULL, 0),
(3, '产品部', NOW(), NOW(), NULL, 0),
(4, '市场部', NOW(), NOW(), NULL, 0),
(5, '财务部', NOW(), NOW(), NULL, 0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`);

-- 5.2 会议室初始数据
INSERT INTO `meeting_room` (`id`, `name`, `location`, `capacity`, `status`, `created_at`, `updated_at`, `deleted_at`, `version`) VALUES
(1, '第一会议室', 'A座 3F-301', 10, 'ENABLED', NOW(), NOW(), NULL, 0),
(2, '第二会议室', 'A座 3F-302', 20, 'ENABLED', NOW(), NOW(), NULL, 0),
(3, '第三会议室', 'B座 2F-205', 30, 'ENABLED', NOW(), NOW(), NULL, 0),
(4, '第四会议室', 'B座 5F-多功能厅', 50, 'ENABLED', NOW(), NOW(), NULL, 0),
(5, '第五会议室 (临时维护)', 'C座 1F-102', 15, 'DISABLED', NOW(), NOW(), NULL, 0)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `location`=VALUES(`location`), `capacity`=VALUES(`capacity`), `status`=VALUES(`status`);

-- 5.3 初始用户数据 (BCrypt哈希，默认密码统设为 123456)
INSERT INTO `user` (`id`, `username`, `password`, `name`, `dept_id`, `role`, `created_at`, `updated_at`) VALUES
(1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '李强(管理员)', 2, 'ADMIN', NOW(), NOW()),
(2, 'emp001', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '张伟(研发)', 1, 'EMPLOYEE', NOW(), NOW()),
(3, 'emp002', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '李雷(产品)', 3, 'EMPLOYEE', NOW(), NOW()),
(4, 'emp003', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '韩梅梅(市场)', 4, 'EMPLOYEE', NOW(), NOW())
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `dept_id`=VALUES(`dept_id`), `role`=VALUES(`role`);

SET FOREIGN_KEY_CHECKS = 1;
