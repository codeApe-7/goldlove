# Social Account Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the four encrypted social-account fields in the guest profile UI and require only WeChat ID when a profile is submitted.

**Architecture:** Register the existing encrypted social columns as CORE field definitions through Flyway, then extend the backend readiness validator to recognize their persisted encrypted values. On the guest client, centralize snake_case definition-code to camelCase DTO-property conversion so rendering, completion, draft hydration, and save payloads share one source of truth.

**Tech Stack:** PostgreSQL 18/Flyway, Java 25/Spring Boot 4.1/MyBatis-Plus/JUnit 5, Vue 3/uni-app/TypeScript/Vitest/Sass.

## Global Constraints

- `wechat_id` is required only for submission; saving an incomplete draft remains allowed.
- `douyin_id`, `douyin_nickname`, and `douyin_profile_url` are optional in both frontend and backend validation.
- All four values continue using the existing encrypted `guest_profile` and `profile_revision` columns; no plaintext or dynamic-field storage is introduced.
- The guest profile keeps the prototype's black, white, and warm-gray card system and supports 390×844 and 320×720 viewports without horizontal overflow.
- Existing user-owned untracked files `.m2/` and `docs/feature-inventory.md` must remain untouched.

---

### Task 1: Register social fields in Flyway and expose them through the guest API

**Files:**
- Create: `services/platform-api/src/main/resources/db/migration/V7__social_account_field_definitions.sql`
- Modify: `services/platform-api/src/test/java/com/love/archive/guest/web/GuestProfileFieldDefinitionsApiTest.java`

**Interfaces:**
- Consumes: the existing `profile_field_definition(field_code, label, storage_kind, data_type, required, enabled, options_json, sort_order, instructions)` schema and `/api/v1/guest/profile/field-definitions` endpoint.
- Produces: enabled CORE definitions `wechat_id`, `douyin_id`, `douyin_nickname`, and `douyin_profile_url`, with only `wechat_id.required=true`.

- [ ] **Step 1: Write the failing field-definition API assertions**

Update `returnsEnabledDefinitionsSortedAndExcludesDisabled()` to expect 11 enabled definitions and assert the social-field metadata:

```java
.andExpect(jsonPath("$.data.length()").value(11))
.andExpect(jsonPath("$.data[?(@.fieldCode == 'wechat_id')].label")
        .value(org.hamcrest.Matchers.contains("微信号")))
.andExpect(jsonPath("$.data[?(@.fieldCode == 'wechat_id')].required")
        .value(org.hamcrest.Matchers.contains(true)))
.andExpect(jsonPath("$.data[?(@.fieldCode == 'douyin_id')].required")
        .value(org.hamcrest.Matchers.contains(false)))
.andExpect(jsonPath("$.data[?(@.fieldCode == 'douyin_nickname')].required")
        .value(org.hamcrest.Matchers.contains(false)))
.andExpect(jsonPath("$.data[?(@.fieldCode == 'douyin_profile_url')].required")
        .value(org.hamcrest.Matchers.contains(false)))
```

- [ ] **Step 2: Run the focused API test and verify RED**

Run:

```bash
./mvnw -pl services/platform-api test -Dtest=GuestProfileFieldDefinitionsApiTest
```

Expected: FAIL because the endpoint still returns 7 definitions and no `wechat_id` item.

- [ ] **Step 3: Add the idempotent V7 migration**

Create `V7__social_account_field_definitions.sql` with an upsert for the four rows:

