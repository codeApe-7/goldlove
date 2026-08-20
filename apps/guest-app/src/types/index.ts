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
  birthDate: string | null
  heightCm: number | null
  education: string | null
  occupation: string | null
  incomeRange: string | null
  city: string | null
  wechatId: string | null
  douyinId: string | null
  douyinNickname: string | null
  douyinProfileUrl: string | null
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
}
