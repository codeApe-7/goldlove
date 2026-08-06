# 嘉宾档案、版本审核与最小授权闭环设计

- 日期：2026-08-06
- 状态：待用户审阅
- 依据：《婚恋智能档案库需求初稿》v0.3 与现有技术架构设计
- 范围：档案草稿、动态字段、不可变提交版本、后台审核，以及档案提交所需的最小授权闭环

## 1. 目标

本阶段交付一条不依赖对象存储的后端闭环：嘉宾查看授权书并主动同意，填写和保存结构化档案，提交不可变审核版本；管理员查看待审任务并通过或退回；审核通过前最后一个已通过版本始终不被覆盖。

本阶段不实现照片上传、对象存储、授权撤回和续期、工作人员 RBAC、资料删除、微信小程序登录、AI 或前端页面。当前所有已登录且状态正常的管理员均可执行审核；完整角色和权限在后续管理员治理阶段补充。

## 2. 方案选择

采用“固定核心字段 + 动态扩展字段 + 不可变审核快照”的混合模型。

- 高频筛选和强校验字段保存在固定列中，便于索引、查询和数据库约束。
- 业务新增字段通过字段定义和值表扩展，不需要为每个低频字段修改表结构。
- 每次提交生成一份完整快照；当前草稿与最后已通过版本分离。
- 不采用单一 JSONB 档案，避免字段约束、筛选和版本差异查询失控。
- 不采用全固定列模型，避免字段配置能力退化为频繁数据库迁移。

## 3. 模块边界

### 3.1 `guest` 模块

拥有嘉宾当前草稿和字段定义：

- `guest_profile`
- `profile_field_definition`
- `profile_field_value`

公开应用接口：

- `GuestProfileDraftService`：读取、创建和更新当前账号的草稿。
- `GuestProfileSnapshotProvider`：向 `review` 模块输出经过校验的完整草稿快照。
- `GuestProfileApprovalPort`：由 `review` 模块在审核通过或退回时更新档案指针与状态。

### 3.2 `review` 模块

拥有不可变版本与审核记录：

- `profile_revision`
- `profile_revision_field_value`
- `profile_review_record`

`review` 只能通过 `GuestProfileSnapshotProvider` 取得草稿，通过 `ConsentEligibility` 校验授权，通过 `GuestProfileApprovalPort` 更新档案状态，不直接访问 `guest` 或 `consent` 的 Mapper。

### 3.3 `consent` 模块

拥有授权书与同意证据：

- `authorization_document`
- `authorization_record`

公开应用接口：

- `AuthorizationDocumentQuery`：查询当前生效或指定版本的授权书。
- `ConsentService`：记录嘉宾主动同意。
- `ConsentEligibility`：判断账号对指定档案提交是否具备有效授权。

### 3.4 既有模块协作

- `identity` 提供当前嘉宾账号 ID 和账号状态，不暴露手机号明文。
- `payment` 在新付款记录中保存付款前展示的授权书版本引用。
- `admin` 与 Sa-Token 提供审核人身份。
- `audit` 追加记录授权同意、草稿关键字段修改、提交、通过和退回事件。

## 4. 数据模型

### 4.1 `authorization_document`

授权书版本不可变，字段包括：

- `id`
- `document_code`
- `version`
- `title`
- `content`
- `content_sha256`
- `status`：`DRAFT`、`ACTIVE`、`RETIRED`
- `effective_at`
- `created_by_admin_id`，系统初始化版本可为空
- `created_at`

`document_code + version` 唯一；同一 `document_code` 只允许一个 `ACTIVE` 版本。已被付款或授权记录引用的文案不允许修改或删除，只能创建新版本。

`RETIRED` 只表示该版本不再用于新付款流程，不会破坏已形成的证据链。如果某个已付款记录引用了该版本，对应嘉宾仍可查看并同意该版本；新付款记录只能引用当前 `ACTIVE` 版本。

迁移中写入 PRD v0.3 已确认的组合授权文案作为首个活动版本。上线前仍需要业务和法律审核；后续调整通过新版本完成。

### 4.2 `authorization_record`

每次同意追加一条记录，不覆盖历史：

- `id`
- `user_account_id`
- `authorization_document_id`
- `accepted_at`
- `effective_at`
- `expires_at`，等于服务端接收时间加一年
- `source_page`
- `client_ip_hmac`
- `user_agent_sha256`
- `session_reference_hmac`
- `created_at`

本阶段不增加可变状态列。有效性实时按文档引用、时间范围和未来的撤回记录判断，避免定时任务延迟造成绕过。

