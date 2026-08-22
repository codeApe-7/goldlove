# 婚恋智能档案库

免费自助注册 → 免费建档（保存即生效，无审核）→ 可选的 VIP 会员升级（在线支付或激活码）。
管理后台可以看到每一份档案、所有支付订单，并生成绑定手机号的激活码。

线上支付走易支付（XPay V2，指定支付宝），凭据缺失时相关接口返回 `PAYMENT_CHANNEL_NOT_CONFIGURED`，
不影响其余模块启动；AI 能力暂未接入。

## 已选技术栈

- Java 25、Spring Boot 4.1、Spring Modulith
- Spring MVC、Sa-Token（不使用 Spring Security）
- MyBatis-Plus 3.5.17：单表 CRUD/Wrapper；复杂查询用 `@Select`/`@SelectProvider`，不使用 Mapper XML
- Lombok：实体与简单配置使用 `@Getter/@Setter`，依赖注入使用 `@RequiredArgsConstructor`
- PostgreSQL 18、Flyway、Redis 8
- Argon2id 密码摘要
- JUnit、Testcontainers

## 本地启动

需要 Docker Desktop 或 OrbStack。先准备本地环境文件：

```bash
cp .env.example .env
```

填入 PostgreSQL 所有者密码、应用运行账号密码、Redis 密码与初始管理员密码，以及腾讯云 COS 凭据。
只有首次需要创建管理员时才把 `ADMIN_BOOTSTRAP_ENABLED` 改为 `true`；创建成功后立即恢复为 `false`。

```bash
docker compose up --build
```

Compose 会先运行一次性 Flyway 迁移容器，再启动 API。长驻 API 进程只持有 `archive_app` 凭据，
不持有数据库所有者密码。其他环境也应把迁移作为独立发布步骤执行。

本地 HTTP 开发允许把 `AUTH_COOKIE_SECURE` 设为 `false`；线上 HTTPS 必须设为 `true`。
`BROWSER_ALLOWED_ORIGINS` 必须填写实际域名，多个来源用英文逗号分隔。

登录限流默认按 API 直接看到的 TCP 来源地址计数。部署在 Nginx、Ingress 等可信反向代理之后时，
可将 `SERVER_FORWARD_HEADERS_STRATEGY` 设为 `framework`；同时必须由代理覆盖客户端传入的
`Forwarded`/`X-Forwarded-*`，并禁止公网绕过代理直连 API，避免伪造来源地址绕过限流。

健康检查地址为 `GET http://localhost:8080/actuator/health`。

## API

| 方法 | 路径 | 认证 | 用途 |
|---|---|---|---|
| POST | `/api/v1/guest/auth/register` | 无 | 免费注册（手机号、密码、确认密码、同意授权书） |
| POST | `/api/v1/guest/auth/login` | 无 | 访客登录 |
| GET | `/api/v1/guest/auth/me` | 访客 | 查询当前会话 |
| POST | `/api/v1/guest/auth/logout` | 访客 | 退出登录 |
| GET | `/api/v1/public/authorization-documents/current` | 无 | 获取当前生效的授权书 |
| GET | `/api/v1/public/authorization-documents/{version}` | 无 | 查看指定版本的授权书 |
| GET | `/api/v1/guest/profile/field-definitions` | 访客 | 查询启用的档案字段定义 |
| GET | `/api/v1/guest/profile/draft` | 访客 | 查询本人档案 |
| PUT | `/api/v1/guest/profile/draft` | 访客 | 保存档案（乐观锁 `expectedVersion`），保存即生效 |
| GET | `/api/v1/guest/profile/status` | 访客 | 查询完成度与缺失的必填字段 |
| POST | `/api/v1/guest/profile/photo-uploads` | 访客 | 校验并上传照片到 COS，仅返回对象键（不落库） |
| GET | `/api/v1/guest/profile/photos` | 访客 | 查询已保存照片集合（含短时预览 URL） |
| GET | `/api/v1/guest/membership` | 访客 | 查询本人会员等级与累计付费额度 |
| POST | `/api/v1/guest/membership/activation-codes` | 访客 | 用激活码升级 |
| GET | `/api/v1/guest/vip-payments/settings` | 访客 | 获取渠道类型与升级金额 |
| POST | `/api/v1/guest/vip-payments/orders` | 访客 | 创建 VIP 升级订单（不传金额、不传账号） |
| GET | `/api/v1/guest/vip-payments/orders/{outTradeNo}` | 访客 | 查询订单状态（本地仍未支付时主动向渠道查单补偿） |
| POST | `/api/v1/public/payment-notifications/xpay` | 验签 | 易支付结果通知，幂等结算 |
| POST | `/api/v1/admin/auth/login` | 无 | 管理员登录 |
| GET | `/api/v1/admin/dashboard/stats` | 管理员 | 工作台统计 |
| GET | `/api/v1/admin/profiles` | 管理员 | 分页档案列表（关键词/完成度/账号状态/等级/地区/创建时间/排序） |
| GET | `/api/v1/admin/profiles/counts` | 管理员 | 各状态 tab 的数量（忽略 tab 自身条件） |
| GET | `/api/v1/admin/profiles/export` | 管理员 | 导出 CSV（可传 `ids` 只导勾选行，上限 5000，写审计） |
| GET | `/api/v1/admin/profiles/{id}` | 管理员 | 档案详情（含动态字段与签名照片地址） |
| POST | `/api/v1/admin/accounts/{id}/suspend` | 管理员 | 停用访客账号（备注写入审计） |
| POST | `/api/v1/admin/accounts/{id}/activate` | 管理员 | 启用访客账号 |
| GET | `/api/v1/admin/payment-orders` | 管理员 | 分页支付订单列表 |
| POST | `/api/v1/admin/activation-codes` | 管理员 | 生成激活码（绑定手机号 + 等级） |
| GET | `/api/v1/admin/activation-codes` | 管理员 | 分页激活码列表（含兑换人） |
| POST | `/api/v1/admin/activation-codes/{id}/revoke` | 管理员 | 作废未使用的激活码 |
| GET | `/api/v1/admin/profile-field-definitions` | 管理员 | 分页查询档案字段定义 |
| POST | `/api/v1/admin/profile-field-definitions` | 管理员 | 新增动态字段定义 |
| PATCH | `/api/v1/admin/profile-field-definitions/{id}` | 管理员 | 更新字段定义（受保护属性不可修改） |

