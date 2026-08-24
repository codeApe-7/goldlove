export type MembershipTier = 'FREE' | 'VIP' | 'SVIP'

export interface GuestSession {
  accountId: number
  status: string
  membershipTier: MembershipTier
  accessToken: string
  expiresIn: number
}

export interface GuestFieldDefinition {
  id: number
  fieldCode: string
  label: string
  storageKind: 'CORE' | 'DYNAMIC'
  dataType: 'TEXT' | 'LONG_TEXT' | 'INTEGER' | 'DECIMAL' | 'DATE' | 'BOOLEAN' | 'SINGLE_OPTION'
  required: boolean
  options: string[]
  sortOrder: number
  instructions: string
}

export interface AuthorizationDocumentView {
  version: string
  title: string
  content: string
  contentSha256: string
  effectiveAt: string
}

export interface GuestProfileDraft {
  profileNo: string | null
  /** NOT_STARTED / DRAFT / COMPLETED —— 没有审核环节，保存即生效。 */
  status: string
  version: number | null
  gender: string | null
  /** 只收年龄，不收出生日期——档案用到的只有「多大」。 */
  age: number | null
  heightCm: number | null
  education: string | null
  occupation: string | null
  incomeRange: string | null
  city: string | null
  wechatId: string | null
  douyinId: string | null
  missingRequiredFieldCodes: string[]
  dynamicFields: unknown[]
}

export interface ProfilePhotoView {
  id: number
  category: 'AVATAR' | 'LIFE'
  objectKey: string
  sortOrder: number
  previewUrl: string
  createdAt: string
}

export interface PhotoUploadResult {
  objectKey: string
  category: 'AVATAR' | 'LIFE'
  previewUrl: string
}

export interface MembershipView {
  tier: MembershipTier
  creditMinor: number
  svipThresholdMinor: number
  creditToNextTierMinor: number
}

/** 线上支付渠道类型。当前只有易支付（跳转支付宝收银台）。 */
export type PaymentChannelType = 'XPAY_ALIPAY'

/** 可以下发前端的渠道设置，不含任何密钥。 */
export interface OnlinePaymentSettings {
  channelType: PaymentChannelType
  amountMinor: number
  orderDescription: string
}

/** 前端跳转收银台所需的参数，由后端签名后下发。 */
export interface PayParameters {
  channelType: PaymentChannelType
  jumpUrl: string | null
}

export interface OnlineOrder {
  outTradeNo: string
  amountMinor: number
  payParameters: PayParameters | null
}

export interface OnlineOrderStatus {
  outTradeNo: string
  status: 'CREATED' | 'PAID' | 'CLOSED'
  amountMinor: number
  /** 该笔付款是否已计入会员额度。 */
  membershipGranted: boolean
  /** 渠道侧订单号，下单成功即有；报障时把它给客服比商户订单号有用。 */
  channelTradeNo: string | null
  expiresAt: string | null
}

/**
 * 「我的订单」列表项。
 *
 * `status === 'CREATED'` 即「还能继续付」——后端在返回列表前会把已过期的订单转成
 * `CLOSED`，所以前端不需要自己拿 `expiresAt` 和当前时间比。
 */
export interface OnlineOrderListItem {
  outTradeNo: string
  status: 'CREATED' | 'PAID' | 'CLOSED'
  amountMinor: number
  channelTradeNo: string | null
  createdAt: string
  paidAt: string | null
  expiresAt: string | null
}

/** 后端 `common.web.PageView`：1 起页码。 */
export interface PageView<T> {
  items: T[]
  page: number
  size: number
  total: number
}

/** 课程类型：图文 / 视频 / 纯文本。 */
export type CourseContentType = 'ARTICLE' | 'VIDEO' | 'TEXT'

/** 目录只统计已发布的课，未发布的草稿访客侧看不到。 */
export interface GuestCourseCollectionView {
  id: number
  name: string
  description: string | null
  courseCount: number
}

/**
 * 列表项**不含正文与视频地址**——列表可见、内容加锁是后端的门禁口径。
 * `locked` 表示当前账号不是付费会员，卡片据此显示锁标。
 * 不要用 `stores/auth.ts` 的 `tier` 判断：那是登录时的会话快照，兑码或支付后不刷新就是旧值。
 */
export interface GuestCourseListItem {
  id: number
  collectionId: number
  collectionName: string
  title: string
  subtitle: string | null
  summary: string | null
  contentType: CourseContentType
  authorName: string | null
  videoDurationSeconds: number | null
  coverPreviewUrl: string | null
  publishedAt: string | null
  locked: boolean
}

/** 详情要 VIP/SVIP，免费账号取会拿到 403 `COURSE_VIP_REQUIRED`。`videoUrl` 是 2 小时签名地址。 */
export interface GuestCourseDetail extends GuestCourseListItem {
  contentMarkdown: string | null
  videoUrl: string | null
}
