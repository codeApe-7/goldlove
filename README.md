# 婚恋智能档案库

当前仓库已实现后端身份与建档审核闭环：管理员登录、登记已付费访客、一次性初始凭证激活、访客登录/会话查询/退出、付费前授权书展示与主动同意、档案草稿保存、不可变提交与幂等重试、管理员审核通过与退回，并已接入腾讯云 COS 对象存储（照片字段的上传闭环待接入）。微信网络调用和 AI 能力暂未接入，因此只需本地生成档案敏感字段加解密密钥与腾讯云 COS 凭据。

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
| POST | `/api/v1/guest/auth/activate` | 无 | 使用手机号、初始凭证设置正式密码 |
| POST | `/api/v1/guest/auth/login` | 无 | 访客登录 |
| GET | `/api/v1/guest/auth/me` | 访客 | 查询当前会话 |
| POST | `/api/v1/guest/auth/logout` | 访客 | 退出登录 |

所有响应统一包含 `success`、`code`、`message`、`data` 和 `requestId`。客户端可以传入安全格式的 `X-Request-ID`，否则服务端自动生成。

管理员和访客分别使用 `archive-token-admin`、`archive-token-guest`，即便主键数值相同也不会共享会话。浏览器 Cookie 默认启用 `Secure`、`HttpOnly`、`SameSite=Lax`。携带 Cookie 的写请求必须提供可信 `Origin` 或 `Referer`；未来微信小程序使用请求头令牌，不受浏览器来源校验影响，但仍复用同一套 Sa-Token 会话与账号状态校验。

## 建档与审核流程

1. 管理员登记已付款访客时，系统会把付款记录绑定到创建访客时传入的授权书版本（`authorizationDocumentVersion`），付款前展示给用户的是同一份授权书。
2. 访客激活后主动同意该版本授权书（一年有效期），然后保存档案草稿；微信号、抖音号、抖音昵称和主页链接以 AES-256-GCM 随机加密存储，查询索引和幂等键只保存 HMAC。
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

管理后台功能：管理员登录/退出、工作台四项统计（待审核、今日登记、今日审核、累计建档）、访客登记与补发初始凭证、档案字段配置、审核列表/详情/通过/退回（含照片预览与字段差异）。

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
- 线上必须通过 HTTPS 提供接口，并妥善保管 `.env` 或由密钥管理服务注入环境变量。

## 后续扩展

下一阶段可在现有 `external_identity` 表和 `guest` 登录体系上增加微信 `code2Session` 适配器。首次绑定必须再次验证现有密码、初始凭证或管理员授权；不能仅凭微信手机号覆盖既有账号。
