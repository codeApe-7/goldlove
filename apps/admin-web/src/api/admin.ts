import { http, unwrap } from './http'
import type {
  AdminActivationCodeItem,
  AdminDashboardStats,
  AdminPaymentOrderItem,
  AdminProfileDetail,
  AdminProfileListItem,
  AdminSession,
  PageView,
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

export function listProfiles(
  params: Record<string, string | number | undefined>,
): Promise<PageView<AdminProfileListItem>> {
  return unwrap(http.get('/admin/profiles', { params }))
}

export function profileDetail(profileId: number): Promise<AdminProfileDetail> {
  return unwrap(http.get(`/admin/profiles/${profileId}`))
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
