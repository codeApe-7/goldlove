# 婚恋智能档案库

当前仓库已实现后端身份与建档审核闭环：管理员登录、登记已付费访客、一次性初始凭证激活、访客登录/会话查询/退出、付费前授权书展示与主动同意、档案草稿保存、不可变提交与幂等重试、管理员审核通过与退回，并已接入腾讯云 COS 对象存储：访客选择照片时先上传 COS 并暂存对象键，保存草稿时才把照片集合写入档案。

在此之上新增「微信支付 → 线上注册建档」路径与 VIP/SVIP 会员等级体系，原有「管理员登记 → 邀请码 → 激活」手动路径原样保留。微信支付走 API v3（JSAPI，公众号），凭据缺失时相关接口返回 `PAYMENT_CHANNEL_NOT_CONFIGURED`，不影响其余模块启动；AI 能力暂未接入。

## 已选技术栈

- Java 25、Spring Boot 4.1、Spring Modulith
- Spring MVC、Sa-Token（不使用 Spring Security）
- MyBatis-Plus 3.5.17：单表 CRUD/Wrapper；不使用 Mapper XML
- Lombok：实体与简单配置使用 `@Getter/@Setter`，依赖注入使用 `@RequiredArgsConstructor`
- PostgreSQL 18、Flyway、Redis 8
- Argon2id 密码摘要、AES-256-GCM 手机号加密、HMAC-SHA256 等值查询索引
- JUnit、Testcontainers

## 本地启动

需要 Docker Desktop 或 OrbStack。先准备本地环境文件：

```bash
cp .env.example .env
openssl rand -base64 32
openssl rand -base64 32
openssl rand -base64 32
openssl rand -base64 32
```

将四次生成的不同值分别填入 `.env` 的 `PHONE_ENCRYPTION_KEY`、`PHONE_SEARCH_KEY`、`PROFILE_ENCRYPTION_KEY` 与 `PROFILE_HMAC_KEY`，并替换 PostgreSQL 所有者、应用运行账号、Redis 和初始管理员密码。手机号密钥与档案密钥必须互相独立；`PROFILE_ENCRYPTION_KEY` 用于加密微信号、抖音号、抖音昵称和主页链接，`PROFILE_HMAC_KEY` 用于生成微信号/抖音号等值查询索引和幂等键摘要。只有首次需要创建管理员时才把 `ADMIN_BOOTSTRAP_ENABLED` 改为 `true`；创建成功后立即恢复为 `false`。随后启动：

```bash
docker compose up --build
```

Compose 会先运行一次性 Flyway 迁移容器，再启动 API。长驻 API 进程只持有 `archive_app` 凭据，不持有数据库所有者密码。其他环境也应把迁移作为独立发布步骤执行；若确需临时由应用迁移，可显式设置 `FLYWAY_ENABLED=true` 并注入独立 Flyway 账号，迁移完成后不要把该账号留给长驻进程。

> 从早期单数据库账号版本升级时，已有 `postgres-data` 卷不会重新执行角色初始化脚本。若卷内没有需要保留的数据，可先执行 `docker compose down -v` 后重新启动（该命令会删除本项目本地数据库与 Redis 数据）；若已有数据，不要删除卷，应先备份并执行所有权迁移与 `archive_app` 降权，再使用本版本。

本地 HTTP 开发允许把 `AUTH_COOKIE_SECURE` 设为 `false`；线上 HTTPS 必须设为 `true`。`BROWSER_ALLOWED_ORIGINS` 必须填写实际 H5 域名，多个来源用英文逗号分隔。

登录限流默认按 API 直接看到的 TCP 来源地址计数。部署在 Nginx、Ingress 等可信反向代理之后时，可将 `SERVER_FORWARD_HEADERS_STRATEGY` 设为 `framework`；同时必须由代理覆盖客户端传入的 `Forwarded`/`X-Forwarded-*`，并禁止公网绕过代理直连 API，避免伪造来源地址绕过限流。

健康检查地址为 `GET http://localhost:8080/actuator/health`。

