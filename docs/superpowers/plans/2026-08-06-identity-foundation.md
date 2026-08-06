# Identity Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a runnable and tested backend vertical slice for administrator login, paid guest provisioning, one-time guest activation, and guest login/logout using Sa-Token without Spring Security.

**Architecture:** Build a Java 25 / Spring Boot 4.1 modular monolith under `services/platform-api`. The `identity`, `admin`, `payment`, and `audit` packages own their data, while application services coordinate transactions. PostgreSQL stores durable identity and audit records, Redis stores Sa-Token sessions, and MyBatis-Plus provides single-table persistence. This slice deliberately excludes object storage, WeChat network calls, profile/review/media features, and Spring AI.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Modulith 2.1.0, Spring MVC, MyBatis-Plus 3.5.17, PostgreSQL 18, Redis 8, Flyway, Sa-Token 1.45.0, Bouncy Castle Argon2id, JUnit 5, Testcontainers, Maven Wrapper.

## Global Constraints

- [ ] Keep Spring Security and its transitive application APIs out of production code.
- [ ] Use MyBatis-Plus built-in CRUD or wrappers for simple single-table access.
- [ ] Do not create Mapper XML files. Any future complex join must use `@Select` or `@SelectProvider` and a typed result DTO.
- [ ] Never persist or log plaintext passwords, activation credentials, phone numbers, OpenID, or UnionID.
- [ ] Do not require object-storage, WeChat, SMS, email, or AI keys in this slice.
- [ ] Use `ApiResponse<T>` with a stable error code and request ID for public endpoints.
- [ ] Run each implementation change through a witnessed failing test before production code is added.

---

## Task 1: Repository and Java 25 Build Bootstrap

**Files:**

- Create: `.gitignore`
- Create: `.editorconfig`
- Create: `pom.xml`
- Create: `.mvn/wrapper/maven-wrapper.properties`
- Create: `mvnw`
- Create: `services/platform-api/pom.xml`
- Create: `services/platform-api/src/main/java/com/love/archive/PlatformApiApplication.java`
- Test: `services/platform-api/src/test/java/com/love/archive/PlatformApiApplicationTest.java`

- [x] Initialize Git on branch `feat/identity-foundation`; ignore `.toolchains/`, IDE files, build output, secrets, and local environment files.
- [x] Add a Maven parent and backend module pinned to Java 25, Spring Boot 4.1.0, Spring Modulith 2.1.0, MyBatis-Plus 3.5.17, and Sa-Token 1.45.0.
- [x] Add `PlatformApiApplicationTest` that loads the Spring context with database, Redis, Flyway, and Sa-Token autoconfiguration excluded; run it and record the expected compilation failure because the application class does not exist.
- [x] Add the minimal `@SpringBootApplication` class and test-only exclusions; run `./mvnw -pl services/platform-api test -Dtest=PlatformApiApplicationTest` and require success.
- [x] Commit the repository bootstrap.

## Task 2: Security Primitives

**Files:**

- Create: `services/platform-api/src/main/java/com/love/archive/identity/domain/PhoneNormalizer.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/security/PhoneProtector.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/security/PasswordHasher.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/security/Argon2PasswordHasher.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/security/InitialCredentialGenerator.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/config/IdentitySecurityProperties.java`
- Test: `services/platform-api/src/test/java/com/love/archive/identity/domain/PhoneNormalizerTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/identity/security/PhoneProtectorTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/identity/security/Argon2PasswordHasherTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/identity/security/InitialCredentialGeneratorTest.java`

- [x] Write failing tests for mainland China phone normalization and invalid input rejection.
- [x] Implement `PhoneNormalizer.normalize(String): String` and make its tests pass.
- [x] Write failing tests proving AES-256-GCM decrypts correctly, randomizes ciphertext, and a keyed HMAC index is deterministic.
- [x] Implement `PhoneProtector.encrypt(String): byte[]`, `decrypt(byte[]): String`, and `searchHash(String): String` using injected Base64 keys; make the tests pass.
- [x] Write failing tests for Argon2id hashing, matching, wrong-password rejection, and unique salts.
- [x] Implement `PasswordHasher` and `Argon2PasswordHasher` directly with Bouncy Castle; make the tests pass without importing Spring Security.
- [x] Write failing entropy/shape tests for one-time initial credentials, implement `InitialCredentialGenerator.generate(): String`, and make them pass.
- [x] Commit the security primitives.

