# 易支付（XPay V2）线上支付渠道接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不动手动路径、保留微信渠道代码的前提下，新增易支付（XPay V2）聚合支付渠道（下单指定支付宝），并把支付渠道抽象成真正渠道无关的多实现接口，业务侧按配置启用的单一渠道完成下单 / 查单 / 验签回调。线上「支付 → 注册建档」与 VIP/SVIP 会员体系保持不变。

**Tech Stack:** Java 25、Spring Boot 4.1、Spring Modulith 2.1、Spring MVC、MyBatis-Plus 3.5.17、PostgreSQL 18、Flyway、Sa-Token、Redis、Lombok、JUnit 5、Testcontainers、易支付 XPay V2（RSA2 双向签名）、Vue 3 + uni-app。

---

## 现状与耦合点（改造对象）

线上支付已具备一层渠道抽象，但抽象类型本身放在 `wechatpay` 模块内，且微信特有概念渗进了业务层：

1. `payment` 模块 `package-info` 直接 `allowedDependencies` 依赖 `wechatpay::application`；`OnlinePaymentService` 直接 import `wechatpay.application.*`。
2. `OnlinePaymentService.createOrder` 强依赖 `WechatOAuthGateway.resolveOpenId(code)` —— 微信特有的「网页授权换 openid」前置步骤。
3. 表 / 实体 / 枚举 / DTO 全是微信命名：`wechat_payment_order`、`WechatPaymentOrderStore`、`WechatOrderStatus`、`PaymentChannelType.WECHAT_JSAPI`、`JsapiPayParameters`。
4. 回调端点 `PublicOnlinePaymentController.notifications` 硬编码解析 `Wechatpay-Serial/Timestamp/Nonce/Signature` 头。
5. `openid` 作为「支付者标识」贯穿下单、回调验签、注册绑 `external_identity(provider=WECHAT)`。
6. 前端 `adapters/wechat.ts` + `pages/payment/index.vue` 依赖「微信内打开 + 网页授权 + `wx.chooseWXPay`」。

---

## 需求决策记录（已对齐）

| 决策点 | 结论 |
|---|---|
| 渠道 | 易支付 XPay V2（`xpay.unbb.cn`，PID `10192`，RSA2 双向签名） |
| 下单方式 | **API 下单** `POST /api/pay/create`，`type=alipay`，`method=jump`，返回跳转链接由前端跳转 |
| 多渠道抽象 | **保留微信渠道代码**，新增易支付渠道，业务侧通过渠道无关接口路由 |
| 手动路径 | 原样保留，不受影响 |
| 会员 / 注册 | 线上「支付 → 注册建档」流程与 VIP/SVIP 体系不变，仅换渠道 |

## 待确认决策点（评审时拍板）

| 决策点 | 推荐 | 备选 | 说明 |
|---|---|---|---|
| 注册时是否绑外部身份 | **不绑定** | 绑 `buyer` | 易支付无稳定 openid，`buyer` 大概率空；防重复由「手机号唯一 + 订单一次性」保证（详见下文） |
| 订单表命名 | **泛化改造**（见下「方案 A」） | 保留 `wechat_payment_order` 名仅放宽约束 | 泛化更干净但牵动面大；保留名改动最小但命名误导 |
| 回调通知验签规则 | 需**实测 / 平台确认** | — | 协议文档未明确通知字段与验签串拼法，见「待确认事项」 |

---

## 易支付协议要点

平台「优云付」（`xpay.unbb.cn`）提供**两套接口**，本项目用**新版 V2**：

| | 老版（易支付） | 新版（XPay V2，本项目采用） |
|---|---|---|
| 签名 | MD5（用 `SECRET`） | RSA2（用商户私钥） |
| 下单 | `/xpay/epay/submit.php`（跳收银台自选） | `/xpay/epayn/api/pay/create`（可指定支付宝） |
| 状态 | 不采用 | 采用 |

