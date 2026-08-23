import { http, unwrap } from './http'
import type {
  AccountStatusView,
  AdminActivationCodeItem,
  AdminCourseCollectionView,
  AdminCourseDetail,
  AdminCourseListItem,
  AdminDashboardStats,
  AdminPaymentOrderItem,
  AdminProfileCounts,
  AdminProfileDetail,
  AdminProfileListItem,
  AdminSession,
  CourseCollectionStatus,
  CourseImageAsset,
  CourseMarkdownAsset,
  CourseVideoAsset,
  CourseVideoPartResult,
  CourseVideoUploadSession,
  PageView,
  PaymentSettingView,
  ProfileFieldDefinitionView,
  SaveCoursePayload,
} from '@/types'

export function login(username: string, password: string): Promise<AdminSession> {
  return unwrap(http.post('/admin/auth/login', { username, password }))
}

export function logout(): Promise<null> {
  return unwrap(http.post('/admin/auth/logout'))
}

export function dashboardStats(): Promise<AdminDashboardStats> {
  return unwrap(http.get('/admin/dashboard/stats'))
}

// ---- 档案 ----

export type ProfileQuery = Record<string, string | number | boolean | undefined>

export function listProfiles(
  params: ProfileQuery,
): Promise<PageView<AdminProfileListItem>> {
  return unwrap(http.get('/admin/profiles', { params }))
}

/** tab 上的数量。只吃筛选条上的条件，status / accountStatus / paidOnly 会被后端忽略。 */
export function profileCounts(params: ProfileQuery): Promise<AdminProfileCounts> {
  return unwrap(http.get('/admin/profiles/counts', { params }))
}

export function profileDetail(profileId: number): Promise<AdminProfileDetail> {
  return unwrap(http.get(`/admin/profiles/${profileId}`))
}

/**
 * 导出 CSV。传 ids 就只导这些行，不传则导当前筛选的全量（后端有 5000 条上限）。
 *
 * ids 用逗号串而不是数组：axios 默认把数组序列化成 `ids[]=1&ids[]=2`，
 * Spring 的 `List<Long>` 收不到，而逗号串它会自己拆开。
 *
 * 失败时的错误文案由 http.ts 的拦截器统一处理（它会把 blob 响应体读成文本再取 message）。
 */
export async function exportProfiles(
  params: ProfileQuery,
  ids: number[] = [],
): Promise<Blob> {
  const query = ids.length > 0 ? { ...params, ids: ids.join(',') } : params
  const response = await http.get('/admin/profiles/export', {
    params: query,
    responseType: 'blob',
  })
  return response.data as Blob
}

// ---- 账号状态 ----

/** 停用账号：该手机号下一次调接口就会被挡住，后台仍能查看档案。 */
export function suspendAccount(accountId: number, reason: string | null): Promise<AccountStatusView> {
  return unwrap(http.post(`/admin/accounts/${accountId}/suspend`, { reason }))
}

export function activateAccount(accountId: number, reason: string | null): Promise<AccountStatusView> {
  return unwrap(http.post(`/admin/accounts/${accountId}/activate`, { reason }))
}

// ---- 字段定义 ----

export function listFieldDefinitions(
  page: number,
  size: number,
): Promise<PageView<ProfileFieldDefinitionView>> {
  return unwrap(http.get('/admin/profile-field-definitions', { params: { page, size } }))
}

export function createFieldDefinition(payload: Record<string, unknown>) {
  return unwrap(http.post('/admin/profile-field-definitions', payload))
}

export function updateFieldDefinition(id: number, payload: Record<string, unknown>) {
  return unwrap(http.patch(`/admin/profile-field-definitions/${id}`, payload))
}

// ---- 支付订单 ----

export function listPaymentOrders(
  params: Record<string, string | number | undefined>,
): Promise<PageView<AdminPaymentOrderItem>> {
  return unwrap(http.get('/admin/payment-orders', { params }))
}

// ---- 激活码 ----

export function listActivationCodes(
  params: Record<string, string | number | undefined>,
): Promise<PageView<AdminActivationCodeItem>> {
  return unwrap(http.get('/admin/activation-codes', { params }))
}

/** 生成激活码。手机号在生成时就绑定，只有该手机号的账号能兑换。 */
export function generateActivationCode(payload: {
  boundPhone: string
  grantedTier: 'VIP' | 'SVIP'
  note?: string | null
}): Promise<AdminActivationCodeItem> {
  return unwrap(http.post('/admin/activation-codes', payload))
}

export function revokeActivationCode(codeId: number): Promise<null> {
  return unwrap(http.post(`/admin/activation-codes/${codeId}/revoke`))
}

// ---- 支付设置 ----

export function paymentSetting(): Promise<PaymentSettingView> {
  return unwrap(http.get('/admin/payment-settings'))
}

/**
 * 改 VIP 升级金额。金额以「分」传输，与后端和渠道一致；界面按元录入后换算。
 * 只影响之后创建的订单，已创建的订单保留下单时的金额。
 */
