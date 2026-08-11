# Frontend Prototype Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the guest H5 app and admin web UI to match the approved black, white, and warm-gray prototypes without changing their APIs or business workflows.

**Architecture:** Keep the existing Pinia stores, API modules, router, and submission behavior. Add a small presentation layer in each app—shared visual tokens, a brand mark, page headers, and status formatting—then reshape each page around those primitives. Implement pure display logic as tested utility functions and validate the CSS-heavy work through production builds plus browser screenshots at the target viewports.

**Tech Stack:** Vue 3, TypeScript, Vite, Element Plus, uni-app, Pinia, Vitest, Vue Test Utils, CSS/SCSS.

## Global Constraints

- Treat the two supplied prototype images as the high-fidelity visual acceptance standard.
- Preserve every existing API path, request payload, cookie/session behavior, optimistic version, and idempotency-key behavior.
- Use `#0D0D0F`, `#1E1E22`, `#6B6C70`, `#E5E3DF`, `#F7F6F3`, `#FFFFFF`, and `#C9A96A` as the core palette; reserve green/yellow/red for status feedback.
- Optimize the guest app for 390px mobile width and the admin app for 1440px desktop width; retain usability at 320px and 1280px respectively.
- Do not add backend endpoints, fake business data, new UI/chart dependencies, dark mode, or internationalization.
- Use system Chinese fonts and `font-variant-numeric: tabular-nums` for numeric data.
- Preserve unrelated user files and changes, including `.m2/` and `docs/feature-inventory.md`.

---

## Planned File Structure

### Admin web

- Create `apps/admin-web/src/components/BrandMark.vue`: reusable vertical-line brand mark.
- Create `apps/admin-web/src/components/PageHeader.vue`: title, description, and optional action slot.
- Create `apps/admin-web/src/components/StatusTag.vue`: typed review-status localization.
- Create `apps/admin-web/src/utils/presentation.ts`: pure label/date/status helpers.
- Create `apps/admin-web/src/utils/presentation.test.ts`: helper tests.
- Modify `apps/admin-web/src/styles/theme.css`: design tokens, Element Plus overrides, shared layout rules.
- Modify `apps/admin-web/src/layouts/AdminLayout.vue`: prototype sidebar/header shell.
- Modify all six files in `apps/admin-web/src/views/`: prototype page structures while retaining business functions.

### Guest app

- Create `apps/guest-app/src/components/BrandMark.vue`: uni-app-compatible brand mark.
- Create `apps/guest-app/src/components/AppIcon.vue`: dependency-free icon glyph component.
- Create `apps/guest-app/src/components/SectionCard.vue`: mobile section wrapper.
- Create `apps/guest-app/src/components/StatusBadge.vue`: guest status display.
- Create `apps/guest-app/src/styles/theme.scss`: shared mobile tokens and primitives.
- Create `apps/guest-app/src/utils/presentation.ts`: field grouping, completion, and status helpers.
- Create `apps/guest-app/src/utils/presentation.test.ts`: helper tests.
- Modify `apps/guest-app/src/App.vue`, `pages.json`, and all five page components.

---

### Task 1: Admin presentation primitives and shell

**Files:**
- Create: `apps/admin-web/src/utils/presentation.ts`
- Create: `apps/admin-web/src/utils/presentation.test.ts`
- Create: `apps/admin-web/src/components/BrandMark.vue`
- Create: `apps/admin-web/src/components/PageHeader.vue`
- Create: `apps/admin-web/src/components/StatusTag.vue`
- Modify: `apps/admin-web/src/styles/theme.css`
- Modify: `apps/admin-web/src/layouts/AdminLayout.vue`

**Interfaces:**
- Produces: `reviewStatusMeta(status): { label: string; tone: 'warning' | 'success' | 'danger' }`.
- Produces: `fieldTypeLabel(type): string` and `storageKindLabel(kind): string`.
- Produces: `<PageHeader title description>` with a named `actions` slot.
- Produces: `<StatusTag :status>` for `PENDING | APPROVED | REJECTED`.
- Consumes: `useAuthStore()`, current router paths, and existing Element Plus icons.

