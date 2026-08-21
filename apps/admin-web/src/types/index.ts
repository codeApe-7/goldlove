export interface AdminSession {
  id: number
  username: string
  displayName: string
}

export interface AdminDashboardStats {
  totalAccounts: number
  todayRegistrations: number
  totalProfiles: number
  completedProfiles: number
  vipMembers: number
  svipMembers: number
  todayPaidAmountMinor: number
}

export interface PageView<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export type MembershipTier = 'FREE' | 'VIP' | 'SVIP'

export interface ProfileFieldDefinitionView {
  id: number
  fieldCode: string
  label: string
  storageKind: 'CORE' | 'DYNAMIC'
  dataType: string
  required: boolean
  enabled: boolean
  options: string[]
  sortOrder: number
  instructions: string | null
  version: number
}

export interface AdminProfileListItem {
  id: number
  profileNo: string
  phone: string
  membershipTier: MembershipTier
  /** DRAFT / COMPLETED —— 没有审核环节，保存即可见。 */
  status: string
  updatedAt: string
}

export interface AdminProfileFieldValue {
  fieldCode: string
  label: string
  dataType: string
  value: string | null
}

/** previewUrl 是 15 分钟内有效的签名地址，不落库。 */
export interface AdminProfilePhoto {
  id: number
  category: 'AVATAR' | 'LIFE'
  sortOrder: number
  previewUrl: string
}

export interface AdminProfileDetail {
  id: number
  profileNo: string
  phone: string
  membershipTier: MembershipTier
  membershipCreditMinor: number
  status: string
  createdAt: string
  updatedAt: string
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
  dynamicFields: AdminProfileFieldValue[]
  photos: AdminProfilePhoto[]
}

export interface AdminPaymentOrderItem {
  id: number
  outTradeNo: string
  phone: string
  channel: string
  amountMinor: number
  status: 'CREATED' | 'PAID' | 'CLOSED'
  paidAt: string | null
  createdAt: string
}

export interface AdminActivationCodeItem {
  id: number
  code: string
  boundPhone: string
  /** 绑定手机号当前是否已注册；未注册时该码可能被抢注者领走。 */
  boundPhoneRegistered: boolean
  grantedTier: 'VIP' | 'SVIP'
  status: 'UNUSED' | 'USED' | 'REVOKED'
  note: string | null
  createdAt: string
  redeemedAt: string | null
  redeemedPhone: string | null
}