所有响应统一包含 `success`、`code`、`message`、`data` 和 `requestId`。客户端可以传入安全格式的
`X-Request-ID`，否则服务端自动生成。

管理员使用 `archive-token-admin` HttpOnly Cookie，访客使用 `Authorization: Bearer <token>` 请求头。
访客会话在每次请求入口统一校验账号启用状态，账号停用后下一次请求立即失效。携带 Cookie 的管理后台
写请求必须提供可信 `Origin` 或 `Referer`；访客请求不依赖 Cookie，不受来源校验影响。

## 注册与建档流程

1. 用户在注册页填手机号、密码、确认密码并勾选同意授权书。后端校验手机号格式与唯一性、
   密码策略（12–128 位含字母数字），在同一个事务内建账号（`ACTIVE` + `FREE`）并写入一条同意记录。
   注册按手机号 + 客户端 IP 双维度限流，成功后**不重置**手机号计数——同一号码只能注册一次，
   重复请求都是异常流量。
2. 用户直接填写档案。选择照片时先上传 COS 并暂存对象键，点击保存时才把照片集合写入档案。
   保存时按必填项与头像是否齐全算出 `DRAFT` / `COMPLETED`，**没有提交与审核环节**，保存即对管理员可见。
3. 会员升级有两条路径，都在「我的 → 会员」页：
   - **在线支付**：下单 → 跳转支付宝收银台 → 回跳查单 → 结算成功后授予 VIP 并累加付费额度
   - **激活码**：管理员生成时绑定手机号，只有该手机号的账号能兑换；兑换直接授予等级，
     **不计入付费额度**（兑码不是付费，不应该顶 SVIP 的阈值）

## 会员等级

| 等级 | 获得方式 |
|---|---|
| `FREE` | 注册即得，免费建档 |
| `VIP` | 在线支付或兑换激活码 |
| `SVIP` | 累计付费额度达到 `MEMBERSHIP_SVIP_THRESHOLD_MINOR`（默认 `59900`，即 ¥599）自动升级 |

- 幂等锚点是 `payment_record.membership_credit_minor`：同一笔付款重复计入只生效一次。
- 等级只升不降，已升级的账号不会因为调高阈值而降级。
- 读取会员信息时会把「额度已达标但等级未跟上」的账号对齐。
- SVIP 的「查看感兴趣用户」权益本期只建等级，不实现查看功能。

## 支付

金额只取服务端配置 `VIP_UPGRADE_AMOUNT_MINOR`，回调金额与订单金额不一致时拒绝结算
（`PAYMENT_AMOUNT_MISMATCH`）。`out_trade_no` 由服务端用 24 字节随机数生成（Base64URL，32 字符），
全局唯一并作为幂等锚点。回调用平台公钥做 RSA2 验签，验签失败返回 401，结算冲突返回 500 让渠道重试。
重复回调幂等，不会重复写付款记录。查单接口只允许查询本人的订单。