- [ ] **Step 1: Write failing presentation helper tests**

```ts
import { describe, expect, it } from 'vitest'
import { fieldTypeLabel, reviewStatusMeta, storageKindLabel } from './presentation'

describe('admin presentation helpers', () => {
  it('localizes every review status', () => {
    expect(reviewStatusMeta('PENDING')).toEqual({ label: '待审核', tone: 'warning' })
    expect(reviewStatusMeta('APPROVED')).toEqual({ label: '已通过', tone: 'success' })
    expect(reviewStatusMeta('REJECTED')).toEqual({ label: '已退回', tone: 'danger' })
  })

  it('localizes field metadata', () => {
    expect(fieldTypeLabel('SINGLE_OPTION')).toBe('单选')
    expect(storageKindLabel('CORE')).toBe('核心')
    expect(storageKindLabel('DYNAMIC')).toBe('自定义')
  })
})
```

- [ ] **Step 2: Run the test and verify the missing module failure**

Run: `cd apps/admin-web && npm run test -- src/utils/presentation.test.ts`

Expected: FAIL because `src/utils/presentation.ts` does not exist.

- [ ] **Step 3: Implement typed helpers**

```ts
export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export function reviewStatusMeta(status: ReviewStatus) {
  return {
    PENDING: { label: '待审核', tone: 'warning' as const },
    APPROVED: { label: '已通过', tone: 'success' as const },
    REJECTED: { label: '已退回', tone: 'danger' as const },
  }[status]
}
```

Implement complete `fieldTypeLabel` mappings for `TEXT`, `LONG_TEXT`, `INTEGER`, `DECIMAL`, `DATE`, `BOOLEAN`, and `SINGLE_OPTION`; map `CORE` and `DYNAMIC` in `storageKindLabel`.

- [ ] **Step 4: Build shared components and theme**

Implement the CSS-only vertical gold brand bars in `BrandMark.vue`; implement a semantic title/description/action row in `PageHeader.vue`; render `StatusTag.vue` from `reviewStatusMeta`. Replace the rose theme with the approved CSS variables, 8px panel radius, thin borders, black primary buttons, visible focus rings, reduced-motion rules, and tabular numeric utility.

Reshape `AdminLayout.vue` to a 164px dark sidebar, 52px white header, selected navigation background, administrator avatar/name, notification icon with “通知功能暂未开放” feedback, and responsive 1280px behavior. Keep `confirmLogout()` unchanged.

- [ ] **Step 5: Run admin unit tests and build**

Run: `cd apps/admin-web && npm run test && npm run build`

Expected: all tests pass and Vite writes `dist/` without TypeScript errors.

- [ ] **Step 6: Commit the admin foundation**

```bash
git add apps/admin-web/src/components apps/admin-web/src/utils apps/admin-web/src/styles/theme.css apps/admin-web/src/layouts/AdminLayout.vue
git commit -m "feat(admin): add prototype design foundation"
```

### Task 2: Admin login, dashboard, and guest registration

**Files:**
- Modify: `apps/admin-web/src/views/LoginView.vue`
- Modify: `apps/admin-web/src/views/DashboardView.vue`
- Modify: `apps/admin-web/src/views/GuestRegisterView.vue`

**Interfaces:**
- Consumes: `<BrandMark>`, `<PageHeader>`, existing `useAuthStore()`, `dashboardStats()`, `provisionGuest()`, `reissueCredential()`, and `currentAuthorizationDocumentVersion()`.
- Produces: unchanged route behavior and unchanged API payloads.

- [ ] **Step 1: Add a focused guest-registration behavior test**

Add a test to `apps/admin-web/src/api/admin.test.ts` that continues to assert `provisionGuest()` sends `amountMinor`, `paidAt`, and `authorizationDocumentVersion` unchanged. This protects the business payload while the page markup changes.

- [ ] **Step 2: Run the focused test before UI changes**