- **签名**：RSA2（SHA256withRSA）。请求按字段名字典序升序拼接（排除 `sign`、`sign_type` 及空值）为 `k1=v1&k2=v2`，用**商户私钥**签名并 Base64 填入 `sign`；响应与通知用平台私钥签名，商户用**平台公钥**验签。
- **金额单位**：**元**（`money`，字符串），非分。与现有 `*_minor`（分）存储需双向转换。
- **凭据**（均服务端环境变量，不下发前端、不写明文进仓库）：
  - `pid` = `10192`
  - 商户私钥（PKCS#8 PEM）、平台公钥（X.509 PEM）—— V2 签名用
  - `SECRET` —— 老版 MD5 那套的密钥，本项目 V2 不用，仅备查（已暴露，建议平台重置）
- **接口**：
  1. `POST /api/pay/create` —— 统一下单，`type=alipay` + `method=jump`，返回 `pay_type=jump` + `pay_info`（跳转链接）。
  2. `POST /api/pay/query` —— 查单，`out_trade_no` 或 `trade_no`，返回 `status: 0 未支付 / 1 已支付`、`trade_no`、`api_trade_no`、`money`、`buyer` 等。
  3. 异步通知 —— 见下「通知字段」。

### 通知字段（异步 notify / 跳转 return）

平台已确认的标准通知字段（老版与 V2 大体一致，V2 以联调为准）：

| 字段 | 说明 |
|---|---|
| `pid` | 商户 ID |
| `trade_no` | 平台订单号 |
| `out_trade_no` | 商户订单号 |
| `type` | 支付方式（alipay 等） |
| `name` | 商品名称 |
| `money` | 金额（元） |
| `trade_status` | 只有 `TRADE_SUCCESS` 视为支付成功（V2 可能为 `status` 0/1） |
| `sign` / `sign_type` | 签名与签名类型 |
| `param` | 业务扩展参数，原样回传 |

收到异步通知后需返回 `success` 表示接收成功；验签失败返回失败体让平台重试。

---

## 架构改造设计

### 1. 渠道无关的出站端口下沉到 `payment` 模块

把渠道契约从 `wechatpay` 移到 `payment`，反转依赖方向（`wechatpay` / `xpay` 依赖 `payment`，而非 `payment` 依赖 `wechatpay`）：

```
payment/application/
  PaymentChannel.java            # 渠道无关出站端口（下单/查单/验签 + 可选 payer 授权能力）
  CreateOrderCommand.java        # payer 改为可空
  CreateOrderResult.java         # prepayId 字段泛化为 channelReference；payParameters 泛化
  PayParameters.java             # 由 JsapiPayParameters 泛化，含 channelType + 各渠道所需字段
  PaymentResult.java             # openid 泛化为 payer（可空）
  NotifyPayload.java             # 泛化为「原始 headers + 原始 params/body」，渠道各自解析
  TradeState.java                # 保留（或泛化为 PaidState）
```

`PaymentChannel` 接口形态（示意）：

```java
public interface PaymentChannel {
    ChannelKind kind();                       // WECHAT_JSAPI / XPAY_ALIPAY，用于落库与路由
    boolean configured();
    boolean requiresPayerAuthorization();      // 微信 true；易支付 false
    String payerAuthorizationUrl(String state); // 微信返回网页授权链接；易支付抛「不支持」
    String resolvePayer(String authorizationCode); // 微信换 openid；易支付抛「不支持」
    CreateOrderResult createOrder(CreateOrderCommand command);
    Optional<PaymentResult> queryByOutTradeNo(String outTradeNo);
    PaymentResult verifyAndDecodeNotify(NotifyPayload payload);
}
```

`payment` 模块 `package-info` 移除对 `wechatpay::application` 的依赖；`wechatpay` 与新增 `xpay` 模块分别 `allowedDependencies = {"common::web", "payment::application"}`。

### 2. 渠道路由

- 凭据齐全才装配渠道实现：微信沿用 `@Conditional(WechatPayCredentialsConfigured)`，易支付新增 `@Conditional(XpayCredentialsConfigured)`。
- `OnlinePaymentService` 注入 `List<PaymentChannel>`，按 `app.payment.online.provider`（`XPAY_ALIPAY` / `WECHAT_JSAPI`）选择；未配置 provider 时取「唯一已配置渠道」，多个则报 `PAYMENT_CHANNEL_AMBIGUOUS`。
- `settings()` 返回内容渠道化：微信返回 `appId + authorizeUrl`；易支付无授权前置，仅返回渠道类型 + 金额 + 描述。

