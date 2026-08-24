import { request, uploadPhoto } from './request'
import type {
  AuthorizationDocumentView,
  GuestCourseCollectionView,
  GuestCourseDetail,
  GuestCourseListItem,
  GuestFieldDefinition,
  GuestProfileDraft,
  GuestSession,
  MembershipView,
  OnlineOrder,
  OnlineOrderListItem,
  OnlineOrderStatus,
  OnlinePaymentSettings,
  PageView,
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

/**
 * 本人的订单列表，新的在前。
 *
 * 这是把订单找回来的唯一途径：订单号原本只存在 `sessionStorage`，换标签页或重新登录就丢，
 * 而查单接口要求已经知道订单号。
 */
export function vipOrders() {
  return request<OnlineOrderListItem[]>({ url: '/guest/vip-payments/orders' })
}

// ---- 课程 ----

export function courseCollections() {
  return request<GuestCourseCollectionView[]>({ url: '/guest/courses/collections' })
}

export interface CourseListQuery {
  /** null / undefined 都表示不筛合集——别把字符串 "null" 拼进 query。 */
  collectionId?: number | null
  page?: number
  size?: number
}

/**
 * 已发布课程的分页列表。免费账号也能调，返回的是元数据：
 * **后端不会在这里下发正文与视频地址**，所以前端也拿不到、不必藏。
 */
export function courses(query: CourseListQuery = {}) {
  const search = new URLSearchParams()
  if (query.collectionId !== null && query.collectionId !== undefined) {
    search.set('collectionId', String(query.collectionId))
  }
  search.set('page', String(query.page ?? 1))
  search.set('size', String(query.size ?? 10))
  return request<PageView<GuestCourseListItem>>({ url: `/guest/courses?${search.toString()}` })
}

/**
 * 课程详情。需要付费会员，免费账号会拿到 403 `COURSE_VIP_REQUIRED`——
 * 那不是会话失效，调用方要用 `apiErrorCode()` 认出来并落到升级引导，不要当错误弹窗。
 */
export function courseDetail(courseId: number) {
  return request<GuestCourseDetail>({ url: `/guest/courses/${courseId}` })
}

export type { PhotoUploadResult }
