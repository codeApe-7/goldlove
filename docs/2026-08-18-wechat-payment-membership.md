# 微信支付、线上注册与会员体系 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留现有「手动路径（管理员登记 → 邀请码 → 激活）」不变的前提下，新增「微信支付 → 线上注册建档」路径，并引入 VIP/SVIP 会员等级体系：建档注册即 VIP，付费累计达到额度即 SVIP。

**Architecture:** 后端新增一个封闭的微信支付渠道模块（照抄 `storage` 对腾讯云 COS 的隔离范式），业务侧通过 `PaymentChannel` 接口下单/查单/验签回调。`payment_record` 扩展线上支付字段；`user_account` 增加会员等级与累计付费额度；新增支付订单、注册令牌两张表。openid 经公众号网页授权获取并落 `external_identity`。线上注册复用现有 `identity`（账号 + 手机号加密）、`consent`（授权书）、`guest`（档案）能力，只是把「激活 + 建档」合并进注册一步。

**Tech Stack:** Java 25、Spring Boot 4.1、Spring Modulith 2.1、Spring MVC、MyBatis-Plus 3.5.17、PostgreSQL 18、Flyway、Sa-Token、Redis、Lombok、JUnit 5、Testcontainers、微信支付 API v3（JSAPI）、Vue 3 + uni-app。

---

## 需求决策记录（已对齐）

| 决策点 | 结论 |
|---|---|
| 双路径并存 | 手动路径**原样保留**；线上路径**新增**，不改老流程 |
| 线上路径流程 | 微信内打开链接 → 微信支付 → 支付成功跳注册页 → 填手机号 + 个人基本信息 → 提交，账号建立完成 |
| 登录方式 | 沿用现有「手机号 + 密码」 |
| 会员等级 | 建档注册即 **VIP**（自动）；付费累计达到额度即 **SVIP** |
| SVIP 权益 | 后续能看到「感兴趣用户」的基本情况（**本期仅建等级，不实现查看功能**） |
| 手动路径与 SVIP | 手动登记的付款金额**计入** SVIP 累计额度，手动路径用户也能升 SVIP |
| 授权书/信息分层 | **本期不碰**，后续再改 |

## 待确认决策（Open Questions，实现前需拍板）

1. **支付产品**：JSAPI（公众号服务号 H5）还是小程序支付？——默认按 **JSAPI** 设计，若走小程序，仅「openid 获取」与「前端调起」两处变化。
2. **SVIP 门槛**：单笔达标 还是 累计额度达标？——默认按 **累计额度** 设计。
3. **额度阈值**：升 SVIP 的具体金额阈值（如累计满 ¥199 或 ¥299）——待定。

---

## Global Constraints

- 生产代码不引入 Spring Security；Sa-Token 仍是唯一认证/会话框架。
- 沿用 Lombok `@Getter/@Setter/@RequiredArgsConstructor`；实体禁用 `@Data`/类级 `@ToString`，密文字节数组防御性复制。
- 沿用 MyBatis-Plus 单表 CRUD/Lambda Wrapper；复杂查询用 `@Select`/`@SelectProvider`，**不用 Mapper XML**。
- 微信支付相关密钥（商户私钥、API v3 密钥、AppSecret）只存服务端环境变量，绝不下发前端。
- 回调必须验签（平台公钥）；金额以**后端订单**为准，绝不信任前端；`out_trade_no` 全局唯一并作为幂等锚点。
- 一个支付订单只能完成一次注册（注册后订单置「已使用」），注册令牌一次性。
- 手机号、openid、支付交易号等敏感字段不落明文、不打日志。
- 沿用「最小权限」：Flyway 用 `archive_owner`，运行时用 `archive_app`；新增表按需授权。
- 跨表写操作置于单个 `@Transactional` 应用服务内。
- 每个生产改动遵循「先写失败测试 → 通过 → 窄测 + 模块测 + commit」。

---

## 数据模型改动（新增迁移 V9）

### 扩展 `payment_record`

| 字段 | 说明 |
|---|---|
| `payment_channel` | 渠道：`MANUAL` / `WECHAT_JSAPI` |
| `out_trade_no` | 商户订单号，全局唯一（线上） |
| `transaction_id` | 微信交易号（回调带回来，线上） |
| `paid_amount_minor` | 线上实际支付金额（分） |
| `paid_at` | 线上支付成功时间（复用/扩展） |
| `membership_credit_minor` | 该笔计入 SVIP 累计额度的金额（分） |
| `registered` | 该订单是否已用于完成注册（防重复） |

### 新增 `wechat_payment_order`

