# Tasks

## 1. Database and Schema Initialization

- [x] 1.1 Create MySQL database tables (`department`, `user`, `meeting_room`, `reservation`) using DDL and verify tables and indexes with `SHOW TABLES;` and `SHOW INDEX FROM reservation;`
- [x] 1.2 Seed initial master data (departments, meeting rooms, and BCrypt-hashed user accounts `admin`, `emp001`, `emp002`, `emp003`) and verify by querying count and status of seeded records

## 2. Backend Infrastructure and Core Framework Setup

- [x] 2.1 Scaffold Spring Boot 3 multi-layer architecture (Maven, Java 17, Web, MyBatis/JPA, Redis, Security/JWT) and verify compilation with `mvn clean compile`
- [x] 2.2 Implement unified `ApiResponse<T>` envelope, global exception handler, and standardized error codes, verifying response format with mock unit test
- [x] 2.3 Configure Redis connection pool and test connectivity with a basic Redis ping test

## 3. Authentication and RBAC Capability

- [x] 3.1 Implement user authentication service with BCrypt password matching and JWT issuance (HS256 with 8-hour TTL), and verify with unit tests for valid and invalid credentials
- [x] 3.2 Implement Redis-backed brute force protection (5 consecutive failures locks account for 15 minutes, IP rate limit) and verify lockout triggers via unit test
- [x] 3.3 Implement JWT authentication filter and Redis token blacklist for `POST /api/auth/logout`, verifying that blacklisted tokens receive HTTP 401
- [x] 3.4 Implement dual-role authorization interceptor (`EMPLOYEE` vs `ADMIN`) and verify that employee requests to admin endpoints return HTTP 403

## 4. Master Data Management (Rooms and Departments)

- [x] 4.1 Implement department CRUD services and controller endpoints (`/api/departments/**`) with soft deletion, and verify that deleted departments are excluded from active list queries
- [x] 4.2 Implement meeting room CRUD services and status toggling (`/api/rooms/**`) with soft deletion, verifying that disabled rooms return `status = 'DISABLED'`
- [x] 4.3 Add validation tests ensuring disabled meeting rooms reject new reservation creation while existing pending reservations remain intact

## 5. Reservation Lifecycle and Collision Engine

- [x] 5.1 Implement Redis submission lock (`reservation:submit_lock:{userId}:{hash}`) with 5-second TTL and verify duplicate submissions within 5 seconds return HTTP 429
- [x] 5.2 Implement half-open interval `[start_time, end_time)` collision detection algorithm with transactional row lock (`SELECT ... FOR UPDATE`), and verify adjacent slots succeed while overlapping slots return HTTP 409
- [x] 5.3 Implement reservation creation (`POST /api/reservations`) with server-side identity injection and name snapshot persistence, verifying rejection of cross-day and past-time requests
- [x] 5.4 Implement dynamic status computation `computeStatus` (evaluating `PENDING`, `ACTIVE`, `ENDED`) and paginated multi-filter queries (`GET /api/reservations`), verifying correct computed status for ongoing and past meetings
- [x] 5.5 Implement reservation cancellation (`POST /api/reservations/{id}/cancel`) with role-based checks (employee own pending only, admin any pending) and verify time slot immediate release
- [x] 5.6 Implement reservation early termination (`POST /api/reservations/{id}/early-end`) with role-based checks (employee own active only, admin any active) and verify state transitions to `ENDED`

## 6. Analytics Dashboard and Cache Coordination

- [x] 6.1 Implement daily and monthly SQL aggregation queries for meeting rooms and departments (booking counts and occupied minutes), verifying zero-activity entities are included
- [x] 6.2 Implement Redis caching for dashboard metrics (`dashboard:stats:{dimension}:{period}`) with 10-minute TTL and proactive cache eviction on reservation write operations, verifying cache invalidation via integration test
- [x] 6.3 Implement dashboard API endpoint (`GET /api/dashboard/stats`) and manual refresh endpoint, verifying that manual refresh queries MySQL directly and updates Redis cache

## 7. Frontend User Interface Implementation

- [x] 7.1 Implement Login page with validation, JWT session persistence, and role-based route guards in Vue 3 + Element Plus, verifying navigation based on auth status
- [x] 7.2 Implement Reservation List page with multi-filter bar (room, date, department, status), computed status badges, and dynamic action buttons based on user role and booking state
- [x] 7.3 Implement Reservation Form page with room/department dropdowns, date/time pickers (30-minute intervals), user profile auto-fill, and pre-submission collision error display
- [x] 7.4 Implement Analytics Dashboard page with Date/Month dimension toggle, 4 KPI cards, and ECharts visualizations (Room Bar Chart, Dept Bar Chart, Occupied Duration Stacked Chart)

## 8. End-to-End System Integration and Verification

- [x] 8.1 Execute integration test suite covering end-to-end user journey: login -> book room -> query list -> view on dashboard -> cancel/early-end -> verify dashboard update
- [x] 8.2 Execute concurrent reservation collision test with multiple threads booking the same time slot simultaneously, verifying exactly one booking succeeds and the rest receive HTTP 409
