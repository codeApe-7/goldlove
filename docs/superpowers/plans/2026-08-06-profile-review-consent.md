# Profile Review and Consent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a tested backend vertical slice in which a paid guest accepts the exact authorization document shown before payment, saves a structured profile, submits an immutable revision, and an administrator approves or rejects it without overwriting the last approved revision.

**Architecture:** Add closed Spring Modulith modules `consent`, `guest`, and `review`, connected only through named application interfaces. PostgreSQL owns durable authorization evidence, editable profile state, immutable revision snapshots, and append-only review records; simple single-table persistence uses MyBatis-Plus Lambda Wrappers, while review joins use typed `@Select`/`@SelectProvider` mappers. Sa-Token supplies guest/admin identities, and a shared AES-GCM/HMAC component protects profile identifiers and consent evidence.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Modulith 2.1.0, Spring MVC, MyBatis-Plus 3.5.17, PostgreSQL 18, Flyway, Sa-Token 1.45.0, Lombok, JUnit 5, Testcontainers, Maven Wrapper.

## Global Constraints

- Keep Spring Security out of production code; Sa-Token remains the only authentication/session framework.
- Use Lombok `@Getter`, `@Setter`, and `@RequiredArgsConstructor`; do not use entity `@Data` or `@ToString`.
- Use MyBatis-Plus built-in CRUD and Lambda Wrappers for simple single-table access.
- Override the generic MyBatis-Plus skill default for this project: complex joins and dynamic review filters must use `@Select` or `@SelectProvider`, never Mapper XML.
- Map every persisted field explicitly to the existing snake_case column when ambiguity exists; rely on the configured underscore-to-camel mapping only for exact conventional pairs.
- Never expose persistence entities or `Map<String, Object>` through controllers; use typed request, command, view, and query DTOs.
- Encrypt WeChat/Douyin identifiers with AES-256-GCM and derive lookup/evidence HMACs from domain-prefixed normalized values.
- Do not persist or log plaintext phone numbers, passwords, activation credentials, WeChat IDs, Douyin IDs, session tokens, or source IP addresses.
- Require `Idempotency-Key` on profile submission; bind a key to one normalized request digest.
- Do not add photos, object storage, authorization withdrawal/renewal, RBAC, mini-program login, AI, or frontend code in this slice.
- Keep all writes that span multiple tables inside one `@Transactional` application-service operation.
- Every production change must be preceded by a witnessed failing test and followed by the narrow test, the affected module suite, and a focused commit.

---

## File Map

### Shared security and identity/payment integration

- `common/security/SensitiveValueProtector.java`: reusable versioned AES-GCM plus domain-separated HMAC.
- `common/security/SensitiveSecurityProperties.java`: `PROFILE_ENCRYPTION_KEY` and `PROFILE_HMAC_KEY` binding.
- `common/security/SensitiveSecurityConfiguration.java`: constructs the protector with the existing `SecureRandom` bean.
- `identity/application/GuestAccountStatusQuery.java`: named interface for transactional `ACTIVE` checks.
- `payment/application/PaymentAuthorizationEvidence.java`: named read interface exposing the paid authorization document ID for one account.
- `payment/application/PaidPayment.java`: carries `presentedAuthorizationDocumentId` into payment persistence.

### Consent module

- `consent/domain/AuthorizationDocumentStatus.java`: `DRAFT`, `ACTIVE`, and `RETIRED` persistence enum.
- `consent/persistence/AuthorizationDocumentEntity.java` and `AuthorizationRecordEntity.java`: authorization rows.
- `consent/persistence/AuthorizationDocumentMapper.java` and `AuthorizationRecordMapper.java`: thin `BaseMapper` interfaces.
- `consent/application/AuthorizationDocumentQuery.java`: resolves current and account-visible document versions.
- `consent/application/ConsentService.java`: records active opt-in evidence.
- `consent/application/ConsentEligibility.java`: verifies same-document, unexpired consent during submission.
- `consent/web/AuthorizationDocumentController.java` and `ConsentController.java`: public current-document and authenticated consent endpoints.

### Guest module

- `guest/domain/ProfileStatus.java`, `FieldStorageKind.java`, and `ProfileFieldType.java`: persisted profile/field enums.
- `guest/persistence/GuestProfileEntity.java`, `ProfileFieldDefinitionEntity.java`, and `ProfileFieldValueEntity.java`: draft rows.
- `guest/persistence/GuestProfileMapper.java`, `ProfileFieldDefinitionMapper.java`, and `ProfileFieldValueMapper.java`: thin `BaseMapper` interfaces.
- `guest/application/GuestProfileDraftService.java`: current-account draft read/save.
- `guest/application/GuestProfileSnapshotProvider.java`: validates and supplies an immutable submission snapshot.
- `guest/application/GuestProfileApprovalPort.java`: applies review outcome without exposing guest mappers.
- `guest/web/GuestProfileController.java` and `AdminProfileFieldDefinitionController.java`: guest draft/status and admin field-definition endpoints.

### Review module

- `review/domain/RevisionStatus.java` and `ReviewResult.java`: persisted revision/review enums.
- `review/persistence/ProfileRevisionEntity.java`, `ProfileRevisionFieldValueEntity.java`, and `ProfileReviewRecordEntity.java`: immutable snapshot/review rows.
- `review/persistence/ProfileRevisionMapper.java`, `ProfileRevisionFieldValueMapper.java`, and `ProfileReviewRecordMapper.java`: thin `BaseMapper` interfaces.
- `review/persistence/ProfileReviewQueryMapper.java` and `ProfileReviewSqlProvider.java`: typed annotated projections and dynamic SQL.
- `review/application/ProfileSubmissionService.java`: idempotent snapshot creation.
- `review/application/ProfileReviewService.java`: paged queries plus transactional approve/reject.
- `review/web/GuestProfileSubmissionController.java` and `AdminProfileReviewController.java`: guest submission/revision and administrator review endpoints.

### Database and tests

- `db/migration/V3__profile_review_consent.sql`: all new tables, constraints, indexes, seed document/core fields, old-table extension, and runtime grants.
- `testsupport/ApiIntegrationTest.java`: includes new tables in deterministic cleanup order and supplies test crypto keys.
- Module-specific unit/integration/API tests mirror each production package.
- Architecture guards verify module boundaries, absence of XML/Spring Security, safe Lombok, and no sensitive logging.

## Stable Error Contract

```text
AUTHORIZATION_DOCUMENT_NOT_FOUND              404
AUTHORIZATION_DOCUMENT_NOT_ACTIVE             409
CONSENT_REQUIRED                              409
CONSENT_EXPIRED                               409
PREPAYMENT_AUTHORIZATION_EVIDENCE_MISSING     409
PROFILE_NOT_STARTED                           409
PROFILE_VALIDATION_FAILED                     400
PROFILE_VERSION_CONFLICT                      409
PROFILE_REVIEW_IN_PROGRESS                    409
PROFILE_REVISION_NOT_FOUND                    404
PROFILE_REVISION_FORBIDDEN                    403 (reserved for an authenticated privileged flow;
                                                    guest ownership checks return NOT_FOUND)
PROFILE_REVIEW_ALREADY_COMPLETED              409
IDEMPOTENCY_KEY_REQUIRED                      400
IDEMPOTENCY_KEY_REUSED                        409
FIELD_DEFINITION_IMMUTABLE                    409
FIELD_VALUE_INVALID                           400
```