Run: `cd apps/admin-web && npm run test -- src/api/admin.test.ts`

Expected: PASS, establishing the protected behavior baseline.

- [ ] **Step 3: Rebuild the three page templates**

Rebuild `LoginView.vue` as a dark left brand rail plus centered white form panel. Keep the account/password validation and `auth.login()` call. Make “忘记密码” show `ElMessage.info('请联系系统管理员重置密码')`; do not add persistence for “记住我”.

Rebuild `DashboardView.vue` with a page description and four left-aligned thin-border metric cards. Render only the four real API values and static explanations; do not add fake deltas or review rows.

Rebuild `GuestRegisterView.vue` as a primary form plus right credential column. Introduce `const reissuePhone = ref('')` so credential reissue no longer shares `form.phone`. Add `copyCredential(value)` using `navigator.clipboard.writeText(value)` with `ElMessage.success('凭证已复制')` and a fallback error message. Preserve yuan-to-minor-unit conversion.

- [ ] **Step 4: Run admin tests and build**

Run: `cd apps/admin-web && npm run test && npm run build`

Expected: all tests pass; login, dashboard, and registration compile.

- [ ] **Step 5: Commit the first admin pages**

```bash
git add apps/admin-web/src/views/LoginView.vue apps/admin-web/src/views/DashboardView.vue apps/admin-web/src/views/GuestRegisterView.vue apps/admin-web/src/api/admin.test.ts
git commit -m "feat(admin): match core pages to prototype"
```

### Task 3: Admin field and review workflows

**Files:**
- Modify: `apps/admin-web/src/views/FieldDefinitionsView.vue`
- Modify: `apps/admin-web/src/views/ReviewsView.vue`
- Modify: `apps/admin-web/src/views/ReviewDetailView.vue`

**Interfaces:**
- Consumes: `<PageHeader>`, `<StatusTag>`, `fieldTypeLabel`, `storageKindLabel`, existing field-definition and review API calls.
- Produces: unchanged create/update/approve/reject payloads and review navigation.

- [ ] **Step 1: Protect review payload behavior**

Keep the existing review API tests and add assertions that `rejectReview(id, version, null, comment)` sends `expectedVersion`, `reasonCode: null`, and the trimmed `comment`, while `approveReview()` sends only `expectedVersion`.

- [ ] **Step 2: Run the focused tests**

Run: `cd apps/admin-web && npm run test -- src/views/reviewApi.test.ts src/api/admin.test.ts`

Expected: PASS before restructuring the page templates.

- [ ] **Step 3: Rebuild field definitions**

Use `PageHeader` with the “新增字段” action, a compact thin-border table, localized type/storage labels, visual switches for required/enabled, and right-aligned pagination. Keep the existing dialog fields and mutation calls. Do not add delete or enabled mutations because the backend does not expose them.

- [ ] **Step 4: Rebuild review list**

Add filter tabs driven by the existing `filters.status`; show localized status tags; add a reset action that restores `{ status: 'PENDING', deadline: undefined, profileNo: undefined }`, resets `page` to 1, and calls `load()`. Add an explicit “查看” link while preserving row click navigation.

- [ ] **Step 5: Rebuild review detail**

Create a two-column content shell: main snapshot/difference/photo panels and a sticky action rail. Render every existing core field including Douyin nickname/profile URL, preserve image preview, and render differences as old value → new value. Replace the prompt-based reject flow with an inline `rejectComment` textarea; keep non-empty trim validation and call `rejectReview(detail.revisionId, detail.version, null, rejectComment.trim())`.

- [ ] **Step 6: Run admin regression**

Run: `cd apps/admin-web && npm run test && npm run build`

Expected: all tests pass and all six routes compile.

- [ ] **Step 7: Commit the admin workflow pages**

```bash
git add apps/admin-web/src/views/FieldDefinitionsView.vue apps/admin-web/src/views/ReviewsView.vue apps/admin-web/src/views/ReviewDetailView.vue apps/admin-web/src/api/admin.test.ts apps/admin-web/src/views/reviewApi.test.ts
git commit -m "feat(admin): restyle field and review workflows"
```

