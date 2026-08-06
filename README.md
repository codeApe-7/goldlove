# 婚恋智能档案库

当前仓库已实现第一阶段后端身份闭环：管理员登录、管理员登记已付费访客、一次性初始凭证激活、访客登录/会话查询/退出。对象存储、微信网络调用、档案内容、审核工作流和 AI 能力暂未接入，因此当前不需要任何云厂商 Key。

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
```

将两次生成的不同值分别填入 `.env` 的 `PHONE_ENCRYPTION_KEY` 与 `PHONE_SEARCH_KEY`，并替换 PostgreSQL 所有者、应用运行账号、Redis 和初始管理员密码。只有首次需要创建管理员时才把 `ADMIN_BOOTSTRAP_ENABLED` 改为 `true`；创建成功后立即恢复为 `false`。随后启动：

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
| POST | `/api/v1/guest/auth/activate` | 无 | 使用手机号、初始凭证设置正式密码 |
| POST | `/api/v1/guest/auth/login` | 无 | 访客登录 |
| GET | `/api/v1/guest/auth/me` | 访客 | 查询当前会话 |
| POST | `/api/v1/guest/auth/logout` | 访客 | 退出登录 |

所有响应统一包含 `success`、`code`、`message`、`data` 和 `requestId`。客户端可以传入安全格式的 `X-Request-ID`，否则服务端自动生成。

管理员和访客分别使用 `archive-token-admin`、`archive-token-guest`，即便主键数值相同也不会共享会话。浏览器 Cookie 默认启用 `Secure`、`HttpOnly`、`SameSite=Lax`。携带 Cookie 的写请求必须提供可信 `Origin` 或 `Referer`；未来微信小程序使用请求头令牌，不受浏览器来源校验影响，但仍复用同一套 Sa-Token 会话与账号状态校验。

## 本机 Java 构建

要求 JDK 25 与 Maven 3.9.11。仓库包含 Maven Wrapper：

```bash
./mvnw -pl services/platform-api test
./mvnw -pl services/platform-api package -DskipTests
```

测试会通过 Testcontainers 启动临时 PostgreSQL 18 和 Redis 8，不读取 `.env` 中的真实凭据。

## 安全约束

- 数据库不保存或记录明文密码、初始凭证、手机号、OpenID 或 UnionID。
- 持久化实体不使用 Lombok `@Data` 或类级 `@ToString`，避免敏感字段进入日志；密文字节数组继续防御性复制。
- 初始凭证仅在管理员创建访客账号的响应中出现一次，库内只保存 Argon2id 摘要。
- 手机号使用 AES-256-GCM 随机加密，并以独立 HMAC 密钥生成查询索引。
- 审计记录在数据库层禁止更新和删除。
- Flyway 使用 `archive_owner`，业务进程使用无 DDL 权限的 `archive_app`；运行账号对审计表只有查询和追加权限。
- 首管理员初始化默认关闭；开启时拒绝空白、过短和常见默认密码。
- 管理员登录、访客登录和激活在执行 Argon2id 前先使用 Redis 做账号与客户端双维度限流。
- 补发激活凭证会立即作废旧凭证；管理端当前应禁用重复提交，后续增加 `Idempotency-Key` 后再支持安全网络重试。
- 线上必须通过 HTTPS 提供接口，并妥善保管 `.env` 或由密钥管理服务注入环境变量。

## 后续扩展

下一阶段可在现有 `external_identity` 表和 `guest` 登录体系上增加微信 `code2Session` 适配器。首次绑定必须再次验证现有密码、初始凭证或管理员授权；不能仅凭微信手机号覆盖既有账号。对象存储接入时再提供独立的服务端访问凭据和私有桶配置，不把 Key 下发到 H5 或小程序。