### 4.3 `payment_record` 扩展

增加可空字段 `presented_authorization_document_id`，记录付款前向嘉宾展示的授权书版本。历史数据不得被虚假回填；旧记录保持为空并被标记为缺少付款前授权证据。

新建付费账号时必须传入当前活动授权书版本，服务端校验后写入付款记录。档案提交要求付款记录与嘉宾同意记录引用同一授权书；历史空值返回 `PREPAYMENT_AUTHORIZATION_EVIDENCE_MISSING`，后续由独立人工补证流程处理。

### 4.4 `guest_profile`

保存当前可编辑草稿和审核指针：

- 标识：`id`、`profile_no`、`user_account_id`
- 核心字段：`gender`、`birth_date`、`height_cm`、`education`、`occupation`、`income_range`、`city`
- 敏感账号字段：微信号、抖音号、抖音昵称、抖音主页链接的密文；需要精确检索的标识同时保存 HMAC
- 状态：`DRAFT`、`PENDING_REVIEW`、`CHANGES_REQUESTED`、`APPROVED`、`SUSPENDED`、`DELETION_PENDING`、`ANONYMIZED`
- 指针：`pending_revision_id`、`current_approved_revision_id`
- 并发：`version`
- 时间：`created_at`、`updated_at`

手机号仍只属于 `identity.user_account`，不在档案表重复保存。敏感账号字段使用与手机号相同原则的应用层 AES-256-GCM 加密和带域隔离的 HMAC；任何日志、错误信息和指标标签均不得记录明文。

七个核心资料字段在第一期固定为启用且提交必填。字段字典尚未最终确认，因此学历、职业、收入区间和城市先保存经过长度与格式校验的结构化字符串；后续选项字典通过字段定义版本演进，不修改历史快照。

### 4.5 `profile_field_definition`

描述核心字段展示元数据和动态扩展字段：

- `id`
- `field_code`，稳定且唯一
- `label`
- `storage_kind`：`CORE`、`DYNAMIC`
- `data_type`：`TEXT`、`LONG_TEXT`、`INTEGER`、`DECIMAL`、`DATE`、`BOOLEAN`、`SINGLE_OPTION`
- `required`
- `enabled`
- `options_json`
- `sort_order`
- `instructions`
- `version`
- `created_at`、`updated_at`

核心字段的 `field_code`、`storage_kind`、`data_type`、启用状态和必填状态不可通过普通配置接口修改；允许调整标签、说明、排序和合法选项。动态字段允许启停和调整必填状态，但一旦产生值，不允许改变 `field_code` 或 `data_type`。定义只停用不物理删除。

### 4.6 `profile_field_value`

保存当前草稿的动态字段值：

- `id`
- `guest_profile_id`
- `field_definition_id`
- 与类型对应的 `text_value`、`integer_value`、`decimal_value`、`date_value`、`boolean_value`、`option_value`
- `created_at`、`updated_at`

`guest_profile_id + field_definition_id` 唯一。数据库检查约束确保最多一个类型值非空；服务端按字段定义验证实际类型、选项、长度和必填规则。

### 4.7 `profile_revision`

每次提交创建一条不可变固定字段快照：

- `id`
- `guest_profile_id`
- `revision_number`
- 与 `guest_profile` 相同的核心字段和加密敏感账号字段
- `status`：`PENDING`、`APPROVED`、`REJECTED`
- `submitted_by_account_id`
- `submitted_at`
- `review_deadline_at`，等于提交时间加 24 小时
- `reviewed_at`
- `submission_key_hmac`
- `request_payload_sha256`
- `version`
- `created_at`

`guest_profile_id + revision_number` 唯一，并使用部分唯一索引保证同一档案最多一个 `PENDING` 版本。快照创建后业务字段不得更新；只有审核状态、审核时间和乐观锁版本可以通过受控服务改变。

### 4.8 `profile_revision_field_value`

保存动态字段的不可变快照。除类型值外，同时保存提交时的 `field_code`、`label`、`data_type` 和选项显示值，保证字段定义后来调整或停用时历史版本仍可解释。

### 4.9 `profile_review_record`

- `id`
- `profile_revision_id`，唯一
- `reviewer_admin_id`
- `result`：`APPROVED`、`REJECTED`
- `reason_code`
- `comment`
- `reviewed_at`
- `request_id`
- `created_at`

退回必须提供面向嘉宾的说明；`reason_code` 可空以兼容初期尚未确定的原因字典。审核记录只追加，不允许修改或删除。

## 5. 状态与业务规则

### 5.1 草稿

