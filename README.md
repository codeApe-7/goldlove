# 婚恋智能档案库

当前仓库已实现第一阶段后端身份闭环：管理员登录、管理员登记已付费访客、一次性初始凭证激活、访客登录/会话查询/退出。对象存储、微信网络调用、档案内容、审核工作流和 AI 能力暂未接入，因此当前不需要任何云厂商 Key。

## 已选技术栈

- Java 25、Spring Boot 4.1、Spring Modulith
- Spring MVC、Sa-Token（不使用 Spring Security）
- MyBatis-Plus 3.5.17：单表 CRUD/Wrapper；不使用 Mapper XML
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

将两次生成的不同值分别填入 `.env` 的 `PHONE_ENCRYPTION_KEY` 与 `PHONE_SEARCH_KEY`，并替换 PostgreSQL、Redis、初始管理员密码。随后启动：

```bash
docker compose up --build
```

本地 HTTP 开发允许把 `AUTH_COOKIE_SECURE` 设为 `false`；线上 HTTPS 必须设为 `true`。`BROWSER_ALLOWED_ORIGINS` 必须填写实际 H5 域名，多个来源用英文逗号分隔。

健康检查地址为 `GET http://localhost:8080/actuator/health`。

## API

| 方法 | 路径 | 认证 | 用途 |
|---|---|---|---|
| POST | `/api/v1/admin/auth/login` | 无 | 管理员登录 |
| POST | `/api/v1/admin/accounts` | 管理员 | 登记已付款访客并仅本次返回初始凭证 |
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
- 初始凭证仅在管理员创建访客账号的响应中出现一次，库内只保存 Argon2id 摘要。
- 手机号使用 AES-256-GCM 随机加密，并以独立 HMAC 密钥生成查询索引。
- 审计记录在数据库层禁止更新和删除。
- 首管理员初始化默认关闭；开启时拒绝空白、过短和常见默认密码。
- 线上必须通过 HTTPS 提供接口，并妥善保管 `.env` 或由密钥管理服务注入环境变量。

## 后续扩展

下一阶段可在现有 `external_identity` 表和 `guest` 登录体系上增加微信 `code2Session` 适配器。首次绑定必须再次验证现有密码、初始凭证或管理员授权；不能仅凭微信手机号覆盖既有账号。对象存储接入时再提供独立的服务端访问凭据和私有桶配置，不把 Key 下发到 H5 或小程序。
