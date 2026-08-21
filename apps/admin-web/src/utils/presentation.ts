export type MembershipTier = 'FREE' | 'VIP' | 'SVIP'
export type ProfileStatus = 'DRAFT' | 'COMPLETED'
export type PaymentOrderStatus = 'CREATED' | 'PAID' | 'CLOSED'
export type ActivationCodeStatus = 'UNUSED' | 'USED' | 'REVOKED'

type Tone = 'info' | 'warning' | 'success' | 'danger'

export function membershipTierMeta(tier: MembershipTier): { label: string; tone: Tone } {
  return ({
    FREE: { label: '普通', tone: 'info' },
    VIP: { label: 'VIP', tone: 'success' },
    SVIP: { label: 'SVIP', tone: 'warning' },
  } as const)[tier] ?? { label: tier, tone: 'info' }
}

export function profileStatusMeta(status: ProfileStatus | string): { label: string; tone: Tone } {
  return ({
    DRAFT: { label: '未填完', tone: 'warning' },
    COMPLETED: { label: '已完善', tone: 'success' },
  } as const)[status as ProfileStatus] ?? { label: status, tone: 'info' }
}

export function paymentOrderStatusMeta(status: PaymentOrderStatus): { label: string; tone: Tone } {
  return ({
    CREATED: { label: '待支付', tone: 'warning' },
    PAID: { label: '已支付', tone: 'success' },
    CLOSED: { label: '已关闭', tone: 'info' },
  } as const)[status] ?? { label: status, tone: 'info' }
}

export function activationCodeStatusMeta(
  status: ActivationCodeStatus,
): { label: string; tone: Tone } {
  return ({
    UNUSED: { label: '未使用', tone: 'warning' },
    USED: { label: '已兑换', tone: 'success' },
    REVOKED: { label: '已作废', tone: 'danger' },
  } as const)[status] ?? { label: status, tone: 'info' }
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
