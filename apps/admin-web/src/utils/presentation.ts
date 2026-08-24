export type MembershipTier = 'FREE' | 'VIP' | 'SVIP'
export type ProfileStatus = 'DRAFT' | 'COMPLETED'
export type AccountStatus = 'ACTIVE' | 'SUSPENDED' | 'CLOSED'
export type PaymentOrderStatus = 'CREATED' | 'PAID' | 'CLOSED'
export type ActivationCodeStatus = 'UNUSED' | 'USED' | 'REVOKED'

type Tone = 'info' | 'warning' | 'success' | 'danger' | 'neutral'

export function membershipTierMeta(tier: MembershipTier): { label: string; tone: Tone } {
  return ({
    FREE: { label: '普通', tone: 'neutral' },
    VIP: { label: 'VIP', tone: 'success' },
    SVIP: { label: 'SVIP', tone: 'warning' },
  } as const)[tier] ?? { label: tier, tone: 'neutral' }
}

export function profileStatusMeta(status: ProfileStatus | string): { label: string; tone: Tone } {
  return ({
    DRAFT: { label: '草稿', tone: 'warning' },
    COMPLETED: { label: '已建档', tone: 'success' },
  } as const)[status as ProfileStatus] ?? { label: status, tone: 'neutral' }
}

/** 账号状态与档案完成度是两件事，分两列展示，不互相覆盖。 */
export function accountStatusMeta(status: AccountStatus | string): { label: string; tone: Tone } {
  return ({
    ACTIVE: { label: '正常', tone: 'info' },
    SUSPENDED: { label: '已停用', tone: 'danger' },
    CLOSED: { label: '已注销', tone: 'neutral' },
  } as const)[status as AccountStatus] ?? { label: status, tone: 'neutral' }
}

export function paymentOrderStatusMeta(status: PaymentOrderStatus): { label: string; tone: Tone } {
  return ({
    CREATED: { label: '待支付', tone: 'warning' },
    PAID: { label: '已支付', tone: 'success' },
    CLOSED: { label: '已关闭', tone: 'neutral' },
  } as const)[status] ?? { label: status, tone: 'neutral' }
}

export function activationCodeStatusMeta(
  status: ActivationCodeStatus,
): { label: string; tone: Tone } {
  return ({
    UNUSED: { label: '未使用', tone: 'warning' },
    USED: { label: '已兑换', tone: 'success' },
    REVOKED: { label: '已作废', tone: 'danger' },
  } as const)[status] ?? { label: status, tone: 'neutral' }
}

/** 金额一律以分存储，展示时才换算成元。 */
export function amountLabel(amountMinor: number): string {
  return `¥${(amountMinor / 100).toFixed(2)}`
}

export function fieldTypeLabel(type: string): string {
  return {
    TEXT: '文本',
    LONG_TEXT: '长文本',
    INTEGER: '整数',
    DECIMAL: '小数',
    DATE: '日期',
    BOOLEAN: '布尔',
    SINGLE_OPTION: '单选',
  }[type] ?? type
}

export function storageKindLabel(kind: string): string {
  return { CORE: '核心', DYNAMIC: '自定义' }[kind] ?? kind
}

/**
 * 年龄现在是档案上的一列，访客自己填，前端不再从出生日期推。
 *
 * <p>之前是「服务端给出生日期、前端算年龄」，因为算的那一刻取决于「今天是哪天」。
 * 现在连出生日期都不收了——档案要的一直只是「多大」——于是这里退化成纯展示：
 * 有值就显示，没值显示占位符。不做区间校验，那是后端与库的 CHECK 的事。</p>
 */
export function ageLabel(age: number | null): string {
  return age === null || age === undefined ? '—' : String(age)
}

/**
 * 档案编号是 UUID，列表里只展示前 8 位；关键词搜索支持用这一段命中。
 *
 * 容忍 null：这个值来自远端接口，一旦某天它没下发，整张表和详情抽屉都会因为
 * 在 null 上调 slice 而白屏——线上就真出过这一次（uuid 列没映射上）。
 */
export function shortProfileNo(profileNo: string | null | undefined): string {
  return profileNo ? profileNo.slice(0, 8).toUpperCase() : '—'
}

/** 空值统一显示成破折号，避免表格里出现 null / undefined。 */
export function orDash(value: string | number | null | undefined): string {
  return value === null || value === undefined || value === '' ? '—' : String(value)
}

/** 把服务端的 ISO 时间戳压成「2024-05-10 14:30」，表格里不需要秒。 */
export function minuteLabel(value: string | null): string {
  if (!value) {
    return '—'
  }
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${parsed.getFullYear()}-${pad(parsed.getMonth() + 1)}-${pad(parsed.getDate())}`
    + ` ${pad(parsed.getHours())}:${pad(parsed.getMinutes())}`
}

/** 课程上下架状态。ARCHIVED 是下架而非删除，所以用中性色而不是危险色。 */
export function courseStatusMeta(status: string): { label: string; tone: Tone } {
  switch (status) {
    case 'PUBLISHED':
      return { label: '已发布', tone: 'success' }
    case 'ARCHIVED':
      return { label: '已下架', tone: 'neutral' }
    default:
      return { label: '草稿', tone: 'warning' }
  }
}

export function courseContentTypeLabel(contentType: string): string {
  switch (contentType) {
    case 'ARTICLE':
      return '图文'
    case 'VIDEO':
      return '视频'
    case 'TEXT':
      return '纯文本'
    default:
      return contentType
  }
}

export function courseCollectionStatusMeta(status: string): { label: string; tone: Tone } {
  return status === 'HIDDEN'
    ? { label: '已隐藏', tone: 'neutral' }
    : { label: '显示中', tone: 'success' };
}

/**
 * 秒 → 「12:30」。
 *
 * 时长是纯展示项：后端只登记不校验，允许为空，所以这里也不对时长做任何区间判断，
 * 空值就是一个破折号。
 */
export function durationLabel(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || !Number.isFinite(seconds) || seconds <= 0) {
    return '—'
  }
  const whole = Math.round(seconds)
  const minutes = Math.floor(whole / 60)
  const rest = whole % 60
  return `${minutes}:${String(rest).padStart(2, '0')}`
}

/** 字节 → 人能读的大小。视频动辄上百 MB，表格里显示字节数没人看得懂。 */
export function fileSizeLabel(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined || !Number.isFinite(bytes) || bytes <= 0) {
    return '—'
  }
  const units = ['B', 'KB', 'MB', 'GB']
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  return `${unit === 0 ? value : value.toFixed(1)} ${units[unit]}`
}
