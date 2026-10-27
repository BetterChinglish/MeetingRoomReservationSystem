# Spec Delta

## Purpose

Manages administrative master data for meeting rooms and organizational departments, including availability status toggling, capacity and location attributes, and cascading validation on bookings.

## ADDED Requirements

### Requirement: Meeting Room Master Data Management
The system SHALL provide administrative management for meeting room resources, including name, location, capacity, and operational status (`ENABLED` or `DISABLED`). Room names SHALL be unique among active rooms. Deletion of meeting rooms referenced by existing reservations SHALL be performed via soft deletion (`deleted_at`).

#### Scenario: Admin creates meeting room
- **WHEN** admin submits a valid meeting room with name, location, capacity, and initial status `ENABLED`
- **THEN** system creates the record and returns HTTP 201 with generated room ID

#### Scenario: Admin toggles meeting room status
- **WHEN** admin changes the status of a room from `ENABLED` to `DISABLED`
- **THEN** system updates the room status and logs the modification timestamp

#### Scenario: Referenced meeting room soft deleted
- **WHEN** admin deletes a meeting room that has existing reservation records
- **THEN** system marks `deleted_at` timestamp rather than hard deleting the row, preserving data integrity for historical audit

### Requirement: Department Master Data Management
The system SHALL provide organizational department records containing department ID and name. Department names SHALL be unique among non-deleted records. Departments referenced by user accounts or reservations SHALL be protected against hard deletion via soft deletion.

#### Scenario: Admin creates new department
- **WHEN** admin submits a new unique department name
- **THEN** system creates the department record and returns HTTP 201 with generated department ID

#### Scenario: Department deletion with active references
- **WHEN** admin deletes a department that is linked to active users or reservations
- **THEN** system applies soft deletion by setting `deleted_at` and removes it from active dropdown selections

### Requirement: Public Read-Only Access to Master Data
The system SHALL allow all authenticated users (both employees and administrators) to retrieve active meeting rooms and departments for dropdown population and search filter criteria.

#### Scenario: Retrieve available meeting rooms for booking dropdown
- **WHEN** user requests the active meeting room selection list
- **THEN** system returns only rooms with `status = 'ENABLED'` and `deleted_at IS NULL`, including id, name, location, and capacity

#### Scenario: Retrieve all meeting rooms for management or filtering
- **WHEN** administrator requests full meeting room list with optional status filter
- **THEN** system returns matching rooms including both enabled and disabled rooms

#### Scenario: Retrieve department list
- **WHEN** user requests active department list
- **THEN** system returns all active departments sorted by display order or ID

### Requirement: Reservation Cascading Impact on Room Status
The system SHALL prevent new reservation creation for any meeting room currently set to `DISABLED`. Disabling a meeting room SHALL NOT automatically cancel existing pending or active reservations, but the system SHALL display a disabled warning indicator on reservation records for administrative visibility.

#### Scenario: Reject reservation creation for disabled room
- **WHEN** user attempts to create a reservation for a meeting room with `status = 'DISABLED'`
- **THEN** system rejects the booking request with HTTP 400 and error message "Meeting room is currently disabled"

#### Scenario: Preserving existing reservations on room disablement
- **WHEN** an admin sets a meeting room status to `DISABLED` while pending reservations exist
- **THEN** system maintains existing reservations in `PENDING` status without automatic cancellation
