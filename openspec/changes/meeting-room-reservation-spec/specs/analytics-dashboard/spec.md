# Spec Delta

## Purpose

Calculates, aggregates, and serves multi-dimensional reservation metrics (booking counts and occupied minutes) across meeting room and department dimensions with date and monthly granularity, optimized with Redis caching.

## ADDED Requirements

### Requirement: Multi-Dimensional Date and Month Filtering
The system SHALL support two mutually exclusive filter dimensions for dashboard statistics: "Daily" (specific date `YYYY-MM-DD`) and "Monthly" (specific month `YYYY-MM`). The system SHALL provide server-authoritative default values (`today` and `current-month` in server timezone `Asia/Shanghai`).

#### Scenario: Query dashboard by specific single date
- **WHEN** user requests dashboard statistics with `dimension = 'DATE'` and `date = '2026-10-27'`
- **THEN** system computes statistics strictly for reservations occurring on that date and returns aggregated metrics

#### Scenario: Query dashboard by specific month
- **WHEN** user requests dashboard statistics with `dimension = 'MONTH'` and `month = '2026-10'`
- **THEN** system computes statistics for all reservations falling within that calendar month (from the 1st day to the last day of the month)

#### Scenario: Fallback to server defaults when filter omitted
- **WHEN** user requests dashboard data without providing explicit filter parameters
- **THEN** system defaults to current date (`today`) and returns daily aggregation metrics

### Requirement: Meeting Room Utilization Metrics Aggregation
The system SHALL aggregate the total booking count and total occupied duration (in minutes) for each meeting room within the selected time window. All active meeting rooms SHALL be included in the aggregation, showing zero counts and durations for rooms with no bookings. Only non-cancelled reservations (`status != 'CANCELLED'`) SHALL be included in calculations.

#### Scenario: Compute meeting room booking counts and occupied minutes
- **WHEN** dashboard metrics are computed for a given date or month
- **THEN** system calculates total booking sessions and total minutes (`SUM(TIMESTAMPDIFF(MINUTE, start_time, end_time))`) for each meeting room

#### Scenario: Retain zero-activity meeting rooms in results
- **WHEN** a meeting room has no valid reservations in the requested filter window
- **THEN** system includes the room in the result set with `bookingCount = 0` and `occupiedMinutes = 0`

#### Scenario: Exclude cancelled reservations from room utilization
- **WHEN** reservations on a given date have status `CANCELLED`
- **THEN** system excludes them from both booking count and occupied duration calculations

### Requirement: Department Usage Metrics Aggregation
The system SHALL aggregate the total booking count and total occupied duration (in minutes) for each organizational department within the selected time window. All active departments SHALL be included in the aggregation. Cancelled reservations SHALL NOT be counted.

#### Scenario: Compute department booking counts and occupied minutes
- **WHEN** dashboard metrics are computed for a given time window
- **THEN** system calculates total booking sessions and total minutes grouped by department

#### Scenario: Retain zero-activity departments in results
- **WHEN** a department has no reservations during the requested period
- **THEN** system includes the department in the result set with `bookingCount = 0` and `occupiedMinutes = 0`

### Requirement: Redis Dashboard Cache and Eviction Strategy
The system SHALL cache dashboard aggregation results in Redis to ensure high-performance response times (P95 <= 500ms). Cache keys SHALL follow the pattern `dashboard:stats:{dimension}:{period}` with a default TTL of 10 minutes. Any reservation write operation (create, cancel, early end) SHALL proactively evict or invalidate relevant cached dashboard keys.

#### Scenario: Cache hit returns cached dashboard payload
- **WHEN** dashboard data is requested and a valid non-expired entry exists in Redis for the given dimension and period
- **THEN** system returns the cached JSON payload without querying the database

#### Scenario: Cache eviction on reservation state change
- **WHEN** a reservation is created, cancelled, or terminated early
- **THEN** system deletes the corresponding date and month Redis cache keys (`dashboard:stats:DATE:{date}` and `dashboard:stats:MONTH:{month}`)

#### Scenario: Manual refresh button forces cache bypass
- **WHEN** user triggers a dashboard refresh action
- **THEN** system queries the database directly, updates the Redis cache with fresh results, and returns the newly computed metrics
