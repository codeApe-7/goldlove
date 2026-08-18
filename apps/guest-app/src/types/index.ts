export interface GuestSession {
  accountId: number
  status: string
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

export interface ConsentView {
  id: number
  authorizationDocumentVersion: string
  acceptedAt: string
  expiresAt: string
}

export interface GuestProfileDraft {
  profileNo: string | null
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
  pendingRevisionId: number | null
  currentApprovedRevisionId: number | null
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
  tier: 'VIP' | 'SVIP'
  creditMinor: number
  svipThresholdMinor: number
  creditToNextTierMinor: number
}

/** 可以下发前端的渠道设置，不含任何密钥。 */
export interface OnlinePaymentSettings {
  appId: string
  amountMinor: number
  orderDescription: string
  authorizeUrl: string
  state: string
}

/** 微信内调起支付所需的参数，全部由后端签名。 */
export interface JsapiPayParameters {
  appId: string
  timeStamp: string
  nonceStr: string
  packageValue: string
  signType: string
  paySign: string
}

export interface OnlineOrder {
  outTradeNo: string
  amountMinor: number
  authorizationDocumentVersion: string
  payParameters: JsapiPayParameters
}

export interface OnlineOrderStatus {
  outTradeNo: string
  status: 'CREATED' | 'PAID' | 'CLOSED' | 'REFUNDED'
  amountMinor: number
  registered: boolean
}

export interface IssuedRegistrationToken {
  token: string
  expiresAt: string
}
