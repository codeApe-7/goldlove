export interface AdminSession {
  id: number
  username: string
  displayName: string
}

export interface AdminDashboardStats {
  pendingReviews: number
  todayRegistrations: number
  todayReviews: number
  totalProfiles: number
}

export interface ProvisionedGuest {
  accountId: number
  phoneMasked: string
  initialCredential: string
}

export interface PageView<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface ProfileReviewListItem {
  revisionId: number
  revisionNumber: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  submittedAt: string
  reviewDeadlineAt: string
  profileNo: string
  currentApprovedRevisionId: number | null
}

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

export interface ProfileReviewPhoto {
  category: 'AVATAR' | 'LIFE'
  sha256: string
  sizeBytes: number
  contentType: string
  width: number
  height: number
  sortOrder: number
  downloadUrl: string
}

export interface ProfileFieldDifference {
  fieldCode: string
  fieldLabel: string
  oldValue: string | null
  newValue: string | null
}

export interface ProfileReviewDetail {
  profileNo: string
  revisionId: number
  revisionNumber: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
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
  submittedAt: string
  reviewDeadlineAt: string
  reviewedAt: string | null
  version: number
  dynamicFields: unknown[]
  lastApprovedRevisionId: number | null
  photos: ProfileReviewPhoto[]
  differences: ProfileFieldDifference[]
}