```sql
INSERT INTO profile_field_definition (
    field_code, label, storage_kind, data_type, required, enabled,
    ever_used, options_json, sort_order, instructions, version,
    created_at, updated_at
) VALUES
    ('wechat_id', '微信号', 'CORE', 'TEXT', TRUE, TRUE,
     FALSE, NULL, 80, '请输入微信号', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('douyin_id', '抖音号', 'CORE', 'TEXT', FALSE, TRUE,
     FALSE, NULL, 90, '选填，请输入抖音号', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('douyin_nickname', '抖音昵称', 'CORE', 'TEXT', FALSE, TRUE,
     FALSE, NULL, 100, '选填，请输入抖音昵称', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('douyin_profile_url', '抖音主页链接', 'CORE', 'TEXT', FALSE, TRUE,
     FALSE, NULL, 110, '选填，请输入抖音主页链接', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (field_code) DO UPDATE SET
    label = EXCLUDED.label,
    storage_kind = EXCLUDED.storage_kind,
    data_type = EXCLUDED.data_type,
    required = EXCLUDED.required,
    enabled = EXCLUDED.enabled,
    options_json = EXCLUDED.options_json,
    sort_order = EXCLUDED.sort_order,
    instructions = EXCLUDED.instructions,
    updated_at = CURRENT_TIMESTAMP;
```

- [ ] **Step 4: Run the focused API test and verify GREEN**

Run:

```bash
./mvnw -pl services/platform-api test -Dtest=GuestProfileFieldDefinitionsApiTest
```

Expected: PASS with one test confirming authentication and one test confirming 11 sorted enabled definitions.

- [ ] **Step 5: Commit Task 1**

```bash
git add services/platform-api/src/main/resources/db/migration/V7__social_account_field_definitions.sql services/platform-api/src/test/java/com/love/archive/guest/web/GuestProfileFieldDefinitionsApiTest.java
git commit -m "feat(profile): register social account fields"
```

---

### Task 2: Require WeChat ID only at backend submission readiness

**Files:**
- Create: `services/platform-api/src/test/java/com/love/archive/guest/application/ProfileSubmissionReadinessValidatorTest.java`
- Modify: `services/platform-api/src/main/java/com/love/archive/guest/application/ProfileSubmissionReadinessValidator.java`

**Interfaces:**
- Consumes: `missingRequiredFieldCodes(GuestProfileEntity, List<ProfileFieldDefinitionEntity>, List<ProfileFieldValueEntity>)` and existing encrypted social columns on `GuestProfileEntity`.
- Produces: CORE-field presence checks for `wechat_id`, `douyin_id`, `douyin_nickname`, and `douyin_profile_url` without changing draft-save validation.

- [ ] **Step 1: Write failing readiness-validator tests**

Create a focused unit test with helpers that build enabled CORE definitions. Cover two behaviors:

```java
@Test
void reportsRequiredWechatWhenEncryptedValueIsMissing() {
    GuestProfileEntity profile = new GuestProfileEntity();
    List<String> missing = validator.missingRequiredFieldCodes(
            profile, List.of(core("wechat_id", true)), List.of());
    assertThat(missing).containsExactly("wechat_id");
}

@Test
void acceptsWechatAndIgnoresEmptyOptionalDouyinFields() {
    GuestProfileEntity profile = new GuestProfileEntity();
    profile.setWechatIdCiphertext(new byte[] {1});
    profile.setWechatIdHmac("wechat-hmac");
    List<String> missing = validator.missingRequiredFieldCodes(
            profile,
            List.of(
                    core("wechat_id", true),
                    core("douyin_id", false),
                    core("douyin_nickname", false),
                    core("douyin_profile_url", false)),
            List.of());
    assertThat(missing).isEmpty();
}
```

The `core` helper must set `id`, `fieldCode`, `storageKind=CORE`, `dataType=TEXT`, `required`, `enabled=true`, and `sortOrder`.

- [ ] **Step 2: Run the unit test and verify RED**

Run:

```bash
./mvnw -pl services/platform-api test -Dtest=ProfileSubmissionReadinessValidatorTest
```

Expected: FAIL because `coreValuePresent()` falls through to `false` for `wechat_id`, including when ciphertext and HMAC are populated.

- [ ] **Step 3: Implement encrypted CORE presence checks**

Extend the switch in `coreValuePresent()`:

```java
case "wechat_id" -> profile.getWechatIdCiphertext() != null
        && hasText(profile.getWechatIdHmac());
case "douyin_id" -> profile.getDouyinIdCiphertext() != null
        && hasText(profile.getDouyinIdHmac());
case "douyin_nickname" -> profile.getDouyinNicknameCiphertext() != null;
case "douyin_profile_url" -> profile.getDouyinProfileUrlCiphertext() != null;
```