### Task 4: Guest presentation utilities and shared shell

**Files:**
- Create: `apps/guest-app/src/utils/presentation.ts`
- Create: `apps/guest-app/src/utils/presentation.test.ts`
- Create: `apps/guest-app/src/components/BrandMark.vue`
- Create: `apps/guest-app/src/components/AppIcon.vue`
- Create: `apps/guest-app/src/components/SectionCard.vue`
- Create: `apps/guest-app/src/components/StatusBadge.vue`
- Create: `apps/guest-app/src/styles/theme.scss`
- Modify: `apps/guest-app/src/App.vue`
- Modify: `apps/guest-app/src/pages.json`
- Modify: `apps/guest-app/src/uni.scss`

**Interfaces:**
- Produces: `profileGroup(fieldCode): 'basic' | 'career' | 'social' | 'more'`.
- Produces: `profileCompletion(definitions, values, hasAvatar): number` returning 0–100.
- Produces: `guestStatusMeta(status)` with Chinese label/tone/description.
- Produces: shared `<BrandMark>`, `<AppIcon name>`, `<SectionCard>`, and `<StatusBadge :status>`.

- [ ] **Step 1: Write failing utility tests**

```ts
import { describe, expect, it } from 'vitest'
import { guestStatusMeta, profileCompletion, profileGroup } from './presentation'

describe('guest presentation helpers', () => {
  it('groups known fields and falls back to more', () => {
    expect(profileGroup('gender')).toBe('basic')
    expect(profileGroup('occupation')).toBe('career')
    expect(profileGroup('wechatId')).toBe('social')
    expect(profileGroup('favoriteBook')).toBe('more')
  })

  it('includes the required avatar in completion', () => {
    const definitions = [{ fieldCode: 'gender' }, { fieldCode: 'city' }]
    expect(profileCompletion(definitions, { gender: '女', city: '' }, false)).toBe(33)
    expect(profileCompletion(definitions, { gender: '女', city: '上海' }, true)).toBe(100)
  })

  it('localizes review states', () => {
    expect(guestStatusMeta('PENDING_REVIEW').label).toBe('审核中')
    expect(guestStatusMeta('CHANGES_REQUESTED').tone).toBe('danger')
  })
})
```

- [ ] **Step 2: Run the test and verify the missing module failure**

Run: `cd apps/guest-app && npx vitest run --config vitest.config.ts src/utils/presentation.test.ts`

Expected: FAIL because `src/utils/presentation.ts` does not exist.

- [ ] **Step 3: Implement the pure helpers**

Treat whitespace-only strings, `null`, and `undefined` as empty; treat `false` and numeric zero as filled. Calculate completion from all enabled definitions plus one avatar slot, round to the nearest integer, and return 0 when no slots exist. Map `DRAFT`, `PENDING_REVIEW`, `APPROVED`, and `CHANGES_REQUESTED` to stable Chinese metadata.

- [ ] **Step 4: Build guest primitives and global theme**

Implement components with uni-app-supported `view`, `text`, and CSS shapes so H5 and future mini-program builds remain compatible. Import `styles/theme.scss` from `App.vue`. Update `pages.json` to warm-white navigation, black selected Tab color, and icon paths only if real static icon assets are added. Keep all five existing page paths.

Define the 430px desktop-H5 container, safe-area padding, section cards, buttons, input borders, status colors, visible focus states, and reduced-motion behavior. Replace old rose uni variables with the approved palette.

- [ ] **Step 5: Run guest unit tests, type check, and build**

Run: `cd apps/guest-app && npx vitest run --config vitest.config.ts && npm run type-check && npm run build:h5`

Expected: all tests pass and H5 output is generated in `dist/build/h5`.

- [ ] **Step 6: Commit the guest foundation**

