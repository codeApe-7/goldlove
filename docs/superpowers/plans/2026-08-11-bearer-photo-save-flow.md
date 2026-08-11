# Bearer Photo Save Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the guest app to `Authorization: Bearer <token>` auth and switch photos to a two-phase flow where selecting an image only uploads to COS, and `guest_profile`/`profile_photo` are written only when the user saves a draft or submits.

**Architecture:** Configure the guest Sa-Token logic to read a Bearer header and return `accessToken` on login/activation, then enforce login + account status once in a guest interceptor. Rewrite the photo upload endpoint to be database-free (COS only, returning an object key), slim `profile_photo`/`profile_revision_photo` to `category + object_key + sort_order` via a new Flyway migration, and make draft save the single write path that reconciles the full photo collection inside one short transaction (COS deletion only after commit). The guest app stores the token in the session, attaches it to every request/upload, keeps uploaded object keys as the local target collection, and refreshes persisted photos after save.

**Tech Stack:** PostgreSQL 18/Flyway, Java 25/Spring Boot 4.1/MyBatis-Plus/Sa-Token 1.45/JUnit 5/Testcontainers, Vue 3/uni-app/TypeScript/Vitest.

## Global Constraints

- Guest requests authenticate only via `Authorization: Bearer <token>`; guest cookies and `BrowserOriginProtectionFilter` guest handling are removed. Admin cookie auth and origin protection stay unchanged.
- Login and activation both create an immediately usable guest session and return `accessToken` + `expiresIn` (2592000 seconds).
- Every `/api/v1/guest/**` request (except login/activate) is checked once at the interceptor: token valid AND `user_account.status = ACTIVE`; on inactive, the current token is logged out and `AUTH_ACCOUNT_INACTIVE` is returned.
- Business services remove duplicate `GuestAccountStatusQuery.requireActive(accountId)` calls; only business state, ownership, and data-integrity checks remain.
- `POST /api/v1/guest/profile/photo-uploads` validates the file (≤10 MiB, decodable JPEG/PNG/WebP), uploads to COS, and returns `{objectKey, category, previewUrl}`. It never reads the profile, checks photo counts, or writes the database.
- `GET /api/v1/guest/profile/photos` returns only persisted rows: `{id, category, objectKey, sortOrder, previewUrl, createdAt}`. Old `POST /api/v1/guest/profile/photos` and `DELETE /api/v1/guest/profile/photos/{photoId}` are removed.
- Draft save accepts the full target collection `photos: {avatar: string|null, life: string[]}` and reconciles `profile_photo` inside the same short transaction; no COS call happens inside the save transaction; removed objects are deleted best-effort only after commit when no `profile_revision_photo` references them.
- Accepted object keys are strictly `profiles/{accountId}/{avatar|life}/{uuid}.{jpg|png|webp}` with the current account prefix; illegal, cross-account, category-mismatched, or duplicate keys return `PHOTO_REFERENCE_INVALID`; life > 6 returns `PHOTO_COUNT_LIMIT_EXCEEDED`.
- `profile_photo` and `profile_revision_photo` drop `sha256`, `size_bytes`, `content_type`, `width`, `height` via a new Flyway migration (V5 is never edited). The immutable trigger and unique constraints remain.
- Version digest for review snapshots uses `category + objectKey + sortOrder` only; changing a photo key changes the digest.
- COS client: connection timeout 5s, socket read timeout 20s, total request timeout 30s enabled, max auto retries 0.
- Guest frontend sends the Bearer header on normal requests and `uni.uploadFile`; `401` and `403` with `AUTH_ACCOUNT_INACTIVE` clear the session and relaunch to the login page; logout clears local session whether or not the server call succeeds.
- Unsaved COS objects may become orphans this phase; no automatic cleanup is added.
- User-owned untracked files `.m2/` and `docs/feature-inventory.md` are never touched or committed.

---

### Task 1: Guest Bearer session and token response

**Files:**
- Modify: `services/platform-api/src/main/java/com/love/archive/identity/config/AuthLogicConfiguration.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/identity/web/GuestSessionView.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/identity/application/GuestAuthService.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/identity/web/GuestAuthController.java`
- Test: `services/platform-api/src/test/java/com/love/archive/identity/security/AuthLogicIntegrationTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/identity/web/GuestAuthApiTest.java`

**Interfaces:**
- Consumes: the existing two `StpLogic` beans (`guestStpLogic`, `adminStpLogic`), `AuthLogics`, and `GuestSessionView(accountId, status)`.
- Produces: `GuestSessionView(Long accountId, AccountStatus status, String accessToken, long expiresIn)`; guest logic reads `Authorization: Bearer` header and never reads cookies; login/activate responses carry `accessToken` and `expiresIn`.

- [ ] **Step 1: Write the failing guest-logic config assertions**

Update `AuthLogicIntegrationTest.guestAndAdminWithSameNumericIdHaveIsolatedRedisSessions()` to add:

```java
assertThat(guest.getTokenName()).isEqualTo("Authorization");
assertThat(guest.getConfig().getTokenPrefix()).isEqualTo("Bearer");
assertThat(guest.getConfig().getIsReadHeader()).isTrue();
assertThat(guest.getConfig().getIsReadCookie()).isFalse();
assertThat(admin.getTokenName()).isEqualTo("archive-token-admin");
assertThat(admin.getConfig().getIsReadCookie()).isTrue();
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home ./mvnw -pl services/platform-api test -Dtest=AuthLogicIntegrationTest
```

Expected: FAIL because guest token name is still `archive-token-guest` and prefix is null.

- [ ] **Step 3: Configure guest Bearer logic**

Replace the `guestStpLogic` bean in `AuthLogicConfiguration.java` with a dedicated builder, keeping `adminStpLogic` on the existing `logic(...)` helper:

```java
@Bean
@Primary
StpLogic guestStpLogic(SaTokenConfig globalConfig) {
    SaTokenConfig isolated = new SaTokenConfig()
            .setTokenName("Authorization")
            .setTimeout(globalConfig.getTimeout())
            .setActiveTimeout(globalConfig.getActiveTimeout())
            .setIsConcurrent(globalConfig.getIsConcurrent())
            .setIsShare(globalConfig.getIsShare())
            .setIsReadBody(globalConfig.getIsReadBody())
            .setIsReadHeader(true)
            .setIsReadCookie(false)
            .setIsLastingCookie(globalConfig.getIsLastingCookie())
            .setIsWriteHeader(globalConfig.getIsWriteHeader())
            .setTokenStyle(globalConfig.getTokenStyle())
            .setTokenPrefix("Bearer")
            .setAutoRenew(globalConfig.getAutoRenew())
            .setCookie(globalConfig.getCookie());
    return new StpLogic("guest").setConfig(isolated);
}
```

- [ ] **Step 4: Extend the session view and service**

Change `GuestSessionView.java` to:

```java
public record GuestSessionView(
        Long accountId,
        AccountStatus status,
        String accessToken,
        long expiresIn) {
}
```

In `GuestAuthService.java`, update the three constructor calls to the four-arg record. The service has no token yet, so pass `null, 0L`:

```java
return new GuestSessionView(account.getId(), AccountStatus.ACTIVE, null, 0L);
```

(two occurrences: `activate` and `authenticate`; `getSession` uses the same call).

- [ ] **Step 5: Issue the session token in the controller**

Update `GuestAuthController.java`: `activate` and `login` wrap the service result with a private helper before returning; `me` and `logout` keep returning/using the plain view:

```java
private GuestSessionView withSessionToken(GuestSessionView session) {
    authLogics.guest().login(session.accountId());
    StpLogic logic = authLogics.guest();
    return new GuestSessionView(
            session.accountId(),
            session.status(),
            logic.getTokenValue(),
            logic.getTokenTimeout());
}
```

Replace both `return ApiResponse.success(session, ...)` calls with `return ApiResponse.success(withSessionToken(session), ...)`. Add the import `cn.dev33.satoken.stp.StpLogic` if not already present.

- [ ] **Step 6: Rewrite the auth API test to Bearer**

In `GuestAuthApiTest.java`:

- Replace the `Set-Cookie` login assertion with JSON assertions:

```java
mockMvc.perform(post("/api/v1/guest/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"13800138000","password":"New-password-2026"}
                        """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accountId").value(provisioned.accountId()))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.expiresIn").value(2592000));
```

- Extract the token instead of the cookie: `String token = accessToken(loginResult);` with

```java
private String accessToken(MvcResult result) throws Exception {
    return new ObjectMapper()
            .readTree(result.getResponse().getContentAsString())
            .get("data")
            .get("accessToken")
            .asText();
}
```

- Replace every `.cookie(guestCookie)` with `.header("Authorization", "Bearer " + token)`; remove the now-unused `Cookie` import and add `com.fasterxml.jackson.databind.ObjectMapper`.
- Remove the `Set-Cookie` matcher block and its `Cookie` variable.
- Assert the guest login response does not set the guest cookie:

```java
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
...
.andExpect(header().doesNotExist("Set-Cookie"))
```

- [ ] **Step 7: Run both focused tests and verify GREEN**

Run:

```bash
JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home ./mvnw -pl services/platform-api test -Dtest=AuthLogicIntegrationTest,GuestAuthApiTest
```

Expected: PASS (both classes; the Redis-session isolation test still passes because `createLoginSession` works independently of header config).

- [ ] **Step 8: Commit Task 1**