export function updatePaymentSetting(vipUpgradeAmountMinor: number): Promise<PaymentSettingView> {
  return unwrap(http.put('/admin/payment-settings', { vipUpgradeAmountMinor }))
}

// ---- 课程合集 ----

/** 合集不分页：只有固定的几个分类，后端直接给数组。 */
export function listCourseCollections(): Promise<AdminCourseCollectionView[]> {
  return unwrap(http.get('/admin/course-collections'))
}

export function createCourseCollection(payload: {
  name: string
  description?: string | null
  sortOrder?: number
}): Promise<AdminCourseCollectionView> {
  return unwrap(http.post('/admin/course-collections', payload))
}

/**
 * 改合集。隐藏 / 显示也走这里（`status`）——合集下面挂着课程，
 * 后端没给 DELETE 权限，删了会留下孤儿课程。
 */
export function updateCourseCollection(
  id: number,
  payload: {
    name?: string
    description?: string | null
    sortOrder?: number
    status?: CourseCollectionStatus
  },
): Promise<AdminCourseCollectionView> {
  return unwrap(http.patch(`/admin/course-collections/${id}`, payload))
}

// ---- 课程 ----

export type CourseQuery = Record<string, string | number | undefined>

export function listCourses(params: CourseQuery): Promise<PageView<AdminCourseListItem>> {
  return unwrap(http.get('/admin/courses', { params }))
}

export function courseDetail(id: number): Promise<AdminCourseDetail> {
  return unwrap(http.get(`/admin/courses/${id}`))
}

export function createCourse(payload: SaveCoursePayload): Promise<AdminCourseDetail> {
  return unwrap(http.post('/admin/courses', payload))
}

/** 修改必须带 `expectedVersion`，不匹配后端返回 409 COURSE_VERSION_CONFLICT。 */
export function updateCourse(
  id: number,
  payload: SaveCoursePayload & { expectedVersion: number },
): Promise<AdminCourseDetail> {
  return unwrap(http.put(`/admin/courses/${id}`, payload))
}

export function publishCourse(id: number): Promise<AdminCourseDetail> {
  return unwrap(http.post(`/admin/courses/${id}/publish`))
}

export function archiveCourse(id: number): Promise<AdminCourseDetail> {
  return unwrap(http.post(`/admin/courses/${id}/archive`))
}

export function deleteCourse(id: number): Promise<null> {
  return unwrap(http.delete(`/admin/courses/${id}`))
}

// ---- 课程素材 ----
//
// 这几个是 admin-web 里唯一的 multipart 请求。**不要手写 Content-Type**：
// 浏览器要自己加 `boundary=...`，写死了后端就解不出分片。响应壳没变，unwrap 照用。

/** 正文插图。返回的 previewUrl 由编辑器插进 markdown。 */
export function uploadCourseImage(file: File): Promise<CourseImageAsset> {
  const form = new FormData()
  form.append('file', file)
  return unwrap(http.post('/admin/course-assets/images', form))
}

/** 上传 `.md`：后端按 UTF-8 读成文本原样返回，不落库，上限 1 MiB。 */
export function uploadCourseMarkdown(file: File): Promise<CourseMarkdownAsset> {
  const form = new FormData()
  form.append('file', file)
  return unwrap(http.post('/admin/course-assets/markdown', form))
}

/** 开一个分块上传会话。分块大小由后端下发，前端照它切片。 */
export function startCourseVideoUpload(payload: {
  filename: string
  contentType: string
  totalBytes: number
}): Promise<CourseVideoUploadSession> {
  return unwrap(http.post('/admin/course-assets/videos/uploads', payload))
}

/**
 * 传一块。`onProgress` 收到的是 axios 的 `loaded`——它算的是整个请求体，
 * 比分块本身大，进度计算那边会拿分块大小夹住。
 */
export function uploadCourseVideoPart(
  uploadId: string,
  partNumber: number,
  chunk: Blob,
  onProgress?: (loadedBytes: number) => void,
): Promise<CourseVideoPartResult> {
  const form = new FormData()
  form.append('partNumber', String(partNumber))
  // Blob 必须带文件名，否则 Spring 收到的是普通表单字段而不是 MultipartFile。
  form.append('file', chunk, `part-${partNumber}`)
  return unwrap(
    http.post(`/admin/course-assets/videos/uploads/${encodeURIComponent(uploadId)}/parts`, form, {
      onUploadProgress: (event) => onProgress?.(event.loaded),
    }),
  )
}

export function completeCourseVideoUpload(
  uploadId: string,
  parts: CourseVideoPartResult[],
): Promise<CourseVideoAsset> {
  return unwrap(
    http.post(`/admin/course-assets/videos/uploads/${encodeURIComponent(uploadId)}/complete`, { parts }),
  )
}

/** 用户取消时中止会话，别在 COS 上留半截数据。 */
export function abortCourseVideoUpload(uploadId: string): Promise<null> {
  return unwrap(http.delete(`/admin/course-assets/videos/uploads/${encodeURIComponent(uploadId)}`))
}
