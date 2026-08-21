import { request, uploadPhoto } from './request'
import type {
  AuthorizationDocumentView,
  GuestFieldDefinition,
  GuestProfileDraft,
  GuestSession,
  MembershipView,
  OnlineOrder,
  OnlineOrderStatus,
  OnlinePaymentSettings,
  PhotoUploadResult,
  ProfilePhotoView,
} from '@/types'

// ---- 账号 ----

/** 免费自助注册：手机号 + 密码 + 勾选授权书，没有前置条件。 */
export function register(
  phone: string,
  password: string,
  confirmPassword: string,
  authorizationDocumentVersion: string,
) {
  return request<GuestSession>({
    url: '/guest/auth/register',
    method: 'POST',
    data: {
      phone,
      password,
      confirmPassword,
      acceptedAuthorization: true,
      authorizationDocumentVersion,
    },
  })
}

export function login(phone: string, password: string) {
  return request<GuestSession>({
    url: '/guest/auth/login',
    method: 'POST',
    data: { phone, password },
  })
}

export function logout() {
  return request<null>({ url: '/guest/auth/logout', method: 'POST' })
}

export function currentAuthorizationDocument() {
  return request<AuthorizationDocumentView>({ url: '/public/authorization-documents/current' })
}

// ---- 档案 ----

export function fieldDefinitions() {
  return request<GuestFieldDefinition[]>({ url: '/guest/profile/field-definitions' })
}

export function getDraft() {
  return request<GuestProfileDraft>({ url: '/guest/profile/draft' })
}

export function saveDraft(payload: Record<string, unknown>) {
  return request<GuestProfileDraft>({
    url: '/guest/profile/draft',
    method: 'PUT',
    data: payload,
  })
}

export function profileStatus() {
  return request<{
    profileNo: string | null
    status: string
    version: number | null
    missingRequiredFieldCodes: string[]
  }>({ url: '/guest/profile/status' })
}

export function listPhotos() {
  return request<ProfilePhotoView[]>({ url: '/guest/profile/photos' })
}

export { uploadPhoto }

// ---- 会员 ----

export function membership() {
  return request<MembershipView>({ url: '/guest/membership' })
}

/** 用激活码升级。码在生成时已绑定手机号，只有本人能兑。 */
export function redeemActivationCode(code: string) {
  return request<MembershipView>({
    url: '/guest/membership/activation-codes',
    method: 'POST',
    data: { code },
  })
}

// ---- VIP 升级支付 ----

export function vipPaymentSettings() {
  return request<OnlinePaymentSettings>({ url: '/guest/vip-payments/settings' })
}

/** 下单。金额与账号都由后端决定，前端什么都不传。 */
export function createVipOrder() {
  return request<OnlineOrder>({ url: '/guest/vip-payments/orders', method: 'POST' })
}

export function vipOrderStatus(outTradeNo: string) {
  return request<OnlineOrderStatus>({
    url: `/guest/vip-payments/orders/${encodeURIComponent(outTradeNo)}`,
  })
}

export type { PhotoUploadResult }