```bash
git add apps/guest-app/src/components apps/guest-app/src/utils apps/guest-app/src/styles apps/guest-app/src/App.vue apps/guest-app/src/pages.json apps/guest-app/src/uni.scss
git commit -m "feat(guest): add prototype design foundation"
```

### Task 5: Guest authentication and authorization pages

**Files:**
- Modify: `apps/guest-app/src/pages/auth/index.vue`
- Modify: `apps/guest-app/src/pages/consent/index.vue`

**Interfaces:**
- Consumes: guest visual primitives, existing `useAuthStore()`, `useConsentStore()`, and authorization API.
- Produces: unchanged activation/login/consent requests and navigation.

- [ ] **Step 1: Run existing auth and consent behavior tests**

Run: `cd apps/guest-app && npx vitest run --config vitest.config.ts src/stores/auth.test.ts src/stores/consent.test.ts`

Expected: PASS, establishing the workflow baseline.

- [ ] **Step 2: Rebuild login/activation**

Create the dark brand hero with low-contrast contour lines, gold `BrandMark`, overlapping white form surface, two mode tabs, bordered fields, black submit button, privacy reassurance, and static service contact. Preserve validation and `auth.activate()`/`auth.login()` calls. Hide native navigation chrome for this page through its `pages.json` style if supported by H5.

- [ ] **Step 3: Rebuild authorization**

Create a paper-like scroll area with title, real version/effective date, and unchanged legal content. Replace the switch with an explicit checkbox row. Keep acceptance disabled until checked and place the action bar above the safe area.

- [ ] **Step 4: Run guest verification**

Run: `cd apps/guest-app && npx vitest run --config vitest.config.ts && npm run type-check && npm run build:h5`

Expected: all tests pass and both pages compile.

- [ ] **Step 5: Commit auth and consent pages**

```bash
git add apps/guest-app/src/pages/auth/index.vue apps/guest-app/src/pages/consent/index.vue apps/guest-app/src/pages.json
git commit -m "feat(guest): match auth flow to prototype"
```

### Task 6: Guest profile page

**Files:**
- Modify: `apps/guest-app/src/pages/profile/index.vue`

**Interfaces:**
- Consumes: `profileGroup`, `profileCompletion`, `<SectionCard>`, field definitions, `useProfileStore()`, validators, and media adapters.
- Produces: the same dynamic value record accepted by `store.save()` and the same photo/submission behavior.

- [ ] **Step 1: Run existing profile tests**

Run: `cd apps/guest-app && npx vitest run --config vitest.config.ts src/stores/profile.test.ts src/validators/profile.test.ts src/adapters/media.test.ts`

Expected: PASS before modifying the template.

- [ ] **Step 2: Add grouped computed state**

```ts
const groupedDefinitions = computed(() => ({
  basic: definitions.value.filter((item) => profileGroup(item.fieldCode) === 'basic'),
  career: definitions.value.filter((item) => profileGroup(item.fieldCode) === 'career'),
  social: definitions.value.filter((item) => profileGroup(item.fieldCode) === 'social'),
  more: definitions.value.filter((item) => profileGroup(item.fieldCode) === 'more'),
}))

const completion = computed(() =>
  profileCompletion(
    definitions.value,
    values,
    store.photos.some((photo) => photo.category === 'AVATAR'),
  ),
)
```

- [ ] **Step 3: Rebuild the form presentation**

Render the completion header/progress line, four semantic field groups, compact label/value rows, photo grids, and fixed dual action bar. Keep the existing type-specific inputs/pickers/switches, missing-field validation, avatar validation, choose/remove behavior, save call, and submission call. Do not rename field codes or reshape `values`.

- [ ] **Step 4: Run profile and full guest verification**

Run: `cd apps/guest-app && npx vitest run --config vitest.config.ts && npm run type-check && npm run build:h5`

Expected: all tests pass and the profile page compiles without type widening errors.

- [ ] **Step 5: Commit the profile page**

```bash
git add apps/guest-app/src/pages/profile/index.vue
git commit -m "feat(guest): rebuild profile form from prototype"
```

