import { describe, expect, it } from 'vitest'
import {
  activationCodeStatusMeta,
  amountLabel,
  fieldTypeLabel,
  membershipTierMeta,
  paymentOrderStatusMeta,
  profileStatusMeta,
  storageKindLabel,
} from './presentation'

describe('admin presentation helpers', () => {
  it('localizes membership tiers', () => {
    expect(membershipTierMeta('FREE')).toEqual({ label: '普通', tone: 'info' })
    expect(membershipTierMeta('VIP')).toEqual({ label: 'VIP', tone: 'success' })
    expect(membershipTierMeta('SVIP')).toEqual({ label: 'SVIP', tone: 'warning' })
  })

  it('localizes profile completeness', () => {
    expect(profileStatusMeta('DRAFT')).toEqual({ label: '未填完', tone: 'warning' })
    expect(profileStatusMeta('COMPLETED')).toEqual({ label: '已完善', tone: 'success' })
  })

  it('localizes payment order and activation code statuses', () => {
    expect(paymentOrderStatusMeta('CREATED')).toEqual({ label: '待支付', tone: 'warning' })
    expect(paymentOrderStatusMeta('PAID')).toEqual({ label: '已支付', tone: 'success' })
    expect(paymentOrderStatusMeta('CLOSED')).toEqual({ label: '已关闭', tone: 'info' })
    expect(activationCodeStatusMeta('UNUSED')).toEqual({ label: '未使用', tone: 'warning' })
    expect(activationCodeStatusMeta('USED')).toEqual({ label: '已兑换', tone: 'success' })
    expect(activationCodeStatusMeta('REVOKED')).toEqual({ label: '已作废', tone: 'danger' })
  })

  it('renders minor units as yuan', () => {
    expect(amountLabel(0)).toBe('¥0.00')
    expect(amountLabel(1)).toBe('¥0.01')
    expect(amountLabel(9900)).toBe('¥99.00')
    expect(amountLabel(59900)).toBe('¥599.00')
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