```bash
git add services/platform-api/src/main/java/com/love/archive/identity/config/AuthLogicConfiguration.java services/platform-api/src/main/java/com/love/archive/identity/web/GuestSessionView.java services/platform-api/src/main/java/com/love/archive/identity/application/GuestAuthService.java services/platform-api/src/main/java/com/love/archive/identity/web/GuestAuthController.java services/platform-api/src/test/java/com/love/archive/identity/security/AuthLogicIntegrationTest.java services/platform-api/src/test/java/com/love/archive/identity/web/GuestAuthApiTest.java
git commit -m "feat(guest): authenticate with bearer token sessions"
```

---

### Task 2: Unified guest status interceptor and duplicate-check removal

**Files:**
- Create: `services/platform-api/src/main/java/com/love/archive/identity/web/GuestStatusInterceptor.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/identity/web/AuthInterceptorConfiguration.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/identity/web/BrowserOriginProtectionFilter.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/application/GuestProfileDraftService.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/application/ProfilePhotoService.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/review/application/ProfileSubmissionService.java`
- Test: `services/platform-api/src/test/java/com/love/archive/identity/web/GuestAuthApiTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/guest/application/GuestProfileDraftServiceTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/review/application/ProfileSubmissionServiceTest.java`

**Interfaces:**
- Consumes: `AuthLogics`, `UserAccountMapper`, `AccountStatus`, `ApiException`, and the existing guest interceptor path list.
- Produces: `GuestStatusInterceptor` (a `HandlerInterceptor`) that checks login, loads the account, logs out the current token and throws `AUTH_ACCOUNT_INACTIVE` when inactive; guest business services no longer call `GuestAccountStatusQuery`.

- [ ] **Step 1: Write the failing API-level suspension test**

Add to `GuestAuthApiTest.java` (autowire `UserAccountMapper accountMapper`):

```java
@Test
void suspendsExistingSessionOnNextRequest() throws Exception {
    ProvisionedGuestView provisioned = provision("13800138000", "PAY-GUEST-SUSPEND");
    mockMvc.perform(post("/api/v1/guest/auth/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(activationBody("13800138000", provisioned.initialCredential())))
            .andExpect(status().isOk());
    String token = accessToken(mockMvc.perform(post("/api/v1/guest/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"phone":"13800138000","password":"New-password-2026"}
                            """))
            .andExpect(status().isOk())
            .andReturn());

    accountMapper.update(com.baomidou.mybatisplus.core.toolkit.Wrappers
            .<com.love.archive.identity.persistence.UserAccountEntity>lambdaUpdate()
            .eq(com.love.archive.identity.persistence.UserAccountEntity::getId,
                    provisioned.accountId())
            .set(com.love.archive.identity.persistence.UserAccountEntity::getStatus,
                    com.love.archive.identity.domain.AccountStatus.SUSPENDED));

    mockMvc.perform(get("/api/v1/guest/profile/draft")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_INACTIVE"));
    mockMvc.perform(get("/api/v1/guest/profile/draft")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home ./mvnw -pl services/platform-api test -Dtest=GuestAuthApiTest#suspendsExistingSessionOnNextRequest
```

Expected: FAIL with status 200 (no status check at the interceptor yet).

- [ ] **Step 3: Create the guest status interceptor**

Create `GuestStatusInterceptor.java`:

```java
package com.love.archive.identity.web;

import com.love.archive.common.web.ApiException;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.identity.security.AuthLogics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class GuestStatusInterceptor implements HandlerInterceptor {

    private final AuthLogics authLogics;
    private final UserAccountMapper userAccountMapper;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        authLogics.guest().checkLogin();
        long accountId = authLogics.guest().getLoginIdAsLong();
        UserAccountEntity account = userAccountMapper.selectById(accountId);
        if (account == null || account.getStatus() != AccountStatus.ACTIVE) {
            authLogics.guest().logout();
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "AUTH_ACCOUNT_INACTIVE",
                    "账号尚未激活或已停用");
        }
        return true;
    }
}
```

- [ ] **Step 4: Register the interceptor and stop cookie checks for guests**

In `AuthInterceptorConfiguration.java`, inject `GuestStatusInterceptor` and replace the guest `SaInterceptor` registration:

```java
registry.addInterceptor(guestStatusInterceptor)
        .addPathPatterns("/api/v1/guest/**")
        .excludePathPatterns(
                "/api/v1/guest/auth/login",
                "/api/v1/guest/auth/activate")
        .order(-100);
```

The admin `SaInterceptor` registration stays unchanged.

In `BrowserOriginProtectionFilter.java`, change the constructor to only track the admin cookie:

```java
this.authCookieNames = Set.of(authLogics.admin().getTokenName());
```

Remove the `authLogics.guest()` usage there; guest requests no longer carry an authentication cookie.

- [ ] **Step 5: Remove duplicate account-status checks from business services**

Delete the following field/method usages (and now-unused imports) in the three services:

- `GuestProfileDraftService.java`: remove the `GuestAccountStatusQuery accountStatusQuery` field and the three `accountStatusQuery.requireActive(accountId);` calls in `get`, `save`, and `validateForSubmission`.
- `ProfilePhotoService.java`: remove the `GuestAccountStatusQuery` field and the `accountStatusQuery.requireActive(accountId);` call in `upload`, `list`, and `delete`.
- `ProfileSubmissionService.java`: remove the `GuestAccountStatusQuery` field and the `accountStatusQuery.requireActive(accountId);` call in `submit`.

- [ ] **Step 6: Drop the obsolete service-level suspension tests**

Delete `GuestProfileDraftServiceTest.rejectsSaveForNonActiveAccount()` (whole method plus the now-unused `insertAccount(AccountStatus.SUSPENDED, ...)` call inside it) and `ProfileSubmissionServiceTest.rejectsSubmissionForDisabledAccount()` (whole method). Both behaviors are now covered by the interceptor test in Step 1.

- [ ] **Step 7: Run the affected suites and verify GREEN**

Run:

```bash
JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home ./mvnw -pl services/platform-api test -Dtest=GuestAuthApiTest,GuestProfileDraftServiceTest,ProfileSubmissionServiceTest,AdminAuthApiTest
```

Expected: PASS (guest suspension returns 403 then 401; admin cookie login still passes; profile/submission service tests no longer reference account status).

- [ ] **Step 8: Commit Task 2**

```bash
git add services/platform-api/src/main/java/com/love/archive/identity/web/GuestStatusInterceptor.java services/platform-api/src/main/java/com/love/archive/identity/web/AuthInterceptorConfiguration.java services/platform-api/src/main/java/com/love/archive/identity/web/BrowserOriginProtectionFilter.java services/platform-api/src/main/java/com/love/archive/guest/application/GuestProfileDraftService.java services/platform-api/src/main/java/com/love/archive/guest/application/ProfilePhotoService.java services/platform-api/src/main/java/com/love/archive/review/application/ProfileSubmissionService.java services/platform-api/src/test/java/com/love/archive/identity/web/GuestAuthApiTest.java services/platform-api/src/test/java/com/love/archive/guest/application/GuestProfileDraftServiceTest.java services/platform-api/src/test/java/com/love/archive/review/application/ProfileSubmissionServiceTest.java
git commit -m "feat(identity): enforce active guest accounts at request entry"
```

---

### Task 3: Harden COS client timeouts and disable retries

**Files:**
- Modify: `services/platform-api/src/main/java/com/love/archive/storage/config/CosStorageConfiguration.java`
- Test: `services/platform-api/src/test/java/com/love/archive/storage/config/CosStorageConfigurationTest.java`

**Interfaces:**
- Consumes: `CosStorageProperties` and the existing `cosClient` bean.
- Produces: a `COSClient` whose `ClientConfig` uses 5s connect, 20s socket read, 30s enabled total timeout, and 0 max error retries.

- [ ] **Step 1: Write the failing config assertions**

Add to `CosStorageConfigurationTest.java`:

```java
import com.qcloud.cos.ClientConfig;
...
contextRunner
        .withPropertyValues(
                "app.storage.cos.secret-id=secret-id",
                "app.storage.cos.secret-key=secret-key",
                "app.storage.cos.region=ap-guangzhou",
                "app.storage.cos.bucket=loveplatform-1314980040")
        .run(context -> {
            assertThat(context).hasSingleBean(COSClient.class);
            ClientConfig config = context.getBean(COSClient.class).getClientConfig();
            assertThat(config.getConnectionTimeout()).isEqualTo(5_000);
            assertThat(config.getSocketTimeout()).isEqualTo(20_000);
            assertThat(config.getRequestTimeout()).isEqualTo(30_000);
            assertThat(config.isRequestTimeOutEnable()).isTrue();
            assertThat(config.getMaxErrorRetry()).isZero();
        });
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home ./mvnw -pl services/platform-api test -Dtest=CosStorageConfigurationTest
```

Expected: FAIL (defaults are different from the required values).

- [ ] **Step 3: Apply the timeouts in the client factory**

In `CosStorageConfiguration.cosClient(...)`:

```java
ClientConfig clientConfig = new ClientConfig(new Region(properties.region()));
clientConfig.setConnectionTimeout(5_000);
clientConfig.setSocketTimeout(20_000);
clientConfig.setRequestTimeout(30_000);
clientConfig.setRequestTimeOutEnable(true);
clientConfig.setMaxErrorRetry(0);
return new COSClient(credentials, clientConfig);
```

- [ ] **Step 4: Run the test and verify GREEN**

Run:

```bash
JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home ./mvnw -pl services/platform-api test -Dtest=CosStorageConfigurationTest
```

Expected: PASS.

- [ ] **Step 5: Commit Task 3**