### Task 7: Guest status and account pages

**Files:**
- Modify: `apps/guest-app/src/pages/status/index.vue`
- Modify: `apps/guest-app/src/pages/mine/index.vue`

**Interfaces:**
- Consumes: `guestStatusMeta`, `<StatusBadge>`, real profile status fields, auth store, and consent store.
- Produces: existing edit/renew/logout navigation plus an approved-revision link only when an ID exists.

- [ ] **Step 1: Run existing session and consent tests**

Run: `cd apps/guest-app && npx vitest run --config vitest.config.ts src/stores/auth.test.ts src/stores/consent.test.ts`

Expected: PASS.

- [ ] **Step 2: Rebuild status page**

Create the dark summary card and three-stage progress rail from real status only. Display one state-specific action card: “去完善档案” for draft, “去修改” for changes requested, no mutation for pending, and a version link only when `currentApprovedRevisionId` exists. Do not invent submission timestamps or rejection comments absent from the response.

- [ ] **Step 3: Rebuild account page**

Create a dark anonymous identity card using `accountId`, a real consent status card, grouped setting rows, and a red text logout action. Wire the authorization row to `renewConsent()`. Make unsupported rows call `uni.showToast({ title: '功能暂未开放', icon: 'none' })` instead of navigating to missing pages.

- [ ] **Step 4: Run guest regression**

Run: `cd apps/guest-app && npx vitest run --config vitest.config.ts && npm run type-check && npm run build:h5`

Expected: all tests pass and all five guest pages compile.

- [ ] **Step 5: Commit status and account pages**

```bash
git add apps/guest-app/src/pages/status/index.vue apps/guest-app/src/pages/mine/index.vue
git commit -m "feat(guest): match status and account pages to prototype"
```

### Task 8: Browser-based visual QA and final regression

**Files:**
- Modify only files with verified visual defects found during this task.

**Interfaces:**
- Consumes: built admin and guest applications plus the two reference prototype images.
- Produces: verified layouts at 390×844, 320×720, 1440×900, and 1280×800.

- [ ] **Step 1: Start both development servers**

Run admin: `cd apps/admin-web && npm run dev -- --host 127.0.0.1`

Run guest: `cd apps/guest-app && npm run dev:h5 -- --host 127.0.0.1`

Expected: admin is served on port 5173 and guest uses its configured free port. If authentication blocks protected pages, use the existing local backend; do not bypass guards in committed source.

- [ ] **Step 2: Capture target viewport screenshots**

Use browser tooling to inspect the guest pages at 390×844 and admin pages at 1440×900. Compare page shell proportions, hierarchy, spacing, colors, borders, button sizes, field alignment, table density, photo layout, and status communication with the supplied prototypes.

- [ ] **Step 3: Check responsive edge viewports**

Inspect guest at 320×720 and admin at 1280×800. Verify no page-level horizontal overflow, hidden primary actions, clipped dialog content, unreadable table columns, or bottom navigation overlap.

- [ ] **Step 4: Fix only observed defects and recapture**

For each defect, record the page and symptom, apply the smallest CSS/template correction, and recapture the affected viewport. Stop when all primary page structures match the prototype and no interaction is obstructed.

- [ ] **Step 5: Run final automated verification**

Run: `cd apps/admin-web && npm run test && npm run build`

Run: `cd apps/guest-app && npx vitest run --config vitest.config.ts && npm run type-check && npm run build:h5`

Expected: every command exits 0.

- [ ] **Step 6: Inspect the final diff**

Run: `git status --short && git diff --check && git diff --stat HEAD~6..HEAD`

Expected: no whitespace errors; only planned frontend files and plan/spec documentation are changed; `.m2/` and `docs/feature-inventory.md` remain untouched.

- [ ] **Step 7: Commit final visual corrections if any**

```bash
git add apps/admin-web apps/guest-app
git commit -m "fix(ui): polish responsive prototype fidelity"
```

Skip this commit when Step 4 produced no file changes.