### 3. 新增 `xpay` 封闭模块（参照 `storage` / `wechatpay`）

```
xpay/
  application/XpayPaymentChannel.java   # 实现 PaymentChannel（kind=XPAY_ALIPAY, requiresPayerAuthorization=false）
  config/XpayProperties.java            # app.xpay.*
  config/XpayConfiguration.java         # @Conditional 凭据齐全才装配 + XpayHttpClient/XpayCryptography
  support/XpayHttpClient.java           # JDK HttpClient（可复用 JdkWechatHttpClient 范式）
  support/XpayCryptography.java         # RSA2 签名/验签；复用现有 PEM 解析逻辑（抽公共工具或复制精简版）
  package-info.java
```

- `createOrder`：`POST /api/pay/create`（`type=alipay`、`method=jump`、`pid`、`out_trade_no`、`notify_url`、`return_url`、`name`、`money` 元、`timestamp`、`sign`），验签响应后取 `pay_info`（jump 链接）封装为 `CreateOrderResult`。
- `queryByOutTradeNo`：`POST /api/pay/query`，验签响应，`status == 1` 视为已支付，`money` 元转分。
- `verifyAndDecodeNotify`：按易支付通知验签（待确认）解析为 `PaymentResult`。

### 4. 数据模型改动（新增迁移 V10）

#### 方案 A（推荐）：`wechat_payment_order` 泛化为线上支付订单

- 表 `wechat_payment_order` 重命名为 `online_payment_order`（或 `payment_order`），新增 `channel VARCHAR`。
- `openid_ciphertext` / `openid_hmac` 改为可空并泛化命名为 `payer_ciphertext` / `payer_hmac`（易支付不填）。
- `registration_token.wechat_payment_order_id` 重命名为 `payment_order_id`。
- `payment_record.payment_channel` 的 CHECK 增加 `XPAY_ALIPAY`；`PaymentChannelType` 枚举增加 `XPAY_ALIPAY`。
- `user_account.registration_channel` 从 `WECHAT_ONLINE` 泛化为 `ONLINE`（或新增 `XPAY_ONLINE`），CHECK 约束同步放宽。
- 若选方案 B（保留表名），则仅新增 `channel` 字段、放宽 `openid_*` 为可空、其余不变。

> 说明：`PaymentChannelType` 目前含 `MANUAL` / `WECHAT_JSAPI`，新增 `XPAY_ALIPAY`；`RegistrationChannel` 由 `WECHAT_ONLINE` 泛化为 `ONLINE`。

### 5. 服务改造

- `OnlinePaymentService`：去掉对 `WechatOAuthGateway` 的直接依赖；`createOrder` 改为「若 `requiresPayerAuthorization()` 才先 `resolvePayer`，否则 payer 为 null → 落订单 → `channel.createOrder`」；`handleNotification` / `status` 逻辑不变。
- `WechatPaymentOrderStore` → 泛化命名（`OnlinePaymentOrderStore`，若选方案 A），payer 可空；`settle` 中「payer 校验」仅在 payer 非空时执行。
- `OnlineRegistrationService`：移除 `bindWechatIdentity`（不绑外部身份）；`registration_channel` 写 `ONLINE`。
- `PublicOnlinePaymentController`：`notifications` 拆分为渠道专属端点（见下）。

### 6. 回调端点改造

通知格式差异大，拆分为渠道专属端点，`NotifyPayload` 泛化为「原始 headers + params/body」由渠道自行解析：

- `POST /api/v1/public/online-payments/notifications/wechat`（保留微信，解析 `Wechatpay-*` 头）
- `POST /api/v1/public/online-payments/notifications/xpay`（新增易支付，解析 form 参数 + `sign`）

两个端点都按各自渠道要求返回成功/失败体。

### 7. 前端 guest-app 改造

