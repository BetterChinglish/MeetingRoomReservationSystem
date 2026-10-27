# Proposal

## Why

In unit administrative offices, meeting rooms are frequently double-booked, improperly occupied, or left as idle "zombie" reservations due to manual and offline booking processes. To ensure collision-free scheduling, strictly enforce dual-role permissions (Employee vs Admin), and provide real-time utilization visibility, this change defines the authoritative behavioral specifications, data models, and API contracts for the Meeting Room Reservation System (Phase 3 Specification of PLAN.md).

## What Changes

This change introduces four core system capabilities and their complete specification contracts:

- **Authentication and RBAC (`auth-rbac`)**:
  - Secure login with BCrypt password verification, brute-force lockout, and JWT token issuance.
  - Dual-role permission enforcement (`EMPLOYEE` vs `ADMIN`) with strict horizontal and vertical privilege separation.
  - Server-side token invalidation via Redis token blacklist upon logout.
- **Master Data Management (`meeting-room-dept`)**:
  - Meeting room and department master data models, status management (`ENABLED` / `DISABLED`), location and capacity attributes.
  - Cascading validation ensuring disabled meeting rooms reject new bookings while preserving existing schedules.
  - Soft deletion protection for master records referenced by historical reservations.
- **Reservation Lifecycle Management (`reservation-lifecycle`)**:
  - Single-day booking creation with server-side identity injection and past-time rejection.
  - Half-open interval `[start_time, end_time)` collision detection algorithm with database-level row locks and Redis anti-duplicate submission lock.
  - Lifecycle state machine: `PENDING` -> `ACTIVE` -> `ENDED` (natural or early-end), plus `PENDING` -> `CANCELLED`.
  - Differentiated permissions: Employees cancel own pending or early-end own active; Admins cancel any pending or early-end any active.
- **Analytics Dashboard (`analytics-dashboard`)**:
  - Daily and monthly aggregation filters for meeting room and department metrics (booking counts and total occupied minutes).
  - Multi-dimensional metrics supporting bar charts, stacked charts, and pivot tables.
  - Performance optimization with Redis multi-level caching, write-triggered cache eviction, and cache-aside fallback.

## Capabilities

### New Capabilities
- `auth-rbac`: Manages user credentials, password security, JWT session lifecycles, and dual-role (`EMPLOYEE` / `ADMIN`) access control.
- `meeting-room-dept`: Manages meeting room and department master data, room availability status, and constraints on reservation creation.
- `reservation-lifecycle`: Governs reservation booking, half-open interval collision validation, dynamic/persisted state transitions, cancellations, and early terminations.
- `analytics-dashboard`: Computes and aggregates reservation counts and occupied durations by room and department across daily and monthly dimensions.

### Modified Capabilities
None. (Initial greenfield specification)

## Impact

- **Database**: Creates tables `user`, `department`, `meeting_room`, `reservation`, with supporting indexes, foreign keys, and constraints.
- **Backend APIs**: Exposes RESTful endpoints under `/api/**` for authentication (`/api/auth/**`), master data (`/api/rooms/**`, `/api/departments/**`), reservations (`/api/reservations/**`), and dashboard analytics (`/api/dashboard/**`).
- **Middleware**: Requires Redis 7 for login failure counters, token blacklists, idempotent submission locks, and dashboard aggregation caching.
- **Frontend**: Drives implementation of Login, Reservation List, Reservation Form, and Analytics Dashboard pages.
