# 照片上传闭环设计

## 背景与目标

后端已具备身份闭环、建档审核闭环与腾讯云 COS 对象存储（`storage` 模块）。本子项目把照片字段接入对象存储，形成完整上传闭环：嘉宾上传头像与生活照、照片随草稿保存、随提交固化进不可变版本快照、管理员审核与嘉宾本人均可见。照片元数据落库，真实图片内容只存在于 COS 私有桶，对象存储 Key 永不进入客户端包体。

## 已确认的业务约束

- 照片类别：`AVATAR`（头像，每人最多 1 张）、`LIFE`（生活照，每人最多 6 张）。
- 单张大小 ≤ 10 MiB；仅允许 JPEG、PNG、WebP。
- 服务端嗅探并解码校验真实图片格式，不信任客户端声明的 `Content-Type`；解码后最小尺寸 64×64，拒绝损坏或空图。
- 提交建档时头像必填、生活照可选；头像缺失时提交返回 `PROFILE_VALIDATION_FAILED` 并列出 `avatar`。
- 草稿阶段删除照片立即删除 COS 对象；照片一旦进入版本快照，即使后续被替换或删除，对象仍保留以支持历史追溯；账号注销或授权到期后的批量清理列为后续任务。
- 上传通道采用服务端中继：multipart → 后端校验 → `ObjectStorageService` 写入 COS（复用方案，见下）。
- 展示地址一律使用短时签名 URL（15 分钟），URL 不落库、不下发长链接。
- 本阶段不做缩略图、图片处理、水印、客户端直传或 STS。

## 架构与模块边界

- `storage` 模块保持不变：`ObjectStorageService` 继续负责对象上传、存在性查询、签名下载 URL 与删除；照片按精确对象键操作，不新增批量接口。
- `guest` 模块新增照片业务：`ProfilePhotoService` 负责上传校验、草稿照片列表、删除，以及照片归属校验。
- `review` 模块改造提交与详情链路：提交时校验头像必填并把草稿照片快照固化进 `profile_revision_photo`；审核详情与嘉宾版本详情返回照片元数据与签名 URL。
- `guest` 与 `review` 继续通过既有公开应用接口协作；`storage` 模块保持 CLOSED，仅依赖 `common::web`。

## 数据模型（Flyway V5）

### `profile_photo`（草稿照片）

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT IDENTITY PK | |
| `guest_profile_id` | BIGINT NOT NULL FK → `guest_profile(id)` | 归属档案 |
| `category` | VARCHAR(16) NOT NULL | `AVATAR` / `LIFE` |
| `object_key` | VARCHAR(1024) NOT NULL | COS 对象键，全局唯一 |
| `sha256` | CHAR(64) NOT NULL | 内容摘要 |
| `size_bytes` | BIGINT NOT NULL | |
| `content_type` | VARCHAR(255) NOT NULL | 服务端按真实格式设定 |
| `width` / `height` | INTEGER NOT NULL | 解码得到的像素尺寸 |
| `sort_order` | INTEGER NOT NULL | 同类别内排序，头像恒为 0 |
| `created_at` / `updated_at` | TIMESTAMPTZ NOT NULL | |

约束：

- `UNIQUE (guest_profile_id, category, sort_order)`。
- `UNIQUE (guest_profile_id) WHERE category = 'AVATAR'`（部分唯一索引，数据库兜底头像最多 1 张）。
- `CHECK (category IN ('AVATAR','LIFE'))`。

### `profile_revision_photo`（不可变版本快照）

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT IDENTITY PK | |
| `profile_revision_id` | BIGINT NOT NULL FK → `profile_revision(id)` | |
| `category` | VARCHAR(16) NOT NULL | |
| `object_key` | VARCHAR(1024) NOT NULL | 提交时草稿照片的对象键 |
| `sha256` / `size_bytes` / `content_type` / `width` / `height` / `sort_order` | 同草稿表 | 提交时固化的元数据 |
| `created_at` | TIMESTAMPTZ NOT NULL | |

约束：

