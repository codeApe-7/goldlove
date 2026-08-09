# Vue 管理后台设计

## 背景与目标

后端已具备管理员认证、访客登记、档案字段配置、审核工作流与照片对象存储。本子项目交付 Vue 管理后台：管理员登录、工作台统计、访客登记（含补发初始凭证）、档案字段配置、审核列表/详情/通过/退回（含照片预览与字段差异），并配套新增两个小型后端接口（工作台统计、管理员登出）。

## 已确认的决策

- 技术栈按架构文档锁定：Vue 3、TypeScript、Vite、Element Plus；状态管理 Pinia、路由 Vue Router、HTTP axios。
- 工作台统计为四项：待审核数量、今日登记访客、今日审核完成、累计建档总数，由一个接口返回。
- 访客管理只做登记表单与补发凭证入口，不做已登记列表（后端暂无列表接口）。
- 视觉方向：温润婚恋调性，定制 Element Plus 主题（暖玫瑰主色、暖灰背景、金色点缀），不做暗色模式与国际化。
- 管理端只查看照片（嘉宾端负责上传）。

## 工程与部署

- 新目录 `apps/admin-web`，与 `services/platform-api` 平级。
- 脚本：`dev`、`build`、`preview`、`test`；依赖管理用 npm。
- 开发代理：Vite `server.proxy['/api']` → `http://localhost:8080`。
- 生产部署：`npm run build` 产出静态文件；Nginx 托管静态资源并把 `/api` 反向代理到后端（同源，保证 `SameSite=Lax` 会话 Cookie 可用）；Cookie `Secure` 由后端环境配置控制。
- 目录结构：`src/{api, components, layouts, pages, router, stores, styles, types}`。

## 认证与全局行为

- 登录：`POST /api/v1/admin/auth/login`，浏览器自动携带会话 Cookie；成功后写入 `auth` store 并跳转工作台。
- axios 实例：`baseURL = '/api/v1'`、`withCredentials = true`；响应拦截器解包 `{success, code, message, data, requestId}`，业务失败用 `ElMessage` 展示 `message`；401 统一清除会话并跳转 `/login`。
- 路由守卫：未登录访问受保护路由重定向 `/login`；已登录访问 `/login` 重定向工作台。
- 退出：调用新增的 `POST /api/v1/admin/auth/logout`，随后清除本地会话。

## 页面清单

| 路由 | 页面 | 内容 |
|---|---|---|
| `/login` | 登录 | 暖色渐变背景、品牌名与一句文案、账号/密码表单 |
| `/` | 布局 | 暖色侧边导航（工作台/访客登记/字段配置/审核管理）+ 顶栏（管理员名、退出） |
| `/dashboard` | 工作台 | 四项统计卡片（待审核、今日登记、今日审核、累计建档） |
| `/guests/register` | 访客登记 | 登记表单（手机号、付款参考、金额、付款时间、授权书版本、备注）+ 补发初始凭证（手机号，成功后仅本次展示凭证） |

金额以元输入、提交时换算为分（`Math.round(元 × 100)`）传给后端 `amountMinor`；付款时间默认当前时间、可修改；授权书版本默认后端当前生效版本 `v0.3`、可下拉选择（下拉选项取自公开授权书接口）。
| `/field-definitions` | 字段配置 | 分页表格 + 新增/编辑弹窗；核心字段受保护属性只读提示 |
| `/reviews` | 审核列表 | 状态/超时/提交时间/档案编号筛选 + 分页表格 |
| `/reviews/:id` | 审核详情 | 档案快照（核心/动态字段、照片签名 URL 预览）、与最后已通过版本的字段差异、通过/退回（退回必填说明） |

## 后端配套接口

### `GET /api/v1/admin/dashboard/stats`

返回：

```json
{
  "pendingReviews": 0,
  "todayRegistrations": 0,
  "todayReviews": 0,
  "totalProfiles": 0
}
```

实现于 `admin` 模块（`AdminDashboardService` + 注解 SQL 聚合查询）：

- 待审核：`profile_revision.status = 'PENDING'` 计数。
- 今日登记：`user_account.created_at` 落在今日边界内计数。
- 今日审核：`profile_review_record.reviewed_at` 落在今日边界内计数。
- 累计建档：`guest_profile` 总数。

今日边界按服务器本地时区（Asia/Shanghai）计算；服务使用可注入 `Clock`，测试用固定时钟断言。

### `POST /api/v1/admin/auth/logout`

调用 `authLogics.admin().logout()`，返回统一成功响应；无状态变更，幂等。

## 视觉规范

- 主色（暖玫瑰）`#B4556D`，辅助强调（暖金）`#C9A227`，成功 `#4E7A5A`，背景暖灰 `#F7F5F2`，侧边/顶栏深暖褐 `#46323A`。
- Element Plus 主题通过 CSS 变量覆盖实现（`--el-color-primary` 等），不引入 Sass 编译链。
- 字体：中文系统字体栈（PingFang SC/微软雅黑），数字与金额使用等宽数字（`font-variant-numeric: tabular-nums`）。
- 登录页签名元素：暖色渐变底 + 品牌名「婚恋智能档案库 · 管理后台」+ 一行服务文案；其余页面克制留白，统计卡片不加图表库。

## 测试与验收

- 后端：stats 与 logout 的集成测试（固定时钟断言今日边界）、相关守卫、全量回归。
- 前端：Vitest 单测覆盖 axios/api 封装、auth store、路由守卫；`vite build` 通过；手动冒烟清单覆盖登录 → 访客登记 → 字段配置 → 审核全流程。
- README 补充前端启动、构建与 Nginx 部署说明。

## 本阶段不做

- 已登记访客列表、RBAC/多角色、审计日志查看、图表库、暗色模式、国际化、E2E 测试、管理端照片上传。