## API

| 方法 | 路径 | 认证 | 用途 |
|---|---|---|---|
| POST | `/api/v1/admin/auth/login` | 无 | 管理员登录 |
| POST | `/api/v1/admin/accounts` | 管理员 | 登记已付款访客并仅本次返回初始凭证 |
| POST | `/api/v1/admin/accounts/activation-credentials/reissue` | 管理员 | 初始响应丢失时补发一次性凭证并作废旧凭证 |
| GET | `/api/v1/public/authorization-documents/current` | 无 | 获取当前生效的授权书 |
| GET | `/api/v1/guest/authorization-documents/{version}` | 访客 | 查看当前或本人付款记录引用的历史授权书 |
| GET | `/api/v1/guest/consents/current` | 访客 | 查询当前有效授权 |
| POST | `/api/v1/guest/consents` | 访客 | 主动同意当前授权书版本 |
| GET | `/api/v1/guest/profile/draft` | 访客 | 查询本人档案草稿（含解密后的本人敏感标识） |
| PUT | `/api/v1/guest/profile/draft` | 访客 | 保存/更新档案草稿（乐观锁 `expectedVersion`） |
| GET | `/api/v1/guest/profile/status` | 访客 | 查询建档状态 |
| POST | `/api/v1/guest/profile/submissions` | 访客 | 提交不可变档案版本（必须携带 `Idempotency-Key` 请求头） |
| GET | `/api/v1/guest/profile/revisions/{revisionId}` | 访客 | 查询本人提交的版本详情 |
| GET | `/api/v1/admin/profile-field-definitions` | 管理员 | 分页查询档案字段定义 |
| POST | `/api/v1/admin/profile-field-definitions` | 管理员 | 新增动态字段定义 |
| PATCH | `/api/v1/admin/profile-field-definitions/{id}` | 管理员 | 更新字段定义（受保护属性不可修改） |
| GET | `/api/v1/admin/profile-reviews` | 管理员 | 分页查询待审版本（支持状态、超时、提交时间、档案编号筛选） |
| GET | `/api/v1/admin/profile-reviews/{revisionId}` | 管理员 | 版本审核详情与相对最后已通过版本的字段差异 |
| POST | `/api/v1/admin/profile-reviews/{revisionId}/approve` | 管理员 | 审核通过（请求体携带 `expectedVersion`） |
| POST | `/api/v1/admin/profile-reviews/{revisionId}/reject` | 管理员 | 审核退回（必须提供面向嘉宾的说明） |
| GET | `/api/v1/public/online-payments/settings` | 无 | 获取公众号 AppID、下单金额与网页授权链接 |
| POST | `/api/v1/public/online-payments/orders` | 无 | 用网页授权 code 下单，返回调起支付参数 |
| GET | `/api/v1/public/online-payments/orders/{outTradeNo}` | 无 | 查询订单状态（本地仍未支付时主动向渠道查单补偿） |
| POST | `/api/v1/public/online-payments/orders/{outTradeNo}/registration-tokens` | 无 | 支付成功后签发一次性注册令牌（明文仅返回一次） |
| POST | `/api/v1/public/online-payments/notifications` | 验签 | 微信支付结果通知，幂等结算 |
| POST | `/api/v1/public/registrations` | 无 | 用注册令牌 + 手机号 + 密码完成线上建档注册 |
| GET | `/api/v1/guest/membership` | 访客 | 查询本人会员等级与累计付费额度 |
| POST | `/api/v1/guest/auth/activate` | 无 | 使用手机号、初始凭证设置正式密码 |
| POST | `/api/v1/guest/auth/login` | 无 | 访客登录 |
| GET | `/api/v1/guest/auth/me` | 访客 | 查询当前会话 |
| POST | `/api/v1/guest/auth/logout` | 访客 | 退出登录 |
| POST | `/api/v1/guest/profile/photo-uploads` | 访客 | 校验并上传照片到 COS，仅返回对象键（不落库） |
| GET | `/api/v1/guest/profile/photos` | 访客 | 查询已保存照片集合（含短时预览 URL） |