- `UNIQUE (profile_revision_id, category, sort_order)`。
- `CHECK (category IN ('AVATAR','LIFE'))`。
- 触发器禁止 `UPDATE` 与 `DELETE`（与 `profile_revision_field_value` 一致，保证不可变）。

权限：`archive_app` 对 `profile_photo` 拥有 `SELECT, INSERT, UPDATE, DELETE`；对 `profile_revision_photo` 拥有 `SELECT, INSERT`；两表序列授予 `USAGE, SELECT`。

## 服务设计

### `ProfilePhotoService`（guest 模块）

输入均从 Sa-Token 会话取得 `accountId`，禁止客户端传入或覆盖。

- `upload(accountId, category, file)`：
  1. `GuestAccountStatusQuery.requireActive(accountId)`。
  2. 校验草稿可编辑状态：`DRAFT` / `CHANGES_REQUESTED` / `APPROVED`；`PENDING_REVIEW` 返回 `PHOTO_NOT_EDITABLE`。
  3. 校验 `category` 合法；读取并校验文件：大小 ≤ 10 MiB、magic bytes 与解码校验（JPEG/PNG/WebP、最小 64×64）。
  4. 校验数量上限：`AVATAR` 已有则返回 `PHOTO_COUNT_LIMIT_EXCEEDED`；`LIFE` 已有 6 张则同样拒绝。
  5. 生成对象键 `profiles/{accountId}/{category小写}/{uuid}.{扩展名}`；`AVATAR` 的 `sort_order` 恒为 0，`LIFE` 追加为当前最大排序 + 1。调用 `ObjectStorageService.put`。
  6. 插入 `profile_photo` 行；落库失败时删除已上传对象并抛出异常，保证不留孤儿对象。
  7. 返回 `ProfilePhotoView`（含元数据与 15 分钟签名 URL）。
- `list(accountId)`：返回该档案全部草稿照片（按类别、sort_order 排序），每项附带 15 分钟签名 URL。
- `delete(accountId, photoId)`：
  1. 校验归属（`guest_profile.user_account_id = accountId`）与可编辑状态；不存在或跨账号一律返回 `PHOTO_NOT_FOUND`。
  2. 删除 `profile_photo` 行。
  3. 若该 `object_key` 已被任何 `profile_revision_photo` 引用则保留对象；否则调用 `ObjectStorageService.delete`。

### 提交链路改造（review 模块）

- `GuestProfileSnapshotProvider.lockAndValidate`：在校验必填字段时增加“至少一张 `AVATAR` 照片”，缺失返回 `PROFILE_VALIDATION_FAILED`（missing 列表含 `avatar`）。
- `ProfileSubmissionService.submit`：同一事务内，把 `profile_photo` 当前行按类别/排序快照复制进 `profile_revision_photo`；任何一步失败整体回滚（已插入快照不残留）。
- `ProfileRevisionView` 与 `ProfileReviewDetail` 增加 `photos` 数组（类别、对象键、摘要、大小、类型、宽高、排序 + 15 分钟签名 URL）。
- 嘉宾版本详情与管理员审核详情均只返回调用者有权查看的数据：嘉宾详情按账号归属过滤；管理员详情展示该版本固化的照片。

## 接口

