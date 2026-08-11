# 嘉宾 Bearer 认证与照片延迟落库设计

## 背景

当前嘉宾端使用 Cookie 会话，自定义来源过滤器会校验 `Origin` / `Referer`。照片上传接口同时要求档案已经存在，并在一个数据库事务内完成账号状态查询、档案状态检查、图片校验、COS 上传和照片元数据落库。COS 网络阻塞时，前端会在 60 秒后超时，但后端 SDK 仍可能继续重试，并长期占用数据库事务。

本设计简化嘉宾认证和照片保存语义：嘉宾端改用 Bearer Token；选择图片时只上传 COS；只有用户点击“保存草稿”或“提交档案”时才创建或更新档案及照片记录。

## 已确认目标

- 嘉宾端使用 `Authorization: Bearer <token>`，不再依赖 Cookie。
- 管理后台认证保持现状，继续使用 HttpOnly Cookie 和来源保护。
- 登录校验和账号启用状态统一在嘉宾请求入口执行一次。
- 账号被停用后，已有嘉宾会话在下一次请求时立即失效。
- 上传图片时不创建 `guest_profile`，也不写 `profile_photo`。
- `guest_profile` 只在用户点击保存草稿或提交档案触发的保存步骤中创建。
- 前端暂存 COS 返回的 `objectKey`；保存时把当前完整照片集合与档案字段一起提交。
- 不引入临时凭证、`uploadId`、Redis 暂存表或临时图片数据库表。
- 照片表只保留档案业务实际需要的关联、类别、对象路径和排序字段。
- COS 网络调用不进入数据库事务，并在前端超时前返回明确结果。

## 不在本阶段处理

- 管理后台 Bearer Token 改造。
- COS 客户端直传或 STS 临时密钥。
- 未保存 COS 对象的自动清理。
- 图片缩略图、水印、裁剪和内容审核。
- 多端同步未保存的本地编辑状态。

## 认证架构

### 嘉宾登录和激活

嘉宾登录和激活成功后，后端创建 Sa-Token 会话，并返回：

```json
{
  "accountId": 1001,
  "status": "ACTIVE",
  "accessToken": "token-value",
  "expiresIn": 2592000
}
```

激活成功与登录成功保持一致，都会创建可立即使用的嘉宾会话。

嘉宾 Sa-Token 逻辑配置为：

- 请求头名称：`Authorization`
- 前缀：`Bearer`
- 读取请求头：开启
- 读取 Cookie：关闭
- Token 和活跃超时沿用当前全局配置

管理员 Sa-Token 逻辑保持现有 Cookie 配置，不受本次改造影响。

### 前端会话

嘉宾前端沿用现有 `sessionStorage` 适配器，但会话对象增加 `accessToken` 和 `expiresIn`。普通 API 请求和 `uni.uploadFile` 都由统一请求封装添加 Bearer 请求头。

收到以下认证错误时，前端清除会话并跳转登录页：

- `401` 未登录、Token 无效或过期。
- `403` 且错误码为 `AUTH_ACCOUNT_INACTIVE`。

退出登录时，后端注销当前 Token，前端无论请求是否成功都清除本地会话。

### 统一账号状态校验

嘉宾全局拦截器对 `/api/v1/guest/**` 执行：

1. 校验 Bearer Token。
2. 取得当前 `accountId`。
3. 查询一次 `user_account.status`，必须为 `ACTIVE`。
4. 若账号已停用，注销当前 Token 并返回 `AUTH_ACCOUNT_INACTIVE`。

登录和激活接口继续排除在拦截器之外。业务服务删除重复的 `GuestAccountStatusQuery.requireActive(accountId)` 调用，只保留业务状态、归属和数据完整性校验。

嘉宾请求不再携带认证 Cookie，因此 `BrowserOriginProtectionFilter` 不再处理嘉宾请求；该过滤器继续保护管理后台的 Cookie 写请求。

## 照片业务语义

照片仍然是档案聚合的一部分：

```text
guest_profile
└── profile_photo
    ├── AVATAR：最多 1 张
    └── LIFE：最多 6 张
```

选择图片和保存档案是两个明确阶段：

1. 选择图片时，后端完成图片校验并上传 COS，只返回对象引用。
2. 前端在当前编辑状态中暂存对象引用并展示预览。
3. 用户点击保存草稿时，才创建或更新 `guest_profile`，并同步 `profile_photo`。
4. 用户点击提交档案时，前端先完成同一保存步骤，成功后再调用提交审核接口。

上传动作不会自动创建空档案。未保存的图片不会产生任何数据库记录。

## 数据模型

### `profile_photo`

保留字段：

| 字段 | 说明 |
|---|---|
| `id` | 主键 |
| `guest_profile_id` | 所属档案 |
| `category` | `AVATAR` / `LIFE` |
| `object_key` | COS 对象路径 |
| `sort_order` | 同类别排序 |
| `created_at` / `updated_at` | 时间戳 |

删除字段：

- `sha256`
- `size_bytes`
- `content_type`
- `width`
- `height`

继续保留：