All failures use the existing `ApiResponse<Void>` envelope and request ID. Constraint names are translated inside application services; SQL details, ciphertext, key material, document existence outside the caller's scope, and internal stack traces never cross the API boundary.

---

### Task 1: Database Foundation and Shared Sensitive-Value Protection

**Files:**

- Create: `services/platform-api/src/main/resources/db/migration/V3__profile_review_consent.sql`
- Create: `services/platform-api/src/main/java/com/love/archive/common/security/package-info.java`
- Create: `services/platform-api/src/main/java/com/love/archive/common/security/SensitiveSecurityProperties.java`
- Create: `services/platform-api/src/main/java/com/love/archive/common/security/SensitiveSecurityConfiguration.java`
- Create: `services/platform-api/src/main/java/com/love/archive/common/security/SensitiveValueProtector.java`
- Create: `services/platform-api/src/main/java/com/love/archive/consent/domain/AuthorizationDocumentStatus.java`
- Create: `services/platform-api/src/main/java/com/love/archive/consent/persistence/AuthorizationDocumentEntity.java`
- Create: `services/platform-api/src/main/java/com/love/archive/consent/persistence/AuthorizationDocumentMapper.java`
- Create: `services/platform-api/src/main/java/com/love/archive/consent/persistence/AuthorizationRecordEntity.java`
- Create: `services/platform-api/src/main/java/com/love/archive/consent/persistence/AuthorizationRecordMapper.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/domain/ProfileStatus.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/domain/FieldStorageKind.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/domain/ProfileFieldType.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/persistence/GuestProfileEntity.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/persistence/GuestProfileMapper.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/persistence/ProfileFieldDefinitionEntity.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/persistence/ProfileFieldDefinitionMapper.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/persistence/ProfileFieldValueEntity.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/persistence/ProfileFieldValueMapper.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/domain/RevisionStatus.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/domain/ReviewResult.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/persistence/ProfileRevisionEntity.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/persistence/ProfileRevisionMapper.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/persistence/ProfileRevisionFieldValueEntity.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/persistence/ProfileRevisionFieldValueMapper.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/persistence/ProfileReviewRecordEntity.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/persistence/ProfileReviewRecordMapper.java`
- Modify: `services/platform-api/src/main/resources/application.yml`
- Modify: `services/platform-api/src/test/resources/application-test.yml`
- Modify: `services/platform-api/src/test/java/com/love/archive/testsupport/ApiIntegrationTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/common/security/SensitiveValueProtectorTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/profile/ProfileConsentPersistenceTest.java`

**Interfaces:**

- Produces: `byte[] SensitiveValueProtector.encrypt(String domain, String plaintext)`
- Produces: `String SensitiveValueProtector.decrypt(String domain, byte[] ciphertext)`
- Produces: `String SensitiveValueProtector.hmac(String domain, String normalizedValue)`
- Produces: mapped tables `authorization_document`, `authorization_record`, `guest_profile`, `profile_field_definition`, `profile_field_value`, `profile_revision`, `profile_revision_field_value`, and `profile_review_record`
- Produces: nullable FK `payment_record.presented_authorization_document_id`

- [ ] **Step 1: Write failing crypto and migration tests**

```java
@Test
void separatesDomainsAndRandomizesCiphertext() {
    byte[] first = protector.encrypt("profile:wechat-id", "wx-alice");
    byte[] second = protector.encrypt("profile:wechat-id", "wx-alice");
    assertThat(first).isNotEqualTo(second);
    assertThat(protector.decrypt("profile:wechat-id", first)).isEqualTo("wx-alice");
    assertThatThrownBy(() -> protector.decrypt("profile:douyin-id", first))
            .isInstanceOf(IllegalArgumentException.class);
    assertThat(protector.hmac("consent:ip", "203.0.113.8"))
            .isNotEqualTo(protector.hmac("profile:wechat-id", "203.0.113.8"));
}

@Test
void seedsAuthorizationAndCoreFieldsAndEnforcesOnePendingRevision() {
    assertThat(jdbc.queryForObject(
            "select count(*) from authorization_document where status='ACTIVE'", Integer.class))
            .isEqualTo(1);
    assertThat(jdbc.queryForObject(
            "select count(*) from profile_field_definition where storage_kind='CORE'", Integer.class))
            .isEqualTo(7);
    jdbc.update("INSERT INTO profile_revision "
            + "(guest_profile_id, revision_number, status, submitted_by_account_id, "
            + "submitted_at, review_deadline_at, submission_key_hmac, request_payload_sha256) "
            + "VALUES (?, 1, 'PENDING', ?, now(), now() + interval '24 hours', ?, ?)",
            profileId, accountId, "key-one", "a".repeat(64));
    assertThatThrownBy(() -> jdbc.update("INSERT INTO profile_revision "
            + "(guest_profile_id, revision_number, status, submitted_by_account_id, "
            + "submitted_at, review_deadline_at, submission_key_hmac, request_payload_sha256) "
            + "VALUES (?, 2, 'PENDING', ?, now(), now() + interval '24 hours', ?, ?)",
            profileId, accountId, "key-two", "b".repeat(64)))
            .hasMessageContaining("uq_profile_revision_pending");
}
```

- [ ] **Step 2: Run the narrow tests and witness failure**

```bash
./mvnw -pl services/platform-api test \
  -Dtest=SensitiveValueProtectorTest,ProfileConsentPersistenceTest
```

Expected: compilation fails because the protector and mapped schema do not exist.

- [ ] **Step 3: Add the complete PostgreSQL schema and immutable seed data**

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE authorization_document (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    document_code VARCHAR(64) NOT NULL,
    version VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
    effective_at TIMESTAMPTZ NOT NULL,
    created_by_admin_id BIGINT REFERENCES admin_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (document_code, version)
);
CREATE UNIQUE INDEX uq_authorization_document_active
    ON authorization_document(document_code) WHERE status = 'ACTIVE';

ALTER TABLE payment_record
    ADD COLUMN presented_authorization_document_id BIGINT
    REFERENCES authorization_document(id);

CREATE TABLE authorization_record (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_account_id BIGINT NOT NULL REFERENCES user_account(id),
    authorization_document_id BIGINT NOT NULL REFERENCES authorization_document(id),
    accepted BOOLEAN NOT NULL CHECK (accepted),
    accepted_at TIMESTAMPTZ NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at = effective_at + INTERVAL '1 year'),
    source_page VARCHAR(64) NOT NULL,
    client_ip_hmac VARCHAR(64) NOT NULL,
    user_agent_sha256 CHAR(64) NOT NULL,
    session_reference_hmac VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (effective_at = accepted_at)
);
CREATE INDEX ix_authorization_record_eligibility
    ON authorization_record(user_account_id, authorization_document_id, expires_at DESC);
