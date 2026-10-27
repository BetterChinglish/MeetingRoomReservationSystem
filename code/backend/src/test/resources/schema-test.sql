-- 1. 部门表 (department)
CREATE TABLE IF NOT EXISTS `department` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(50) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL DEFAULT NULL,
  `version` INT NOT NULL DEFAULT 0
);

-- 2. 用户表 (user)
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `username` VARCHAR(50) NOT NULL UNIQUE,
  `password` VARCHAR(100) NOT NULL,
  `name` VARCHAR(64) NOT NULL,
  `dept_id` BIGINT NOT NULL,
  `role` VARCHAR(20) NOT NULL DEFAULT 'EMPLOYEE',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. 会议室表 (meeting_room)
CREATE TABLE IF NOT EXISTS `meeting_room` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(50) NOT NULL,
  `location` VARCHAR(100) NOT NULL,
  `capacity` INT NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL DEFAULT NULL,
  `version` INT NOT NULL DEFAULT 0
);

-- 4. 预约记录表 (reservation)
CREATE TABLE IF NOT EXISTS `reservation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `room_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `reserve_date` DATE NOT NULL,
  `start_time` TIME NOT NULL,
  `end_time` TIME NOT NULL,
  `dept_id` BIGINT NOT NULL,
  `title` VARCHAR(100) NOT NULL,
  `room_name` VARCHAR(50) NOT NULL,
  `dept_name` VARCHAR(50) NOT NULL,
  `user_name` VARCHAR(64) NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5.1 部门初始数据
INSERT INTO `department` (`id`, `name`) VALUES
(1, '研发部'),
(2, '行政部'),
(3, '产品部'),
(4, '市场部'),
(5, '财务部');

-- 5.2 会议室初始数据
INSERT INTO `meeting_room` (`id`, `name`, `location`, `capacity`, `status`) VALUES
(1, '第一会议室', 'A座 3F-301', 10, 'ENABLED'),
(2, '第二会议室', 'A座 3F-302', 20, 'ENABLED'),
(3, '第三会议室', 'B座 2F-205', 30, 'ENABLED'),
(4, '第四会议室', 'B座 5F-多功能厅', 50, 'ENABLED'),
(5, '第五会议室 (临时维护)', 'C座 1F-102', 15, 'DISABLED');

-- 5.3 初始用户数据
INSERT INTO `user` (`id`, `username`, `password`, `name`, `dept_id`, `role`) VALUES
(1, 'admin', '$2a$10$rIpStP96Lsqs4pp2cEpHM.mk/pJ4q/LsNn9sJXq7TWn6rmAw/r1w6', '李强(管理员)', 2, 'ADMIN'),
(2, 'emp001', '$2a$10$rIpStP96Lsqs4pp2cEpHM.mk/pJ4q/LsNn9sJXq7TWn6rmAw/r1w6', '张伟(研发)', 1, 'EMPLOYEE'),
(3, 'emp002', '$2a$10$rIpStP96Lsqs4pp2cEpHM.mk/pJ4q/LsNn9sJXq7TWn6rmAw/r1w6', '李雷(产品)', 3, 'EMPLOYEE'),
(4, 'emp003', '$2a$10$rIpStP96Lsqs4pp2cEpHM.mk/pJ4q/LsNn9sJXq7TWn6rmAw/r1w6', '韩梅梅(市场)', 4, 'EMPLOYEE');
