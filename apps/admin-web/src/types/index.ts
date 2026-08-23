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

/** 账号状态。停用是账号级别的动作，档案自身只有完成度。 */
export type AccountStatus = 'ACTIVE' | 'SUSPENDED' | 'CLOSED'

/** 列表排序。后端是白名单枚举，传别的值会被拒。 */
export type ProfileSort =
  | 'UPDATED_DESC'
  | 'CREATED_DESC'
  | 'CREATED_ASC'
  | 'PHONE_ASC'
  | 'PHONE_DESC'

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
  /** 停用 / 启用调的是账号接口，列表必须带上账号 ID。 */
  accountId: number
  phone: string
  membershipTier: MembershipTier
  accountStatus: AccountStatus
  /** DRAFT / COMPLETED —— 没有审核环节，保存即可见。 */
  status: string
  gender: string | null
  /** 年龄由前端从出生日期算，服务端不下发年龄。 */
  birthDate: string | null
  city: string | null
  createdAt: string
  updatedAt: string
}

/** 档案列表各 tab 的数量。统计忽略 tab 自身的条件，只吃筛选条上的条件。 */
export interface AdminProfileCounts {
  total: number
  draft: number
  completed: number
  suspended: number
  paid: number
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
  accountId: number
  phone: string
  membershipTier: MembershipTier
  membershipCreditMinor: number
  accountStatus: AccountStatus
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

/** 停用 / 启用接口的返回值。 */
export interface AccountStatusView {
  accountId: number
  status: AccountStatus
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

/**
 * 支付设置。金额有两个来源：后台设过就用后台的（`managedInAdmin` 为 true），
 * 没设过则回落到服务端配置 `configuredAmountMinor`，所以两个值都下发。
 * 上下限也由后端给，前端不再抄一份。
 */
export interface PaymentSettingView {
  vipUpgradeAmountMinor: number
  configuredAmountMinor: number
  managedInAdmin: boolean
  updatedAt: string | null
  /** 下单商品描述，只读——仍由服务端环境变量决定。 */
  orderDescription: string
  minAmountMinor: number
  maxAmountMinor: number
}

// ---- 课程模块 ----

/** 图文 / 视频 / 纯文本。图文与纯文本的正文存库，视频存 COS。 */
export type CourseContentType = 'ARTICLE' | 'VIDEO' | 'TEXT'

export type CourseStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

/** 合集只有显示与隐藏两态——它下面挂着课程，后端不给 DELETE。 */
export type CourseCollectionStatus = 'ACTIVE' | 'HIDDEN'

export interface AdminCourseCollectionView {
  id: number
  name: string
  description: string | null
  sortOrder: number
  status: CourseCollectionStatus
  courseCount: number
  publishedCourseCount: number
  createdAt: string
  updatedAt: string
}

export interface AdminCourseListItem {
  id: number
  collectionId: number
  collectionName: string
  /** 教材名称。 */
  title: string
  subtitle: string | null
  contentType: CourseContentType
  status: CourseStatus
  /** 讲师。 */
  authorName: string | null
  /** 只登记不校验，允许为空。 */
  videoDurationSeconds: number | null
  sortOrder: number
  publishedAt: string | null
  updatedAt: string
  /** 乐观锁版本，PUT 时要原样回传。 */
  version: number
}

/** coverPreviewUrl / videoPreviewUrl 都是签名地址，不落库，刷新后会变。 */
export interface AdminCourseDetail extends AdminCourseListItem {
  summary: string | null
  contentMarkdown: string | null
  coverObjectKey: string | null
  coverPreviewUrl: string | null
  videoObjectKey: string | null
  videoPreviewUrl: string | null
  videoSizeBytes: number | null
  videoContentType: string | null
  createdAt: string
}

/** 新建与修改共用一个请求体；`expectedVersion` 只有修改时带。 */
export interface SaveCoursePayload {
  collectionId: number
  title: string
  subtitle?: string | null
  summary?: string | null
  authorName?: string | null
  contentType: CourseContentType
  contentMarkdown?: string | null
  coverObjectKey?: string | null
  videoObjectKey?: string | null
  videoDurationSeconds?: number | null
  videoSizeBytes?: number | null
  videoContentType?: string | null
  sortOrder?: number
  expectedVersion?: number
}

/** 正文里的插图。previewUrl 是签名地址，编辑器把它插进 markdown。 */
export interface CourseImageAsset {
  objectKey: string
  previewUrl: string
  sizeBytes: number
  contentType: string
}

/** `.md` 上传的回执：只把文本读回来，不落库。 */
export interface CourseMarkdownAsset {
  content: string
  sizeBytes: number
}

/** 分块上传会话。`partSizeBytes` 由后端决定，前端照它切片。 */
export interface CourseVideoUploadSession {
  uploadId: string
  objectKey: string
  partSizeBytes: number
}

export interface CourseVideoPartResult {
  partNumber: number
  etag: string
}

export interface CourseVideoAsset {
  objectKey: string
  sizeBytes: number
  contentType: string
  /** 短时签名地址，让管理员保存前就能回放确认传对了文件。不落库，刷新后会变。 */
  previewUrl: string
}

/** 课程表单里的视频字段。时长可为空，不参与任何校验。 */
export interface CourseVideoBinding {
  objectKey: string | null
  sizeBytes: number | null
  contentType: string | null
  durationSeconds: number | null
  /** 已保存课程回显用的签名地址，新上传的视频没有。 */
  previewUrl?: string | null
}