```

In that migration, create the remaining tables with these exact persistence rules:

```text
guest_profile:
  unique profile_no UUID; unique user_account_id FK; nullable seven core fields;
  wechat_id_ciphertext/HMAC and douyin_id_ciphertext/HMAC paired by CHECK;
  encrypted nickname/profile URL; approved/pending revision pointers; status; version; timestamps.
profile_field_definition:
  unique field_code; storage_kind; data_type; required/enabled; options_json TEXT;
  sort_order; instructions; version; timestamps.
profile_field_value:
  unique profile+definition; text_value, integer_value, decimal_value, date_value,
  boolean_value, option_value; num_nonnulls(text_value, integer_value, decimal_value,
  date_value, boolean_value, option_value) = 1; timestamps.
profile_revision:
  unique profile+revision_number and profile+submission_key_hmac; fixed-field snapshot;
  protected identifier snapshot; status; submitter/time/deadline/reviewed time;
  request digest; version; created_at; partial unique PENDING index.
profile_revision_field_value:
  unique revision+field_code; field label/type/display option snapshot;
  text_value, integer_value, decimal_value, date_value, boolean_value, option_value;
  num_nonnulls(text_value, integer_value, decimal_value, date_value, boolean_value,
  option_value) = 1; created_at.
profile_review_record:
  unique revision FK; reviewer FK; result; nullable reason_code; required comment for REJECTED;
  reviewed_at; request_id; created_at; trigger rejecting UPDATE and DELETE.
```

Add the two revision pointer foreign keys only after `profile_revision` exists. Index every FK plus review status/deadline, profile status, field-definition enabled/sort, and revision-field revision ID. Create the seven core definitions with stable codes `gender`, `birth_date`, `height_cm`, `education`, `occupation`, `income_range`, and `city`.

Seed document code `PAID_PROFILE_LIVE_CONTENT`, version `v0.3`, and title `付费建档与直播内容授权书` with this exact content (paragraph breaks preserved):

```text
我已阅读并同意《付费建档与直播内容授权书》。

我知悉并同意，本次付费服务是由婚恋档案建立与维护、一次朋友圈锐评直播机会，以及经审核和去标识化处理后的直播内容和宣传素材使用共同组成的组合服务。

我同意平台收集、存储和使用本人提交的手机号、微信号、抖音号、个人资料、生活照片及与朋友圈锐评有关的信息；同意工作人员添加本人微信，在本人设置的可见范围内查看、筛选和整理朋友圈内容；同意平台对相关内容进行必要的截图、打码、裁剪和编辑，并用于一次朋友圈锐评直播、直播回顾、抖音短视频及与本平台婚恋服务直接相关的宣传素材。

平台在公开使用前，应当遮挡或删除本人真实姓名、手机号、微信号、抖音号、二维码、精确地址、工作单位、车牌及其他能够直接识别身份的信息，并对朋友圈中出现的其他人员头像、昵称、评论、照片及相关信息进行遮挡或裁剪。

我已知悉，即使完成打码和去标识化处理，公开直播仍可能存在被观众录屏、转发或根据内容间接识别的风险。

未经本授权书明确约定，平台不会出售本人的个人信息，也不会将相关资料用于与建档、朋友圈锐评和本平台婚恋服务宣传无关的用途。

本授权自本人主动勾选同意之日起生效，有效期为一年。授权到期后，本人可以重新查看并主动确认当时版本的授权书，以完成续期。如本人未续期，平台不得使用本人的原始资料制作新的直播、视频或宣传素材；授权有效期内已经发布的视频可以保留在原发布位置，但不得重新剪辑、重新发布或用于新的推广活动。

如不同意上述组合服务内容，将无法建立档案。平台应在本人付款前提供本授权说明，以便本人自主决定是否购买。本人可以联系管理员撤回对未来直播和素材使用的授权；撤回不影响撤回前已经依法完成的处理，后续账号和服务将按照服务协议办理终止、退款或资料删除。
```

Compute `content_sha256` inside the insert with `encode(digest(convert_to(content, 'UTF8'), 'sha256'), 'hex')` so content and digest cannot diverge.

Grant the runtime role as follows:

```text
authorization_document: SELECT
authorization_record: SELECT, INSERT
guest_profile: SELECT, INSERT, UPDATE
profile_field_definition: SELECT, INSERT, UPDATE
profile_field_value: SELECT, INSERT, UPDATE, DELETE
profile_revision: SELECT, INSERT, UPDATE
profile_revision_field_value: SELECT, INSERT
profile_review_record: SELECT, INSERT
payment_record: retain existing SELECT, INSERT, UPDATE
all new identity sequences: USAGE, SELECT
```

`DELETE` is granted only on current draft dynamic values so a guest can clear a draft field; immutable revision values preserve history. Do not grant `TRUNCATE`, schema creation, or DDL. Add immutable-row triggers to `authorization_record`, `profile_revision_field_value`, and `profile_review_record`; add a revision trigger that permits changes only to `status`, `reviewed_at`, and `version` after insertion.

- [ ] **Step 4: Implement domain-bound AES-GCM/HMAC and safe entities**

```java
byte[] encrypt(String domain, String plaintext)
String decrypt(String domain, byte[] ciphertext)
String hmac(String domain, String normalizedValue)
```

The concrete class must encode one format byte, a fresh 12-byte nonce, and AES-GCM ciphertext; call `cipher.updateAAD(domain.getBytes(UTF_8))` for both encryption and decryption. HMAC input is the UTF-8 sequence `domain + ':' + normalizedValue`, encoded as unpadded Base64 URL. Reject blank domains/values, malformed version/length, wrong AAD, and keys shorter than 32 bytes.

Use `@Getter/@Setter`, `@TableName`, `@TableId(type = IdType.AUTO)`, `@TableField`, and `@Version` on mapped entities. Implement defensive copies in every `byte[]` getter/setter exactly as existing identity entities do. Do not add `equals`, `hashCode`, or generated string representations containing protected fields.

- [ ] **Step 5: Run the narrow and architecture tests**

```bash
./mvnw -pl services/platform-api test \
  -Dtest=SensitiveValueProtectorTest,ProfileConsentPersistenceTest,LombokEntitySafetyTest,SensitiveDataGuardTest