所有响应统一包含 `success`、`code`、`message`、`data` 和 `requestId`。客户端可以传入安全格式的 `X-Request-ID`，否则服务端自动生成。

管理员使用 `archive-token-admin` HttpOnly Cookie，访客使用 `Authorization: Bearer <token>` 请求头（登录/激活响应返回 `accessToken` 与 `expiresIn`）。访客会话在每次请求入口统一校验账号启用状态，账号停用后下一次请求立即失效。携带 Cookie 的管理后台写请求必须提供可信 `Origin` 或 `Referer`；访客请求不依赖 Cookie，不受来源校验影响，但仍复用同一套 Sa-Token 会话与账号状态校验。

## 建档与审核流程

1. 管理员登记已付款访客时，系统会把付款记录绑定到创建访客时传入的授权书版本（`authorizationDocumentVersion`），付款前展示给用户的是同一份授权书。
2. 访客激活后主动同意该版本授权书（一年有效期）；选择照片时仅上传 COS 并暂存对象键，点击保存草稿时才创建/更新档案并把完整照片集合写入 `profile_photo`，点击提交时先保存再提交审核；微信号、抖音号、抖音昵称和主页链接以 AES-256-GCM 随机加密存储，查询索引和幂等键只保存 HMAC。
3. 访客提交时生成不可变版本快照和 24 小时审核截止时间；`Idempotency-Key` 绑定“账号 + 键 + 规范化内容摘要”，相同内容重试返回原版本，键被用于不同内容时返回 `IDEMPOTENCY_KEY_REUSED`。
4. 管理员在审核列表按状态/即将超时/已超时/提交时间/档案编号筛选，查看相对最后已通过版本的字段级差异后通过或退回；退回必须附带面向嘉宾的说明。通过会切换档案的已通过版本指针，退回保留最后已通过版本并将档案置为待修改。
5. 审核记录只追加、版本快照不可变；重复提交相同审核结果幂等返回，把已完成版本改成另一结果返回 `PROFILE_REVIEW_ALREADY_COMPLETED`。

照片字段的上传闭环将在下一阶段接入；对象存储 Key 始终只保存在服务端，不会下发到 H5 或小程序。

## 对象存储（腾讯云 COS）

仓库使用腾讯云 COS 官方 Java SDK（`com.qcloud:cos_api`）访问**私有桶**，凭据只存在于服务端环境变量，绝不随前端包下发：

| 环境变量 | 说明 |
|---|---|
| `COS_SECRET_ID` | 腾讯云 API 密钥 SecretId |
| `COS_SECRET_KEY` | 腾讯云 API 密钥 SecretKey |
| `COS_REGION` | 桶地域，默认 `ap-guangzhou` |
| `COS_BUCKET` | 桶名，如 `loveplatform-1314980040` |

`app.storage.cos` 四项配置齐全时才会创建 COS 客户端；未配置时相关接口返回 `OBJECT_STORAGE_NOT_CONFIGURED`，不影响其他模块启动。`ObjectStorageService` 提供上传、存在性查询、短时签名下载地址（默认最长 7 天）和删除；对象键仅允许 `[0-9a-zA-Z._/-]`，单对象上限 10 MiB，上传会记录 SHA-256。

本地验证真实桶连通性（需要 `.env` 或导出 `COS_*` 环境变量）：

```bash
./mvnw -pl services/platform-api test -Dtest=CosLiveSmokeTest -Dcos.live.smoke=true
```

冒烟测试会向桶写入 `smoke-tests/{uuid}.txt`，验证上传、签名下载、读取和删除后即清理，默认不执行。

下一阶段把照片字段接入该服务：上传后保存对象键到档案/版本快照，审核与直播素材按原始、处理、发布三个前缀隔离，并只在需要时签发短时下载地址。

## 照片上传

