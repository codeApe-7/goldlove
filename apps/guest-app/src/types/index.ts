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
  sha256: string
  sizeBytes: number
  contentType: string
  width: number
  height: number
  sortOrder: number
  downloadUrl: string
  createdAt: string
}