```

Expected: all tests pass; PostgreSQL rejects invalid states and the runtime role has only declared privileges.

- [ ] **Step 6: Commit the database and protection foundation**

```bash
git add services/platform-api/src
git commit -m "feat: add profile consent persistence foundation"
```

---

### Task 2: Authorization Documents and Payment Evidence

**Files:**

- Create: `services/platform-api/src/main/java/com/love/archive/consent/package-info.java`
- Create: `services/platform-api/src/main/java/com/love/archive/consent/application/package-info.java`
- Create: `services/platform-api/src/main/java/com/love/archive/consent/application/AuthorizationDocumentQuery.java`
- Create: `services/platform-api/src/main/java/com/love/archive/consent/application/DatabaseAuthorizationDocumentQuery.java`
- Create: `services/platform-api/src/main/java/com/love/archive/consent/application/AuthorizationDocumentView.java`
- Create: `services/platform-api/src/main/java/com/love/archive/consent/web/AuthorizationDocumentController.java`
- Create: `services/platform-api/src/main/java/com/love/archive/payment/application/PaymentAuthorizationEvidence.java`
- Create: `services/platform-api/src/main/java/com/love/archive/payment/application/PresentedAuthorization.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/payment/application/PaidPayment.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/payment/application/DatabasePaymentRecorder.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/payment/persistence/PaymentRecordEntity.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/identity/application/GuestProvisioningService.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/identity/web/CreateGuestAccountRequest.java`
- Modify: existing provisioning service/API tests
- Test: `services/platform-api/src/test/java/com/love/archive/consent/web/AuthorizationDocumentApiTest.java`

**Interfaces:**

- Produces: `AuthorizationDocumentView AuthorizationDocumentQuery.requireActive(String documentCode, String version)`
- Produces: `AuthorizationDocumentView AuthorizationDocumentQuery.current(String documentCode)`
- Produces: `AuthorizationDocumentView AuthorizationDocumentQuery.requireVisibleToGuest(long accountId, String documentCode, String version)`
- Produces: `Optional<PresentedAuthorization> PaymentAuthorizationEvidence.findPaidAuthorization(long accountId)`
- Consumes: `PaidPayment(Long userAccountId, String paymentReference, Long amountMinor, OffsetDateTime paidAt, Long operatorAdminId, Long presentedAuthorizationDocumentId, String note, OffsetDateTime createdAt)`

Declare `consent` as a closed module with `allowedDependencies = {"common::web", "common::security", "audit::application", "payment::application", "identity::security"}` and expose only `consent.application` through `@NamedInterface("application")`.

- [ ] **Step 1: Write failing document visibility and provisioning tests**

```java
@Test
void exposesOnlyTheActiveDocumentPublicly() throws Exception {
    mockMvc.perform(get("/api/v1/public/authorization-documents/current"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.version").value("v0.3"))
            .andExpect(jsonPath("$.data.contentSha256").isNotEmpty());
}

@Test
void provisioningRequiresAndPersistsTheActivePresentedVersion() {
    ProvisionedGuestView result = service.provision(
            adminId, phone, paymentReference, 19900L, paidAt, "v0.3", note, requestId);
    assertThat(paymentMapper.selectByAccountId(result.accountId())
            .getPresentedAuthorizationDocumentId()).isNotNull();
}
```

Create these additional test methods with one focused assertion each:

```java
@Test void rejectsUnknownAuthorizationVersionWithDocumentNotFound();
@Test void rejectsDraftAuthorizationVersionWithDocumentNotActive();
@Test void reportsMissingEvidenceForHistoricalNullPaymentVersion();
@Test void requiresLoginForRetiredDocumentVersion();
@Test void letsGuestReadRetiredVersionReferencedByOwnPaidRecord();
```

- [ ] **Step 2: Run tests and witness failure**

```bash
./mvnw -pl services/platform-api test \
  -Dtest=AuthorizationDocumentApiTest,GuestProvisioningServiceTest,AdminGuestAccountApiTest
```

Expected: compilation or assertion failure because the authorization version is not yet accepted or persisted.

- [ ] **Step 3: Implement typed document and payment interfaces**

```java
public interface AuthorizationDocumentQuery {
    AuthorizationDocumentView current(String documentCode);
    AuthorizationDocumentView requireActive(String documentCode, String version);
    AuthorizationDocumentView requireVisibleToGuest(long accountId, String documentCode, String version);
}

public record AuthorizationDocumentView(
        long id, String documentCode, String version, String title,
        String content, String contentSha256, OffsetDateTime effectiveAt) {}

public interface PaymentAuthorizationEvidence {
    Optional<PresentedAuthorization> findPaidAuthorization(long accountId);
}

public record PresentedAuthorization(long paymentRecordId, long authorizationDocumentId) {}
```

Implement document lookups with `AuthorizationDocumentMapper.selectOne(Wrappers.<AuthorizationDocumentEntity>lambdaQuery().eq(AuthorizationDocumentEntity::getDocumentCode, documentCode).eq(AuthorizationDocumentEntity::getVersion, version))`. Implement payment lookup with a Lambda Wrapper filtered by `userAccountId` and `PAID`, ordered by `paidAt DESC, id DESC`, limited to one. Keep wrappers inside application services.

- [ ] **Step 4: Require the version at guest provisioning**

```java
public record CreateGuestAccountRequest(
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Size(max = 100) String paymentReference,
        @NotNull @Positive Long amountMinor,
        @NotNull @PastOrPresent OffsetDateTime paidAt,
        @NotBlank @Size(max = 32) String authorizationDocumentVersion,
        @Size(max = 500) String note) {}
```

Resolve `PAID_PROFILE_LIVE_CONTENT + authorizationDocumentVersion` through `requireActive` before inserting the account. Pass its numeric ID to `PaidPayment`. Preserve atomic rollback and the existing duplicate-phone/payment-reference behavior.

- [ ] **Step 5: Run affected tests**

```bash
./mvnw -pl services/platform-api test \
  -Dtest=AuthorizationDocumentApiTest,GuestProvisioningServiceTest,AdminGuestAccountApiTest,ModularityTest
```

Expected: public current document, authenticated referenced retired version, and provisioning evidence tests pass.

- [ ] **Step 6: Commit authorization presentation evidence**

```bash
git add services/platform-api/src
git commit -m "feat: bind payments to authorization versions"
```

---

### Task 3: Guest Active Consent Evidence

**Files:**

- Create: `services/platform-api/src/main/java/com/love/archive/consent/application/ConsentService.java`
- Create: `services/platform-api/src/main/java/com/love/archive/consent/application/ConsentEligibility.java`
- Create: `services/platform-api/src/main/java/com/love/archive/consent/application/ConsentEvidenceCommand.java`
- Create: `services/platform-api/src/main/java/com/love/archive/consent/application/ConsentView.java`
- Create: `services/platform-api/src/main/java/com/love/archive/consent/web/ConsentController.java`
- Create: `services/platform-api/src/main/java/com/love/archive/consent/web/AcceptConsentRequest.java`
- Create: `services/platform-api/src/main/java/com/love/archive/consent/web/ClientEvidenceResolver.java`
- Test: `services/platform-api/src/test/java/com/love/archive/consent/application/ConsentServiceTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/consent/web/ConsentApiTest.java`

**Interfaces:**

- Produces: `ConsentView ConsentService.accept(long accountId, ConsentEvidenceCommand command)`
- Produces: `Optional<ConsentView> ConsentService.current(long accountId)`
- Produces: `void ConsentEligibility.requireValid(long accountId, long authorizationDocumentId, OffsetDateTime now)`
- Consumes: `PaymentAuthorizationEvidence.findPaidAuthorization(long)`
- Consumes: `SensitiveValueProtector.hmac(domain, value)`

- [ ] **Step 1: Write failing service and API tests**

```java
@Test
void recordsServerTimedOneYearConsentForThePaidVersion() {
    ConsentView consent = service.accept(accountId, new ConsentEvidenceCommand(
            "v0.3", true, "guest-activation", "203.0.113.8", "Mozilla/5.0", "token-value"));
    assertThat(consent.effectiveAt()).isEqualTo(consent.acceptedAt());
    assertThat(consent.expiresAt()).isEqualTo(consent.effectiveAt().plusYears(1));
    AuthorizationRecordEntity stored = recordMapper.selectById(consent.id());
    assertThat(stored.getClientIpHmac()).doesNotContain("203.0.113.8");
    assertThat(stored.getSessionReferenceHmac()).doesNotContain("token-value");
}
```

Create these additional test methods:

```java
@Test void rejectsAcceptedFalseAtValidationBoundary();
@Test void rejectsSourcePageOutsideWhitelist();
@Test void rejectsDocumentDifferentFromPaidEvidence();
@Test void rejectsRetiredDocumentNotReferencedByOwnPayment();
@Test void returnsExistingUnexpiredConsentForRepeatedAcceptance();
@Test void treatsConsentAsExpiredAtExactExpiryInstant();
@Test void ignoresForwardedForWithoutTrustedProxyConfiguration();
@Test void requiresGuestLoginForConsentEndpoints();
```

- [ ] **Step 2: Run tests and witness failure**

```bash
./mvnw -pl services/platform-api test -Dtest=ConsentServiceTest,ConsentApiTest
```

Expected: compilation failure because consent services/controllers do not exist.

- [ ] **Step 3: Implement evidence capture and eligibility**

```java
public record ConsentEvidenceCommand(
        String authorizationDocumentVersion,
        boolean accepted,
        String sourcePage,
        String clientIp,
        String userAgent,
        String sessionReference) {}

@Transactional
public ConsentView accept(long accountId, ConsentEvidenceCommand command) {
    validateAccepted(command.accepted());
    validateSourcePage(command.sourcePage());
    AuthorizationDocumentView document = documentQuery.requireVisibleToGuest(
            accountId, DOCUMENT_CODE, command.authorizationDocumentVersion());
    requirePaidDocument(accountId, document.id());
    OffsetDateTime now = OffsetDateTime.now(clock);
    ConsentView existing = findValid(accountId, document.id(), now);
    if (existing != null) return existing;
    return appendEvidenceAndAudit(accountId, document, command, now, now.plusYears(1));
}
```

Whitelist `guest-activation`, `guest-profile`, and `guest-consent`. Hash IP using domain `consent:ip`, SHA-256 the normalized User-Agent, and HMAC the Sa-Token value using `consent:session`. Resolve forwarded IP only when Spring's trusted forward-header strategy is explicitly enabled; otherwise use the servlet remote address.

- [ ] **Step 4: Implement endpoints without accepting identity/evidence overrides**

```java
public record AcceptConsentRequest(
        @NotBlank @Size(max = 32) String authorizationDocumentVersion,
        @AssertTrue boolean accepted,
        @NotBlank @Size(max = 64) String sourcePage) {}
```

`POST /api/v1/guest/consents` obtains account ID and token from guest `StpLogic`, and transport evidence from `HttpServletRequest`; it never accepts `userAccountId`, IP, User-Agent, timestamps, or session reference in JSON. `GET /api/v1/guest/consents/current` returns `null` data with an explicit `hasValidConsent=false` view when absent.

- [ ] **Step 5: Run tests**

```bash
./mvnw -pl services/platform-api test \
  -Dtest=ConsentServiceTest,ConsentApiTest,SensitiveDataGuardTest,ModularityTest
```

Expected: all consent evidence, privacy, idempotency, and module-boundary tests pass.

- [ ] **Step 6: Commit active consent**

```bash
git add services/platform-api/src
git commit -m "feat: record guest authorization consent"
```

---

### Task 4: Field Definitions and Guest Profile Draft

**Files:**

- Create: `services/platform-api/src/main/java/com/love/archive/guest/package-info.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/package-info.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/domain/ProfileStatus.java` only if API labels require behavior; persistence values stay unchanged
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/domain/FieldStorageKind.java` only if API labels require behavior; persistence values stay unchanged
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/domain/ProfileFieldType.java` only if API labels require behavior; persistence values stay unchanged
- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/GuestProfileDraftService.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/ProfileFieldDefinitionService.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/SaveGuestProfileCommand.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/ProfileFieldInput.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/GuestProfileDraftView.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/CreateProfileFieldDefinitionCommand.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/UpdateProfileFieldDefinitionCommand.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/ProfileFieldDefinitionView.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/web/GuestProfileController.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/web/AdminProfileFieldDefinitionController.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/web/SaveGuestProfileRequest.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/web/CreateProfileFieldDefinitionRequest.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/web/UpdateProfileFieldDefinitionRequest.java`
- Create: `services/platform-api/src/main/java/com/love/archive/common/web/PageView.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/application/GuestAccountStatusQuery.java`
- Create: `services/platform-api/src/main/java/com/love/archive/identity/application/package-info.java`
- Test: `services/platform-api/src/test/java/com/love/archive/guest/application/GuestProfileDraftServiceTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/guest/web/GuestProfileApiTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/guest/web/AdminProfileFieldDefinitionApiTest.java`

**Interfaces:**

- Produces: `GuestProfileDraftView GuestProfileDraftService.get(long accountId)`
- Produces: `GuestProfileDraftView GuestProfileDraftService.save(long accountId, SaveGuestProfileCommand command, String requestId)`
- Produces: `void GuestAccountStatusQuery.requireActive(long accountId)`
- Produces: typed definition list/create/update operations
- Produces: `record PageView<T>(List<T> items, long page, long size, long total)`

Declare `guest` as a closed module with `allowedDependencies = {"common::web", "common::security", "audit::application", "identity::application", "identity::security"}` and expose only `guest.application` through `@NamedInterface("application")`.

- [ ] **Step 1: Write failing draft, ownership, validation, and definition tests**

```java
@Test
void getDoesNotCreateAndFirstSaveCreatesAnOwnedDraft() {
    assertThat(service.get(accountId).status()).isEqualTo("NOT_STARTED");
    assertThat(profileMapper.selectCount(Wrappers.lambdaQuery())).isZero();
    GuestProfileDraftView saved = service.save(accountId, validCommand(null), requestId);
    assertThat(saved.status()).isEqualTo(ProfileStatus.DRAFT);
    assertThat(saved.version()).isZero();
}

@Test
void rejectsStaleVersionAndEditsDuringReview() {
    service.save(accountId, validCommand(null), requestId);
    assertThatThrownBy(() -> service.save(accountId, validCommand(99L), requestId))
            .extracting("code").isEqualTo("PROFILE_VERSION_CONFLICT");
}
```

Create these additional test methods:

```java
@Test void neverLoadsAnotherAccountsDraft();
@Test void rejectsSaveForNonActiveAccount();
@Test void reportsEveryMissingCoreFieldAtSubmissionValidation();
@Test void persistsEverySupportedDynamicValueTypeInItsTypedColumn();
@Test void rejectsOptionOutsideDefinitionOptions();
@Test void rejectsValueForDisabledDefinition();
@Test void rejectsCoreStorageTypeEnabledAndRequiredChanges();
@Test void rejectsDynamicCodeOrTypeChangeAfterFirstValue();
@Test void encryptsSameIdentifierToDifferentCiphertextAcrossSaves();
@Test void auditContainsChangedFieldCodesButNoProtectedPlaintext();
```

- [ ] **Step 2: Run tests and witness failure**

```bash
./mvnw -pl services/platform-api test \
  -Dtest=GuestProfileDraftServiceTest,GuestProfileApiTest,AdminProfileFieldDefinitionApiTest
```

Expected: compilation failure because guest draft and field-definition services do not exist.

- [ ] **Step 3: Implement typed commands and draft state transitions**

```java
public record SaveGuestProfileCommand(
        Long expectedVersion,
        String gender,
        LocalDate birthDate,
        Integer heightCm,
        String education,
        String occupation,
        String incomeRange,
        String city,
        String wechatId,
        String douyinId,
        String douyinNickname,
        URI douyinProfileUrl,
        List<ProfileFieldInput> dynamicFields) {}

public sealed interface ProfileFieldInput permits TextFieldInput, IntegerFieldInput,
        DecimalFieldInput, DateFieldInput, BooleanFieldInput, OptionFieldInput {
    String fieldCode();
}
```

For first save, create a UUID profile number and `DRAFT`. For updates, load by `userAccountId`, reject `PENDING_REVIEW`, require `expectedVersion`, and use a safe conditional update on `id + version`; on zero rows return `PROFILE_VERSION_CONFLICT`. Transition `APPROVED` or `CHANGES_REQUESTED` to `DRAFT` while preserving `currentApprovedRevisionId`.

- [ ] **Step 4: Implement definition management and value replacement**

```java
public record CreateProfileFieldDefinitionCommand(
        String fieldCode, String label, ProfileFieldType dataType,
        boolean required, List<String> options, int sortOrder, String instructions) {}

public record UpdateProfileFieldDefinitionCommand(
        Long expectedVersion, String label, Boolean required, Boolean enabled,
        List<String> options, Integer sortOrder, String instructions) {}
```

Core definitions allow only label/options/sort/instructions changes. Dynamic definitions become code/type immutable after any value exists. Validate all values against enabled definitions before replacing current dynamic values in the same transaction; never build wrappers in controllers and never accept raw database column/order strings.

- [ ] **Step 5: Implement guest/admin endpoints**

```text
GET   /api/v1/guest/profile/draft
PUT   /api/v1/guest/profile/draft
GET   /api/v1/guest/profile/status
GET   /api/v1/admin/profile-field-definitions?page=1&size=20
POST  /api/v1/admin/profile-field-definitions
PATCH /api/v1/admin/profile-field-definitions/{id}
```

Limit page size to 100, return typed page metadata, and obtain all actor/account IDs from Sa-Token. The guest draft view decrypts protected identifiers only for that guest; definition list views never expose persistence entities.

- [ ] **Step 6: Run tests**

```bash
./mvnw -pl services/platform-api test \
  -Dtest=GuestProfileDraftServiceTest,GuestProfileApiTest,AdminProfileFieldDefinitionApiTest,LombokEntitySafetyTest,ModularityTest
```

Expected: all draft state, ownership, field validation, optimistic locking, and admin-definition tests pass.

- [ ] **Step 7: Commit guest drafts**

```bash
git add services/platform-api/src
git commit -m "feat: add configurable guest profile drafts"
```

---

### Task 5: Immutable and Idempotent Profile Submission

**Files:**

- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/GuestProfileSnapshotProvider.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/GuestProfileApprovalPort.java`
- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/GuestProfileSnapshot.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/package-info.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/application/ProfileSubmissionService.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/application/ProfileRevisionView.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/web/GuestProfileSubmissionController.java`
- Test: `services/platform-api/src/test/java/com/love/archive/review/application/ProfileSubmissionServiceTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/review/web/GuestProfileSubmissionApiTest.java`

**Interfaces:**

- Produces: `GuestProfileSnapshot GuestProfileSnapshotProvider.lockAndValidate(long accountId)`
- Produces: `void GuestProfileApprovalPort.markPending(long profileId, long revisionId, long expectedVersion)`
- Produces: `void GuestProfileApprovalPort.approve(long profileId, long revisionId, long expectedProfileVersion)`
- Produces: `void GuestProfileApprovalPort.reject(long profileId, long revisionId, long expectedProfileVersion)`
- Produces: `ProfileRevisionView ProfileSubmissionService.submit(long accountId, String idempotencyKey, String requestId)`
- Consumes: `GuestAccountStatusQuery.requireActive(long)`
- Consumes: `PaymentAuthorizationEvidence.findPaidAuthorization(long)`
- Consumes: `ConsentEligibility.requireValid(long, long, OffsetDateTime)`

Declare `review` as a closed module with `allowedDependencies = {"common::web", "common::security", "audit::application", "identity::application", "identity::security", "guest::application", "consent::application", "payment::application"}` and expose only intentionally reusable review views from `review.application`.

- [ ] **Step 1: Write failing submission tests**

```java
@Test
void createsACompleteImmutableSnapshotAndDeadline() {
    ProfileRevisionView revision = service.submit(accountId, "submit-001", requestId);
    assertThat(revision.status()).isEqualTo(RevisionStatus.PENDING);
    assertThat(revision.reviewDeadlineAt()).isEqualTo(revision.submittedAt().plusHours(24));
    assertThat(revisionMapper.selectById(revision.id()).getWechatIdCiphertext()).isNotNull();
    assertThat(revisionFieldMapper.selectByRevisionId(revision.id()))
            .extracting(ProfileRevisionFieldValueEntity::getFieldCode)
            .containsExactlyInAnyOrderElementsOf(enabledFieldCodes);
}

@Test
void retriesSameKeyButRejectsKeyReusedForDifferentDraft() {
    ProfileRevisionView first = service.submit(accountId, "submit-002", requestId);
    assertThat(service.submit(accountId, "submit-002", requestId).id()).isEqualTo(first.id());
    changeDraftDirectlyForTest();
    assertThatThrownBy(() -> service.submit(accountId, "submit-002", requestId))
            .extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
}
```

Create these additional test methods:

```java
@Test void requiresIdempotencyKeyHeader();
@Test void rejectsMissingConsent();
@Test void rejectsConsentAtOrAfterExpiry();
@Test void rejectsHistoricalPaymentWithoutPresentedDocument();
@Test void rejectsConsentForDifferentDocument();
@Test void rejectsMissingRequiredCoreAndDynamicFields();
@Test void rejectsSecondPendingRevision();
@Test void rejectsSubmissionForDisabledAccount();
@Test void incrementsRevisionNumbersPerProfile();
@Test void rollsBackRevisionWhenDynamicSnapshotInsertFails();
```

- [ ] **Step 2: Run tests and witness failure**

```bash
./mvnw -pl services/platform-api test \
  -Dtest=ProfileSubmissionServiceTest,GuestProfileSubmissionApiTest
```

Expected: compilation failure because the snapshot/submission ports do not exist.

- [ ] **Step 3: Implement canonical request hashing and submission transaction**

```java
@Transactional
public ProfileRevisionView submit(long accountId, String idempotencyKey, String requestId) {
    if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 128) {
        throw api(BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
    }
    GuestProfileSnapshot snapshot = snapshotProvider.lockAndValidate(accountId);
    String keyHmac = protector.hmac("profile:submission", idempotencyKey);
    String payloadSha256 = canonicalSnapshotHasher.sha256(snapshot);
    ProfileRevisionView replay = resolveReplay(snapshot.profileId(), keyHmac, payloadSha256);
    if (replay != null) return replay;
    accountStatusQuery.requireActive(accountId);
    PresentedAuthorization payment = requirePaymentEvidence(accountId);
    consentEligibility.requireValid(accountId, payment.authorizationDocumentId(), OffsetDateTime.now(clock));
    return insertSnapshotMarkPendingAndAudit(snapshot, keyHmac, payloadSha256, requestId);
}
```

Canonical hashing must write all fixed fields and dynamic fields sorted by `fieldCode`, with explicit null markers and UTF-8 length-prefix framing; do not hash arbitrary JSON serialization whose property order may change.

- [ ] **Step 4: Expose guest submission and owned revision detail**

```text
POST /api/v1/guest/profile/submissions  (required Idempotency-Key header)
GET  /api/v1/guest/profile/revisions/{revisionId}
```

The detail query must include `guest_profile.user_account_id = currentAccountId`; return `PROFILE_REVISION_NOT_FOUND` for both absent and foreign revisions so ownership is not leaked.

- [ ] **Step 5: Run tests**

```bash
./mvnw -pl services/platform-api test \
  -Dtest=ProfileSubmissionServiceTest,GuestProfileSubmissionApiTest,ConsentServiceTest,ModularityTest
```

Expected: immutable snapshot, authorization, deadline, idempotency, rollback, and ownership tests pass.

- [ ] **Step 6: Commit submission workflow**

```bash
git add services/platform-api/src
git commit -m "feat: add immutable profile submissions"
```

---

### Task 6: Annotated Administrator Review Queries and Decisions

**Files:**

- Create: `services/platform-api/src/main/java/com/love/archive/review/application/ProfileReviewService.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/application/ProfileReviewFilter.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/application/DeadlineFilter.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/application/ProfileReviewListItem.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/application/ProfileReviewDetail.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/application/ProfileFieldDifference.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/application/ProfileReviewDecisionView.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/persistence/ProfileReviewQueryMapper.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/persistence/ProfileReviewSqlProvider.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/persistence/query/ProfileReviewListRow.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/persistence/query/ProfileReviewHeaderRow.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/persistence/query/ProfileRevisionFieldRow.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/web/AdminProfileReviewController.java`
- Create: `services/platform-api/src/main/java/com/love/archive/review/web/ReviewDecisionRequest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/review/persistence/ProfileReviewQueryMapperTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/review/application/ProfileReviewServiceTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/review/web/AdminProfileReviewApiTest.java`

**Interfaces:**

- Produces: `PageView<ProfileReviewListItem> ProfileReviewService.search(ProfileReviewFilter filter)`
- Produces: `ProfileReviewDetail ProfileReviewService.detail(long revisionId)`
- Produces: `ProfileReviewDecisionView ProfileReviewService.approve(long adminId, long revisionId, long expectedVersion, String requestId)`
- Produces: `ProfileReviewDecisionView ProfileReviewService.reject(long adminId, long revisionId, long expectedVersion, String reasonCode, String comment, String requestId)`
- Consumes: `GuestProfileApprovalPort.approve(long profileId, long revisionId, long expectedProfileVersion)` and `GuestProfileApprovalPort.reject(long profileId, long revisionId, long expectedProfileVersion)`

```java
public record ProfileReviewFilter(
        RevisionStatus status,
        DeadlineFilter deadline,
        OffsetDateTime submittedFrom,
        OffsetDateTime submittedUntil,
        UUID profileNo) {}
```

- [ ] **Step 1: Write failing annotated-query and decision tests**

```java
@Test
void filtersPendingReviewsByDeadlineAndReturnsStableOrder() {
    Page<ProfileReviewListRow> page = new Page<>(1, 20);
    mapper.search(page, new ProfileReviewFilter(
            RevisionStatus.PENDING, DeadlineFilter.OVERDUE, null, null, null));
    assertThat(page.getRecords()).allMatch(row -> row.reviewDeadlineAt().isBefore(now));
    assertThat(page.getRecords()).isSortedAccordingTo(
            Comparator.comparing(ProfileReviewListRow::reviewDeadlineAt)
                    .thenComparing(ProfileReviewListRow::revisionId));
}

@Test
void approvingSwitchesPointerButRejectingPreservesLastApprovedPointer() {
    long firstApproved = approveFirstRevision();
    long secondPending = submitChangedDraft();
    service.reject(adminId, secondPending, 0L, "CONTENT_INCOMPLETE", "请补充职业信息", requestId);
    assertThat(profileMapper.selectById(profileId).getCurrentApprovedRevisionId())
            .isEqualTo(firstApproved);
}
```

Create these additional test methods:

```java
@Test void capsReviewPageSizeAtOneHundred();
@Test void filtersByStatusSubmissionHalfOpenRangeAndProfileNumber();
@Test void classifiesDueSoonAndOverdueAgainstServerClock();
@Test void returnsTypedDifferencesAgainstLastApprovedRevision();
@Test void appendsReviewRecordAndAuditForApproveAndReject();
@Test void requiresGuestFacingRejectComment();
@Test void rejectsStaleExpectedRevisionVersion();
@Test void repeatsIdenticalCompletedDecisionIdempotently();
@Test void rejectsConflictingSecondDecision();
@Test void requiresActiveAdministratorSession();
```

- [ ] **Step 2: Run tests and witness failure**

```bash
./mvnw -pl services/platform-api test \
  -Dtest=ProfileReviewQueryMapperTest,ProfileReviewServiceTest,AdminProfileReviewApiTest
```

Expected: compilation failure because review query/service types do not exist.

- [ ] **Step 3: Implement the dynamic list with a whitelisted provider**

```java
@Mapper
public interface ProfileReviewQueryMapper {
    @SelectProvider(type = ProfileReviewSqlProvider.class, method = "search")
    IPage<ProfileReviewListRow> search(IPage<ProfileReviewListRow> page,
                                       @Param("filter") ProfileReviewFilter filter);

    @Select("""
        SELECT r.id AS revision_id, r.status, r.submitted_at, r.review_deadline_at,
               p.profile_no, p.current_approved_revision_id
          FROM profile_revision r
          JOIN guest_profile p ON p.id = r.guest_profile_id
         WHERE r.id = #{revisionId}
        """)
    ProfileReviewHeaderRow detailHeader(@Param("revisionId") long revisionId);
}
```

`ProfileReviewSqlProvider` may append only fixed SQL fragments selected by enums. Bind values with exact names such as `#{filter.status}`, `#{filter.submittedFrom}`, and `#{filter.profileNo}`. Use explicit column lists and stable aliases; never interpolate client strings. Execute every provider branch against PostgreSQL 18 in `ProfileReviewQueryMapperTest`.

- [ ] **Step 4: Implement transactional approve/reject**

```java
@Transactional
public ProfileReviewDecisionView approve(
        long adminId, long revisionId, long expectedVersion, String requestId) {
    ProfileRevisionEntity revision = lockRevision(revisionId);
    ProfileReviewDecisionView replay = resolveCompletedDecision(revision, ReviewResult.APPROVED);
    if (replay != null) return replay;
    updatePendingRevision(revisionId, expectedVersion, RevisionStatus.APPROVED, OffsetDateTime.now(clock));
    appendReviewRecord(adminId, revisionId, ReviewResult.APPROVED, null, null, requestId);
    profileApprovalPort.approve(revision.getGuestProfileId(), revisionId, currentProfileVersion(revision));
    appendAudit(adminId, revisionId, "PROFILE_APPROVED", requestId);
    return decisionView(revisionId);
}
```

Reject follows the same lock order. It must preserve `currentApprovedRevisionId`, set profile status `CHANGES_REQUESTED`, and require a trimmed 1-1000 character guest-facing comment. A unique constraint on `profile_review_record.profile_revision_id` remains the final concurrency guard.

- [ ] **Step 5: Expose typed review APIs**

```text
GET  /api/v1/admin/profile-reviews?page=1&size=20&status=PENDING&deadline=OVERDUE
GET  /api/v1/admin/profile-reviews/{revisionId}
POST /api/v1/admin/profile-reviews/{revisionId}/approve
POST /api/v1/admin/profile-reviews/{revisionId}/reject
```

Approve/reject bodies include `expectedVersion`; reject additionally includes optional `reasonCode` and required `comment`. Detail returns the pending revision, last approved revision when present, and typed field differences. No entity or untyped map crosses the controller boundary.

- [ ] **Step 6: Run review and regression tests**

```bash
./mvnw -pl services/platform-api test \
  -Dtest=ProfileReviewQueryMapperTest,ProfileReviewServiceTest,AdminProfileReviewApiTest,GuestProfileSubmissionApiTest,ModularityTest
```

Expected: annotated SQL, paging, differences, review concurrency, idempotency, pointer, and authorization tests pass.

- [ ] **Step 7: Commit the review workflow**

```bash
git add services/platform-api/src
git commit -m "feat: add administrator profile review workflow"
```

---

### Task 7: Architecture Guards, Documentation, and Release Verification

**Files:**

- Modify: `services/platform-api/src/test/java/com/love/archive/architecture/ModularityTest.java`
- Modify: `services/platform-api/src/test/java/com/love/archive/architecture/SensitiveDataGuardTest.java`
- Modify: `services/platform-api/src/test/java/com/love/archive/architecture/LombokEntitySafetyTest.java`
- Modify: `README.md`
- Modify: `.env.example`
- Modify: `compose.yaml` only if Flyway/runtime grants require another declared setting

**Interfaces:**

- Consumes: all endpoints and named interfaces from Tasks 1-6
- Produces: verified source guards, documented environment variables/endpoints, runnable package, and clean release state

- [ ] **Step 1: Write failing release guards for the new surface**

```java
@Test
void protectedEntitiesDefensivelyCopyEveryCiphertext() {
    GuestProfileEntity profile = new GuestProfileEntity();
    byte[] supplied = {1, 2, 3};
    profile.setWechatIdCiphertext(supplied);
    supplied[0] = 9;
    assertThat(profile.getWechatIdCiphertext()).containsExactly(1, 2, 3);
}

@Test
void repositoryStillContainsNoMapperXmlOrSensitiveLogging() throws IOException {
    assertThat(findMapperXml()).isEmpty();
    assertThat(readProductionSources())
            .doesNotContain("org.springframework.security")
            .doesNotContainPattern("(?i)LOGGER\\.(trace|debug|info|warn|error)\\([^;]*(wechat|douyin|clientIp|sessionReference)");
}
```

Create three explicit guard methods:

```java
@Test void profileAndReviewEntitiesDoNotUseDataOrToString();
@Test void everyCustomJoinMapperUsesSelectOrSelectProviderAnnotations();
@Test void sourceAndResourceTreesContainNoMapperXml();
```

- [ ] **Step 2: Run guards and witness failure before completing documentation/config**

```bash
./mvnw -pl services/platform-api test \
  -Dtest=ModularityTest,SensitiveDataGuardTest,LombokEntitySafetyTest
```

Expected: at least the new key/config/documentation assertion fails until the final production configuration is complete.

- [ ] **Step 3: Complete configuration and operating documentation**

```yaml
app:
  sensitive-security:
    encryption-key: ${PROFILE_ENCRYPTION_KEY:}
    hmac-key: ${PROFILE_HMAC_KEY:}
```

Add empty `PROFILE_ENCRYPTION_KEY=` and `PROFILE_HMAC_KEY=` entries to `.env.example`, document secure 32-byte Base64 key generation, never add real keys, list new consent/profile/review endpoints, explain the payment authorization-version requirement, and explicitly note that photo upload remains unavailable until object-storage credentials are supplied.

- [ ] **Step 4: Run the complete Java 25 verification suite**

```bash
./mvnw -pl services/platform-api test
./mvnw -pl services/platform-api package -DskipTests
```

Expected: zero failures/errors/skips in tests and a successful executable JAR.

- [ ] **Step 5: Verify forbidden dependencies/resources and repository hygiene**

```bash
./mvnw -pl services/platform-api dependency:tree
git diff --check
git status --short
```

Expected: Sa-Token is present; Spring Security and Mapper XML are absent; no whitespace errors, secrets, generated target files, or unrelated changes are staged.

- [ ] **Step 6: Commit the verified slice**

```bash
git add README.md .env.example compose.yaml services/platform-api/src
git commit -m "docs: verify profile review and consent slice"
```

Record the final test count, package result, and any intentionally deferred object-storage configuration in the commit handoff.