- 嘉宾接口：`POST /api/v1/guest/profile/photos`（multipart：`file` + `category`，类别 `AVATAR`/`LIFE`）、`GET /api/v1/guest/profile/photos`、`DELETE /api/v1/guest/profile/photos/{photoId}`。
- 限制：头像 1 张、生活照最多 6 张；单张 ≤ 10 MiB；仅 JPEG/PNG/WebP；服务端校验真实格式与最小 64×64 尺寸，不信任客户端声明的类型。
- 提交建档时头像必填；照片随草稿保存、随提交固化进不可变版本快照；审核详情与嘉宾版本详情返回照片元数据与 15 分钟短时签名 URL（URL 不落库）。
- 草稿删除立即删除 COS 对象；进入版本快照后对象保留用于历史追溯；账号注销/授权到期后的批量清理待后续接入。

## 微信支付与线上注册（双路径）

建档账号有两条并存的入口，互不影响：

| 路径 | 流程 | 账号状态 |
|---|---|---|
| 手动（原有） | 管理员登记已付费访客 → 一次性初始凭证 → 访客激活设密码 | `PAID_PENDING_ACTIVATION` → `ACTIVE` |
| 线上（新增） | 微信内打开链接 → 网页授权取 openid → 微信支付 → 领取一次性注册令牌 → 填手机号与密码 | 直接 `ACTIVE` |

线上路径的状态流转：

```text
下单(CREATED) → 微信回调验签 → 订单 PAID + 写 payment_record → 签发注册令牌(UNUSED)
  → 提交注册 → 建账号(ACTIVE + VIP) + 绑 openid + 订单置 registered + 令牌 USED
  → 累加会员额度 → 达阈值升 SVIP
```

后端约束：

- 金额只取服务端配置 `ONLINE_REGISTRATION_AMOUNT_MINOR`，回调金额与订单金额不一致时拒绝结算（`PAYMENT_AMOUNT_MISMATCH`）。
- 回调必须用微信支付平台公钥验签，并校验 `Wechatpay-Serial` 与 5 分钟时间戳窗口；重复回调幂等，不会重复写付款记录。
- `out_trade_no` 由服务端用 24 字节随机数生成（Base64URL，32 字符），全局唯一并作为幂等锚点。
- 注册令牌一次性且默认 30 分钟有效，库内只存 HMAC；重新签发会作废该订单此前的待用令牌。一个支付订单只能完成一次注册。
- openid 与微信交易号加密存储（AES-256-GCM）并另存 HMAC 供等值查询，不落明文、不打日志。
- 网页授权回跳地址由服务端配置固定，不接受调用方传入，避免开放重定向。
- 注册成功后账号即 `ACTIVE`，随后仍需按现有流程主动同意授权书再填写档案；付款前展示的授权书版本会钉在订单与付款记录上。

| 环境变量 | 说明 |
|---|---|
| `WECHAT_APP_ID` / `WECHAT_APP_SECRET` | 公众号 AppID 与 AppSecret（AppSecret 仅服务端） |
| `WECHAT_MERCHANT_ID` / `WECHAT_MERCHANT_SERIAL_NUMBER` | 商户号与 API 证书序列号 |
| `WECHAT_MERCHANT_PRIVATE_KEY` | 商户 API 私钥（PKCS#8 PEM） |
| `WECHAT_API_V3_KEY` | API v3 密钥（32 字节），用于回调资源解密 |
| `WECHAT_PLATFORM_PUBLIC_KEY_ID` / `WECHAT_PLATFORM_PUBLIC_KEY` | 微信支付平台公钥 ID 与公钥（X.509 PEM），用于回调验签 |
| `WECHAT_PAY_NOTIFY_URL` | 公网 HTTPS 回调地址，指向 `POST /api/v1/public/online-payments/notifications` |
| `WECHAT_OAUTH_REDIRECT_URI` | 网页授权回跳地址，需与公众号后台「网页授权域名」一致 |
| `ONLINE_REGISTRATION_AMOUNT_MINOR` | 线上建档下单金额（分），默认 `100`（¥1，便于联调） |
| `REGISTRATION_TOKEN_TTL` | 注册令牌有效期，默认 `30m` |