| 环境变量 | 说明 |
|---|---|
| `XPAY_PID` | 易支付商户 ID |
| `XPAY_MERCHANT_PRIVATE_KEY` / `XPAY_PLATFORM_PUBLIC_KEY` | 商户私钥 / 平台公钥（PEM），RSA2 双向签名 |
| `XPAY_NOTIFY_URL` | 异步回调地址，指向 `POST /api/v1/public/payment-notifications/xpay` |
| `XPAY_RETURN_URL` | 支付完成后同步跳转地址（回 guest-app 会员页） |
| `XPAY_BASE_URL` | 渠道网关地址，**必填**（刻意不设默认值，服务商域名属于部署配置） |
| `ONLINE_PAYMENT_PROVIDER` | 启用的渠道；留空则取唯一已配置渠道 |
| `VIP_UPGRADE_AMOUNT_MINOR` | VIP 升级金额（分），默认 `100`（¥1，便于联调） |
| `MEMBERSHIP_SVIP_THRESHOLD_MINOR` | 升 SVIP 的累计付费额度（分），默认 `59900` |

上述五项凭据齐全时才装配渠道客户端；缺任何一项，支付接口返回 `PAYMENT_CHANNEL_NOT_CONFIGURED`（503），
其余功能不受影响。

## 错误码

| 错误码 | HTTP | 含义 |
|---|---|---|
| `PHONE_INVALID` | 400 | 手机号格式不正确 |
| `PASSWORD_POLICY_VIOLATION` | 400 | 密码需为 12 至 128 位并同时包含字母和数字 |
| `PASSWORD_CONFIRMATION_MISMATCH` | 400 | 两次输入的密码不一致 |
| `CONSENT_ACCEPTANCE_REQUIRED` | 400 | 必须阅读并同意授权书 |
| `ACCOUNT_ALREADY_EXISTS` | 409 | 该手机号已存在账号 |
| `AUTH_INVALID_CREDENTIALS` | 401 | 手机号或密码错误 |
| `AUTH_ACCOUNT_INACTIVE` | 403 | 账号已停用 |
| `AUTH_RATE_LIMITED` | 429 | 尝试次数过多 |
| `ACTIVATION_CODE_NOT_FOUND` | 404 | 激活码不存在 |
| `ACTIVATION_CODE_USED` | 409 | 激活码已被使用 |
| `ACTIVATION_CODE_REVOKED` | 409 | 激活码已作废 |
| `ACTIVATION_CODE_PHONE_MISMATCH` | 409 | 该激活码不属于当前手机号 |
| `ACTIVATION_CODE_NOT_REVOCABLE` | 409 | 只有未使用的激活码可以作废 |
| `PAYMENT_CHANNEL_NOT_CONFIGURED` | 503 | 渠道凭据未配置齐全 |
| `PAYMENT_CHANNEL_UNAVAILABLE` | 502 | 渠道网络不可用 |
| `PAYMENT_CHANNEL_ORDER_FAILED` / `PAYMENT_CHANNEL_QUERY_FAILED` | 502 | 渠道下单 / 查单失败 |
| `PAYMENT_CHANNEL_RESPONSE_INVALID` | 502 | 渠道响应无法解析或缺字段 |
| `PAYMENT_ORDER_NOT_FOUND` | 404 | 订单不存在或不属于当前账号 |
| `PAYMENT_ORDER_STATE_CONFLICT` | 409 | 订单状态并发变化，请重试 |
| `PAYMENT_AMOUNT_MISMATCH` | 409 | 支付金额与服务端订单金额不一致 |
| `PAYMENT_NOTIFY_SIGNATURE_INVALID` | 400 | 回调验签失败（回调端点对外返回 401） |
| `PROFILE_VERSION_CONFLICT` | 409 | 档案版本已变化，请刷新后重试 |
| `PROFILE_NOT_FOUND` | 404 | 档案不存在 |
| `PROFILE_EXPORT_TOO_LARGE` | 400 | 单次导出超过 5000 条 |
| `REQUEST_PARAM_INVALID` | 400 | 请求参数取值不正确（枚举值不认识等，消息带参数名） |
| `ACCOUNT_NOT_FOUND` | 404 | 账号不存在 |
| `ACCOUNT_CLOSED` | 409 | 账号已注销，不能再改状态 |
| `ACCOUNT_STATUS_CONFLICT` | 409 | 账号状态并发变化，请重试 |

## 对象存储（腾讯云 COS）

仓库使用腾讯云 COS 官方 Java SDK（`com.qcloud:cos_api`）访问**私有桶**，凭据只存在于服务端环境变量：

| 环境变量 | 说明 |
|---|---|
| `COS_SECRET_ID` / `COS_SECRET_KEY` | 腾讯云 API 密钥 |
| `COS_REGION` | 桶地域，默认 `ap-guangzhou` |
| `COS_BUCKET` | 桶名 |