- [ ] **Step 4: Run focused backend tests and verify GREEN**

Run:

```bash
./mvnw -pl services/platform-api test -Dtest=ProfileSubmissionReadinessValidatorTest,GuestProfileFieldDefinitionsApiTest
```

Expected: PASS; a populated WeChat encrypted value satisfies the required field, while empty optional Douyin fields do not enter the missing list.

- [ ] **Step 5: Commit Task 2**

```bash
git add services/platform-api/src/main/java/com/love/archive/guest/application/ProfileSubmissionReadinessValidator.java services/platform-api/src/test/java/com/love/archive/guest/application/ProfileSubmissionReadinessValidatorTest.java
git commit -m "feat(profile): require wechat on submission"
```

---

### Task 3: Centralize frontend profile-field mapping and localized validation

**Files:**
- Create: `apps/guest-app/src/utils/profileFields.ts`
- Create: `apps/guest-app/src/utils/profileFields.test.ts`
- Modify: `apps/guest-app/src/types/index.ts`
- Modify: `apps/guest-app/src/utils/presentation.ts`
- Modify: `apps/guest-app/src/utils/presentation.test.ts`
- Modify: `apps/guest-app/src/validators/profile.ts`
- Modify: `apps/guest-app/src/validators/profile.test.ts`

**Interfaces:**
- Consumes: `GuestProfileDraft`, `GuestFieldDefinition`, and `ProfileValues`.
- Produces: `draftToProfileValues(draft): ProfileValues`, `profileValuesToDraftPayload(values, expectedVersion): Record<string, unknown>`, snake_case grouping, and localized missing labels.

- [ ] **Step 1: Write failing mapping, grouping, and validation tests**

Add tests proving:

```ts
expect(profileGroup('birth_date')).toBe('basic')
expect(profileGroup('income_range')).toBe('career')
expect(profileGroup('wechat_id')).toBe('social')
expect(profileGroup('douyin_profile_url')).toBe('social')
```

Create `profileFields.test.ts` asserting that a draft containing `wechatId: 'wx_demo'` hydrates `values.wechat_id`, and that values containing `douyin_nickname` produce payload property `douyinNickname`. Add a validator case with required `wechat_id` and optional `douyin_id`, asserting the result is `['微信号']`.

- [ ] **Step 2: Run the frontend tests and verify RED**

Run:

```bash
cd apps/guest-app && npx vitest run --config vitest.config.ts src/utils/profileFields.test.ts src/utils/presentation.test.ts src/validators/profile.test.ts
```

Expected: FAIL because mapping helpers do not exist, snake_case social fields fall into `more`, and validation returns the internal field code.

- [ ] **Step 3: Implement the mapping boundary**

Create a single map from definition code to DTO property:

```ts
export const CORE_FIELD_PROPERTIES = {
  gender: 'gender',
  birth_date: 'birthDate',
  height_cm: 'heightCm',
  education: 'education',
  occupation: 'occupation',
  income_range: 'incomeRange',
  city: 'city',
  wechat_id: 'wechatId',
  douyin_id: 'douyinId',
  douyin_nickname: 'douyinNickname',
  douyin_profile_url: 'douyinProfileUrl',
} as const
```

Use it in `draftToProfileValues()` and `profileValuesToDraftPayload()`. The save payload must include `expectedVersion` and all mapped core properties; empty optional values remain empty strings so the backend normalizer converts them to null.

- [ ] **Step 4: Align grouping, types, and localized validation**

Add `storageKind: 'CORE' | 'DYNAMIC'` to `GuestFieldDefinition` and add `storageKind` to every typed fixture in `profile.test.ts`. Replace camelCase keys in `FIELD_GROUPS` with the API's snake_case codes. Change `validateProfileForm()` to push `definition.label` instead of `definition.fieldCode` so submission feedback says “微信号”.

- [ ] **Step 5: Run focused frontend tests and verify GREEN**

Run:

```bash
cd apps/guest-app && npx vitest run --config vitest.config.ts src/utils/profileFields.test.ts src/utils/presentation.test.ts src/validators/profile.test.ts
```

Expected: PASS for mapping, grouping, completion, and localized required-field validation.

- [ ] **Step 6: Commit Task 3**

```bash
git add apps/guest-app/src/utils/profileFields.ts apps/guest-app/src/utils/profileFields.test.ts apps/guest-app/src/types/index.ts apps/guest-app/src/utils/presentation.ts apps/guest-app/src/utils/presentation.test.ts apps/guest-app/src/validators/profile.ts apps/guest-app/src/validators/profile.test.ts
git commit -m "fix(guest): map profile field definitions consistently"
```

---

### Task 4: Render the prototype social-account card and use mapped save data

**Files:**
- Modify: `apps/guest-app/src/pages/profile/index.vue`

**Interfaces:**
- Consumes: `draftToProfileValues()`, `profileValuesToDraftPayload()`, snake_case field definitions, and existing `SectionCard`/`form-row` styles.
- Produces: a four-row social card between career and image sections, with `wechat_id` marked required and the card meta showing filled count out of four.

- [ ] **Step 1: Wire the profile page to the mapping helpers**

Import both helpers. Replace the manual camelCase `Object.assign` block with:

```ts
if (store.draft) Object.assign(values, draftToProfileValues(store.draft))
```

Change `save()` to call:

```ts
await store.save(profileValuesToDraftPayload(values, store.draft?.version ?? null))
```

- [ ] **Step 2: Preserve the prototype's social-card presentation**

Keep the group order `basic`, `career`, `social`, `more`. For text inputs, use `definition.instructions` so the optional Douyin rows read “选填…”. Keep the red required marker driven by `definition.required`, and add an optional hint after non-required social labels:

```vue
<text v-if="group.key === 'social' && !definition.required" class="optional">选填</text>
```

Add restrained `.optional` typography in auxiliary gray without changing existing card padding or row height.

- [ ] **Step 3: Run guest tests, type-check, and build**

Run:

```bash
cd apps/guest-app
npx vitest run --config vitest.config.ts
npm run type-check
npm run build:h5
```

Expected: all Vitest files pass, TypeScript exits 0, and H5 output is generated under `dist/build/h5`.

- [ ] **Step 4: Commit Task 4**

```bash
git add apps/guest-app/src/pages/profile/index.vue
git commit -m "feat(guest): show social account profile section"
```

---

### Task 5: Full verification and high-fidelity browser QA

**Files:**
- Modify only if verification finds a defect in files already listed above.

**Interfaces:**
- Consumes: completed backend migration/validation and guest UI implementation.
- Produces: evidence that tests, builds, the real API, and responsive UI meet the approved design.

- [ ] **Step 1: Run the complete automated verification suite**

Run:

```bash
./mvnw -pl services/platform-api test
cd apps/guest-app && npx vitest run --config vitest.config.ts && npm run type-check && npm run build:h5
```

Expected: Maven reports zero failures/errors; every guest Vitest test passes; type-check and build exit 0.

- [ ] **Step 2: Rebuild and restart the local API**

Run:

```bash
docker compose up --build -d api
curl -sS http://127.0.0.1:8080/actuator/health
```

Expected: health response contains `"status":"UP"`, and Flyway applies V7 exactly once.

- [ ] **Step 3: Verify the live API contract**

Log in with a local test guest and call `/api/v1/guest/profile/field-definitions`. Confirm four social definitions are present and only `wechat_id` has `required=true`.

- [ ] **Step 4: Inspect responsive UI in the browser**

At `http://localhost:5174/`, inspect 390×844 and 320×720 layouts. Confirm card order, four social rows, `1/4`-style count, WeChat required marker, optional Douyin labels, long URL behavior, no horizontal overflow, and no new browser console errors.

- [ ] **Step 5: Verify repository state**

Run:

```bash
git status --short
git log --oneline -6
```

Expected: only the user's pre-existing `.m2/` and `docs/feature-inventory.md` remain untracked; implementation commits are present and no temporary QA files remain.
