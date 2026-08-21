import { describe, expect, it } from 'vitest'
import { profileControl } from './profileControls'
import type { GuestFieldDefinition } from '@/types'

function definition(patch: Partial<GuestFieldDefinition>): GuestFieldDefinition {
  return {
    id: 1,
    fieldCode: 'demo',
    label: '示例',
    storageKind: 'CORE',
    dataType: 'TEXT',
    required: true,
    options: [],
    sortOrder: 10,
    instructions: '',
    ...patch,
  }
}

describe('profileControl 按字段编码指定专用控件', () => {
  it('性别用并排格子单选', () => {
    expect(profileControl(definition({
      fieldCode: 'gender',
      dataType: 'SINGLE_OPTION',
      options: ['男', '女', '不公开'],
    }))).toBe('radio-tiles')
  })

  it('所在城市用三级联动，与后端是否下发选项无关', () => {
    expect(profileControl(definition({ fieldCode: 'city', dataType: 'TEXT' }))).toBe('cascade')
  })

  it('年薪用底部弹层滚轮', () => {
    expect(profileControl(definition({
      fieldCode: 'income_range',
      dataType: 'SINGLE_OPTION',
      options: ['20万以下', '20万-30万'],
    }))).toBe('wheel-select')
  })

  it('职业用可搜索下拉', () => {
    expect(profileControl(definition({
      fieldCode: 'occupation',
      dataType: 'SINGLE_OPTION',
      options: ['产品经理', '项目经理'],
    }))).toBe('searchable-select')
  })

  it('学历用内联下拉', () => {
    expect(profileControl(definition({
      fieldCode: 'education',
      dataType: 'SINGLE_OPTION',
      options: ['大学本科', '大专'],
    }))).toBe('select')
  })
})

describe('profileControl 在后端还没下发选项时安全回落', () => {
  // V2 迁移是独立发布步骤，前端必须能在迁移前后都正常工作。
  it('学历仍是无选项文本字段时回落成输入框', () => {
    expect(profileControl(definition({
      fieldCode: 'education',
      dataType: 'TEXT',
      options: [],
    }))).toBe('text')
  })

  it('职业仍是无选项文本字段时回落成输入框', () => {
    expect(profileControl(definition({
      fieldCode: 'occupation',
      dataType: 'TEXT',
      options: [],
    }))).toBe('text')
  })

  it('单选字段没有选项时回落成输入框', () => {
    expect(profileControl(definition({
      fieldCode: 'income_range',
      dataType: 'SINGLE_OPTION',
      options: [],
    }))).toBe('text')
  })
})

describe('profileControl 按数据类型兜底', () => {
  it.each([
    ['DATE', 'date'],
    ['INTEGER', 'number'],
    ['DECIMAL', 'number'],
    ['LONG_TEXT', 'textarea'],
    ['BOOLEAN', 'checkbox'],
    ['TEXT', 'text'],
  ] as const)('%s 映射到 %s', (dataType, expected) => {
    expect(profileControl(definition({ dataType }))).toBe(expected)
  })

  it('未特殊指定的单选字段用内联下拉', () => {
    expect(profileControl(definition({
      fieldCode: 'marital_status',
      dataType: 'SINGLE_OPTION',
      options: ['未婚', '离异'],
    }))).toBe('select')
  })
})