```bash
git add services/platform-api/src/main/java/com/love/archive/storage/config/CosStorageConfiguration.java services/platform-api/src/test/java/com/love/archive/storage/config/CosStorageConfigurationTest.java
git commit -m "feat(storage): bound COS request timeouts and retries"
```

---

### Task 4: Slim photo metadata via V8 migration

**Files:**
- Create: `services/platform-api/src/main/resources/db/migration/V8__slim_photo_metadata.sql`
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/persistence/ProfilePhotoEntity.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/review/persistence/ProfileRevisionPhotoEntity.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/application/ProfilePhotoView.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/application/ProfilePhotoService.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/application/GuestProfileSnapshot.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/application/DatabaseGuestProfileSnapshotProvider.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/review/application/ProfileSubmissionService.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/review/application/ProfileRevisionView.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/review/application/ProfileReviewService.java`
- Test: `services/platform-api/src/test/java/com/love/archive/profile/ProfilePhotoPersistenceTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/guest/application/ProfilePhotoServiceTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/review/application/ProfileReviewServiceTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/review/application/ProfileSubmissionServiceTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/review/web/AdminProfileReviewApiTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/review/web/GuestProfileSubmissionApiTest.java`

**Interfaces:**
- Consumes: current `ProfilePhotoEntity`/`ProfileRevisionPhotoEntity` fields and the existing photo views/snapshot records.
- Produces: `ProfilePhotoView(long id, String category, String objectKey, int sortOrder, String downloadUrl, OffsetDateTime createdAt)`, `GuestProfileSnapshot.Photo(String category, String objectKey, int sortOrder)`, `ProfileRevisionView.Photo(String category, String objectKey, int sortOrder, String downloadUrl)`; the DB no longer stores photo metadata.

- [ ] **Step 1: Write the failing persistence expectations**

Update `ProfilePhotoPersistenceTest` SQL inserts to the slim column list:

```java
INSERT INTO profile_photo (
    guest_profile_id, category, object_key, sort_order
) VALUES (?, ?, ?, ?)
```

and

```java
INSERT INTO profile_revision_photo (
    profile_revision_id, category, object_key, sort_order, created_at
) VALUES (?, 'AVATAR', 'profiles/1/avatar/snap.jpg', 0, ?)
```

The invalid-category test should insert `(?, 'VIDEO', 'profiles/1/video/v.mp4', 0)` and keep expecting `ck_profile_photo_category`; the immutable-row test keeps the same UPDATE/DELETE rejection assertions.

- [ ] **Step 2: Run the focused persistence test and verify RED**

Run:

```bash
JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home ./mvnw -pl services/platform-api test -Dtest=ProfilePhotoPersistenceTest
```

Expected: FAIL — the slim `INSERT` omits the still-required `NOT NULL` metadata columns (`sha256`, `size_bytes`, `content_type`, `width`, `height`), so Postgres reports a null-value violation.

- [ ] **Step 3: Create the V8 migration**

Create `V8__slim_photo_metadata.sql`:

```sql
ALTER TABLE profile_photo
    DROP COLUMN sha256,
    DROP COLUMN size_bytes,
    DROP COLUMN content_type,
    DROP COLUMN width,
    DROP COLUMN height;

ALTER TABLE profile_revision_photo
    DROP COLUMN sha256,
    DROP COLUMN size_bytes,
    DROP COLUMN content_type,
    DROP COLUMN width,
    DROP COLUMN height;
```

- [ ] **Step 4: Slim the entities and views**

- `ProfilePhotoEntity.java`: remove fields `sha256`, `sizeBytes`, `contentType`, `width`, `height` (and their `@TableField` annotations).
- `ProfileRevisionPhotoEntity.java`: remove the same five fields.
- `ProfilePhotoView.java`: replace the record with

```java
public record ProfilePhotoView(
        long id,
        String category,
        String objectKey,
        int sortOrder,
        String downloadUrl,
        OffsetDateTime createdAt) {
}
```

- `GuestProfileSnapshot.Photo`: replace with

```java
public record Photo(String category, String objectKey, int sortOrder) {
}
```

- `ProfileRevisionView.Photo`: replace with

```java
public record Photo(
        String category,
        String objectKey,
        int sortOrder,
        String downloadUrl) {
}
```

- [ ] **Step 5: Update all producers/consumers of the slimmed records**

- `ProfilePhotoService.upload`: keep validating and uploading as before, but remove the four `photo.setSha256(...)`, `photo.setSizeBytes(...)`, `photo.setContentType(...)`, `photo.setWidth(...)`, `photo.setHeight(...)` calls (content type is still passed to `storageService.put` and used for the extension).
- `ProfilePhotoService.toView`: map to the new record:

```java
return new ProfilePhotoView(
        photo.getId(),
        photo.getCategory().name(),
        photo.getObjectKey(),
        photo.getSortOrder(),
        storageService.signDownloadUrl(photo.getObjectKey(), URL_TTL),
        photo.getCreatedAt());
```

- `DatabaseGuestProfileSnapshotProvider`: map photos to

```java
new GuestProfileSnapshot.Photo(
        photo.getCategory().name(),
        photo.getObjectKey(),
        photo.getSortOrder())
```

- `ProfileSubmissionService.insertSnapshotMarkPendingAndAudit`: drop the five metadata setters, keeping

```java
stored.setCategory(PhotoCategory.valueOf(photo.category()));
stored.setObjectKey(photo.objectKey());
stored.setSortOrder(photo.sortOrder());
```

- `ProfileSubmissionService.toView`: map to

```java
new ProfileRevisionView.Photo(
        photo.getCategory().name(),
        photo.getObjectKey(),
        photo.getSortOrder(),
        storageService.signDownloadUrl(photo.getObjectKey(), Duration.ofMinutes(15)))
```

- `CanonicalSnapshotHasher.sha256`: replace the per-photo metadata entries with

```java
writeEntry(output, "photo_category", photo.category());
writeEntry(output, "photo_object_key", photo.objectKey());
writeEntry(output, "photo_sort_order", photo.sortOrder());
```

- `ProfileReviewService` (photo list mapping in the review-detail method): map to the same four-arg `ProfileRevisionView.Photo` shape as `ProfileSubmissionService.toView`.

- [ ] **Step 6: Update test helpers that set/assert metadata**

In each of these test files, remove the metadata setters in the photo-insert helpers (`photo.setSha256(...)`, `photo.setSizeBytes(...)`, `photo.setContentType(...)`, `photo.setWidth(...)`, `photo.setHeight(...)`):

- `ProfilePhotoServiceTest.insertRevisionSnapshot`
- `ProfileReviewServiceTest` (photo insert helper around line 316)
- `ProfileSubmissionServiceTest` (photo insert helper around line 374)
- `AdminProfileReviewApiTest` (photo insert helper around line 228)
- `GuestProfileSubmissionApiTest.insertAvatarPhoto`

In `ProfilePhotoServiceTest.uploadStoresAvatarAndReturnsViewWithSignedUrl`, replace the metadata assertions:

```java
assertThat(view.objectKey()).startsWith("profiles/" + accountId + "/avatar/");
assertThat(view.downloadUrl()).startsWith("https://loveplatform-1314980040");
ProfilePhotoEntity stored = photoMapper.selectOne(
        Wrappers.<ProfilePhotoEntity>lambdaQuery()
                .eq(ProfilePhotoEntity::getGuestProfileId, profileId));
assertThat(stored.getObjectKey()).startsWith("profiles/" + accountId + "/avatar/");
assertThat(stored.getSortOrder()).isZero();
```

- [ ] **Step 7: Run the affected suites and verify GREEN**

Run:

```bash
JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home ./mvnw -pl services/platform-api test -Dtest=ProfilePhotoPersistenceTest,ProfilePhotoServiceTest,ProfileReviewServiceTest,ProfileSubmissionServiceTest,AdminProfileReviewApiTest,GuestProfileSubmissionApiTest,CanonicalSnapshotHasherTest
```

Expected: PASS.

- [ ] **Step 8: Commit Task 4**

```bash
git add services/platform-api/src/main/resources/db/migration/V8__slim_photo_metadata.sql services/platform-api/src/main/java/com/love/archive/guest/persistence/ProfilePhotoEntity.java services/platform-api/src/main/java/com/love/archive/review/persistence/ProfileRevisionPhotoEntity.java services/platform-api/src/main/java/com/love/archive/guest/application/ProfilePhotoView.java services/platform-api/src/main/java/com/love/archive/guest/application/ProfilePhotoService.java services/platform-api/src/main/java/com/love/archive/guest/application/GuestProfileSnapshot.java services/platform-api/src/main/java/com/love/archive/guest/application/DatabaseGuestProfileSnapshotProvider.java services/platform-api/src/main/java/com/love/archive/review/application/ProfileSubmissionService.java services/platform-api/src/main/java/com/love/archive/review/application/ProfileRevisionView.java services/platform-api/src/main/java/com/love/archive/review/application/ProfileReviewService.java services/platform-api/src/test/java/com/love/archive/profile/ProfilePhotoPersistenceTest.java services/platform-api/src/test/java/com/love/archive/guest/application/ProfilePhotoServiceTest.java services/platform-api/src/test/java/com/love/archive/review/application/ProfileReviewServiceTest.java services/platform-api/src/test/java/com/love/archive/review/application/ProfileSubmissionServiceTest.java services/platform-api/src/test/java/com/love/archive/review/web/AdminProfileReviewApiTest.java services/platform-api/src/test/java/com/love/archive/review/web/GuestProfileSubmissionApiTest.java
git commit -m "feat(profile): store only photo references after metadata slim migration"
```

---

### Task 5: Staged COS-only photo upload

**Files:**
- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/StagedPhotoView.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/application/ProfilePhotoService.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/web/GuestProfilePhotoController.java`
- Test: `services/platform-api/src/test/java/com/love/archive/guest/web/GuestProfilePhotoApiTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/guest/application/ProfilePhotoServiceTest.java`