九项 `WECHAT_*` 凭据（除 `WECHAT_OAUTH_REDIRECT_URI`）齐全时才装配渠道客户端；缺任何一项，线上支付相关接口返回 `PAYMENT_CHANNEL_NOT_CONFIGURED`（503），手动路径与其余功能不受影响。

新增错误码（沿用 `ApiResponse` 包装与 `requestId`）：

| 错误码 | HTTP | 含义 |
|---|---|---|
| `PAYMENT_CHANNEL_NOT_CONFIGURED` | 503 | 渠道凭据未配置齐全 |
| `PAYMENT_CHANNEL_UNAVAILABLE` | 502 | 渠道网络不可用 |
| `PAYMENT_CHANNEL_ORDER_FAILED` / `PAYMENT_CHANNEL_QUERY_FAILED` | 502 | 渠道下单 / 查单失败 |
| `PAYMENT_CHANNEL_RESPONSE_INVALID` | 502 | 渠道响应无法解析或缺字段 |
| `PAYMENT_ORDER_NOT_FOUND` | 404 | 商户订单号不存在 |
| `PAYMENT_ORDER_ALREADY_PAID` | 409 | 渠道侧订单已支付或已关闭 |
| `PAYMENT_ORDER_STATE_CONFLICT` | 409 | 订单状态并发变化，请重试 |
| `PAYMENT_AMOUNT_MISMATCH` | 409 | 支付金额与服务端订单金额不一致 |
| `PAYMENT_ORDER_PAYER_MISMATCH` | 409 | 支付者与下单人不一致 |
| `PAYMENT_NOTIFY_SIGNATURE_INVALID` | 400 | 回调验签失败（回调端点对外返回 401） |
| `WECHAT_AUTHORIZATION_CODE_INVALID` | 400 | 网页授权 code 无效或已过期 |
| `REGISTRATION_TOKEN_INVALID` | 400 | 注册令牌不存在 |
| `REGISTRATION_TOKEN_EXPIRED` | 410 | 注册令牌已过期或被重新签发顶替 |
| `REGISTRATION_TOKEN_USED` | 409 | 注册令牌已被使用 |
| `REGISTRATION_ORDER_NOT_PAID` | 409 | 订单尚未支付成功 |
| `REGISTRATION_ALREADY_COMPLETED` | 409 | 该订单已完成注册 |
| `WECHAT_ACCOUNT_ALREADY_BOUND` | 409 | 该微信已绑定其他账号 |

## 会员等级

| 等级 | 获得方式 |
|---|---|
| `VIP` | 建档注册即自动获得（两条路径都是） |
| `SVIP` | 累计付费额度达到 `MEMBERSHIP_SVIP_THRESHOLD_MINOR`（默认 `59900`，即 ¥599）自动升级 |

- 手动登记金额与线上支付金额都计入 `user_account.membership_credit_minor`，手动路径用户同样能升 SVIP。
- 幂等锚点是 `payment_record.membership_credit_minor`：同一笔付款重复计入只生效一次。
- 阈值属于应用配置，历史手动付款在 V9 迁移中只回填累计额度不推断等级；读取会员信息时会把「额度已达标但等级未跟上」的账号对齐。
- 已升级的账号不会因为调高阈值而降级。
- SVIP 的「查看感兴趣用户」权益本期只建等级，不实现查看功能。

## 本机 Java 构建

要求 JDK 25 与 Maven 3.9.11。仓库包含 Maven Wrapper：

```bash
./mvnw -pl services/platform-api test
./mvnw -pl services/platform-api package -DskipTests
```

测试会通过 Testcontainers 启动临时 PostgreSQL 18 和 Redis 8，不读取 `.env` 中的真实凭据。

## 管理后台（apps/admin-web）

Vue 3 + TypeScript + Vite + Element Plus，位于 `apps/admin-web`：

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

管理后台功能：管理员登录/退出、工作台六项统计（待审核、今日登记、今日审核、累计建档、VIP 会员、SVIP 会员）、访客登记与补发初始凭证、档案字段配置、审核列表/详情/通过/退回（含照片预览与字段差异）。