- `types/index.ts`：`OnlinePaymentSettings` 增加 `channelType`；`OnlineOrder.payParameters` 泛化为 `PayParameters`（含 `channelType` + `jumpUrl` 或微信 `JsapiPayParameters`）。
- 新增 `adapters/payment.ts` 渠道分发：`channelType === 'XPAY_ALIPAY'` 时 `location.href = jumpUrl`（无需微信环境、无需授权前置）；微信走现有 `adapters/wechat.ts`。
- `pages/payment/index.vue`：去掉「微信内打开」强制提示与「网页授权回跳」逻辑（易支付无授权前置），下单 → 跳转支付宝 → `return_url` 回跳 → 查单 → 领注册令牌。
- `stores/payment.ts`：`createOrder` 不再传 `authorizationCode`（易支付无需）；支付结果通过 `return_url` 回跳后 `refreshStatus` 补偿。

### 8. 配置

`application.yml` 新增 `app.xpay.*`；`.env.example` 新增易支付变量；移除/保留 `WECHAT_*`（保留，将来切回微信用）。

| 环境变量 | 说明 |
|---|---|
| `XPAY_PID` | 商户 ID（10192） |
| `XPAY_MERCHANT_PRIVATE_KEY` | 商户私钥（PKCS#8 PEM，单行 `\n` 换行） |
| `XPAY_PLATFORM_PUBLIC_KEY` | 平台公钥（X.509 PEM） |
| `XPAY_BASE_URL` | 默认 `https://xpay.unbb.cn/xpay/epayn` |
| `XPAY_NOTIFY_URL` | 指向 `POST /api/v1/public/online-payments/notifications/xpay` |
| `XPAY_RETURN_URL` | 支付完成后同步跳转地址（回 guest-app 支付页） |
| `ONLINE_PAYMENT_PROVIDER` | 启用渠道：`XPAY_ALIPAY` / `WECHAT_JSAPI` |

---

## 关键状态流转

```
易支付线上：下单(create, type=alipay, method=jump, CREATED) → 前端跳转支付宝收银
  → 支付成功 → return_url 回跳 + 异步通知(验签) → 订单 PAID + 写 payment_record
  → 前端查单/领一次性注册令牌 → 提交手机号+密码 → 建账号(ACTIVE+VIP) + 订单置 registered + 令牌 USED
  → 累加会员额度 → 达阈值升 SVIP
```

手动路径、会员累计逻辑完全不变。

---

## File Map

### 后端 `services/platform-api`

- 新增 `com/love/archive/xpay/**`（package-info、application、config、support）
- 移动 / 泛化：`payment/application/PaymentChannel.java`、`CreateOrderCommand.java`、`CreateOrderResult.java`、`PayParameters.java`、`PaymentResult.java`、`NotifyPayload.java`
- 修改 `payment/application/OnlinePaymentService.java`、`WechatPaymentOrderStore.java`、`RegistrationTokenService.java`、`OnlinePaymentSettingsView.java`、`OnlineOrderView.java`
- 修改 `payment/web/PublicOnlinePaymentController.java`（拆分回调端点）
- 修改 `payment/domain/PaymentChannelType.java`、`identity/domain/RegistrationChannel.java`
- 修改 `identity/application/OnlineRegistrationService.java`（移除外部身份绑定）
- 修改 `wechatpay/**`（实现 `payment` 接口；移除被迁移的契约类型）
- 新增 `db/migration/V10__online_payment_channel.sql`
- 修改 `application.yml`、`application-test.yml`、`.env.example`
- 新增对应测试（易支付渠道、路由、订单泛化、回调拆分、前端 store）

### 前端 `apps/guest-app`

- 修改 `src/types/index.ts`、`src/api/index.ts`
- 新增 `src/adapters/payment.ts`；修改 `src/adapters/wechat.ts`
- 修改 `src/pages/payment/index.vue`、`src/stores/payment.ts`
- 修改对应测试

---

## 错误码

沿用 `ApiResponse` 包装与 `requestId`。新增：

```text
PAYMENT_CHANNEL_AMBIGUOUS            500
XPAY_RESPONSE_INVALID                502
XPAY_SIGNATURE_INVALID               400
XPAY_ORDER_FAILED                    502
```