## Task 3: PostgreSQL Schema and MyBatis-Plus Persistence

**Files:**

- Create: `services/platform-api/src/main/resources/db/migration/V1__identity_foundation.sql`
- Create: `services/platform-api/src/main/resources/application.yml`
- Create: `services/platform-api/src/test/resources/application-test.yml`
- Create: `services/platform-api/src/main/java/com/love/archive/common/persistence/MybatisPlusConfiguration.java`
- Create: `services/platform-api/src/main/java/com/love/archive/admin/domain/AdminStatus.java`
- Create: `services/platform-api/src/main/java/com/love/archive/admin/persistence/AdminUserEntity.java`
- Create: `services/platform-api/src/main/java/com/love/archive/admin/persistence/AdminUserMapper.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/domain/AccountStatus.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/persistence/UserAccountEntity.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/persistence/UserAccountMapper.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/persistence/ActivationCredentialEntity.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/persistence/ActivationCredentialMapper.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/persistence/ExternalIdentityEntity.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/persistence/ExternalIdentityMapper.java`
- Create: `services/platform-api/src/main/java/com/love/archive/payment/persistence/PaymentRecordEntity.java`
- Create: `services/platform-api/src/main/java/com/love/archive/payment/persistence/PaymentRecordMapper.java`
- Create: `services/platform-api/src/main/java/com/love/archive/audit/persistence/AuditLogEntity.java`
- Create: `services/platform-api/src/main/java/com/love/archive/audit/persistence/AuditLogMapper.java`
- Create: `services/platform-api/src/test/java/com/love/archive/testsupport/PostgresIntegrationTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/identity/persistence/IdentityPersistenceTest.java`

- [x] Write a failing Testcontainers-backed persistence test that starts PostgreSQL, runs Flyway, inserts an account through `BaseMapper`, and finds it through a Lambda Wrapper.
- [x] Add the six tables with primary keys, timestamps, state checks, unique phone HMAC, one-time activation constraints, external identity uniqueness, and append-oriented audit fields.
- [x] Add explicit MyBatis-Plus entities and `BaseMapper` interfaces; do not add service-implementation inheritance or Mapper XML.
- [x] Configure underscore-to-camel mapping, enum persistence, and disabled automatic schema mutation; make the persistence test pass against PostgreSQL.
- [x] Commit the database foundation.

## Task 4: Sa-Token Guest/Admin Isolation and Redis Sessions

**Files:**

- Create: `services/platform-api/src/main/java/com/love/archive/identity/config/AuthLogicConfiguration.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/security/AuthLogics.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/web/AuthInterceptorConfiguration.java`
- Create: `services/platform-api/src/test/java/com/love/archive/testsupport/RedisIntegrationTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/identity/security/AuthLogicIntegrationTest.java`

- [x] Write a failing Redis-backed integration test proving guest and admin can use the same numeric ID without sharing login state or token namespace.
- [x] Define named `StpLogic` instances with login types `guest` and `admin`, persisted through the Sa-Token Redis DAO.
- [x] Add route guards: `/api/v1/admin/**` requires admin login except `/auth/login`; `/api/v1/guest/**` requires guest login except `/auth/login` and `/auth/activate`.
- [x] Make isolation, logout, and Redis persistence tests pass.
- [x] Commit Sa-Token session integration.

## Task 5: Common HTTP Contract and Administrator Login

**Files:**

- Create: `services/platform-api/src/main/java/com/love/archive/common/web/ApiResponse.java`
- Create: `services/platform-api/src/main/java/com/love/archive/common/web/ApiException.java`
- Create: `services/platform-api/src/main/java/com/love/archive/common/web/GlobalExceptionHandler.java`
- Create: `services/platform-api/src/main/java/com/love/archive/common/web/RequestIdFilter.java`
- Create: `services/platform-api/src/main/java/com/love/archive/admin/application/AdminAuthService.java`
- Create: `services/platform-api/src/main/java/com/love/archive/admin/web/AdminAuthController.java`
- Create: `services/platform-api/src/main/java/com/love/archive/admin/web/AdminLoginRequest.java`
- Create: `services/platform-api/src/main/java/com/love/archive/admin/web/AdminSessionView.java`
- Create: `services/platform-api/src/main/java/com/love/archive/admin/config/AdminBootstrapProperties.java`
- Create: `services/platform-api/src/main/java/com/love/archive/admin/config/AdminBootstrapRunner.java`
- Test: `services/platform-api/src/test/java/com/love/archive/admin/web/AdminAuthApiTest.java`