## 嘉宾端（apps/guest-app）

uni-app（Vue 3 + TypeScript），第一期发布 H5，后续适配微信小程序：

```bash
cd apps/guest-app
npm install          # 项目含 .npmrc（legacy-peer-deps），按配置安装即可
npm run dev:h5       # 开发：/api 代理到 http://localhost:8080
npx vitest run --config vitest.config.ts
npm run build:h5     # 产物 dist/build/h5
```

部署：H5 静态托管 + Nginx 同源反代 `/api`，并确保后端 `BROWSER_ALLOWED_ORIGINS` 包含 H5 域名（浏览器 Cookie 写请求的可信来源校验）。

嘉宾端功能：激活登录、授权书查看与主动同意、动态档案表单（核心 + 自定义字段）、头像/生活照上传与删除、保存草稿、幂等提交（`Idempotency-Key`）、状态查看与退回修改重提、个人中心（授权有效期、重新授权、退出）。

线上建档入口为 `pages/payment/index`：展示授权书 → 勾选同意 → 跳转公众号网页授权 → 回跳后下单并调起微信支付 → 支付成功进入 `pages/register/index` 填手机号与密码 → 建账号后进入既有授权书与档案流程。注册令牌只放在会话存储、不进地址栏；在微信外打开会给出兜底提示，支付中断可用订单号恢复。该入口需要在微信内访问，且 `WECHAT_OAUTH_REDIRECT_URI` 要指向部署后的支付页地址。

## 安全约束

- 数据库不保存或记录明文密码、初始凭证、手机号、OpenID 或 UnionID。
- 持久化实体不使用 Lombok `@Data` 或类级 `@ToString`，避免敏感字段进入日志；密文字节数组继续防御性复制。
- 初始凭证仅在管理员创建访客账号的响应中出现一次，库内只保存 Argon2id 摘要。
- 手机号使用 AES-256-GCM 随机加密，并以独立 HMAC 密钥生成查询索引。
- 微信号、抖音号、抖音昵称和主页链接使用独立于手机号的 AES-256-GCM 密钥加密，只对确需精确查询的标识生成 HMAC。
- 档案提交版本与审核记录在数据库层不可变；审核退回保留最后已通过版本，不覆盖历史审核结果。
- 提交接口必须携带 `Idempotency-Key`，键与请求内容摘要绑定，避免重复提交产生重复版本。
- 审计记录在数据库层禁止更新和删除。
- Flyway 使用 `archive_owner`，业务进程使用无 DDL 权限的 `archive_app`；运行账号对审计表只有查询和追加权限。
- 首管理员初始化默认关闭；开启时拒绝空白、过短和常见默认密码。
- 管理员登录、访客登录和激活在执行 Argon2id 前先使用 Redis 做账号与客户端双维度限流。
- 补发激活凭证会立即作废旧凭证；管理端当前应禁用重复提交，后续增加 `Idempotency-Key` 后再支持安全网络重试。
- 微信支付商户私钥、API v3 密钥与 AppSecret 只存服务端环境变量，绝不下发前端；下发前端的只有公众号 AppID 与后端签名后的调起支付参数。
- 支付回调按微信支付要求返回 `{"code":"SUCCESS"}`，验签失败返回 401，结算冲突返回 500 让微信重试；不向回调方泄露内部细节。
- 线上必须通过 HTTPS 提供接口，并妥善保管 `.env` 或由密钥管理服务注入环境变量。

## 后续扩展

线上注册已把公众号 openid 写入 `external_identity`（`provider = WECHAT`）。下一阶段在此基础上增加微信一键登录：用 `code2Session` 换 openid 后按 `subject_hmac` 命中账号即可建立会话；把已有账号首次绑定微信时，必须再次验证现有密码、初始凭证或管理员授权，不能仅凭微信手机号覆盖既有账号。

其他待接入项：SVIP 的「查看感兴趣用户」权益、退款回调与订单关闭、过期注册令牌与未支付订单的定时清理、授权书与信息分层改造。
