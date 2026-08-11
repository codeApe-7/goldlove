import { describe, expect, it } from 'vitest'
import { fieldTypeLabel, reviewStatusMeta, storageKindLabel } from './presentation'

describe('admin presentation helpers', () => {
  it('localizes every review status', () => {
    expect(reviewStatusMeta('PENDING')).toEqual({ label: '待审核', tone: 'warning' })
    expect(reviewStatusMeta('APPROVED')).toEqual({ label: '已通过', tone: 'success' })
    expect(reviewStatusMeta('REJECTED')).toEqual({ label: '已退回', tone: 'danger' })
  })

  it('localizes field metadata', () => {
    expect(fieldTypeLabel('TEXT')).toBe('文本')
    expect(fieldTypeLabel('LONG_TEXT')).toBe('长文本')
    expect(fieldTypeLabel('INTEGER')).toBe('整数')
    expect(fieldTypeLabel('DECIMAL')).toBe('小数')
    expect(fieldTypeLabel('DATE')).toBe('日期')
    expect(fieldTypeLabel('BOOLEAN')).toBe('布尔')
    expect(fieldTypeLabel('SINGLE_OPTION')).toBe('单选')
    expect(storageKindLabel('CORE')).toBe('核心')
    expect(storageKindLabel('DYNAMIC')).toBe('自定义')
  })
})