**Interfaces:**
- Consumes: `PhotoFileValidator`, `ObjectStorageService`, `PhotoCategory`, and the current `ProfilePhotoView`.
- Produces: `StagedPhotoView(String objectKey, String category, String previewUrl)`; `ProfilePhotoService.upload(long accountId, PhotoCategory, byte[])` returns it without touching the DB; `delete(...)` no longer exists; `GET /photos` keeps returning persisted rows.

- [ ] **Step 1: Write the failing service tests**

In `ProfilePhotoServiceTest.java`, rewrite the setup and tests:

- Remove `profileId` seeding, the `rejectsUploadBeforeDraftExists` test, the `rejectsUploadOrDeleteWhenPendingReview` test, `avatarUploadReplacesExistingAvatarAndLifeStillCapsAtSix`, `avatarReplacementKeepsOldObjectWhenSnapshotted`, `deleteRemovesRowAndObjectWhenNotSnapshotted`, `deleteKeepsObjectWhenSnapshotted`, and `deleteRejectsForeignPhotoAsNotFound`. Keep a slim `insertAccount` helper for the account prefix.
- Add:

```java
@Test
void uploadReturnsObjectKeyAndNeverWritesDatabase() throws Exception {
    ProfilePhotoService.StagedPhotoView staged =
            photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());

    assertThat(staged.objectKey()).startsWith("profiles/" + accountId + "/avatar/");
    assertThat(staged.category()).isEqualTo("AVATAR");
    assertThat(staged.previewUrl()).startsWith("https://loveplatform-1314980040");
    assertThat(photoMapper.selectCount(Wrappers.lambdaQuery())).isZero();
    assertThat(profileMapper.selectCount(Wrappers.lambdaQuery())).isZero();
    verify(storageService, never()).delete(anyString());
}

@Test
void listReturnsOnlyPersistedRows() throws Exception {
    assertThat(photoService.list(accountId)).isEmpty();

    photoService.upload(accountId, PhotoCategory.AVATAR, imageBytes());

    assertThat(photoService.list(accountId)).isEmpty();
}
```

The `@BeforeEach` should still stub `storageService.put(...)` and `signDownloadUrl(...)`; it no longer inserts a `GuestProfileEntity`.

- [ ] **Step 2: Run the service tests and verify RED**

Run:

```bash
JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home ./mvnw -pl services/platform-api test -Dtest=ProfilePhotoServiceTest
```

Expected: FAIL (upload still requires a profile and writes rows).

- [ ] **Step 3: Create the staged view and rewrite the service**

Create `StagedPhotoView.java`:

```java
package com.love.archive.guest.application;

public record StagedPhotoView(
        String objectKey,
        String category,
        String previewUrl) {
}
```

Rewrite `ProfilePhotoService.java` to:

- keep fields `profileMapper`, `photoMapper`, `storageService`, `photoFileValidator`; remove `accountStatusQuery`.
- replace `upload` with

```java
public StagedPhotoView upload(long accountId, PhotoCategory category, byte[] content) {
    PhotoFileValidator.ImageInfo image = photoFileValidator.validate(content);
    String objectKey = photoKey(accountId, category, image.contentType());
    StoredObjectView stored = storageService.put(objectKey, content, image.contentType());
    return new StagedPhotoView(
            stored.objectKey(),
            category.name(),
            storageService.signDownloadUrl(stored.objectKey(), URL_TTL));
}
```

- keep `list(long accountId)` exactly as is (it already handles a missing profile by returning `List.of()`), but drop only its `accountStatusQuery.requireActive` call; keep the existing `@Transactional(readOnly = true)` annotation.
- delete `delete`, `requireOwnedProfile`, `requireEditable`, `replaceExistingAvatar`, `requireLifeCountAvailable`, `countLimit`, and `photoNotFound`; remove now-unused imports (`ProfileStatus`, `GuestProfileMapper` if only used by those helpers, `Wrappers` still needed by `list`).

- [ ] **Step 4: Move the controller to the staged endpoint**

Rewrite `GuestProfilePhotoController.java`:

```java
@PostMapping("/photo-uploads")
public ApiResponse<StagedPhotoView> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam PhotoCategory category,
        HttpServletRequest request) {
    byte[] content;
    try {
        content = file.getBytes();
    } catch (IOException exception) {
        throw new ApiException(
                HttpStatus.BAD_REQUEST, "PHOTO_CONTENT_INVALID", "文件内容不能为空");
    }
    StagedPhotoView staged = photoService.upload(
            guestIdentity.currentGuestAccountId(), category, content);
    return ApiResponse.success(staged, RequestIdFilter.current(request));
}

@GetMapping("/photos")
public ApiResponse<List<ProfilePhotoView>> list(HttpServletRequest request) {
    return ApiResponse.success(
            photoService.list(guestIdentity.currentGuestAccountId()),
            RequestIdFilter.current(request));
}
```

Remove `@DeleteMapping`, the `delete` handler, and the `ResponseEntity`/`HttpStatus.CREATED` usage; remove unused imports (`DeleteMapping`, `PathVariable`, `ResponseEntity`).

- [ ] **Step 5: Rewrite the photo API tests for the staged endpoint**

In `GuestProfilePhotoApiTest.java`:

- The `@BeforeEach` no longer needs to save a draft; keep provisioning, but change `guestCookie` to `String guestToken` from `activateAndLogin(...)`, which now returns the token:

```java
private String activateAndLogin(String phone, String credential, String password)
        throws Exception {
    mockMvc.perform(post("/api/v1/guest/auth/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"phone":"%s","initialCredential":"%s","newPassword":"%s"}
                            """.formatted(phone, credential, password)))
            .andExpect(status().isOk());
    MvcResult login = mockMvc.perform(post("/api/v1/guest/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"phone":"%s","password":"%s"}
                            """.formatted(phone, password)))
            .andExpect(status().isOk())
            .andReturn();
    return new ObjectMapper()
            .readTree(login.getResponse().getContentAsString())
            .get("data")
            .get("accessToken")
            .asText();
}
```

- Replace `photoEndpointsRequireGuestLogin` with `POST /photo-uploads` and `GET /photos` without a token → 401.
- Replace `uploadsValidPngAndReturns201` with:

```java
@Test
void uploadsValidPngReturnsObjectKeyWithoutDatabaseWrites() throws Exception {
    mockMvc.perform(multipart("/api/v1/guest/profile/photo-uploads")
                    .file("file", pngBytes())
                    .param("category", "AVATAR")
                    .header("Authorization", "Bearer " + guestToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.objectKey")
                    .value(org.hamcrest.Matchers.matchesPattern(
                            "profiles/\\d+/avatar/[0-9a-fA-F-]{36}\\.png")))
            .andExpect(jsonPath("$.data.category").value("AVATAR"))
            .andExpect(jsonPath("$.data.previewUrl").isNotEmpty());

    assertThat(photoMapper.selectCount(Wrappers.lambdaQuery())).isZero();
    assertThat(profileMapper.selectCount(Wrappers.lambdaQuery())).isZero();
}
```

  (autowire `ProfilePhotoMapper photoMapper` and `GuestProfileMapper profileMapper`; `Wrappers` import from MyBatis-Plus.)
- Keep the invalid-format/oversized/category tests, moving each request to `/photo-uploads` and swapping `.cookie(guestCookie)`/`.header("Origin", ...)` for `.header("Authorization", "Bearer " + guestToken)`.
- Replace `deletesOwnedPhoto` with:

```java
@Test
void oldPersistentEndpointsAreRemoved() throws Exception {
    mockMvc.perform(multipart("/api/v1/guest/profile/photos")
                    .file("file", pngBytes())
                    .param("category", "AVATAR")
                    .header("Authorization", "Bearer " + guestToken))
            .andExpect(status().isNotFound());
    mockMvc.perform(delete("/api/v1/guest/profile/photos/1")
                    .header("Authorization", "Bearer " + guestToken))
            .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/v1/guest/profile/photos")
                    .header("Authorization", "Bearer " + guestToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
}
```

- [ ] **Step 6: Run the photo suites and verify GREEN**

Run:

```bash
JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home ./mvnw -pl services/platform-api test -Dtest=ProfilePhotoServiceTest,GuestProfilePhotoApiTest
```

Expected: PASS.

- [ ] **Step 7: Commit Task 5**

```bash
git add services/platform-api/src/main/java/com/love/archive/guest/application/StagedPhotoView.java services/platform-api/src/main/java/com/love/archive/guest/application/ProfilePhotoService.java services/platform-api/src/main/java/com/love/archive/guest/web/GuestProfilePhotoController.java services/platform-api/src/test/java/com/love/archive/guest/web/GuestProfilePhotoApiTest.java services/platform-api/src/test/java/com/love/archive/guest/application/ProfilePhotoServiceTest.java
git commit -m "feat(guest): stage photo uploads to COS without database writes"
```

---

### Task 6: Save draft with full photo collection sync

**Files:**
- Create: `services/platform-api/src/main/java/com/love/archive/guest/application/ProfilePhotoTarget.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/application/SaveGuestProfileCommand.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/web/SaveGuestProfileRequest.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/application/GuestProfileDraftService.java`
- Test: `services/platform-api/src/test/java/com/love/archive/guest/application/GuestProfilePhotoSaveTest.java`
- Test: `services/platform-api/src/test/java/com/love/archive/guest/web/GuestProfilePhotoApiTest.java`