- [x] Write failing API tests for valid login, uniform invalid-credential responses, disabled administrators, request IDs, and an HttpOnly `SameSite=Lax` cookie.
- [x] Implement transactional administrator authentication with Argon2id and the `admin` Sa-Token logic; return no password-related fields.
- [x] Add optional environment-driven first-admin bootstrap that stores only a password hash and refuses blank/default credentials.
- [x] Add the stable error envelope and request ID propagation; make the API tests pass.
- [x] Commit administrator authentication.

## Task 6: Paid Guest Provisioning

**Files:**

- Create: `services/platform-api/src/main/java/com/love/archive/identity/application/GuestProvisioningService.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/web/AdminGuestAccountController.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/web/CreateGuestAccountRequest.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/web/ProvisionedGuestView.java`
- Test: `services/platform-api/src/test/java/com/love/archive/identity/application/GuestProvisioningServiceTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/identity/web/AdminGuestAccountApiTest.java`

- [ ] Write failing service tests for atomic payment/account/activation/audit creation, duplicate phone rejection, and rollback on a duplicate payment reference.
- [ ] Implement `GuestProvisioningService.provision(...)` with one transaction; persist encrypted phone plus HMAC and only the Argon2id hash of the generated initial credential.
- [ ] Return the plaintext initial credential exactly once in `ProvisionedGuestView`; ensure it is absent from all subsequent reads and logs.
- [ ] Write failing API tests for admin authorization, validation, and duplicate handling, then implement `POST /api/v1/admin/accounts` and make them pass.
- [ ] Commit paid guest provisioning.

## Task 7: Guest Activation and Session APIs

**Files:**

- Create: `services/platform-api/src/main/java/com/love/archive/identity/application/GuestAuthService.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/web/GuestAuthController.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/web/ActivateGuestRequest.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/web/GuestLoginRequest.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/web/GuestSessionView.java`
- Test: `services/platform-api/src/test/java/com/love/archive/identity/application/GuestAuthServiceTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/identity/web/GuestAuthApiTest.java`

- [ ] Write failing service tests for successful activation, expired/used/wrong credentials, password policy, atomic status change, and audit creation.
- [ ] Implement activation so it verifies the initial Argon2id hash, saves the new Argon2id password hash, marks the credential consumed, changes status to `ACTIVE`, and writes audit in one transaction.
- [ ] Write failing API tests for activation, login, `/me`, logout, inactive account rejection, uniform login errors, and guest cookies.
- [ ] Implement `POST /api/v1/guest/auth/activate`, `POST /login`, `POST /logout`, and `GET /me`; make the tests pass.
- [ ] Commit guest activation and login.

## Task 8: Local Runtime, Security Checks, and Release Verification

**Files:**

- Create: `compose.yaml`
- Create: `.env.example`
- Create: `services/platform-api/Dockerfile`
- Create: `README.md`
- Create: `services/platform-api/src/test/java/com/love/archive/architecture/ModularityTest.java`
- Create: `services/platform-api/src/test/java/com/love/archive/architecture/SensitiveDataGuardTest.java`

- [ ] Write a failing Spring Modulith test for module-boundary verification; expose only deliberate application interfaces and make the test pass.
- [ ] Write a failing source/resource guard test that detects Mapper XML, Spring Security imports/dependencies, committed plaintext secret defaults, and logging of sensitive request fields; make it pass.
- [ ] Add local PostgreSQL/Redis Compose services, environment placeholders, container build, and operating instructions without real secrets.
- [ ] Run `./mvnw -pl services/platform-api test` on Java 25 and require zero failures.
- [ ] Run `./mvnw -pl services/platform-api package -DskipTests` and require a successful runnable JAR.
- [ ] Review `git diff --check`, repository status, dependency tree, and the OpenAPI endpoint surface; record any environmental limitation rather than claiming unverified success.
- [ ] Commit the verified identity-foundation slice.

## Deferred Follow-up Plans

- Guest profile fields, immutable revisions, and review workflow.
- Consent lifecycle, media metadata, object-storage adapter, and live-review workflow.
- uni-app guest H5 and Vue/Element Plus administrator UI.
- WeChat mini-program identity binding and `code2Session` adapter.
- Optional Spring AI module after privacy, prompt-versioning, and model-provider decisions.