### 嘉宾

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/v1/guest/profile/photos` | multipart：`file` + `category`；201 返回照片视图 |
| `GET` | `/api/v1/guest/profile/photos` | 草稿照片列表（含短时签名 URL） |
| `DELETE` | `/api/v1/guest/profile/photos/{photoId}` | 删除草稿照片；未进入快照则同步删除对象 |

### 审核与版本详情（改造）

- `GET /api/v1/admin/profile-reviews/{revisionId}`：响应新增 `photos`。
- `GET /api/v1/guest/profile/revisions/{revisionId}`：响应新增 `photos`。

## 校验与安全

- 格式校验：先比对 magic bytes（JPEG `FF D8 FF`；PNG `89 50 4E 47 0D 0A 1A 0A`；WebP `RIFF .... WEBP`），再用 `ImageIO.read` 解码并读取宽高；解码失败或尺寸 < 64×64 返回 `PHOTO_FORMAT_UNSUPPORTED`。
- 缺失或空文件返回 `PHOTO_CONTENT_INVALID`；multipart 请求体超过 10 MiB 由全局异常映射为 `PHOTO_TOO_LARGE`（`application.yml` 配置 `spring.servlet.multipart.max-file-size` 与 `max-request-size` 为 10MB，`GlobalExceptionHandler` 增加 `MaxUploadSizeExceededException` 处理器）。
- 落库 `content_type` 由服务端按真实格式设定（`image/jpeg` / `image/png` / `image/webp`），不采用客户端声明值。
- 对象键仅由服务端生成，包含 `{accountId}` 与随机 `uuid`；`ObjectStorageService` 既有键白名单规则继续生效。
- 所有照片查询与删除带 `guest_profile.user_account_id = accountId` 条件；跨账号与不存在统一返回 `PHOTO_NOT_FOUND`，不泄露存在性。
- 签名 URL 有效期 15 分钟，仅按需签发，不持久化。
- 照片对象键、签名 URL 与图片内容不进入任何日志；沿用 `SensitiveDataGuardTest` 的敏感字段约束（`wechat/douyin/clientIp/sessionReference` 模式扩展至照片对象键不在日志参数中出现）。

## 错误码

| 错误码 | HTTP | 场景 |
|---|---|---|
| `PHOTO_CATEGORY_INVALID` | 400 | 类别不在 `AVATAR`/`LIFE` |
| `PHOTO_CONTENT_INVALID` | 400 | 缺失或空文件 |
| `PHOTO_FORMAT_UNSUPPORTED` | 400 | 非 JPEG/PNG/WebP、损坏、小于 64×64 |
| `PHOTO_TOO_LARGE` | 413 | 单张超过 10 MiB |
| `PHOTO_COUNT_LIMIT_EXCEEDED` | 409 | 头像已有 1 张或生活照已有 6 张 |
| `PHOTO_NOT_FOUND` | 404 | 照片不存在或不属于当前账号 |
| `PHOTO_NOT_EDITABLE` | 409 | 档案处于 `PENDING_REVIEW` 等不可编辑状态 |
| `PROFILE_VALIDATION_FAILED` | 400 | 提交时缺少头像（missing 含 `avatar`） |
| `OBJECT_STORAGE_NOT_CONFIGURED` | 503 | COS 未配置（沿用现有码） |
| `OBJECT_STORAGE_OPERATION_FAILED` | 502 | 对象存储调用失败（沿用现有码） |

## 测试策略

1. 单元：图片格式嗅探与解码校验（最小合法 JPEG/PNG/WebP 样本、伪装扩展名、损坏字节、超小尺寸）；对象键生成规则。
2. 服务集成（Testcontainers PostgreSQL，`ObjectStorageService` 以 Mockito mock）：头像唯一、生活照数量上限、头像必填提交校验、`PENDING_REVIEW` 拒绝上传/删除、提交时快照固化、删除语义（未快照对象被删 / 已快照对象保留）、跨账号 `PHOTO_NOT_FOUND`、落库失败回滚删除对象。
3. API（MockMvc）：未登录 401；合法上传 201；伪格式/超大/超数量/类别非法；审核中拒绝；删除后列表为空；审核详情与版本详情返回 `photos`。
4. 数据库迁移：V5 建表、部分唯一索引、快照触发器不可变、`archive_app` 权限。
5. 真桶行为继续由 gated 的 `CosLiveSmokeTest` 覆盖，常规套件不依赖云。
6. 架构守卫：`ModularityTest` 验证 `guest`/`review`/`storage` 模块边界；`SensitiveDataGuardTest` 验证照片对象键不进入日志。

## 本阶段不做（后续）

- 缩略图、图片处理、水印与“原始/处理/发布”三前缀隔离。
- 客户端直传 / STS 临时密钥。
- 账号注销与授权到期后的对象批量回收任务。
- 照片在直播与宣传素材中的使用流程。