**Interfaces:**
- Consumes: `SaveGuestProfileCommand`, `ProfilePhotoMapper`, `ProfilePhotoEntity`, `ObjectStorageService`, `PhotoCategory`.
- Produces: `ProfilePhotoTarget(String avatar, List<String> life)`, `SaveGuestProfileCommand(..., List<ProfileFieldInput> dynamicFields, ProfilePhotoTarget photos)` (plus a legacy 13-arg constructor defaulting photos to empty), and draft-save photo reconciliation returning the removed object keys for post-commit deletion.

- [ ] **Step 1: Write the failing save-sync service tests**

Create `GuestProfilePhotoSaveTest.java` in `com.love.archive.guest.application` extending `ApiIntegrationTest`, with the same seeding pattern as `ProfilePhotoServiceTest` (admin + active account + `@MockitoBean ObjectStorageService` stubbing `put`, `signDownloadUrl`, `delete`). Add:

```java
@Test
void firstSaveCreatesSlimPhotoRows() throws Exception {
    draftService.save(accountId, command(target(avatarKey("a"), List.of(lifeKey("b")))), "req-1");

    List<ProfilePhotoEntity> rows = photoMapper.selectList(Wrappers.lambdaQuery());
    assertThat(rows).extracting(ProfilePhotoEntity::getObjectKey)
            .containsExactlyInAnyOrder(avatarKey("a"), lifeKey("b"));
    assertThat(rows).extracting(ProfilePhotoEntity::getSortOrder)
            .containsExactlyInAnyOrder(0, 0);
}

@Test
void resaveReplacesRemovesAndReorders() throws Exception {
    draftService.save(accountId, command(target(avatarKey("a"), List.of(lifeKey("b"), lifeKey("c")))), "req-1");
    draftService.save(accountId, command(target(avatarKey("d"), List.of(lifeKey("c"), lifeKey("b")))), "req-2");

    List<ProfilePhotoEntity> rows = photoMapper.selectList(
            Wrappers.<ProfilePhotoEntity>lambdaQuery().orderByAsc(ProfilePhotoEntity::getSortOrder));
    assertThat(rows).extracting(ProfilePhotoEntity::getObjectKey)
            .containsExactly(lifeKey("b"), lifeKey("c"), avatarKey("d"));
    assertThat(rows).extracting(ProfilePhotoEntity::getCategory)
            .containsExactly(PhotoCategory.LIFE, PhotoCategory.LIFE, PhotoCategory.AVATAR);
    assertThat(photoMapper.selectCount(
                    Wrappers.<ProfilePhotoEntity>lambdaQuery()
                            .eq(ProfilePhotoEntity::getObjectKey, avatarKey("a"))))
            .isZero();
}

@Test
void rejectsInvalidDuplicateOrOversizedPhotoCollections() throws Exception {
    assertCode(() -> draftService.save(accountId, command(target(avatarKey("a"), List.of(avatarKey("b")))), "req-bad-1"),
            "PHOTO_REFERENCE_INVALID");
    assertCode(() -> draftService.save(accountId, command(target(
            "profiles/999/avatar/" + uuid() + ".jpg", List.of())), "req-bad-2"),
            "PHOTO_REFERENCE_INVALID");
    assertCode(() -> draftService.save(accountId, command(target(
            null, List.of(avatarKey("a")))), "req-bad-3"),
            "PHOTO_REFERENCE_INVALID");
    assertCode(() -> draftService.save(accountId, command(target(
            null, java.util.stream.IntStream.range(0, 7)
                    .mapToObj(i -> lifeKey("x" + i)).toList())), "req-bad-4"),
            "PHOTO_COUNT_LIMIT_EXCEEDED");
}

@Test
void deletesUnreferencedObjectsOnlyAfterCommit() throws Exception {
    draftService.save(accountId, command(target(avatarKey("a"), List.of())), "req-1");

    draftService.save(accountId, command(target(avatarKey("b"), List.of())), "req-2");

    verify(storageService).delete(avatarKey("a"));
}

@Test
void keepsObjectsReferencedByRevisions() throws Exception {
    draftService.save(accountId, command(target(avatarKey("a"), List.of())), "req-1");
    insertRevisionSnapshot(avatarKey("a"));

    draftService.save(accountId, command(target(avatarKey("b"), List.of())), "req-2");

    verify(storageService, never()).delete(anyString());
}

@Test
void saveTransactionNeverCallsObjectStoragePut() throws Exception {
    draftService.save(accountId, command(target(avatarKey("a"), List.of())), "req-1");

    verify(storageService, never()).put(anyString(), any(byte[].class), anyString());
}
```

Helpers: `avatarKey(String uuidSuffix)` builds `"profiles/" + accountId + "/avatar/" + uuidSuffix + ".jpg"`, `lifeKey` similar; `uuid()` returns a fresh `UUID.randomUUID().toString()`; `target(avatar, life)` returns `new ProfilePhotoTarget(avatar, life)`; `command(...)` wraps into `new SaveGuestProfileCommand(null, "男", null, null, null, null, null, null, null, null, null, null, List.of(), photos)`; reuse `insertRevisionSnapshot` from `ProfilePhotoServiceTest` minus metadata setters; keep the local `assertCode` helper.

- [ ] **Step 2: Run the new tests and verify RED**

Run:

```bash
JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home ./mvnw -pl services/platform-api test -Dtest=GuestProfilePhotoSaveTest
```

Expected: FAIL (command record has no `photos` component yet).

- [ ] **Step 3: Add the photo target record and command support**

Create `ProfilePhotoTarget.java`:

```java
package com.love.archive.guest.application;

import java.util.List;

public record ProfilePhotoTarget(String avatar, List<String> life) {

    public ProfilePhotoTarget {
        life = life == null ? List.of() : List.copyOf(life);
    }

    public static ProfilePhotoTarget empty() {
        return new ProfilePhotoTarget(null, List.of());
    }
}
```

In `SaveGuestProfileCommand.java`, add the component and a legacy constructor:

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
        List<ProfileFieldInput> dynamicFields,
        ProfilePhotoTarget photos) {

    public SaveGuestProfileCommand {
        dynamicFields = dynamicFields == null ? List.of() : List.copyOf(dynamicFields);
        photos = photos == null ? ProfilePhotoTarget.empty() : photos;
    }

    public SaveGuestProfileCommand(
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
            List<ProfileFieldInput> dynamicFields) {
        this(expectedVersion, gender, birthDate, heightCm, education, occupation,
                incomeRange, city, wechatId, douyinId, douyinNickname,
                douyinProfileUrl, dynamicFields, ProfilePhotoTarget.empty());
    }
}
```

In `SaveGuestProfileRequest.java`, add the request component and mapping:

```java
public record SaveGuestProfileRequest(
        ...,
        List<@Valid DynamicFieldRequest> dynamicFields,
        PhotoCollectionRequest photos) {

    public SaveGuestProfileCommand toCommand() {
        List<ProfileFieldInput> fields = dynamicFields == null
                ? List.of()
                : dynamicFields.stream().map(DynamicFieldRequest::toInput).toList();
        return new SaveGuestProfileCommand(
                expectedVersion, gender, birthDate, heightCm, education, occupation,
                incomeRange, city, wechatId, douyinId, douyinNickname,
                douyinProfileUrl, fields,
                photos == null ? ProfilePhotoTarget.empty() : photos.toTarget());
    }

    public record PhotoCollectionRequest(
            @Size(max = 1024) String avatar,
            @Size(max = 6) List<@Size(max = 1024) String> life) {

        ProfilePhotoTarget toTarget() {
            return new ProfilePhotoTarget(avatar, life);
        }
    }
}
```

- [ ] **Step 4: Implement validation and sync in the draft service**

In `GuestProfileDraftService.java`:

- Add dependencies: `ProfilePhotoMapper photoMapper`, `ObjectStorageService storageService`; add `private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(GuestProfileDraftService.class);` and `private static final Pattern PHOTO_OBJECT_KEY = Pattern.compile("profiles/(\\d+)/(avatar|life)/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\.(jpg|png|webp)");` and imports for `PhotoCategory`, `ProfilePhotoEntity`, `java.util.regex.Matcher`, `java.util.regex.Pattern`, `org.springframework.transaction.support.TransactionSynchronization`, `org.springframework.transaction.support.TransactionSynchronizationManager`, `java.util.HashSet`, `java.util.LinkedHashSet`, `java.util.stream.Collectors`.
- In `save`, after `replaceDynamicValues(...)` and before `appendAudit(...)`:

```java
List<TargetPhoto> targets = validatePhotos(accountId, command.photos());
Set<String> removedKeys = syncPhotos(saved.getId(), targets);
scheduleOrphanDeletion(removedKeys);
```

- Add the validation and sync methods:

```java
private List<TargetPhoto> validatePhotos(long accountId, ProfilePhotoTarget photos) {
    List<TargetPhoto> targets = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    if (photos == null) {
        return List.of();
    }
    if (photos.avatar() != null) {
        targets.add(requirePhoto(accountId, "avatar", photos.avatar(), seen, 0));
    }
    if (photos.life() != null) {
        if (photos.life().size() > 6) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PHOTO_COUNT_LIMIT_EXCEEDED",
                    "头像最多 1 张，生活照最多 6 张");
        }
        for (int index = 0; index < photos.life().size(); index++) {
            targets.add(requirePhoto(
                    accountId, "life", photos.life().get(index), seen, index));
        }
    }
    return List.copyOf(targets);
}

