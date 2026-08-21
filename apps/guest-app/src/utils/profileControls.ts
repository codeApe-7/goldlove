import type { GuestFieldDefinition } from '@/types'

/**
 * 字段定义 → 录入控件。规范图第 3 区块给几个核心字段单独设计了交互形态，
 * 其余字段按 dataType 兜底。沿用 utils/presentation.ts 里按 fieldCode 分组的既有做法。
 */
export type ProfileControlKind =
  | 'radio-tiles'
  | 'cascade'
  | 'date'
  | 'wheel-select'
  | 'select'
  | 'searchable-select'
  | 'number'
  | 'textarea'
  | 'checkbox'
  | 'text'

// 这几个控件都要求后端下发了选项，没有选项就必须回落。
const OPTION_DRIVEN_CONTROLS: Record<string, ProfileControlKind> = {
  gender: 'radio-tiles',
  income_range: 'wheel-select',
  occupation: 'searchable-select',
  education: 'select',
}

const BY_DATA_TYPE: Record<GuestFieldDefinition['dataType'], ProfileControlKind> = {
  TEXT: 'text',
  LONG_TEXT: 'textarea',
  INTEGER: 'number',
  DECIMAL: 'number',
  DATE: 'date',
  BOOLEAN: 'checkbox',
  SINGLE_OPTION: 'select',
}

export function profileControl(definition: GuestFieldDefinition): ProfileControlKind {
  // 三级联动的省市区数据在前端，后端只把结果当文本存，因此不看选项也不看 dataType。
  if (definition.fieldCode === 'city') {
    return 'cascade'
  }
  const hasOptions = definition.options.length > 0
  const preferred = OPTION_DRIVEN_CONTROLS[definition.fieldCode]
  if (preferred && hasOptions) {
    return preferred
  }
  // V2 迁移是独立发布步骤，迁移前单选字段可能还没有选项，此时退回输入框而不是渲染空下拉。
  if (definition.dataType === 'SINGLE_OPTION' && !hasOptions) {
    return 'text'
  }
  return BY_DATA_TYPE[definition.dataType]
}
