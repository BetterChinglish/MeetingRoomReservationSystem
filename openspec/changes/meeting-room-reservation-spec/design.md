# Design

## Context

See `proposal.md` for background and motivation. The system is constrained to a pure Web application with a Java 17 / Spring Boot 3 RESTful backend, MySQL 8 database, Redis 7 caching and coordination layer, and a Vue 3 + Element Plus frontend. The prototype in `design/prototype.html` and PRD documents in `prd/` establish the foundational domain models and user interaction requirements.

## Goals / Non-Goals

**Goals:**
- Provide zero-collision meeting room booking via a mathematically rigorous half-open interval algorithm and database-level row locking.
- Enforce strict dual-role access control (`EMPLOYEE` vs `ADMIN`) for all API endpoints and business actions.
- Implement an explicit lifecycle state machine (`PENDING`, `ACTIVE`, `ENDED`, `CANCELLED`) with dynamic status computation (`computeStatus`).
- Support high-performance multi-dimensional dashboard aggregations with multi-level Redis caching and proactive cache eviction.
- Standardize the unified RESTful API envelope and error taxonomy across all services.

**Non-Goals:**
- Mobile application adaptation or responsive viewport support beyond standard desktop browsers.
- Multi-day or recurring reservation rules (strictly limited to single-day slots within 00:00~23:59).
- Complex multi-level approval workflows or equipment checkout management.
- Multi-tenant partitioning or external OAuth/SSO integrations (username/password JWT is authoritative).

## Decisions

### 1. Data Schema and Table Design (MySQL 8)