private static TargetPhoto requirePhoto(
        long accountId,
        String expectedCategory,
        String objectKey,
        Set<String> seen,
        int sortOrder) {
    Matcher matcher = PHOTO_OBJECT_KEY.matcher(objectKey);
    if (!matcher.matches()
            || !matcher.group(1).equals(Long.toString(accountId))
            || !matcher.group(2).equals(expectedCategory)
            || !seen.add(objectKey)) {
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "PHOTO_REFERENCE_INVALID",
                "照片对象引用不合法");
    }
    PhotoCategory category = PhotoCategory.valueOf(
            expectedCategory.toUpperCase(Locale.ROOT));
    return new TargetPhoto(category, objectKey, sortOrder);
}

private Set<String> syncPhotos(long profileId, List<TargetPhoto> targets) {
    List<ProfilePhotoEntity> existing = photoMapper.selectList(
            Wrappers.<ProfilePhotoEntity>lambdaQuery()
                    .eq(ProfilePhotoEntity::getGuestProfileId, profileId));
    Map<String, ProfilePhotoEntity> existingByKey = existing.stream()
            .collect(Collectors.toMap(ProfilePhotoEntity::getObjectKey, photo -> photo));
    Set<String> targetKeys = targets.stream()
            .map(TargetPhoto::objectKey)
            .collect(Collectors.toSet());
    Set<String> removedKeys = existing.stream()
            .map(ProfilePhotoEntity::getObjectKey)
            .filter(key -> !targetKeys.contains(key))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    List<ProfilePhotoEntity> retained = existing.stream()
            .filter(photo -> targetKeys.contains(photo.getObjectKey()))
            .toList();

    if (!removedKeys.isEmpty()) {
        photoMapper.delete(Wrappers.<ProfilePhotoEntity>lambdaQuery()
                .eq(ProfilePhotoEntity::getGuestProfileId, profileId)
                .in(ProfilePhotoEntity::getObjectKey, removedKeys));
    }
    for (ProfilePhotoEntity photo : retained) {
        photoMapper.update(Wrappers.<ProfilePhotoEntity>lambdaUpdate()
                .eq(ProfilePhotoEntity::getId, photo.getId())
                .set(ProfilePhotoEntity::getSortOrder, -(photo.getSortOrder() + 1)));
    }
    OffsetDateTime now = OffsetDateTime.now();
    for (TargetPhoto target : targets) {
        if (existingByKey.containsKey(target.objectKey())) {
            continue;
        }
        ProfilePhotoEntity photo = new ProfilePhotoEntity();
        photo.setGuestProfileId(profileId);
        photo.setCategory(target.category());
        photo.setObjectKey(target.objectKey());
        photo.setSortOrder(target.sortOrder());
        photo.setCreatedAt(now);
        photo.setUpdatedAt(now);
        photoMapper.insert(photo);
    }
    for (TargetPhoto target : targets) {
        ProfilePhotoEntity photo = existingByKey.get(target.objectKey());
        if (photo != null) {
            photoMapper.update(Wrappers.<ProfilePhotoEntity>lambdaUpdate()
                    .eq(ProfilePhotoEntity::getId, photo.getId())
                    .set(ProfilePhotoEntity::getSortOrder, target.sortOrder()));
        }
    }
    return removedKeys;
}

