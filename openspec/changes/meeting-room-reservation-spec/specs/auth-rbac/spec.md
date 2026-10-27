# Spec Delta

## Purpose

Provides secure user authentication, BCrypt credential verification, brute-force attack prevention, JWT session management, and dual-role (EMPLOYEE vs ADMIN) access control for the meeting room reservation system.

## ADDED Requirements

### Requirement: User Authentication and Credential Validation
The system SHALL authenticate users using their username and password. The system SHALL convert usernames to lowercase for storage and comparison, verify passwords against BCrypt hashes, and reject unauthenticated requests to protected endpoints.

#### Scenario: Successful login with valid credentials
- **WHEN** user submits valid username and password matching an active user record
- **THEN** system returns HTTP 200 with a valid JWT token and user profile containing userId, username, displayName, role, and deptId

#### Scenario: Login failed with invalid credentials
- **WHEN** user submits an invalid username or incorrect password
- **THEN** system returns HTTP 401 with generic error message "Invalid username or password" and increments the failure counter

#### Scenario: Username case insensitivity during login
- **WHEN** user submits a username with uppercase or mixed-case letters matching an existing lowercase username
- **THEN** system matches the user record and processes authentication successfully

### Requirement: Brute Force Protection and Account Lockout
The system SHALL record consecutive failed login attempts in Redis. When an account accumulates 5 consecutive failed attempts, the system SHALL lock the account for 15 minutes. When an IP address accumulates 10 failed attempts within 10 minutes, the system SHALL reject requests from that IP for 10 minutes.

#### Scenario: Account lockout after 5 consecutive failures
- **WHEN** an account records 5 consecutive failed login attempts within 15 minutes
- **THEN** system rejects subsequent login attempts for that username with HTTP 423 or error message "Account locked. Please try again later", even if correct password is provided

#### Scenario: IP address rate limiting
- **WHEN** an IP address triggers 10 failed login attempts across any accounts within 10 minutes
- **THEN** system temporarily rejects login requests from that IP address with HTTP 429

#### Scenario: Successful login resets failure counter
- **WHEN** user successfully logs in before reaching the maximum lockout threshold
- **THEN** system clears the consecutive failure count for that username in Redis

### Requirement: JWT Token Issuance and Session Invalidation
The system SHALL issue an HMAC-SHA256 (HS256) signed JWT token upon successful login with an 8-hour expiration period. The token payload SHALL include iss, sub (userId), username, role, deptId, iat, exp, and a unique jti UUID. The system SHALL support immediate server-side logout by recording the jti in a Redis blacklist until its expiration.

#### Scenario: Protected endpoint token verification
- **WHEN** client sends a request to a protected `/api/**` endpoint with valid `Authorization: Bearer <token>`
- **THEN** system verifies the signature, expiration, and blacklist status, and permits the request

#### Scenario: Expired or blacklisted token rejected
- **WHEN** client presents an expired token or a token whose jti is present in the Redis blacklist
- **THEN** system rejects the request with HTTP 401 Unauthorized

#### Scenario: User explicit logout
- **WHEN** authenticated user calls `POST /api/auth/logout`
- **THEN** system extracts the jti, stores it in Redis blacklist with TTL equal to the token remaining lifespan, and returns HTTP 200

### Requirement: Dual-Role Authorization Enforcement
The system SHALL enforce dual-role authorization区分 `EMPLOYEE` and `ADMIN`. Protected administrative endpoints SHALL require `role == 'ADMIN'`. The system SHALL inject authenticated user context into request processing and prevent horizontal or vertical privilege escalation.

#### Scenario: Non-admin user blocked from administrative endpoints
- **WHEN** user with role `EMPLOYEE` calls an admin-only endpoint (such as room status toggle)
- **THEN** system rejects the request with HTTP 403 Forbidden

#### Scenario: Authenticated identity injected from token
- **WHEN** user submits a request requiring creator identification (such as creating a reservation)
- **THEN** system resolves the operator's userId directly from the verified JWT context, ignoring any conflicting userId in request parameters