#### Table `department`
```sql
CREATE TABLE `department` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Department ID',
  `name` VARCHAR(50) NOT NULL COMMENT 'Department Name',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL DEFAULT NULL COMMENT 'Soft delete timestamp',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_name` (`name`, `deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Department Master Table';
```

#### Table `user`
```sql
CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'User ID',
  `username` VARCHAR(50) NOT NULL COMMENT 'Unique lowercase username',
  `password` VARCHAR(100) NOT NULL COMMENT 'BCrypt hashed password',
  `name` VARCHAR(64) NOT NULL COMMENT 'Display full name',
  `dept_id` BIGINT NOT NULL COMMENT 'Department ID',
  `role` VARCHAR(20) NOT NULL DEFAULT 'EMPLOYEE' COMMENT 'Role: EMPLOYEE, ADMIN',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  KEY `idx_user_dept` (`dept_id`),
  CONSTRAINT `fk_user_dept` FOREIGN KEY (`dept_id`) REFERENCES `department` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User Table';
```

#### Table `meeting_room`
```sql
CREATE TABLE `meeting_room` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Meeting Room ID',
  `name` VARCHAR(50) NOT NULL COMMENT 'Room Name',
  `location` VARCHAR(100) NOT NULL COMMENT 'Room Location (e.g. Building A 3F)',
  `capacity` INT NOT NULL COMMENT 'Maximum seating capacity',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED / DISABLED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL DEFAULT NULL COMMENT 'Soft delete timestamp',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
  PRIMARY KEY (`id`),
  KEY `idx_room_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Meeting Room Table';
```

#### Table `reservation`
```sql
CREATE TABLE `reservation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Reservation ID',
  `room_id` BIGINT NOT NULL COMMENT 'Meeting Room ID',
  `user_id` BIGINT NOT NULL COMMENT 'Reservee User ID',
  `reserve_date` DATE NOT NULL COMMENT 'Reservation Date (YYYY-MM-DD)',
  `start_time` TIME NOT NULL COMMENT 'Start Time (HH:mm:ss)',
  `end_time` TIME NOT NULL COMMENT 'End Time (HH:mm:ss)',
  `dept_id` BIGINT NOT NULL COMMENT 'Booking Department ID',
  `title` VARCHAR(100) NOT NULL COMMENT 'Meeting Title',
  `room_name` VARCHAR(50) NOT NULL COMMENT 'Snapshot room name',
  `dept_name` VARCHAR(50) NOT NULL COMMENT 'Snapshot department name',
  `user_name` VARCHAR(64) NOT NULL COMMENT 'Snapshot reservee name',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, ACTIVE, ENDED, CANCELLED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_res_room_date_time` (`room_id`, `reserve_date`, `start_time`, `end_time`),
  KEY `idx_res_user` (`user_id`),
  KEY `idx_res_dept` (`dept_id`),
  KEY `idx_res_status` (`status`),
  CONSTRAINT `fk_res_room` FOREIGN KEY (`room_id`) REFERENCES `meeting_room` (`id`),
  CONSTRAINT `fk_res_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_res_dept` FOREIGN KEY (`dept_id`) REFERENCES `department` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Reservation Table';
```

*Rationale:* Storing `room_name`, `dept_name`, and `user_name` as snapshot columns in `reservation` guarantees historical query stability even if master data is updated or soft-deleted.

---

### 2. Time-Slot Collision Detection and Concurrency Control

- **Collision Formula**:
  For target interval `[s_new, e_new)` and any existing valid record `[s_exist, e_exist)` on identical `(room_id, reserve_date)`:
  $$\text{Collision} \iff \max(s_{new}, s_{exist}) < \min(e_{new}, e_{exist}) \iff (s_{new} < e_{exist} \land s_{exist} < e_{new})$$
  Adjacent intervals ($s_{new} = e_{exist}$ or $e_{new} = s_{exist}$) evaluate to `false` and are permitted.
- **Valid Reservation Set**:
  Any reservation record where `status != 'CANCELLED'`.
- **Concurrency Safety**:
  - Step 1: Redis Idempotency Lock: `SET reservation:submit_lock:{userId}:{hash} 1 NX EX 5`
  - Step 2: Database Transaction: `@Transactional(isolation = Isolation.REPEATABLE_READ)`
  - Step 3: Row Exclusive Lock:
    ```sql
    SELECT id, start_time, end_time, title, user_name 
    FROM reservation 
    WHERE room_id = :roomId 
      AND reserve_date = :reserveDate 
      AND status != 'CANCELLED'
    FOR UPDATE;
    ```
    If no rows exist yet for that date, acquire row lock on `meeting_room`:
    ```sql
    SELECT id FROM meeting_room WHERE id = :roomId FOR UPDATE;
    ```
  - Step 4: In-transaction interval evaluation against query results. If collision detected, abort and return HTTP 409 with conflict details. Otherwise, execute `INSERT INTO reservation ...`.

---

### 3. Dual Status Model (Stored vs Dynamic)

- **Stored Status**: Stored in DB as `PENDING`, `ACTIVE`, `ENDED`, `CANCELLED`.
- **Dynamic Status (`computeStatus`)**:
  When reading reservation records for UI list and detail endpoints:
  - If stored `status == 'CANCELLED'` $\to$ `CANCELLED`
  - If stored `status == 'ENDED'` $\to$ `ENDED`
  - If stored `status == 'PENDING'`:
    - Let $T_{start} = \text{combine}(reserveDate, start\_time)$, $T_{end} = \text{combine}(reserveDate, end\_time)$.
    - If $now < T_{start} \to$ `PENDING`
    - If $T_{start} \le now < T_{end} \to$ `ACTIVE`
    - If $now \ge T_{end} \to$ `ENDED`
- **Early End Behavior**:
  Calling early end sets stored status to `ENDED` and records `updated_at = now()`. The original `[start_time, end_time)` is preserved for audit consistency and room slot reservation protection.

---

### 4. Unified API Protocol and Error Taxonomy

All endpoints return a uniform envelope:
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

Standardized Error Codes:
- `200`: Success
- `400`: Bad Request / Validation Failure (invalid format, end_time <= start_time, past time)
- `401`: Unauthorized (missing, expired, or blacklisted JWT)
- `403`: Forbidden (horizontal/vertical privilege violation)
- `404`: Not Found (record does not exist)
- `409`: Conflict (time slot already reserved, returns conflicting details)
- `423`: Account Locked (brute force lockout)
- `429`: Too Many Requests (rate limit or duplicate submission)
- `500`: Internal Server Error

---

### 5. Redis Key Topology and TTL Strategy

| Key Pattern | Type | TTL | Purpose |
| :--- | :--- | :--- | :--- |
| `auth:lockout:{username}` | String | 15 min | Account failed login counter |
| `auth:ip_limit:{clientIp}` | String | 10 min | IP login failure counter |
| `auth:blacklist:{jti}` | String | Residual token TTL | Logout revoked token blacklist |
| `reservation:submit_lock:{userId}:{hash}` | String | 5 sec | Idempotent booking submit lock |
| `dashboard:stats:DATE:{date}` | String (JSON) | 10 min | Daily dashboard metrics cache |
| `dashboard:stats:MONTH:{month}` | String (JSON) | 10 min | Monthly dashboard metrics cache |

---

## Risks / Trade-offs

- **[Risk: Heavy Lock Contention on Hot Meeting Rooms]**
  → *Mitigation:* The exclusive lock is scoped to `(room_id, reserve_date)`. Requests booking different dates or different rooms execute fully in parallel.
- **[Risk: Redis Service Outage]**
  → *Mitigation:* Fail-open degradation for submit locks and dashboard caching. If Redis is down, submit lock logs a warning and allows the request to fall through to MySQL transactions. Dashboard queries bypass cache and read directly from MySQL.
- **[Risk: Timezone Discrepancy between Client and Server]**
  → *Mitigation:* The backend explicitly binds to `Asia/Shanghai` (`ZoneId.of("Asia/Shanghai")`). Dates and times are parsed as ISO-8601 strings and compared against server authoritative time.

## Migration Plan

1. Execute DDL script (`ddl.sql`) against the target MySQL database `exam`.
2. Populate initial departments, meeting rooms, and seeded user accounts (`admin`, `emp001`, `emp002`, `emp003`) with BCrypt hashed passwords.
3. Verify Redis connection and ping health.
4. Launch backend Spring Boot application on port 8080 and Nginx reverse proxy on port 80.