private void scheduleOrphanDeletion(Set<String> removedKeys) {
    if (removedKeys.isEmpty()) {
        return;
    }
    TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String objectKey : removedKeys) {
                        if (photoMapper.countRevisionReferences(objectKey) == 0) {
                            try {
                                storageService.delete(objectKey);
                            } catch (RuntimeException exception) {
                                LOGGER.warn("删除未引用照片对象失败: {}", objectKey, exception);
                            }
                        }
                    }
                }
            });
}
```

- Add the nested record near the other private records:

```java
private record TargetPhoto(
        PhotoCategory category,
        String objectKey,
        int sortOrder) {
}
```

- [ ] **Step 5: Add the API-level save/photo binding test**

In `GuestProfilePhotoApiTest.java` add (reuse the seeded guest token; no draft needed):

```java
@Test
void savesDraftWithPhotoCollectionAndListsPersistedRows() throws Exception {
    String avatarKey = "profiles/" + guestAccountId() + "/avatar/"
            + java.util.UUID.randomUUID() + ".jpg";
    mockMvc.perform(put("/api/v1/guest/profile/draft")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + guestToken)
                    .content("""
                            {"expectedVersion":null,
                             "photos":{"avatar":"%s","life":[]}}
                            """.formatted(avatarKey)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"));

    mockMvc.perform(get("/api/v1/guest/profile/photos")
                    .header("Authorization", "Bearer " + guestToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].objectKey").value(avatarKey))
            .andExpect(jsonPath("$.data[0].previewUrl").isNotEmpty());
}

@Test
void rejectsCrossAccountPhotoReferenceOnSave() throws Exception {
    mockMvc.perform(put("/api/v1/guest/profile/draft")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + guestToken)
                    .content("""
                            {"expectedVersion":null,
                             "photos":{"avatar":"profiles/999/avatar/%s.jpg","life":[]}}
                            """.formatted(java.util.UUID.randomUUID())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PHOTO_REFERENCE_INVALID"));
}
```

Add a small helper `long guestAccountId()` that returns the provisioned guest's `accountId` (store it as a field during `@BeforeEach`), and import `put` and `MediaType`.

- [ ] **Step 6: Run the photo save suites and verify GREEN**

Run:

```bash
JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home ./mvnw -pl services/platform-api test -Dtest=GuestProfilePhotoSaveTest,GuestProfilePhotoApiTest,GuestProfileDraftServiceTest
```

Expected: PASS (existing draft-save tests still pass through the legacy constructor; the new sync tests pass; the API binding tests pass).

- [ ] **Step 7: Commit Task 6**

```bash
git add services/platform-api/src/main/java/com/love/archive/guest/application/ProfilePhotoTarget.java services/platform-api/src/main/java/com/love/archive/guest/application/SaveGuestProfileCommand.java services/platform-api/src/main/java/com/love/archive/guest/web/SaveGuestProfileRequest.java services/platform-api/src/main/java/com/love/archive/guest/application/GuestProfileDraftService.java services/platform-api/src/test/java/com/love/archive/guest/application/GuestProfilePhotoSaveTest.java services/platform-api/src/test/java/com/love/archive/guest/web/GuestProfilePhotoApiTest.java
git commit -m "feat(guest): save full photo collection with draft"
```

---

### Task 7: Photo snapshot behavior in submission and review

**Files:**
- Modify: `services/platform-api/src/test/java/com/love/archive/review/application/CanonicalSnapshotHasherTest.java`
- Modify: `services/platform-api/src/test/java/com/love/archive/review/application/ProfileSubmissionServiceTest.java`
- Modify: `services/platform-api/src/test/java/com/love/archive/review/web/GuestProfileSubmissionApiTest.java`

**Interfaces:**
- Consumes: slimmed `GuestProfileSnapshot.Photo`, `ProfileRevisionView.Photo`, and the canonical hasher.
- Produces: verified behavior that a changed photo key or sort order changes the submission digest and that revision snapshots persist and expose `category`, `objectKey`, `sortOrder`, and a signed `downloadUrl`.

- [ ] **Step 1: Write the failing hasher photo tests**

In `CanonicalSnapshotHasherTest.java`, add a photos-capable snapshot overload and two tests:

```java
@Test
void changesDigestWhenPhotoObjectKeyChanges() {
    List<GuestProfileSnapshot.Photo> photos = List.of(
            new GuestProfileSnapshot.Photo("AVATAR", "profiles/1/avatar/a.jpg", 0));
    List<GuestProfileSnapshot.Photo> changed = List.of(
            new GuestProfileSnapshot.Photo("AVATAR", "profiles/1/avatar/b.jpg", 0));

    assertThat(hasher.sha256(snapshot(BASE, photos)))
            .isNotEqualTo(hasher.sha256(snapshot(BASE, changed)));
}

@Test
void changesDigestWhenPhotoSortOrderChanges() {
    List<GuestProfileSnapshot.Photo> photos = List.of(
            new GuestProfileSnapshot.Photo("LIFE", "profiles/1/life/a.jpg", 0),
            new GuestProfileSnapshot.Photo("LIFE", "profiles/1/life/b.jpg", 1));
    List<GuestProfileSnapshot.Photo> reordered = List.of(
            new GuestProfileSnapshot.Photo("LIFE", "profiles/1/life/a.jpg", 1),
            new GuestProfileSnapshot.Photo("LIFE", "profiles/1/life/b.jpg", 0));

    assertThat(hasher.sha256(snapshot(BASE, photos)))
            .isNotEqualTo(hasher.sha256(snapshot(BASE, reordered)));
}
```

Refactor `snapshot(ProtectedPlaintext values)` to delegate to `snapshot(values, List.of())`, and implement `snapshot(ProtectedPlaintext values, List<GuestProfileSnapshot.Photo> photos)` by appending `photos` as the last argument of the existing `GuestProfileSnapshot` constructor call.

- [ ] **Step 2: Run the hasher test and verify RED**

Run:

```bash
JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home ./mvnw -pl services/platform-api test -Dtest=CanonicalSnapshotHasherTest
```

Expected: FAIL to compile or assert (photo fields removed in Task 4; if the file already compiles, the new assertions pass — in that case this step is a no-op RED and GREEN is confirmed in Step 4).

- [ ] **Step 3: Strengthen submission/review photo assertions**

In `ProfileSubmissionServiceTest.snapshotsCurrentPhotosIntoImmutableRevision`, extend assertions:

```java
assertThat(revision.photos().getFirst().objectKey())
        .isEqualTo("profiles/" + profile().getId() + "/avatar/fixture.jpg");
assertThat(revision.photos().getFirst().sortOrder()).isZero();
assertThat(revision.photos().getFirst().downloadUrl())
        .startsWith("https://loveplatform-1314980040");
ProfileRevisionPhotoEntity snapshot = revisionPhotoMapper.selectOne(
        Wrappers.<ProfileRevisionPhotoEntity>lambdaQuery()
                .eq(ProfileRevisionPhotoEntity::getProfileRevisionId, revision.id()));
assertThat(snapshot.getObjectKey())
        .isEqualTo("profiles/" + profile().getId() + "/avatar/fixture.jpg");
assertThat(snapshot.getSortOrder()).isZero();
```

In `GuestProfileSubmissionApiTest`, after the existing submit assertions, add:

```java
assertThat(result.getResponse().getContentAsString()).contains("fixture.jpg");
```

and assert the revision detail endpoint returns a signed photo URL:

```java
long revisionId = new ObjectMapper()
        .readTree(result.getResponse().getContentAsString())
        .get("data")
        .get("id")
        .asLong();
mockMvc.perform(get("/api/v1/guest/profile/revisions/{revisionId}", revisionId)
                .header("Authorization", "Bearer " + guestToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.photos[0].objectKey").value(
                "profiles/" + profileId + "/avatar/fixture.jpg"))
        .andExpect(jsonPath("$.data.photos[0].downloadUrl").isNotEmpty());
```

(adapt to the existing field names in that test — the fixture photo is inserted with `profiles/{profileId}/avatar/fixture.jpg`.)

- [ ] **Step 4: Run the affected suites and verify GREEN**

Run:

```bash
JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home ./mvnw -pl services/platform-api test -Dtest=CanonicalSnapshotHasherTest,ProfileSubmissionServiceTest,GuestProfileSubmissionApiTest
```

Expected: PASS.

- [ ] **Step 5: Commit Task 7**

```bash
git add services/platform-api/src/test/java/com/love/archive/review/application/CanonicalSnapshotHasherTest.java services/platform-api/src/test/java/com/love/archive/review/application/ProfileSubmissionServiceTest.java services/platform-api/src/test/java/com/love/archive/review/web/GuestProfileSubmissionApiTest.java
git commit -m "test(review): cover photo snapshot digest and detail views"
```

---

### Task 8: Guest frontend Bearer auth

**Files:**
- Modify: `apps/guest-app/src/types/index.ts`
- Modify: `apps/guest-app/src/api/request.ts`
- Modify: `apps/guest-app/src/api/index.ts`
- Test: `apps/guest-app/src/api/request.test.ts`
- Test: `apps/guest-app/src/api/upload.test.ts`
- Test: `apps/guest-app/src/stores/auth.test.ts`

**Interfaces:**
- Consumes: `sessionStorage['guest-session']` containing `{accountId, status, accessToken, expiresIn}`.
- Produces: `request<T>` and `uploadPhoto` attach `Authorization: Bearer <token>`; both invoke the unauthorized handler on `401` and on `403` with code `AUTH_ACCOUNT_INACTIVE`; `GuestSession` gains `accessToken`/`expiresIn`.

- [ ] **Step 1: Write the failing request/auth tests**

In `request.test.ts` add:

```ts
it('attaches bearer token from stored session', async () => {
  sessionStorage.setItem(
    'guest-session',
    JSON.stringify({ accountId: 7, status: 'ACTIVE', accessToken: 'tok-1', expiresIn: 2592000 }),
  )
  stubRequest((options) => {
    expect(options.header).toMatchObject({ Authorization: 'Bearer tok-1' })
    options.success?.({
      statusCode: 200,
      header: {},
      cookies: [],
      data: { success: true, code: 'OK', message: '成功', data: null, requestId: 'r' },
    } as UniApp.RequestSuccessCallbackResult)
  })
  await request<null>({ url: '/guest/profile/draft' })
})

it('invokes unauthorized handler on 403 AUTH_ACCOUNT_INACTIVE', async () => {
  const handler = vi.fn()
  setUnauthorizedHandler(handler)
  stubRequest((options) => {
    options.success?.({
      statusCode: 403,
      header: {},
      cookies: [],
      data: { success: false, code: 'AUTH_ACCOUNT_INACTIVE', message: '账号已停用', data: null, requestId: 'r' },
    } as UniApp.RequestSuccessCallbackResult)
  })
  await expect(request<never>({ url: '/x' })).rejects.toThrow('账号已停用')
  expect(handler).toHaveBeenCalledTimes(1)
})
```

In `stores/auth.test.ts`, update the mock login response to include `accessToken` and add:

```ts
it('login persists bearer token in session', async () => {
  vi.mocked(api.login).mockResolvedValue({
    accountId: 1,
    status: 'ACTIVE',
    accessToken: 'tok-1',
    expiresIn: 2592000,
  })
  const store = useAuthStore()
  await store.login('13800138000', 'secret')
  expect(JSON.parse(sessionStorage.getItem('guest-session')!)).toMatchObject({
    accessToken: 'tok-1',
    expiresIn: 2592000,
  })
})
```

- [ ] **Step 2: Run the frontend tests and verify RED**

```bash
cd apps/guest-app && npx vitest run src/api/request.test.ts src/stores/auth.test.ts
```

Expected: FAIL (no Authorization header; session type lacks the fields).

- [ ] **Step 3: Add the token to the session type**

In `types/index.ts`:

```ts
export interface GuestSession {
  accountId: number
  status: string
  accessToken: string
  expiresIn: number
}
```

- [ ] **Step 4: Attach the header and handle inactive accounts in `request.ts`**

Add before `request`:

```ts
function bearerHeader(): Record<string, string> {
  const raw = sessionStorage.getItem('guest-session')
  if (!raw) return {}
  try {
    const session = JSON.parse(raw) as { accessToken?: string }
    if (session.accessToken) return { Authorization: `Bearer ${session.accessToken}` }
  } catch {
    // 会话损坏时按未登录处理
  }
  return {}
}
```

In `request`, change the header to:

```ts
header: { 'Content-Type': 'application/json', ...bearerHeader(), ...(options.header ?? {}) },
```

and after the existing `401` branch add:

```ts
if (response.statusCode === 403 && envelope?.code === 'AUTH_ACCOUNT_INACTIVE') {
  unauthorizedHandler()
  reject(new Error(envelope.message || '账号已停用'))
  return
}
```

In `uploadPhoto`, change the URL to `/guest/profile/photo-uploads`, add `header: bearerHeader()` to the `UniApp.UploadFileOption`, and in the success callback before the envelope check add:

```ts
const envelope = JSON.parse(response.data) as ApiEnvelope<ProfilePhotoView>
if (response.statusCode === 401 || (response.statusCode === 403 && envelope.code === 'AUTH_ACCOUNT_INACTIVE')) {
  unauthorizedHandler()
  reject(new Error(envelope.message || '请先登录'))
  return
}
```

- [ ] **Step 5: Update upload tests to the staged endpoint**

In `upload.test.ts`, change the expected URL to `/api/v1/guest/profile/photo-uploads`, store a session in `beforeEach` so the header assertion holds, and assert:

```ts
expect(options.header).toMatchObject({ Authorization: 'Bearer tok-1' })
```

Change the failure-case payload to `PHOTO_CONTENT_INVALID` (the staged endpoint never returns count-limit errors).

- [ ] **Step 6: Run the frontend tests and verify GREEN**

```bash
cd apps/guest-app && npx vitest run src/api src/stores
```

Expected: PASS.

- [ ] **Step 7: Commit Task 8**

```bash
git add apps/guest-app/src/types/index.ts apps/guest-app/src/api/request.ts apps/guest-app/src/api/index.ts apps/guest-app/src/api/request.test.ts apps/guest-app/src/api/upload.test.ts apps/guest-app/src/stores/auth.test.ts
git commit -m "feat(guest-app): send bearer token on requests and uploads"
```

---

### Task 9: Guest frontend staged photo state and save flow

**Files:**
- Modify: `apps/guest-app/src/types/index.ts`
- Modify: `apps/guest-app/src/api/index.ts`
- Modify: `apps/guest-app/src/stores/profile.ts`
- Modify: `apps/guest-app/src/pages/profile/index.vue`
- Test: `apps/guest-app/src/stores/profile.test.ts`

**Interfaces:**
- Consumes: `PhotoUploadResult {objectKey, category, previewUrl}`, `ProfilePhotoView {id, category, objectKey, sortOrder, previewUrl, createdAt}`.
- Produces: profile store photos as the local target collection `Array<{objectKey, category, previewUrl}>`; `save()` sends `photos: {avatar, life}` and then refreshes persisted photos; `removePhoto` never calls the API; `submit()` keeps strict save-then-submit ordering.

- [ ] **Step 1: Write the failing store tests**

In `profile.test.ts`, replace the mocked API list with `getDraft, listPhotos, saveDraft, submitProfile` (remove `deletePhoto`), update `ProfilePhotoView` fixtures to the slim shape, and add:

```ts
it('addUploaded replaces avatar and appends life uploads', () => {
  const store = useProfileStore()
  store.photos = [photo('AVATAR', 'a'), photo('LIFE', 'b')]

  store.addUploaded({ objectKey: 'c', category: 'AVATAR', previewUrl: 'https://cos/c' })
  store.addUploaded({ objectKey: 'd', category: 'LIFE', previewUrl: 'https://cos/d' })

  expect(store.photos.map((item) => item.objectKey)).toEqual(['c', 'b', 'd'])
})

it('removePhoto only changes local collection', () => {
  const store = useProfileStore()
  store.photos = [photo('AVATAR', 'a'), photo('LIFE', 'b')]

  store.removeByObjectKey('a')

  expect(store.photos.map((item) => item.objectKey)).toEqual(['b'])
  expect(api.deletePhoto).not.toHaveBeenCalled()
})

it('save sends the photo collection then refreshes persisted photos', async () => {
  vi.mocked(api.saveDraft).mockResolvedValue({ profileNo: 'p1', status: 'DRAFT' } as never)
  vi.mocked(api.listPhotos).mockResolvedValue([photo('AVATAR', 'a')])
  const store = useProfileStore()
  store.photos = [photo('AVATAR', 'a')]

  await store.save({ gender: '男' })

  expect(api.saveDraft).toHaveBeenCalledWith({
    gender: '男',
    photos: { avatar: 'a', life: [] },
  })
  expect(api.listPhotos).toHaveBeenCalledTimes(1)
  expect(store.photos.map((item) => item.objectKey)).toEqual(['a'])
})
```

Adjust the existing `loads draft and photos together` test to the slim `ProfilePhotoView` shape and keep `addPhoto` tests removed (replaced by `addUploaded`).

- [ ] **Step 2: Run the store tests and verify RED**

```bash
cd apps/guest-app && npx vitest run src/stores/profile.test.ts
```

Expected: FAIL (store API differs).

- [ ] **Step 3: Update types and API surface**

In `types/index.ts`:

```ts
export interface ProfilePhotoView {
  id: number
  category: 'AVATAR' | 'LIFE'
  objectKey: string
  sortOrder: number
  previewUrl: string
  createdAt: string
}

export interface PhotoUploadResult {
  objectKey: string
  category: 'AVATAR' | 'LIFE'
  previewUrl: string
}
```

In `api/index.ts`:

- `uploadPhoto` return type becomes `Promise<ApiEnvelope<PhotoUploadResult>>`; import `PhotoUploadResult`.
- `listPhotos` stays; delete `deletePhoto`.
- The store imports `PhotoUploadResult` from `@/types` directly; no re-export is needed.

- [ ] **Step 4: Rewrite the profile store**

Replace `stores/profile.ts`:

```ts
import { defineStore } from 'pinia'
import * as api from '@/api'
import type { GuestProfileDraft, PhotoUploadResult } from '@/types'

const IDEMPOTENCY_KEY = 'profile-submission-key'

export type LocalPhoto = Pick<PhotoUploadResult, 'objectKey' | 'category' | 'previewUrl'>

export const useProfileStore = defineStore('guest-profile', {
  state: () => ({
    draft: null as GuestProfileDraft | null,
    photos: [] as LocalPhoto[],
    loading: false,
  }),
  getters: {
    avatar: (state): LocalPhoto | undefined =>
      state.photos.find((photo) => photo.category === 'AVATAR'),
    lifePhotos: (state): LocalPhoto[] =>
      state.photos.filter((photo) => photo.category === 'LIFE'),
  },
  actions: {
    async load(): Promise<void> {
      this.loading = true
      try {
        const [draft, photos] = await Promise.all([api.getDraft(), api.listPhotos()])
        this.draft = draft
        this.photos = photos.map((photo) => ({
          objectKey: photo.objectKey,
          category: photo.category,
          previewUrl: photo.previewUrl,
        }))
      } finally {
        this.loading = false
      }
    },
    addUploaded(upload: PhotoUploadResult): void {
      if (upload.category === 'AVATAR') {
        this.photos = this.photos.filter((photo) => photo.category !== 'AVATAR')
      }
      this.photos.push({
        objectKey: upload.objectKey,
        category: upload.category,
        previewUrl: upload.previewUrl,
      })
    },
    removeByObjectKey(objectKey: string): void {
      this.photos = this.photos.filter((photo) => photo.objectKey !== objectKey)
    },
    async save(values: Record<string, unknown>): Promise<void> {
      const avatar = this.avatar
      this.draft = await api.saveDraft({
        ...values,
        photos: {
          avatar: avatar?.objectKey ?? null,
          life: this.lifePhotos.map((photo) => photo.objectKey),
        },
      })
      await this.refreshPhotos()
    },
    async refreshPhotos(): Promise<void> {
      const photos = await api.listPhotos()
      this.photos = photos.map((photo) => ({
        objectKey: photo.objectKey,
        category: photo.category,
        previewUrl: photo.previewUrl,
      }))
    },
    async submit(): Promise<{ id: number; status: string; reviewDeadlineAt: string }> {
      const key = idempotencyKey()
      return api.submitProfile(key)
    },
  },
})

function idempotencyKey(): string {
  const existing = sessionStorage.getItem(IDEMPOTENCY_KEY)
  if (existing) return existing
  const key = crypto.randomUUID()
  sessionStorage.setItem(IDEMPOTENCY_KEY, key)
  return key
}
```

- [ ] **Step 5: Update the profile page**

In `pages/profile/index.vue`:

- `avatar` computed → `store.avatar`; `lifePhotos` → `store.lifePhotos`.
- `choosePhoto`: after `const uploaded = await uploadPhoto(photo, category)`, call `store.addUploaded(uploaded.data as PhotoUploadResult)` and keep `uni.showToast({ title: '已上传', icon: 'success' })`.
- `removePhoto(photo: { objectKey: string })` → `store.removeByObjectKey(photo.objectKey)` and show a success toast; remove the `await`/API error handling.
- `save()` stays, but `store.save(...)` now sends the photo collection itself.
- `submit()` avatar check uses `store.avatar`.
- In the template, `:src="photo.previewUrl"` for staged and persisted photos (both shapes carry `previewUrl`); keys for `v-for` stay `photo.objectKey`.
- Import `PhotoUploadResult` type.

- [ ] **Step 6: Run frontend tests and type-check**

```bash
cd apps/guest-app && npx vitest run && npm run type-check
```

Expected: PASS (vitest and `vue-tsc --noEmit`).

- [ ] **Step 7: Commit Task 9**

```bash
git add apps/guest-app/src/types/index.ts apps/guest-app/src/api/index.ts apps/guest-app/src/stores/profile.ts apps/guest-app/src/pages/profile/index.vue apps/guest-app/src/stores/profile.test.ts
git commit -m "feat(guest-app): stage uploaded photos and save them with draft"
```

---

### Task 10: Documentation and full verification

**Files:**
- Modify: `README.md`
- Test: full backend and frontend suites

**Interfaces:**
- Consumes: everything from Tasks 1-9.
- Produces: an accurate README (guest Bearer auth, staged photo-upload endpoint, no cookie claims) and a fully green verification run.

- [ ] **Step 1: Update the README**

- In the intro, replace "照片字段的上传闭环待接入" with a statement that photo selection uploads to COS first and photos are persisted when the draft is saved.
- In the API table, add:

```text
| POST | `/api/v1/guest/profile/photo-uploads` | 访客 | 校验并上传照片到 COS，仅返回对象键（不落库） |
| GET | `/api/v1/guest/profile/photos` | 访客 | 查询已保存照片集合（含短时预览 URL） |
```

- In the auth paragraph, replace the cookie/Bearer description:

```text
管理员使用 `archive-token-admin` HttpOnly Cookie，访客使用 `Authorization: Bearer <token>` 请求头（登录/激活返回 `accessToken` 与 `expiresIn`）。访客会话在每次请求入口统一校验账号启用状态，账号停用后下一次请求立即失效。携带 Cookie 的管理后台写请求必须提供可信 `Origin` 或 `Referer`；访客请求不依赖 Cookie，不受来源校验影响。
```

- In the flow section, replace the draft-save sentence with:

```text
2. 访客选择图片时仅上传 COS 并暂存对象键；点击保存草稿时才创建/更新档案并把完整照片集合写入 `profile_photo`，点击提交时先保存再提交审核。
```

- [ ] **Step 2: Run the full backend test suite**

```bash
JAVA_HOME=/Users/alex/Documents/ChatGPT/love/.toolchains/jdk-25.0.4+7/Contents/Home ./mvnw -pl services/platform-api test
```

Expected: BUILD SUCCESS with all tests passing.

- [ ] **Step 3: Run the full frontend checks**

```bash
cd apps/guest-app && npx vitest run && npm run type-check
```

Expected: PASS.

- [ ] **Step 4: Commit Task 10**

```bash
git add README.md
git commit -m "docs: record bearer photo save flow"
```

---

## Self-Review Notes

- Spec coverage: auth architecture (Tasks 1-2), COS timeouts (Task 3), data model migration (Task 4), staged upload API (Task 5), save-time photo sync (Task 6), submission/review snapshot digest (Tasks 4+7), frontend bearer (Task 8), frontend staged state (Task 9), README (Task 10).
- No placeholders: every task carries exact code for the new logic and exact test assertions; mechanical cross-file edits list each file and the concrete replacement.
- Type consistency: `StagedPhotoView.objectKey/category/previewUrl`, `ProfilePhotoView.objectKey/previewUrl`, `GuestProfileSnapshot.Photo(category, objectKey, sortOrder)`, and `ProfileRevisionView.Photo(category, objectKey, sortOrder, downloadUrl)` are used with matching names across backend and frontend tasks.