| 字段 | 说明 |
|---|---|
| `id` / `out_trade_no` | 主键 / 商户订单号（唯一） |
| `openid` | 下单时的微信 openid |
| `amount_minor` | 下单金额（分） |
| `status` | `CREATED` / `PAID` / `CLOSED` / `REFUNDED` |
| `prepay_id` | 微信预支付 ID |
| `transaction_id` | 微信交易号 |
| `paid_at` / `created_at` | 时间戳 |

### 新增 `registration_token`

| 字段 | 说明 |
|---|---|
| `id` / `token_hmac` | 主键 / 令牌 HMAC（不落明文 token） |
| `out_trade_no` | 关联支付订单 |
| `openid` | 支付者 openid |
| `status` | `UNUSED` / `USED` / `EXPIRED` |
| `expires_at` | 过期时间 |
| `created_at` | 时间戳 |

### 扩展 `user_account`

| 字段 | 说明 |
|---|---|
| `membership_tier` | `VIP` / `SVIP`（默认 `VIP`） |
| `membership_credit_minor` | 累计付费额度（分），达标自动升 SVIP |

---

## 模块与接口设计

### 新增 `wechatpay` 封闭模块（参照 `storage`）

- `package-info.java`：`@ApplicationModule(type = CLOSED, allowedDependencies = {"common::web"})`，仅暴露 `application` 接口。
- `application/PaymentChannel.java`（命名接口）：
  - `CreateOrderResult createOrder(CreateOrderCommand command)`
  - `Optional<PaymentResult> queryByOutTradeNo(String outTradeNo)`
  - `PaymentResult verifyAndDecodeNotify(NotifyPayload payload)`（验签 + 解密 + 金额校验）
- `application/WechatPaymentChannel.java`：微信 API v3 实现。
- `config/WechatPayProperties.java` + `WechatPayConfiguration.java`：绑定 `WECHAT_*` 配置，构造客户端（无凭据时不装配，返回 `PAYMENT_CHANNEL_NOT_CONFIGURED`，不影响启动）。

### 扩展 `payment` 模块

- `application/PaymentChannelPort.java`：下单/查单出站端口，由 `wechatpay` 实现注入。
- `application/OnlinePaymentService.java`：生成 out_trade_no → 调渠道下单 → 落 `wechat_payment_order`；回调验签后幂等更新订单 + `payment_record`。
- `application/RegistrationTokenService.java`：支付成功后签发一次性注册令牌；注册时校验令牌有效 + 订单已支付未使用。

### 扩展 `identity` 模块

- `application/OnlineRegistrationService.java`：校验注册令牌 → 创建 `user_account`（`ACTIVE` + `VIP`）→ 绑定 openid 到 `external_identity` → 写 `payment_record.registered=true` → 标记令牌 `USED`。全程一个事务。
- `application/MembershipService.java`：累加 `membership_credit_minor`，达阈值自动升 `SVIP`（手动路径与线上路径共用）。

---

## 关键状态流转

```
线上：openid 下单(CREATED) → 微信回调 → 订单 PAID → 签发注册令牌
      → 注册页提交 → 校验令牌 → 建账号(ACTIVE+VIP) + 绑 openid + 订单置 registered
      → 累加额度 → 达标升 SVIP

手动：管理员登记 → 生成邀请码（现有，不动）→ 用户激活 → 建档即 VIP
      → 登记金额计入额度 → 达标升 SVIP
```

---

## File Map

### 后端 `services/platform-api`

- 新增 `db/migration/V9__wechat_payment_membership.sql`
- 新增 `com/love/archive/wechatpay/**`（package-info、application、config）
- 修改 `com/love/archive/payment/**`（渠道端口、线上支付服务、注册令牌服务）
- 修改 `com/love/archive/identity/**`（在线注册服务、会员服务、账号实体加字段）
- 修改 `com/love/archive/payment/persistence/PaymentRecordEntity.java`
- 修改 `com/love/archive/identity/persistence/UserAccountEntity.java`
- 修改 `application.yml`、`application-test.yml`
- 修改 `.env.example`（新增 `WECHAT_*` 变量）
- 新增对应测试（渠道、订单、注册令牌、会员、在线注册）

### 前端

- 修改 `apps/guest-app`：新增「支付下单 → 微信调起 → 支付成功跳注册页 → 手机号+信息提交」页面与 store
- 修改 `apps/admin-web`：会员等级展示（可选，本期可仅展示）

---

## Stable Error Contract（新增错误码）

