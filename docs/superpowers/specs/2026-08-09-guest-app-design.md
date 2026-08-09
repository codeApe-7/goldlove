# uni-app 嘉宾端设计

## 背景与目标

后端已具备嘉宾身份闭环（激活/登录/会话）、授权书与主动同意、档案草稿、照片对象存储、不可变提交与管理员审核。本子项目交付 uni-app 嘉宾端（第一期 H5）：激活登录 → 授权同意 → 档案填写（含照片）→ 提交 → 状态查看与退回重提 → 个人中心，并配套新增一个访客可见的字段定义接口。

## 已确认的决策

- 技术栈按架构文档：uni-app（Vue 3 + Vite + TypeScript），第一期发布 H5，后续适配微信小程序；组件库 uni-ui；状态管理 Pinia。
- 视觉方向：温润婚恋移动端品牌，与管理后台同调性（暖玫瑰/暖金/暖灰/深暖褐）。
- 页面范围：建档核心闭环（激活登录、授权同意、档案表单、提交、状态、退回重提、个人中心）。
- 一期不做：微信小程序构建目标（仅预留适配层）、朋友圈素材上传、直播排期/预约、消息通知、暗色模式。
- 后端配套仅一个接口：`GET /api/v1/guest/profile/field-definitions`；其余复用现有接口。

## 工程与部署

- 新目录 `apps/guest-app`，使用 uni-app 官方 `Vue3 + Vite + TS` 模板（`uni-preset-vue#vite-ts`）。
- 依赖：`@dcloudio/uni-app` 系列（模板自带）、`@dcloudio/uni-ui`、`pinia`、TypeScript。
- 目录：`src/{api, adapters, components, pages, stores, styles, types}` + `src/pages.json`。
- 适配层：`adapters/` 收敛平台差异（auth 会话存取、文件选择、隐私提示），一期 H5 实现，小程序预留接口。
- H5 部署：`build:h5` 产物静态托管，Nginx 同源反代 `/api`；后端 `BROWSER_ALLOWED_ORIGINS` 必须包含 H5 域名（浏览器 Cookie 写请求的可信来源校验）。
- 会话：后端 Cookie 会话（H5 同源自动携带）；适配层封装存取与 401 处理。

## 页面与流程

### tabBar

| Tab | 页面 | 说明 |
|---|---|---|
| 档案 | `/pages/profile/index` | 档案表单与照片 |
| 状态 | `/pages/status/index` | 建档/审核状态 |
| 我的 | `/pages/mine/index` | 会话、授权、已通过档案、退出 |

### 非 tab 页面

| 页面 | 说明 |
|---|---|
| `/pages/auth/index` | 未激活：手机号 + 初始凭证 + 新密码激活；已激活：手机号 + 密码登录 |
| `/pages/consent/index` | 授权书全文 + 勾选同意；未同意前不允许进入档案填写 |

提交成功、退回提示与跳转统一在状态页展示，不设独立结果页。

### 流程编排

1. 启动 → 检查会话；未登录进入 `/auth`。
2. 登录后 → 检查授权：未同意或已过期 → `/consent` 查看并同意。
3. 同意后 → `/profile` 填写档案（含照片），可保存草稿；头像缺失或必填缺失时提交被拦截。
4. 提交成功 → `/status` 显示待审核与截止时间；`Idempotency-Key` 前端生成并本地保存，网络重试复用同一键。
5. 审核退回 → `/status` 展示退回说明 → 跳 `/profile` 修改后重新提交。
6. 审核通过 → `/status` 显示已通过；`/mine` 可查看已通过档案（含照片）。
7. 授权到期 → `/mine` 提示并跳 `/consent` 重新查看与同意。

## 后端配套接口

### `GET /api/v1/guest/profile/field-definitions`

访客登录后调用，返回**启用**的字段定义，按 `sortOrder`、`id` 升序：

```json
{
  "items": [
    {
      "id": 1,
      "fieldCode": "gender",
      "label": "性别",
      "dataType": "SINGLE_OPTION",
      "required": true,
      "options": ["男", "女"],
      "sortOrder": 10,
      "instructions": "请选择性别"
    }
  ]
}
```

实现于 `guest` 模块：`ProfileFieldDefinitionQuery`（服务）+ 注解 SQL 或 MyBatis-Plus 查询，仅 `enabled = true`，不含 `version`、`storageKind` 等后台管理属性。核心与动态字段都返回。

其余复用接口：`POST /guest/auth/activate`、`POST /guest/auth/login`、`POST /guest/auth/logout`、`GET /guest/auth/me`、`GET /public/authorization-documents/current`、`GET /guest/consents/current`、`POST /guest/consents`、`GET/PUT /guest/profile/draft`、`GET /guest/profile/status`、`GET/POST/DELETE /guest/profile/photos`、`POST /guest/profile/submissions`、`GET /guest/profile/revisions/{id}`。

## 表单与照片

- 表单由字段定义动态渲染：TEXT/LONG_TEXT（文本框/多行）、INTEGER/DECIMAL（数字）、DATE（日期选择）、BOOLEAN（开关）、SINGLE_OPTION（单选）；核心七字段与动态字段同规则。
- 保存草稿：`PUT /profile/draft` 携带 `expectedVersion`；乐观锁冲突提示刷新重试。
- 提交前本地校验必填与类型，最终以后端校验为准。
- 照片：头像 1 张 + 生活照 ≤ 6 张；`uni.chooseMedia` 选择、`uni.uploadFile` 上传（multipart `file` + `category`）、列表展示（签名 URL）、删除；删除语义由后端保证（已进快照对象保留）。

## 视觉规范

- 主色暖玫瑰 `#B4556D`、辅助暖金 `#C9A227`、成功 `#4E7A5A`、背景暖灰 `#F7F5F2`、深暖褐 `#46323A`；通过 CSS 变量覆盖 uni-ui 主色。
- 移动端大标题、卡片式表单、圆角；激活/授权/提交成功页使用暖色渐变与一句品牌文案。
- 数字与金额使用等宽数字（`font-variant-numeric: tabular-nums`）。

## 测试与验收

- 后端：`field-definitions` 接口集成测试（启用/停用过滤、排序、必填/选项字段、未登录 401）+ 全量回归。
- 前端：Vitest 单测覆盖 api 层（路径/参数/解包）、auth/consent/profile store、必填与类型校验规则；`build:h5` 通过。
- 手动冒烟：激活 → 授权同意 → 填表传照 → 保存草稿 → 提交 → 退回修改重提 → 通过后查看已通过档案。
- README 补充嘉宾端启动/构建/部署与 `BROWSER_ALLOWED_ORIGINS` 配置说明。

## 本阶段不做

- 微信小程序构建目标（仅预留适配层接口）、朋友圈素材上传、直播排期/预约、消息通知、暗色模式、多语言。