四项配置齐全时才会创建 COS 客户端；未配置时相关接口返回 `OBJECT_STORAGE_NOT_CONFIGURED`。
对象键仅允许 `[0-9a-zA-Z._/-]`，单对象上限 10 MiB。

本地验证真实桶连通性：

```bash
./mvnw -pl services/platform-api test -Dtest=CosLiveSmokeTest -Dcos.live.smoke=true
```

## 照片上传

- 限制：头像 1 张、生活照最多 6 张；单张 ≤ 10 MiB；仅 JPEG/PNG/WebP；
  服务端校验真实格式与最小 64×64 尺寸，不信任客户端声明的类型。
- 对象键始终只保存在服务端；预览地址是 15 分钟短时签名 URL，从不落库。
- 保存时从档案里移除的照片会在事务提交后直接删除 COS 对象（没有版本快照需要留档了）。

## 本机 Java 构建

要求 JDK 25 与 Maven 3.9.11。仓库包含 Maven Wrapper：

```bash
./mvnw -pl services/platform-api test
./mvnw -pl services/platform-api package -DskipTests
```

测试会通过 Testcontainers 启动临时 PostgreSQL 18 和 Redis 8，不读取 `.env` 中的真实凭据。

## 管理后台（apps/admin-web）

Vue 3 + TypeScript + Vite + Element Plus：

```bash
cd apps/admin-web
npm install
npm run dev        # 开发：/api 代理到 http://localhost:8080
npm run test       # Vitest
npm run build      # 产物 dist/
```

生产部署：Nginx 托管 `dist/` 静态资源，并把 `/api` 反向代理到后端（同源保证 Cookie 会话可用）：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

功能：管理员登录/退出、工作台统计、档案列表与详情、支付订单列表、激活码生成/复制/作废、字段配置。

## 嘉宾端（apps/guest-app）

uni-app（Vue 3 + TypeScript），第一期发布 H5：

```bash
cd apps/guest-app
npm install          # 项目含 .npmrc（legacy-peer-deps），按配置安装即可
npm run dev:h5       # 开发：/api 代理到 http://localhost:8080
npx vitest run --config vitest.config.ts
npm run build:h5     # 产物 dist/build/h5
```

部署：H5 静态托管 + Nginx 同源反代 `/api`，并确保后端 `BROWSER_ALLOWED_ORIGINS` 包含 H5 域名。

页面：登录（含免费注册入口）、注册、档案填写与照片上传、我的、会员升级（支付 + 激活码）。
H5 走 hash 路由，`XPAY_RETURN_URL` 应指向会员页，例如 `https://<域名>/#/pages/vip/index`。

## 安全约束

- **密码只存 Argon2id 摘要**，永不可逆，也不记录明文。
- 手机号、微信号、抖音号、支付交易号以明文列存储（本次重构的显式取舍，见下方风险）。
- 持久化实体不使用 Lombok `@Data` 或类级 `@ToString`，避免整行数据进入日志；
  `SensitiveDataGuardTest` 会静态检查日志语句里不出现这些字段名。
- 同意记录与审计记录在数据库层不可更新、不可删除。
- Flyway 使用 `archive_owner`，业务进程使用无 DDL 权限的 `archive_app`；
  运行账号对同意记录与审计表只有查询和追加权限，对账目类表没有 DELETE。
- 首管理员初始化默认关闭；开启时拒绝空白、过短和常见默认密码。
- 管理员登录、访客登录与注册在执行 Argon2id 前先使用 Redis 做账号与客户端双维度限流。
- 支付商户私钥只存服务端环境变量，绝不下发前端；下发前端的只有后端签名后的跳转链接。
- 线上必须通过 HTTPS 提供接口，并妥善保管 `.env` 或由密钥管理服务注入环境变量。

### 已知风险

1. **注册不做短信验证**，只校验手机号位数，因此任何人都能用他人手机号注册。
   激活码在生成时绑定手机号，若该号尚未注册，抢先注册的人就能领走这个码。
   管理后台的激活码列表与生成弹窗会标注「该手机号未注册」作为提醒；
   彻底堵住需要引入短信验证码。
2. **敏感字段为明文**，数据库或备份一旦泄露即为可读的手机号与社交账号。
   请务必限制数据库端口只对本机开放，并妥善保管备份。
3. **激活码明文存库**（管理员必须能复读并分发）。拖库会泄露所有未兑换的码，
   但码的价值仅为一次会员升级且可随时作废。

## 后续扩展

SVIP 的「查看感兴趣用户」权益、退款回调与订单关闭、未支付订单的定时清理、
微信支付与微信一键登录（本次已连同 openid 绑定一并移除，如需接入是全新的一件事）。