- `UNIQUE (guest_profile_id, category, sort_order)`。
- 每个档案最多一个 `AVATAR` 的部分唯一索引。
- `object_key` 全局唯一约束。

### `profile_revision_photo`

保留字段：

| 字段 | 说明 |
|---|---|
| `id` | 主键 |
| `profile_revision_id` | 所属不可变版本 |
| `category` | `AVATAR` / `LIFE` |
| `object_key` | COS 对象路径快照 |
| `sort_order` | 提交时排序 |
| `created_at` | 创建时间 |

同样删除摘要、大小、格式、宽高字段。审核版本摘要改为使用 `category + objectKey + sortOrder`，对象键包含随机 UUID 且上传后不覆盖，因此能够标识具体图片版本。

数据库变更通过新的 Flyway 迁移完成，不修改已经执行过的 V5。迁移只删除技术元数据列，保留现有照片对象路径和档案/版本关联。

## COS 对象路径

对象键只能由后端生成：

```text
profiles/{accountId}/avatar/{uuid}.{jpg|png|webp}
profiles/{accountId}/life/{uuid}.{jpg|png|webp}
```

COS 存储桶保持私有。`objectKey` 不是访问凭证；图片展示继续使用短时签名 URL。

保存档案时后端只接受严格匹配当前账号、类别、UUID 和扩展名的对象键。跨账号路径、路径穿越、任意前缀或类别不匹配均返回 `PHOTO_REFERENCE_INVALID`。

为了保持保存链路简单且不再次受 COS 网络影响，保存时不调用 COS `HEAD` / `exists`。正常前端只会提交上传接口成功返回的对象键。恶意客户端伪造当前账号前缀下不存在的 UUID，最多造成其本人档案中的无效图片引用，不会获得或引用其他账号的对象。

## API 设计

### 上传到 COS

```http
POST /api/v1/guest/profile/photo-uploads
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

请求字段：

- `file`
- `category`：`AVATAR` / `LIFE`

响应：

```json
{
  "objectKey": "profiles/1001/avatar/uuid.jpg",
  "category": "AVATAR",
  "previewUrl": "https://signed-preview-url"
}
```

接口职责：

1. 校验文件非空且不超过 10 MiB。
2. 通过真实内容嗅探和解码确认 JPEG、PNG 或 WebP。
3. 生成当前账号前缀下的随机对象键。
4. 上传 COS。
5. 返回对象键和短时预览 URL。

接口不读取档案、不检查档案编辑状态或照片数量，也不写数据库。编辑状态和数量在保存时统一校验。

旧的 `POST /api/v1/guest/profile/photos` 持久化上传语义被替换，不保留兼容入口。

### 查询已保存照片

`GET /api/v1/guest/profile/photos` 继续存在，只返回已经写入 `profile_photo` 的照片。响应包含 `id`、`category`、`objectKey`、`sortOrder`、`previewUrl` 和 `createdAt`。

删除 `DELETE /api/v1/guest/profile/photos/{photoId}` 接口。新增、替换、排序和移除都随完整档案保存统一提交，避免用户尚未点击保存时数据库已经变化，也避免同一照片集合存在两套写入语义。

### 保存档案

`PUT /api/v1/guest/profile/draft` 的请求新增照片集合：

```json
{
  "expectedVersion": 3,
  "gender": "FEMALE",
  "wechatId": "wechat_8888",
  "dynamicFields": [],
  "photos": {
    "avatar": "profiles/1001/avatar/uuid.jpg",
    "life": [
      "profiles/1001/life/uuid-1.jpg",
      "profiles/1001/life/uuid-2.jpg"
    ]
  }
}
```

`photos` 表示保存后的完整目标集合：

- `avatar` 为 `null` 或一个对象键。
- `life` 为有序对象键数组，最多 6 项。
- 重复对象键返回 `PHOTO_REFERENCE_INVALID`。
- 类别必须与路径中的 `avatar` / `life` 一致。

保存流程位于一个短数据库事务中：

1. 创建或更新 `guest_profile`。
2. 校验档案处于 `DRAFT`、`CHANGES_REQUESTED` 或 `APPROVED`。
3. 对比当前 `profile_photo` 与目标集合。
4. 删除已移除的照片行。
5. 插入新照片行。
6. 更新生活照排序。
7. 提交事务。

事务提交后，对于已从档案移除且没有被任何 `profile_revision_photo` 引用的对象，后端尽力删除 COS 对象。删除失败只留下不可访问的孤儿对象，不回滚已经成功的档案保存。

保存响应返回最新档案版本。保存成功后，前端调用 `GET /api/v1/guest/profile/photos` 刷新已经持久化的照片集合和短时预览 URL，再用结果替换本地待保存状态。预览 URL 在保存事务之外生成。

### 提交审核

用户点击“提交档案”时，前端顺序执行：

```text
PUT /guest/profile/draft
    ↓ 成功