```text
PAYMENT_CHANNEL_NOT_CONFIGURED            503
PAYMENT_ORDER_NOT_FOUND                   404
PAYMENT_ORDER_ALREADY_PAID                409
PAYMENT_AMOUNT_MISMATCH                   409
PAYMENT_NOTIFY_SIGNATURE_INVALID          400
REGISTRATION_TOKEN_INVALID                400
REGISTRATION_TOKEN_EXPIRED                410
REGISTRATION_TOKEN_USED                   409
REGISTRATION_ORDER_NOT_PAID               409
MEMBERSHIP_TIER_UPGRADED                  200 (幂等，返回当前等级)
```

所有错误沿用 `ApiResponse<Void>` 包裹与 requestId；不向 API 边界泄露 SQL 细节、密文、密钥或内部堆栈。

---

## Tasks

### Task 1：数据库与会员基础

**Files:** `V9__wechat_payment_membership.sql`、`UserAccountEntity.java`、`PaymentRecordEntity.java`、`MembershipService.java`、测试。

**要点：** 新增 `wechat_payment_order`、`registration_token` 表；扩展 `payment_record`、`user_account`；`membership_tier` 默认 `VIP`；运行时角色授权（`SELECT/INSERT/UPDATE`，无 DDL）。

- [ ] 写失败测试：迁移建表 + 角色授权 + 会员字段默认值
- [ ] 实现迁移与实体字段
- [ ] 窄测 + 架构守卫测试通过后 commit

### Task 2：微信支付渠道模块

**Files:** `wechatpay/**`、`application.yml`、`.env.example`、测试。

**要点：** `PaymentChannel` 接口 + 微信 API v3 实现（下单/查单/验签）；无凭据不装配；`WECHAT_*` 环境变量。

- [ ] 写失败测试：无凭据返回 `PAYMENT_CHANNEL_NOT_CONFIGURED`；下单/查单/验签 mock
- [ ] 实现渠道 + 配置
- [ ] commit

### Task 3：线上支付下单与回调

**Files:** `payment/**`、`wechatpay` 接口接入、测试。

**要点：** `OnlinePaymentService` 生成 out_trade_no、下单、落订单；回调验签后幂等更新订单 + `payment_record`，并签发注册令牌。

- [ ] 写失败测试：下单成功 / 金额不一致 / 回调重复幂等 / 回调后签发令牌
- [ ] 实现下单 + 回调幂等
- [ ] commit

### Task 4：在线注册与 openid 绑定

**Files:** `identity/OnlineRegistrationService.java`、`RegistrationTokenService.java`、测试。

**要点：** 校验令牌 + 订单已支付未使用 → 建账号（`ACTIVE`+`VIP`）+ 手机号加密 + 绑 openid 到 `external_identity` + 标记订单已注册 + 令牌置 `USED`，单事务。

- [ ] 写失败测试：令牌无效/过期/已用/订单未支付/重复注册
- [ ] 实现在线注册
- [ ] commit

### Task 5：会员额度累计与 SVIP 升级

**Files:** `MembershipService.java`、`payment/**` 接入、测试。

**要点：** 手动登记金额与线上支付金额均计入 `membership_credit_minor`，达阈值自动升 `SVIP`；升级幂等。

- [ ] 写失败测试：累计达标升 SVIP / 未达标不升 / 重复累计幂等
- [ ] 实现额度累计与升级
- [ ] commit

### Task 6：前端 guest-app 支付与注册流程

**Files:** `apps/guest-app/src/**`（新增支付/注册页、store、api）。

**要点：** 支付下单 → `wx.chooseWXPay` 调起 → 成功跳注册页 → 手机号 + 信息提交 → 复用现有档案表单能力；微信外打开给出兜底提示。

- [ ] 写失败测试：注册页提交 payload、支付状态查询
- [ ] 实现页面与 store
- [ ] commit

### Task 7：文档与收尾

**Files:** `README.md`、`.env.example`。

**要点：** 记录新端点、环境变量、双路径说明、会员规则；`git diff --check`；完整测试 + 打包验证。

- [ ] 更新文档与配置示例
- [ ] 全量验证（`./mvnw -pl services/platform-api test`、前端 build/test）
- [ ] commit

---

## Verification

```bash
# 后端
./mvnw -pl services/platform-api test
./mvnw -pl services/platform-api package -DskipTests

# 前端
cd apps/admin-web && npm run test && npm run build
cd apps/guest-app && npx vitest run --config vitest.config.ts && npm run type-check && npm run build:h5

# 架构守卫
./mvnw -pl services/platform-api test -Dtest=ModularityTest,SensitiveDataGuardTest,LombokEntitySafetyTest
```

## 待确认事项（实现前必须拍板）

1. 支付产品：**JSAPI（公众号）** 还是 **小程序**？（默认 JSAPI）
2. SVIP 门槛：**单笔达标** 还是 **累计额度**？（默认累计）
3. SVIP 额度阈值具体金额？（待定）
