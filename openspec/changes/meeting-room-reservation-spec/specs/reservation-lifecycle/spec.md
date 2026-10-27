# Spec Delta

## Purpose

Governs reservation creation, half-open interval collision validation, Redis idempotent locking, state machine transitions (PENDING, ACTIVE, ENDED, CANCELLED), cancellation, and early termination with role-based permissions.

## ADDED Requirements

### Requirement: Reservation Creation and Parameter Validation
The system SHALL allow authenticated users to submit meeting room reservations. Required fields SHALL include roomId, reserveDate (YYYY-MM-DD), startTime (HH:mm), endTime (HH:mm), deptId, and title. The user_id SHALL be resolved strictly from the authenticated JWT session context. The system SHALL reject cross-day reservations (where startTime >= endTime or dates span midnight) and bookings for past time intervals.

#### Scenario: Successful single-day reservation creation
- **WHEN** user submits valid booking parameters with `endTime > startTime` on current or future date/time
- **THEN** system creates the reservation in `PENDING` status, captures name snapshots (roomName, deptName, userName), and returns HTTP 201

#### Scenario: Reject cross-day reservation
- **WHEN** user submits reservation parameters where `startTime >= endTime`
- **THEN** system rejects the request with HTTP 400 Bad Request

#### Scenario: Reject past time reservation
- **WHEN** user submits a reservation where `combine(reserveDate, startTime) < now()` in server timezone (Asia/Shanghai)
- **THEN** system rejects the request with HTTP 400 Bad Request stating "Cannot book past time slots"

#### Scenario: Idempotent submission lock via Redis
- **WHEN** user submits identical reservation parameters within 5 seconds
- **THEN** system detects active Redis submit lock (`reservation:submit_lock:{userId}:{hash}`) and rejects subsequent requests with HTTP 429 or duplicate submission message

### Requirement: Half-Open Interval Collision Detection Algorithm
The system SHALL enforce zero-collision scheduling for the same meeting room on the same date. The conflict condition between new interval `[s_new, e_new)` and existing active reservation interval `[s_exist, e_exist)` SHALL be defined as: `s_new < e_exist AND s_exist < e_new`. Adjacent bookings (where `s_new == e_exist` or `e_new == s_exist`) SHALL NOT conflict. Cancelled reservations (`status = 'CANCELLED'`) SHALL be excluded from collision checks.

#### Scenario: Adjacent time slot bookings permitted
- **WHEN** user books `[10:00, 11:00)` for a room that has an existing reservation for `[09:00, 10:00)`
- **THEN** system permits the booking without reporting conflict

#### Scenario: Overlapping time slot booking rejected
- **WHEN** user books `[09:30, 10:30)` for a room that has an existing reservation for `[09:00, 10:00)`
- **THEN** system rejects the request with HTTP 409 Conflict, returning details of the conflicting reservation (title, time slot, reservee name)

#### Scenario: Cancelled reservation ignored during collision check
- **WHEN** user books a time slot that overlaps with a reservation whose status is `CANCELLED`
- **THEN** system allows the booking and allocates the time slot

#### Scenario: Concurrent booking race condition protection
- **WHEN** two concurrent requests attempt to reserve conflicting intervals for the same room and date simultaneously
- **THEN** system acquires an exclusive lock (`SELECT ... FOR UPDATE`), serializes collision checks, permits exactly one booking, and rejects the second with HTTP 409

### Requirement: Dynamic Status Computation and Lifecycle Transitions
The system SHALL manage reservation status through four discrete states: `PENDING`, `ACTIVE`, `ENDED`, and `CANCELLED`. In query results, the system SHALL compute the dynamic operational status using `computeStatus(record, now)`: `PENDING` if `now < startTime`; `ACTIVE` if `startTime <= now < endTime` and not ended; `ENDED` if `now >= endTime` or early ended; and `CANCELLED` if explicitly cancelled.

#### Scenario: Dynamic status evaluated as ACTIVE during ongoing meeting
- **WHEN** client queries a reservation whose stored status is `PENDING` but current server time has reached `[startTime, endTime)`
- **THEN** system returns operational status as `ACTIVE`

#### Scenario: Dynamic status evaluated as ENDED after scheduled finish
- **WHEN** client queries a reservation whose stored status is not cancelled and current server time has passed `endTime`
- **THEN** system returns operational status as `ENDED`

### Requirement: Reservation Cancellation Rules and Permissions
The system SHALL permit cancellation only for reservations in `PENDING` status (where `computeStatus == 'PENDING'`). An employee SHALL only cancel reservations where `userId == jwtUserId`. An administrator SHALL be authorized to cancel any pending reservation. Upon cancellation, the reservation status SHALL become `CANCELLED` and its time slot SHALL be immediately released for re-booking.

#### Scenario: Employee cancels own pending reservation
- **WHEN** user with role `EMPLOYEE` requests cancellation of their own pending reservation
- **THEN** system updates status to `CANCELLED`, records update timestamp, and returns HTTP 200

#### Scenario: Employee blocked from cancelling another user reservation
- **WHEN** user with role `EMPLOYEE` attempts to cancel a reservation created by another user
- **THEN** system rejects the request with HTTP 403 Forbidden

#### Scenario: Admin cancels any pending reservation
- **WHEN** administrator requests cancellation of a pending reservation created by any user
- **THEN** system permits the cancellation, updates status to `CANCELLED`, and returns HTTP 200

#### Scenario: Reject cancellation of active or ended reservation
- **WHEN** user attempts to cancel a reservation whose dynamic status is `ACTIVE`, `ENDED`, or `CANCELLED`
- **THEN** system rejects the operation with HTTP 400 Bad Request stating "Only pending reservations can be cancelled"

### Requirement: Early End Rules and Permissions
The system SHALL permit early termination only for reservations currently in `ACTIVE` status (where `computeStatus == 'ACTIVE'`). An employee SHALL only early end reservations where `userId == jwtUserId`. An administrator SHALL be authorized to early end any active reservation. Upon early termination, the reservation status SHALL transition to `ENDED`.

#### Scenario: Employee early ends own active reservation
- **WHEN** user with role `EMPLOYEE` requests early end for their own active reservation
- **THEN** system updates status to `ENDED`, persists termination timestamp, and returns HTTP 200

#### Scenario: Admin early ends any active reservation
- **WHEN** administrator requests early end for an active reservation created by any user
- **THEN** system permits early end, updates status to `ENDED`, and returns HTTP 200

#### Scenario: Reject early end of non-active reservation
- **WHEN** user attempts to early end a reservation whose status is `PENDING`, `ENDED`, or `CANCELLED`
- **THEN** system rejects the request with HTTP 400 Bad Request stating "Only active reservations can be terminated early"

### Requirement: Reservation Query and Detail Retrieval
The system SHALL provide paginated reservation queries supporting multi-field filtering by roomId, reserveDate, deptId, computed status, and userId. All authenticated users SHALL have read access to view all meeting room reservation records. For employee queries, passing a specific `userId` filter SHALL be constrained to the current user's ID, while administrators MAY filter by any userId.

#### Scenario: Paginated query with combined filters
- **WHEN** user queries reservations with specific roomId and reserveDate
- **THEN** system returns matching reservations ordered by `id DESC` with total count, pagination metadata, and snapshot names (roomName, deptName, userName)

#### Scenario: Retrieve single reservation detail
- **WHEN** authenticated user requests reservation detail by ID
- **THEN** system returns the complete reservation record with computed status and audit timestamps
