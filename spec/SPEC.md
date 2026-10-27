# 会议室预约系统 — 规格契约说明书（SPEC Master）

> **文档标识**：SPEC-MASTER-01  
> **文档版本**：v1.0.0 (Release)  
> **归属环节**：AI Coding 全流程实操笔试（第 1 套）· 环节 3 — 规格编写  
> **交付物保存位置**：`D:\exam\spec\SPEC.md`  
> **配套交付物**：数据库建表脚本 [`./ddl.sql`](./ddl.sql)  
> **OpenSpec 追踪变更**：`openspec/changes/meeting-room-reservation-spec/`  
> **技术基准**：Java 17 + Spring Boot 3 / MySQL 8 / Redis 7 / Vue 3 + Element Plus + ECharts / JWT (HS256)

---

## 目录

1. [规格总览与架构契约](#1-规格总览与架构契约)
2. [数据模型与数据库约束](#2-数据模型与数据库约束)
3. [统一接口协议与错误码体系](#3-统一接口协议与错误码体系)
4. [全量 RESTful API 接口规格](#4-全量-restful-api-接口规格)
5. [关键业务算法与并发控制规格](#5-关键业务算法与并发控制规格)
6. [生命周期状态机与动态计算规格](#6-生命周期状态机与动态计算规格)
7. [权限与安全防护规格](#7-权限与安全防护规格)
8. [验收断言矩阵（驱动测试验证）](#8-验收断言矩阵驱动测试验证)

---

## 1. 规格总览与架构契约

### 1.1 系统定位与核心边界
本系统为单位行政全 Web 端会议室预约管理平台，服务于普通员工与行政管理员两类角色。系统保证同一会议室同一时间段不可重复预约；提供预约的查询、取消、提前结束全生命周期流转；并提供按日期与月份双维度的数据看板。

### 1.2 架构分层契约

```
[前端层: Vue 3 + Element Plus + ECharts + Pinia]
                         │
                         ▼ HTTP/JSON (统一 ApiResponse 封装)
[接入网关 / 反向代理: Nginx] (/api/** 路由代理, 端口 80)
                         │
                         ▼
[应用服务层: Spring Boot 3 (Java 17)]
  ├── Filter: JwtAuthenticationFilter (Token校验 / Redis黑名单拦截)
  ├── Security / RBAC: @PreAuthorize / Context 身份注入
  ├── Controller: 参数校验 (@Valid) / 统一异常捕获 (GlobalExceptionHandler)
  ├── Service: 冲突判定 / 状态机流转 / 行锁控制 (@Transactional)
  └── Mapper: MyBatis-Plus / 原生 SQL
                         │
        ┌────────────────┴────────────────┐
        ▼                                 ▼
[持久层: MySQL 8 关系数据库]     [缓存协调层: Redis 7]
  ├── 表: user, department        ├── auth:lockout:{username} (暴破锁定)
  ├── 表: meeting_room            ├── auth:blacklist:{jti} (登出黑名单)
  └── 表: reservation             ├── reservation:submit_lock:{userId}:{hash} (防重)
                                  └── dashboard:stats:{dim}:{period} (看板聚合缓存)
```

---

## 2. 数据模型与数据库约束

### 2.1 实体关系模型 (ER Topology)

```
┌──────────────────┐               ┌──────────────────┐
│    department    │ 1           N │       user       │
│──────────────────│───────────────│──────────────────│
│ PK id            │               │ PK id            │
│    name (UK)     │               │    username (UK) │
│    created_at    │               │    password      │
│    deleted_at    │               │    name          │
│    version       │               │ FK dept_id       │
└────────┬─────────┘               │    role          │
         │                         └────────┬─────────┘
         │ 1                                │ 1
         │                                  │
         │ N                                │ N
┌────────┴──────────────────────────────────┴─────────┐
│                     reservation                     │
│─────────────────────────────────────────────────────│
│ PK id                                               │
│ FK room_id ──► meeting_room(id)                     │
│ FK user_id ──► user(id)                             │
│ FK dept_id ──► department(id)                       │
│    reserve_date (DATE)                              │
│    start_time (TIME)                                │
│    end_time (TIME)                                  │
│    title (VARCHAR)                                  │
│    room_name (快照 VARCHAR)                         │
│    dept_name (快照 VARCHAR)                         │
│    user_name (快照 VARCHAR)                         │
│    status (PENDING, ACTIVE, ENDED, CANCELLED)       │
│    created_at / updated_at                          │
└─────────────────────────────────────────────────────┘
```

### 2.2 数据库物理表结构定义

#### 2.2.1 部门表 (`department`)
- **存储引擎**：InnoDB，字符集：utf8mb4
- **字段规范**：
  - `id`: BIGINT AUTO_INCREMENT PRIMARY KEY
  - `name`: VARCHAR(50) NOT NULL, 部门名称
  - `created_at`: DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
  - `updated_at`: DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  - `deleted_at`: DATETIME NULL DEFAULT NULL, 软删除时间戳
  - `version`: INT NOT NULL DEFAULT 0, 乐观锁版本号
- **索引**：`UNIQUE KEY uk_dept_name (name, deleted_at)`

#### 2.2.2 用户表 (`user`)
- **字段规范**：
  - `id`: BIGINT AUTO_INCREMENT PRIMARY KEY
  - `username`: VARCHAR(50) NOT NULL, 小写英文字符与数字组合，全局唯一
  - `password`: VARCHAR(100) NOT NULL, BCrypt 加密密文
  - `name`: VARCHAR(64) NOT NULL, 显示姓名
  - `dept_id`: BIGINT NOT NULL, 外键引用 `department(id)`
  - `role`: VARCHAR(20) NOT NULL DEFAULT 'EMPLOYEE', 枚举值 `EMPLOYEE` / `ADMIN`
  - `created_at`: DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
  - `updated_at`: DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
- **索引**：`UNIQUE KEY uk_user_username (username)`, `KEY idx_user_dept (dept_id)`

#### 2.2.3 会议室表 (`meeting_room`)
- **字段规范**：
  - `id`: BIGINT AUTO_INCREMENT PRIMARY KEY
  - `name`: VARCHAR(50) NOT NULL, 会议室名称
  - `location`: VARCHAR(100) NOT NULL, 物理位置（如：A座 3F-301）
  - `capacity`: INT NOT NULL, 容纳人数上限
  - `status`: VARCHAR(20) NOT NULL DEFAULT 'ENABLED', 枚举值 `ENABLED` / `DISABLED`
  - `created_at`: DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
  - `updated_at`: DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  - `deleted_at`: DATETIME NULL DEFAULT NULL, 软删除时间戳
  - `version`: INT NOT NULL DEFAULT 0, 乐观锁版本号
- **索引**：`KEY idx_room_status (status)`

#### 2.2.4 预约记录表 (`reservation`)
- **字段规范**：
  - `id`: BIGINT AUTO_INCREMENT PRIMARY KEY
  - `room_id`: BIGINT NOT NULL, 外键引用 `meeting_room(id)`
  - `user_id`: BIGINT NOT NULL, 外键引用 `user(id)`
  - `reserve_date`: DATE NOT NULL, 预约日期（格式 YYYY-MM-DD）
  - `start_time`: TIME NOT NULL, 开始时间（格式 HH:mm:ss）
  - `end_time`: TIME NOT NULL, 结束时间（格式 HH:mm:ss）
  - `dept_id`: BIGINT NOT NULL, 部门ID
  - `title`: VARCHAR(100) NOT NULL, 会议主题
  - `room_name`: VARCHAR(50) NOT NULL, 创建时会议室名称快照
  - `dept_name`: VARCHAR(50) NOT NULL, 创建时部门名称快照
  - `user_name`: VARCHAR(64) NOT NULL, 创建时预约人姓名快照
  - `status`: VARCHAR(20) NOT NULL DEFAULT 'PENDING', 状态枚举 `PENDING` / `ACTIVE` / `ENDED` / `CANCELLED`
  - `created_at`: DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
  - `updated_at`: DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
- **核心索引**：
  - 核心联合索引：`KEY idx_res_room_date_time (room_id, reserve_date, start_time, end_time)`（支撑冲突检测与排期时间轴查询）
  - 辅助过滤索引：`KEY idx_res_user (user_id)`, `KEY idx_res_dept (dept_id)`, `KEY idx_res_status (status)`

---

## 3. 统一接口协议与错误码体系

### 3.1 统一响应报文封装 (`ApiResponse<T>`)

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

### 3.2 错误码分类标准表

| HTTP 状态码 | 业务 code | 说明 / 触发条件 | 前端提示举例 |
| :--- | :--- | :--- | :--- |
| `200` | 200 | 请求处理成功 | 操作成功 |
| `201` | 201 | 资源创建成功 | 创建成功 |
| `400` | 400 | 请求参数缺失、格式错误、起止颠倒、预约过去时段 | 请检查输入参数 / 不可预约过去时段 |
| `401` | 401 | 未登录、Token过期、Token签名错误、Token在登出黑名单 | 登录凭据已失效，请重新登录 |
| `403` | 403 | 越权访问：员工试图取消他人预约、操作停用会议室 | 无权进行此项操作 |
| `404` | 404 | 请求的目标资源（用户、会议室、预约记录）不存在 | 对应记录不存在 |
| `409` | 409 | 业务冲突：该会议室在目标时段已存在重叠预约 | 该时段已被预约，请选择其他时段 |
| `423` | 423 | 账号锁定：密码连续输错5次触发锁定15分钟 | 账号已锁定，请15分钟后再试 |
| `429` | 429 | 频控/防重：5秒内重复提交相同预约申请，或IP暴破限流 | 请勿重复提交 / 请求过于频繁 |
| `500` | 500 | 服务端未知未捕获异常 | 系统繁忙，请稍后再试 |

---

## 4. 全量 RESTful API 接口规格

### 4.1 认证模块 (`/api/auth`)

#### 4.1.1 账号密码登录
- **端点**：`POST /api/auth/login`
- **认证**：匿名（白名单开放）
- **请求体 (JSON)**：
  ```json
  {
    "username": "emp001",
    "password": "Password123!"
  }
  ```
- **业务校验**：
  1. `username` 自动转小写比对；
  2. 检查 Redis 键 `auth:lockout:{username}`，若值 >= 5，返回 HTTP 423；
  3. 检查客户端 IP 在 Redis 键 `auth:ip_limit:{ip}`，10分钟超10次返回 HTTP 429；
  4. 使用 BCrypt 比对密码，若不匹配，累加失败次数并返回 HTTP 401（统一返回“用户名或密码错误”）；
  5. 登录成功清除失败计数，签发 HS256 JWT，有效期 8 小时。
- **成功响应 (HTTP 200)**：
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "expiresIn": 28800,
      "user": {
        "userId": 2,
        "username": "emp001",
        "name": "张伟",
        "role": "EMPLOYEE",
        "deptId": 1,
        "deptName": "研发部"
      }
    }
  }
  ```

#### 4.1.2 用户登出
- **端点**：`POST /api/auth/logout`
- **认证**：Bearer Token
- **业务逻辑**：从 JWT 解析出唯一 `jti`，写入 Redis 键 `auth:blacklist:{jti}`，TTL 为 Token 剩余有效时间。
- **成功响应 (HTTP 200)**：`{ "code": 200, "message": "退出成功", "data": null }`

#### 4.1.3 当前登录人信息
- **端点**：`GET /api/auth/me`
- **认证**：Bearer Token
- **成功响应 (HTTP 200)**：返回当前用户基本属性（与登录接口 user 对象一致）。

---

### 4.2 主数据模块 (`/api/rooms`, `/api/departments`)

#### 4.2.1 查询可选会议室（下拉专用）
- **端点**：`GET /api/rooms/active`
- **认证**：Bearer Token（员工 & 管理员均可）
- **过滤条件**：`status = 'ENABLED' AND deleted_at IS NULL`
- **成功响应 (HTTP 200)**：
  ```json
  {
    "code": 200,
    "message": "success",
    "data": [
      {
        "id": 1,
        "name": "第一会议室",
        "location": "A座 3F-301",
        "capacity": 10,
        "status": "ENABLED"
      }
    ]
  }
  ```

#### 4.2.2 查询全部会议室列表（支持管理筛选）
- **端点**：`GET /api/rooms`
- **入参 (Query)**：`status`（可选，ENABLED/DISABLED）, `keyword`（可选）
- **认证**：Bearer Token

#### 4.2.3 切换会议室启用/停用状态
- **端点**：`PUT /api/rooms/{id}/status`
- **认证**：管理员专属（`role == 'ADMIN'`）
- **请求体 (JSON)**：`{ "status": "DISABLED" }`
- **成功响应 (HTTP 200)**：返回更新后的会议室实体。

#### 4.2.4 查询部门列表
- **端点**：`GET /api/departments`
- **认证**：Bearer Token
- **成功响应 (HTTP 200)**：返回活跃部门列表 `[{ "id": 1, "name": "研发部" }, ...]`.

---

### 4.3 预约核心模块 (`/api/reservations`)

#### 4.3.1 创建新预约
- **端点**：`POST /api/reservations`
- **认证**：Bearer Token
- **请求体 (JSON)**：
  ```json
  {
    "roomId": 1,
    "reserveDate": "2026-10-28",
    "startTime": "09:00",
    "endTime": "10:30",
    "deptId": 1,
    "title": "Q4 产品技术架构评审会"
  }
  ```
- **服务端处理与校验链路**：
  1. **防重拦截**：计算键 `reservation:submit_lock:{userId}:{md5(roomId|date|start|end|title)}`，执行 `SET key 1 NX EX 5`，失败则响应 HTTP 429；
  2. **基础校验**：`startTime < endTime`，两者必须在单日内；拒绝过去时间 `combine(reserveDate, startTime) < now()`；
  3. **会议室可用性**：查询会议室，必须存在且 `status = 'ENABLED'`，否则响应 HTTP 400（"会议室已停用"）；
  4. **并发控制与加锁**：开启事务，执行 `SELECT ... FROM reservation WHERE room_id=? AND reserve_date=? AND status != 'CANCELLED' FOR UPDATE`；
  5. **半开区间冲突检测**：遍历同日期同房间已有有效预约，若存在任一条记录满足 `s_new < r.end_time AND r.start_time < e_new`，则回滚并响应 HTTP 409（返回冲突详情）；
  6. **落库插入**：写入快照 `room_name`、`dept_name`、`user_name`，初始状态 `PENDING`；
  7. **失效看板缓存**：删除 Redis 键 `dashboard:stats:DATE:{reserveDate}` 及对应的 `MONTH` 键。
- **成功响应 (HTTP 201)**：
  ```json
  {
    "code": 201,
    "message": "预约创建成功",
    "data": {
      "id": 101,
      "roomId": 1,
      "roomName": "第一会议室",
      "userId": 2,
      "userName": "张伟",
      "deptId": 1,
      "deptName": "研发部",
      "reserveDate": "2026-10-28",
      "startTime": "09:00:00",
      "endTime": "10:30:00",
      "title": "Q4 产品技术架构评审会",
      "status": "PENDING",
      "createdAt": "2026-10-27 10:30:00"
    }
  }
  ```
- **冲突失败响应 (HTTP 409)**：
  ```json
  {
    "code": 409,
    "message": "该时段已被预约，存在时段冲突",
    "data": {
      "conflictReservation": {
        "title": "研发早会",
        "reserveDate": "2026-10-28",
        "startTime": "09:00:00",
        "endTime": "10:00:00",
        "userName": "李雷"
      }
    }
  }
  ```

#### 4.3.2 分页多条件查询预约列表
- **端点**：`GET /api/reservations`
- **认证**：Bearer Token（所有员工均可查询所有记录）
- **查询参数 (Query)**：
  - `roomId`（可选，Long）
  - `reserveDate`（可选，String YYYY-MM-DD）
  - `deptId`（可选，Long）
  - `status`（可选，PENDING/ACTIVE/ENDED/CANCELLED）
  - `userId`（可选，Long：员工传入只能为当前本人，否则强制覆盖为当前用户；管理员可查任意人）
  - `page`（默认 1），`size`（默认 20，超 100 自动截断为 100）
- **成功响应 (HTTP 200)**：
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "total": 45,
      "page": 1,
      "size": 20,
      "list": [
        {
          "id": 101,
          "roomId": 1,
          "roomName": "第一会议室",
          "userId": 2,
          "userName": "张伟",
          "deptId": 1,
          "deptName": "研发部",
          "reserveDate": "2026-10-28",
          "startTime": "09:00:00",
          "endTime": "10:30:00",
          "title": "Q4 产品技术架构评审会",
          "status": "PENDING",
          "computedStatus": "PENDING",
          "createdAt": "2026-10-27 10:30:00"
        }
      ]
    }
  }
  ```

#### 4.3.3 取消预约
- **端点**：`POST /api/reservations/{id}/cancel`
- **认证**：Bearer Token
- **业务规则**：
  1. 记录存在且 `computeStatus(record, now) == 'PENDING'`，否则返回 HTTP 400（"仅未开始的预约可取消"）；
  2. 权限判定：`record.userId == currentUser.id || currentUser.role == 'ADMIN'`，否则返回 HTTP 403；
  3. 更新存储态 `status = 'CANCELLED'`, `updated_at = NOW()`；
  4. 主动淘汰该日期/月份看板 Redis 缓存。
- **成功响应 (HTTP 200)**：`{ "code": 200, "message": "预约已成功取消", "data": null }`

#### 4.3.4 提前结束预约
- **端点**：`POST /api/reservations/{id}/early-end`
- **认证**：Bearer Token
- **业务规则**：
  1. 记录存在且 `computeStatus(record, now) == 'ACTIVE'`，否则返回 HTTP 400（"仅已开始的预约可提前结束"）；
  2. 权限判定：`record.userId == currentUser.id || currentUser.role == 'ADMIN'`，否则返回 HTTP 403；
  3. 更新存储态 `status = 'ENDED'`, `updated_at = NOW()`；原 `start_time` 与 `end_time` 保持不变；
  4. 主动淘汰该日期/月份看板 Redis 缓存。
- **成功响应 (HTTP 200)**：`{ "code": 200, "message": "会议已提前结束", "data": null }`

---

### 4.4 数据看板模块 (`/api/dashboard`)

#### 4.4.1 查询统计看板数据
- **端点**：`GET /api/dashboard/stats`
- **认证**：Bearer Token（所有员工及管理员均可查阅）
- **入参 (Query)**：
  - `dimension`: `DATE` 或 `MONTH`（必填）
  - `date`: `YYYY-MM-DD`（dimension=DATE时生效，不传默认当天）
  - `month`: `YYYY-MM`（dimension=MONTH时生效，不传默认当月）
- **缓存策略**：优先读取 Redis 键 `dashboard:stats:{dimension}:{date/month}`。命中直接返回；未命中则聚合 MySQL，写入 Redis（TTL 10 分钟）并返回。
- **指标统计口径**：
  - 样本范围：`status != 'CANCELLED'` 的所有预约（未被取消的预约均计入场次与预定占有时长）；
  - 会议室全集包含：即使场次为 0 的活跃会议室亦包含在列表中展示；
  - 部门全集包含：即使场次为 0 的活跃部门亦包含在列表中展示；
  - 占有时长：$\sum (\text{TIMESTAMPDIFF(MINUTE, start\_time, end\_time)})$ 分钟。
- **成功响应 (HTTP 200)**：
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "dimension": "DATE",
      "period": "2026-10-27",
      "summary": {
        "totalBookings": 12,
        "totalMinutes": 720,
        "activeRooms": 4,
        "activeDepts": 3
      },
      "roomStats": [
        { "roomId": 1, "roomName": "第一会议室", "bookingCount": 4, "occupiedMinutes": 240 },
        { "roomId": 2, "roomName": "第二会议室", "bookingCount": 3, "occupiedMinutes": 180 },
        { "roomId": 3, "roomName": "第三会议室", "bookingCount": 0, "occupiedMinutes": 0 }
      ],
      "deptStats": [
        { "deptId": 1, "deptName": "研发部", "bookingCount": 6, "occupiedMinutes": 360 },
        { "deptId": 2, "deptName": "行政部", "bookingCount": 4, "occupiedMinutes": 240 },
        { "deptId": 3, "deptName": "产品部", "bookingCount": 2, "occupiedMinutes": 120 }
      ]
    }
  }
  ```

#### 4.4.2 强制刷新看板（跳过缓存）
- **端点**：`POST /api/dashboard/refresh`
- **认证**：Bearer Token
- **业务逻辑**：绕过 Redis 缓存直接回源 MySQL 重新执行聚合计算，将最新结果覆盖回写 Redis 并重置 10 分钟 TTL。接口对同一客户端做 3 秒防抖。

---

## 5. 关键业务算法与并发控制规格

### 5.1 半开区间冲突判定算法
在数学集合论中，时段预约属于实数轴上的半开区间 $[start, end)$。

对于指定会议室与预约日期，设新申请区间为 $[s_N, e_N)$，任一已有非取消记录区间为 $[s_E, e_E)$。
两时段相交有且仅当：
$$\max(s_N, s_E) < \min(e_N, e_E) \iff (s_N < e_E) \land (s_E < e_N)$$

#### 边界用例判定断言：
1. **相邻时段**：已有 `[08:00, 09:00)`，申请 `[09:00, 10:00)` $\to$ $s_N = e_E$ $\to$ **无冲突（合法）**；
2. **完全包含**：已有 `[09:00, 11:00)`，申请 `[09:30, 10:30)` $\to$ **冲突（拒绝）**；
3. **部分重叠**：已有 `[09:00, 10:00)`，申请 `[09:30, 10:30)` $\to$ **冲突（拒绝）**；
4. **完全相同**：已有 `[09:00, 10:00)`，申请 `[09:00, 10:00)` $\to$ **冲突（拒绝）**；
5. **已取消记录**：已有同区间记录但 `status = 'CANCELLED'` $\to$ **无冲突（合法）**。

### 5.2 数据库排他并发控制方案
为了应对多个并发线程同时抢定同一房间同一时段的竞态条件：
1. 开启 Spring `@Transactional(isolation = Isolation.REPEATABLE_READ)`；
2. 先行执行排他行锁查询：
   ```sql
   SELECT id, start_time, end_time, title, user_name 
   FROM reservation 
   WHERE room_id = :roomId 
     AND reserve_date = :reserveDate 
     AND status != 'CANCELLED' 
   FOR UPDATE;
   ```
3. 若当前日期下尚无任何预约记录，为了防止并发间隙插入（Phantom Read），退化为对 `meeting_room` 表锁定对应行：
   ```sql
   SELECT id FROM meeting_room WHERE id = :roomId FOR UPDATE;
   ```
4. 在锁保护的安全上下文中比对所有现存区间；
5. 校验无冲突后执行 `INSERT` 并提交事务释放锁。

---

## 6. 生命周期状态机与动态计算规格

### 6.1 状态流转图

```
        创建成功
           │
           ▼
     [ PENDING ] ────────── 取消 (本人/管理员) ──────────► [ CANCELLED ]
           │                                                 (释放时段)
           │  当前时间 ≥ reserveDate + startTime
           ▼
     [ ACTIVE ] ────────── 提前结束 (本人/管理员) ────────► [ ENDED ]
           │                                                 (标记结束)
           │  当前时间 ≥ reserveDate + endTime
           ▼
     [ ENDED ]
     (自然结束)
```

### 6.2 动态即时态计算函数 (`computeStatus`)
为了彻底避免“时钟已过开始时间但数据库字段仍为PENDING”导致的状态割裂，列表展示与取消/提前结束判定统一采用 `computeStatus(record, now)` 计算：

```java
public ReservationStatus computeStatus(Reservation r, LocalDateTime now) {
    if (r.getStatus() == ReservationStatus.CANCELLED) {
        return ReservationStatus.CANCELLED;
    }
    if (r.getStatus() == ReservationStatus.ENDED) {
        return ReservationStatus.ENDED;
    }
    // 处于 PENDING 存储态时，结合系统权威当前时钟即时判定
    LocalDateTime startDateTime = LocalDateTime.of(r.getReserveDate(), r.getStartTime());
    LocalDateTime endDateTime = LocalDateTime.of(r.getReserveDate(), r.getEndTime());

    if (now.isBefore(startDateTime)) {
        return ReservationStatus.PENDING;
    } else if (!now.isBefore(startDateTime) && now.isBefore(endDateTime)) {
        return ReservationStatus.ACTIVE;
    } else {
        return ReservationStatus.ENDED;
    }
}
```

---

## 7. 权限与安全防护规格

### 7.1 用户与权限矩阵 (RBAC)

| 操作能力 | 普通员工 (EMPLOYEE) | 管理员 (ADMIN) | 越权防护机制 |
| :--- | :--- | :--- | :--- |
| 查询会议室列表 / 下拉 | ✅ 允许 | ✅ 允许 | 公共只读 |
| 切换会议室启用/停用 | ❌ 拒绝 (403) | ✅ 允许 | 角色注解 `@PreAuthorize("hasRole('ADMIN')")` |
| 查询全量预约列表 | ✅ 允许 | ✅ 允许 | 公共可见（员工传他人 userId 强制覆盖为本人） |
| 发起会议室预约 | ✅ 允许 | ✅ 允许 | 预约人 `user_id` 从 JWT 强制提取注入 |
| 取消**本人未开始**预约 | ✅ 允许 | ✅ 允许 | 校验 `userId == jwtUserId` 且 `status == PENDING` |
| 取消**他人未开始**预约 | ❌ 拒绝 (403) | ✅ 允许 | 非本人且非管理员拦截 403 |
| 提前结束**本人已开始**预约 | ✅ 允许 | ✅ 允许 | 校验 `userId == jwtUserId` 且 `status == ACTIVE` |
| 提前结束**他人已开始**预约 | ❌ 拒绝 (403) | ✅ 允许 | 非本人且非管理员拦截 403 |
| 查阅数据看板 | ✅ 允许 | ✅ 允许 | 公共可见 |

### 7.2 安全基线与合规防护
1. **密码安全**：强哈希 `BCryptPasswordEncoder(10)`，禁止明文落库，入参校验 8~64 位字符；
2. **暴破防护**：Redis 计数器 `auth:lockout:{username}` 达 5 次自动锁定 15 分钟；IP 计数达 10 次限流 10 分钟；
3. **JWT 令牌规范**：固定算法 `HS256`，密钥大于 32 字节，Payload 包含 `sub(userId)`, `username`, `role`, `deptId`, `jti`；登出即时加入 Redis 黑名单；
4. **SQL 防注入**：MyBatis 全部使用 `#{}` 参数化绑定，杜绝 `${}` 拼接；
5. **环境隔离**：真实服务器凭证严禁出现在测试账号与代码注释中，预置账号统一采用虚构测试数据（`admin`, `emp001`, `emp002`）。

---

## 8. 验收断言矩阵（驱动测试验证）

| 编号 | 测试分类 | 前置条件 / 输入 | 预期断言与结果 |
| :--- | :--- | :--- | :--- |
| **AC-01** | 认证鉴权 | 输入正确账号 `admin` 与密码 `123456` | 响应 200，返回合法 JWT，解析含 `role=ADMIN` |
| **AC-02** | 认证鉴权 | 连续输入 5 次错误密码 | 第 5 次立即返回 423 Account Locked，持续 15 分钟 |
| **AC-03** | 会议室管理 | 员工账号调用 `PUT /api/rooms/1/status` | 响应 403 Forbidden |
| **AC-04** | 会议室停用 | 会议室置为 `DISABLED` 后发起预约 | 响应 400 "会议室已停用" |
| **AC-05** | 预约创建 | 提交合法单日时段 `[09:00, 10:00)` | 响应 201，状态为 `PENDING`，快照信息正确 |
| **AC-06** | 冲突校验 | 对已有 `[09:00, 10:00)` 预约提交 `[09:30, 10:30)` | 响应 409 Conflict，返回已有预约冲突信息 |
| **AC-07** | 冲突校验 | 对已有 `[09:00, 10:00)` 预约提交 `[10:00, 11:00)`（相邻） | 响应 201 成功，两时段互不影响 |
| **AC-08** | 并发防超订 | 10 个线程同时提交相同房间相同时间段 | 严格仅 1 个线程返回 201，其余 9 个返回 409 |
| **AC-09** | 防重复提交 | 同一用户 1 秒内双击提交相同预约内容 | 第 2 次请求被 Redis 锁拦截，返回 429 |
| **AC-10** | 取消权限 | 员工 A 尝试取消员工 B 的 `PENDING` 预约 | 响应 403 Forbidden |
| **AC-11** | 取消权限 | 管理员取消员工 B 的 `PENDING` 预约 | 响应 200，状态变为 `CANCELLED`，该时段立即释放 |
| **AC-12** | 提前结束 | 员工 A 提前结束本人已开始 (`ACTIVE`) 预约 | 响应 200，状态变为 `ENDED` |
| **AC-13** | 看板统计 | 存在 2 场已取消、3 场已预约有效记录 | 看板场次为 3，已取消记录不计入场次与占用时长 |
| **AC-14** | 看板全集 | 第五会议室 0 预约场次 | 看板会议室列表中包含第五会议室，数值为 0 |
| **AC-15** | 缓存主动失效 | 创建/取消预约后立即查看板 | 看板数据即时包含最新变动，缓存已完成主动更新 |

---

> 本规格文档已固化定稿，作为后续 **环节 4（编码实现）** 与 **环节 5（测试验证）** 的顶层契约准绳。
