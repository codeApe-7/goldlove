import { http, unwrap } from './http'
import type {
  AccountStatusView,
  AdminActivationCodeItem,
  AdminDashboardStats,
  AdminPaymentOrderItem,
  AdminProfileCounts,
  AdminProfileDetail,
  AdminProfileListItem,
  AdminSession,
  PageView,
  PaymentSettingView,
  ProfileFieldDefinitionView,
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