现有 `PAYMENT_CHANNEL_NOT_CONFIGURED` / `PAYMENT_AMOUNT_MISMATCH` / `PAYMENT_ORDER_*` / `REGISTRATION_*` 继续复用。

---

## 待向平台确认 / 实测事项

1. **V2 通知的精确字段与验签串**：标准字段已确认（见「通知字段」），V2 版是 `trade_status` 还是 `status`（0/1）、验签串拼接是否与请求同款字典序拼串，联调时用平台「支付测试」核对。
2. **`buyer` 字段实际是否返回**：决定是否启用外部身份绑定（当前推荐不绑定，若平台稳定返回用户号可再评估）。
3. **金额精度**：`money` 为元字符串，转换时用 `BigDecimal` 避免浮点误差。
4. **`return_url` 与支付页恢复**：回跳后订单状态查询的补偿时序需实测。

---

## Tasks

### Task 1：渠道契约下沉（重构，无行为变化）

**Files:** `payment/application/PaymentChannel.java` 及契约 DTO、`wechatpay/**`、`package-info.java`。

**要点：** 把渠道契约从 `wechatpay` 移到 `payment`，`wechatpay` 改为实现接口；`payment` 不再依赖 `wechatpay`；现有 259 个测试全绿。

- [ ] 写失败测试 / 跑既有测试确认重构前绿
- [ ] 移动契约类型，反转依赖
- [ ] 架构守卫（`ModularityTest`）通过后 commit

### Task 2：易支付渠道模块

**Files:** `xpay/**`、`application.yml`、`.env.example`、测试。

**要点：** `XpayPaymentChannel`（API 下单指定支付宝、查单、验签）、RSA2 签名/验签、无凭据不装配。

- [ ] 写失败测试：无凭据 `PAYMENT_CHANNEL_NOT_CONFIGURED`；下单/查单/验签 mock
- [ ] 实现渠道 + 配置
- [ ] commit

### Task 3：订单泛化与数据迁移 V10

**Files:** `V10__online_payment_channel.sql`、`WechatPaymentOrderStore`/`Entity` 泛化、`PaymentChannelType`、`RegistrationChannel`。

**要点：** 方案 A 或 B（评审定）的迁移与实体字段、payer 可空、枚举扩展、运行时角色授权。

- [ ] 写失败测试：迁移 + 角色授权 + 枚举扩展
- [ ] 实现迁移与实体
- [ ] commit

### Task 4：服务与回调拆分

**Files:** `OnlinePaymentService`、`PublicOnlinePaymentController`、`OnlineRegistrationService`、`RegistrationTokenService`。

**要点：** 渠道路由、payer 可选、回调端点拆分（wechat/xpay）、移除外部身份绑定。

- [ ] 写失败测试：路由选择、payer 为空、易支付回调、注册不绑身份
- [ ] 实现
- [ ] commit

### Task 5：前端 guest-app 适配

**Files:** `src/types/index.ts`、`src/api/index.ts`、`src/adapters/payment.ts`、`src/pages/payment/index.vue`、`src/stores/payment.ts`、测试。

**要点：** 渠道分发、易支付跳转支付宝、return_url 回跳补偿、移除微信内强制提示。

- [ ] 写失败测试：渠道分发、下单 payload、支付结果恢复
- [ ] 实现页面与 store
- [ ] commit

### Task 6：文档与收尾

**Files:** `README.md`、`.env.example`、本方案更新。

**要点：** 记录新端点、环境变量、渠道说明；`git diff --check`；全量验证。

- [ ] 更新文档
- [ ] 全量验证（后端 `./mvnw -pl services/platform-api test`、前端 build/test）
- [ ] commit

---

## Verification

```bash
# 后端
./mvnw -pl services/platform-api test
./mvnw -pl services/platform-api package -DskipTests

# 前端
cd apps/guest-app && npx vitest run --config vitest.config.ts && npm run type-check && npm run build:h5

# 架构守卫
./mvnw -pl services/platform-api test -Dtest=ModularityTest,SensitiveDataGuardTest,LombokEntitySafetyTest
```