POST /guest/profile/submissions
```

保存失败时不得继续提交。提交接口继续使用 `Idempotency-Key`，并从已经持久化的 `profile_photo` 创建不可变照片快照。

## 前端状态

档案页面把照片分为两种来源，但使用统一展示模型：

- 已保存照片：来自 `GET /guest/profile/photos`。
- 新上传照片：来自 `POST /guest/profile/photo-uploads`。

选择新图片后立即调用上传接口；成功后把返回的 `objectKey` 加入本地档案编辑状态。用户移除照片时只修改本地目标集合，直到保存成功前不修改数据库。

未保存状态仅存在当前页面内存中。刷新页面或关闭页面会丢弃未保存编辑状态，但已上传 COS 对象仍会保留。这是本阶段接受的简化取舍。

## 超时和事务边界

腾讯 COS 客户端统一配置：

- 连接超时：5 秒。
- Socket 读取超时：20 秒。
- 单次请求总超时：30 秒并启用总超时。
- 最大自动重试：0。

嘉宾前端上传上限继续为 60 秒，因此对象存储失败会先由后端返回，不再出现前端超时后后端继续重试数分钟的情况。

COS 上传接口不启动数据库事务。档案保存事务不调用 COS。COS 删除只在事务提交后执行。

## 错误处理

| 错误码 | 场景 |
|---|---|
| `AUTH_ACCOUNT_INACTIVE` | 账号已停用，注销当前 Token |
| `PHOTO_CONTENT_INVALID` | 文件为空或无法读取 |
| `PHOTO_TOO_LARGE` | 文件超过 10 MiB |
| `PHOTO_FORMAT_UNSUPPORTED` | 不是可解码的 JPEG、PNG、WebP |
| `OBJECT_STORAGE_OPERATION_FAILED` | COS 上传超时或失败 |
| `PHOTO_REFERENCE_INVALID` | 对象键非法、跨账号、类别不符或重复 |
| `PHOTO_COUNT_LIMIT_EXCEEDED` | 头像或生活照数量超过限制 |
| `PHOTO_NOT_EDITABLE` | 当前档案状态不允许保存修改 |

上传失败时前端保留本地文件预览并允许重新上传。保存失败时保留已经成功上传的对象引用和当前表单，允许直接重试。

## 安全边界

- 认证身份只来自 Bearer Token，不接受请求体中的账号 ID。
- 每个嘉宾请求统一检查账号 `ACTIVE` 状态。
- 对象键由后端生成，保存时严格限制为当前账号前缀。
- COS 桶保持私有，客户端拿到对象键不能直接读取文件。
- 预览使用短时签名 URL，不持久化 URL。
- 图片真实格式和大小仍在服务端校验。
- 管理后台 Cookie 与来源保护不变。

## 测试策略

### 后端认证

- 登录和激活返回 Bearer Token。
- 无 Token、错误 Token、过期 Token 返回 401。
- 正常 Token 可以访问嘉宾接口。
- 账号停用后，原 Token 下一次请求立即返回 `AUTH_ACCOUNT_INACTIVE`。
- 管理后台 Cookie 登录不受影响。

### 照片上传

- 合法 JPEG、PNG、WebP 上传成功并返回账号前缀对象键。
- 空文件、超大文件、伪格式和损坏图片被拒绝。
- COS 客户端失败映射为 `OBJECT_STORAGE_OPERATION_FAILED`。
- 上传成功后 `guest_profile` 和 `profile_photo` 行数不变。

### 保存和编辑

- 首次保存时才创建 `guest_profile`。
- 保存头像和生活照后创建精简的 `profile_photo` 行。
- 再次保存支持替换头像、新增、排序和移除生活照。
- 跨账号对象键、非法路径、重复对象键和超数量被拒绝。
- 保存事务不调用 `ObjectStorageService`；无历史引用对象的删除只允许在事务提交后执行。
- 移除已进入历史版本的照片时保留 COS 对象。

### 提交和审核

- 点击提交时先保存，保存失败不调用提交接口。
- 缺少头像时提交仍返回 `PROFILE_VALIDATION_FAILED`。
- 提交成功后按类别、对象键和排序创建不可变照片快照。
- 嘉宾版本详情与管理员审核详情可以正常生成签名预览 URL。
- 版本摘要包含照片类别、对象键和排序，换图会改变摘要。

### 前端

- 普通请求和上传请求都携带 Bearer Token。
- 401 和账号停用错误清除会话。
- 图片上传成功只更新本地编辑状态。
- 保存成功后使用保存响应刷新档案版本，再通过 `GET /guest/profile/photos` 刷新照片状态。
- 未保存时移除图片不会调用数据库删除接口。
- 提交按钮严格按“保存 → 提交”顺序执行。

## 已接受取舍

- 未保存的 COS 图片可能成为孤儿对象，本阶段不自动清理。
- 保存时不再次查询 COS；伪造当前账号前缀下不存在的对象键只会影响该账号自身的档案展示。
- `sessionStorage` 中的 Bearer Token 可被同源 XSS 读取；前端不得使用未净化的动态 HTML，并应保持依赖安全更新。
- 未保存的本地编辑状态不跨刷新或新标签页恢复。
