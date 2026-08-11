export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export function reviewStatusMeta(status: ReviewStatus): {
  label: string
  tone: 'warning' | 'success' | 'danger'
} {
  return ({
    PENDING: { label: '待审核', tone: 'warning' },
    APPROVED: { label: '已通过', tone: 'success' },
    REJECTED: { label: '已退回', tone: 'danger' },
  } as const)[status]
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
