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
 * 年龄由出生日期推导——库里没有年龄列，存一个每年都会过期的数字没有意义。
 * 服务端也不下发年龄：那会把「今天是哪天」的判断挪到服务器时区上。
 *
 * 出生日期手动拆成本地日期再比：`new Date('1992-03-30')` 按 UTC 解析，
 * 与本地的「今天」直接比较会在时区偏移里差出一天，生日当天就会算错。
 */
export function ageLabel(birthDate: string | null, today: Date = new Date()): string {
  const born = parseLocalDate(birthDate)
  if (!born || born > today) {
    return '—'
  }
  let age = today.getFullYear() - born.getFullYear()
  const monthDiff = today.getMonth() - born.getMonth()
  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < born.getDate())) {
    age -= 1
  }
  return age < 0 ? '—' : String(age)
}

function parseLocalDate(value: string | null): Date | null {
  if (!value) {
    return null
  }
  const matched = /^(\d{4})-(\d{2})-(\d{2})/.exec(value)
  if (!matched) {
    return null
  }
  const parsed = new Date(Number(matched[1]), Number(matched[2]) - 1, Number(matched[3]))
  return Number.isNaN(parsed.getTime()) ? null : parsed
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