- 尚未保存时 API 返回逻辑状态 `NOT_STARTED`，不因 GET 请求创建数据库记录。
- 首次保存创建 `guest_profile` 并分配不可猜测的档案编号。
- 更新请求必须携带当前 `version`；条件更新失败返回 `PROFILE_VERSION_CONFLICT`。
- `PENDING_REVIEW` 状态禁止修改草稿，返回 `PROFILE_REVIEW_IN_PROGRESS`。
- 已通过档案再次保存时，状态转为 `DRAFT`，但 `current_approved_revision_id` 保持不变。
- 退回后的档案可以继续修改，状态从 `CHANGES_REQUESTED` 转为 `DRAFT`。

### 5.2 提交

提交前在一个事务中完成：

1. 锁定当前档案。
2. 校验账号为 `ACTIVE`。
3. 校验付款记录有关联的付款前授权书版本。
4. 实时校验嘉宾已主动同意同一版本且未过期。
5. 按当前启用字段定义校验七个核心必填字段和所有动态必填字段。
6. 确认不存在待审版本。
7. 固化固定字段与动态字段快照。
8. 创建 `PENDING` 版本和 24 小时截止时间。
9. 更新档案状态与待审指针。
10. 追加审计记录。

`Idempotency-Key` 为提交接口必填请求头。服务端保存其 HMAC 和请求内容摘要；相同账号、相同键、相同内容重复提交时返回原版本，相同键但内容不同返回 `IDEMPOTENCY_KEY_REUSED`。

### 5.3 审核通过

在一个事务中锁定档案和待审版本，校验版本仍为 `PENDING` 且 `expectedVersion` 匹配，然后：

1. 将版本标记为 `APPROVED`。
2. 创建审核记录。
3. 将 `current_approved_revision_id` 指向该版本。
4. 清空 `pending_revision_id`。
5. 将档案状态设为 `APPROVED`。
6. 追加审计记录。

### 5.4 审核退回

退回同样使用事务和乐观锁，将版本标记为 `REJECTED`，保存原因与说明，清空待审指针并把档案状态设为 `CHANGES_REQUESTED`。`current_approved_revision_id` 不变，因此旧的已通过版本继续可供内部业务使用。

重复提交相同审核结果返回当前结果；尝试把已完成审核改成另一结果返回 `PROFILE_REVIEW_ALREADY_COMPLETED`。

## 6. API

### 6.1 授权文档接口

- `GET /api/v1/public/authorization-documents/current`
- `GET /api/v1/guest/authorization-documents/{version}`

`current` 公开接口只返回当前 `ACTIVE` 版本。指定版本接口要求嘉宾登录：可返回 `ACTIVE` 版本，也可在当前账号付款记录引用该版本时返回 `RETIRED` 版本；绝不返回 `DRAFT` 版本。响应只包含版本号、标题、完整文案、摘要和生效时间，不包含后台信息。

### 6.2 嘉宾授权接口

- `GET /api/v1/guest/consents/current`
- `POST /api/v1/guest/consents`

同意请求包含 `authorizationDocumentVersion`、必须为 `true` 的 `accepted` 和白名单化的 `sourcePage`。可同意当前 `ACTIVE` 版本，也可同意当前账号付款记录已引用的 `RETIRED` 版本；其他停用版本返回 `AUTHORIZATION_DOCUMENT_NOT_ACTIVE`。重复同意同一版本返回现有有效记录，不重复创建。

### 6.3 嘉宾档案接口

- `GET /api/v1/guest/profile/draft`
- `PUT /api/v1/guest/profile/draft`
- `POST /api/v1/guest/profile/submissions`
- `GET /api/v1/guest/profile/status`
- `GET /api/v1/guest/profile/revisions/{revisionId}`

所有接口从 Sa-Token 会话取得账号 ID，禁止客户端传入或覆盖 `userAccountId`。版本详情只能读取当前账号自己的版本。

### 6.4 管理端字段配置接口

- `GET /api/v1/admin/profile-field-definitions`
- `POST /api/v1/admin/profile-field-definitions`
- `PATCH /api/v1/admin/profile-field-definitions/{id}`

本阶段由任意活动管理员调用。接口明确限制核心字段和已有数据字段的不可变属性。

### 6.5 管理端审核接口

- `GET /api/v1/admin/profile-reviews`
- `GET /api/v1/admin/profile-reviews/{revisionId}`
- `POST /api/v1/admin/profile-reviews/{revisionId}/approve`
- `POST /api/v1/admin/profile-reviews/{revisionId}/reject`

