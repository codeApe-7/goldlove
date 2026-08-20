import { request, uploadPhoto } from './request'
import type {
  AuthorizationDocumentView,
  ConsentView,
  GuestFieldDefinition,
  GuestProfileDraft,
  GuestSession,
  IssuedRegistrationToken,
  MembershipView,
  OnlineOrder,
  OnlineOrderStatus,
  OnlinePaymentSettings,
  PhotoUploadResult,
  ProfilePhotoView,
} from '@/types'

export function activate(phone: string, initialCredential: string, newPassword: string) {
  return request<GuestSession>({
    url: '/guest/auth/activate',
    method: 'POST',
    data: { phone, initialCredential, newPassword },
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

export function currentConsent() {
  return request<ConsentView | null>({ url: '/guest/consents/current' })
}

export function acceptConsent(authorizationDocumentVersion: string, sourcePage: string) {
  return request<ConsentView>({
    url: '/guest/consents',
    method: 'POST',
    data: { authorizationDocumentVersion, accepted: true, sourcePage },
  })
}

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
    status: string
    pendingRevisionId: number | null
    currentApprovedRevisionId: number | null
  }>({ url: '/guest/profile/status' })
}

export function listPhotos() {
  return request<ProfilePhotoView[]>({ url: '/guest/profile/photos' })
}

export function submitProfile(idempotencyKey: string) {
  return request<{ id: number; status: string; reviewDeadlineAt: string }>({
    url: '/guest/profile/submissions',
    method: 'POST',
    header: { 'Idempotency-Key': idempotencyKey },
  })
}

export { uploadPhoto }

export function membership() {
  return request<MembershipView>({ url: '/guest/membership' })
}

// ---- 线上支付 → 注册建档 ----

export function onlinePaymentSettings() {
  return request<OnlinePaymentSettings>({ url: '/public/online-payments/settings' })
}

/**
 * 下单。金额由后端配置决定，前端不传金额。
 * 手机号在此处预检（是否已有账号）并钉在订单上，注册时必须一致。
 */
export function createOnlineOrder(
  phone: string,
  authorizationCode: string,
  authorizationDocumentVersion: string,
) {
  return request<OnlineOrder>({
    url: '/public/online-payments/orders',
    method: 'POST',
    data: { phone, authorizationCode, authorizationDocumentVersion },
  })
}

export function onlineOrderStatus(outTradeNo: string) {
  return request<OnlineOrderStatus>({
    url: `/public/online-payments/orders/${encodeURIComponent(outTradeNo)}`,
  })
}

export function issueRegistrationToken(outTradeNo: string) {
  return request<IssuedRegistrationToken>({
    url: `/public/online-payments/orders/${encodeURIComponent(outTradeNo)}/registration-tokens`,
    method: 'POST',
  })
}

export function registerOnline(registrationToken: string, phone: string, password: string) {
  return request<GuestSession>({
    url: '/public/registrations',
    method: 'POST',
    data: { registrationToken, phone, password },
  })
}