列表强制分页，可按状态、是否即将超时、是否已超时、提交时间和档案编号筛选。详情返回待审版本、最后已通过版本和字段级差异，不返回实体对象或无类型 Map。

## 7. 数据访问

- 单表保存、按 ID 查询、状态条件更新和简单分页使用 MyBatis-Plus `BaseMapper` 与 Lambda Wrapper。
- 审核列表、版本详情和字段差异等连表查询使用 Mapper `@Select`；动态筛选使用 `@SelectProvider`。
- 所有查询返回明确的 Query DTO/View，不直接返回持久化实体。
- 动态排序只接受后端枚举映射的列名，不拼接客户端原始字符串。
- 注解 SQL 必须在 PostgreSQL 18 Testcontainers 中执行。
- 不创建 Mapper XML。

## 8. 安全与审计

- 微信号、抖音标识、昵称和主页链接以独立字段域进行 AES-GCM 加密；只为确需精确查询的标识生成 HMAC。
- API 默认不返回敏感标识；嘉宾本人草稿和管理员审核详情按最小需要解密。
- 授权证据中的 IP、User-Agent 和会话引用只保存不可逆摘要/HMAC，不保存 Sa-Token 原始令牌。
- 所有 HMAC 在归一化后追加固定字段域前缀，例如 `consent:ip:` 和 `profile:wechat-id:`，避免不同字段的相同明文得到可关联的摘要。
- 客户端 IP 从服务端连接信息取得；只在明确配置可信反向代理后解析转发头，不相信任意客户端传入的 IP 头。
- 关键审计事件包括 `CONSENT_ACCEPTED`、`PROFILE_DRAFT_SAVED`、`PROFILE_SUBMITTED`、`PROFILE_APPROVED`、`PROFILE_REJECTED` 和字段定义变更。
- 审计元数据仅记录字段代码和变更类型；敏感字段只记录“已变更”，不记录前后明文。
- 浏览器 Cookie 写请求继续执行可信来源校验；接口不得通过错误差异泄露其他嘉宾是否存在档案。

## 9. 错误码

- `AUTHORIZATION_DOCUMENT_NOT_FOUND`
- `AUTHORIZATION_DOCUMENT_NOT_ACTIVE`
- `CONSENT_REQUIRED`
- `CONSENT_EXPIRED`
- `PREPAYMENT_AUTHORIZATION_EVIDENCE_MISSING`
- `PROFILE_NOT_STARTED`
- `PROFILE_VALIDATION_FAILED`
- `PROFILE_VERSION_CONFLICT`
- `PROFILE_REVIEW_IN_PROGRESS`
- `PROFILE_REVISION_NOT_FOUND`
- `PROFILE_REVISION_FORBIDDEN`
- `PROFILE_REVIEW_ALREADY_COMPLETED`
- `IDEMPOTENCY_KEY_REQUIRED`
- `IDEMPOTENCY_KEY_REUSED`
- `FIELD_DEFINITION_IMMUTABLE`
- `FIELD_VALUE_INVALID`

错误响应继续使用现有 `ApiResponse` 和请求追踪编号，不返回 SQL、密文、对象键或内部堆栈。

## 10. 测试策略

1. 数据库迁移测试验证全部外键、唯一约束、部分索引、字段类型和运行账号权限。
2. 单元测试覆盖档案状态机、动态字段类型校验、必填规则、授权一年有效期和审核截止时间。
3. 服务集成测试覆盖保存草稿、乐观锁冲突、提交快照、幂等重试、授权缺失和过期、通过指针切换、退回不覆盖已通过版本。
4. API 测试覆盖嘉宾数据归属、管理员登录要求、分页边界、稳定错误码和敏感字段不泄露。
5. PostgreSQL 集成测试执行所有注解连表 SQL 和动态查询 Provider。
6. Spring Modulith 测试验证 `guest`、`review`、`consent` 只通过公开应用接口协作。
7. 源码守卫继续禁止 Mapper XML、Spring Security、实体 `@Data/@ToString` 和敏感字段日志。

## 11. 交付顺序

1. 授权与档案数据库迁移及实体映射。
2. 授权书查询、付款版本关联和嘉宾主动同意。
3. 字段定义、草稿保存和类型校验。
4. 不可变提交快照与幂等处理。
5. 管理端审核列表、详情、通过与退回。
6. 模块边界、安全守卫和完整回归。

完成本阶段后，再进入 Vue 管理后台和 uni-app 嘉宾端页面；照片字段先展示为未接入状态，等对象存储方案及 Key 准备好后实现上传闭环。
