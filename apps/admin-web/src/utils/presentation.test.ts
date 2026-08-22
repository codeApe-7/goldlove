import { describe, expect, it } from 'vitest'
import {
  accountStatusMeta,
  activationCodeStatusMeta,
  ageLabel,
  amountLabel,
  fieldTypeLabel,
  membershipTierMeta,
  minuteLabel,
  orDash,
  paymentOrderStatusMeta,
  profileStatusMeta,
  shortProfileNo,
  storageKindLabel,
} from './presentation'

describe('admin presentation helpers', () => {
  it('localizes membership tiers', () => {
    expect(membershipTierMeta('FREE')).toEqual({ label: '普通', tone: 'neutral' })
    expect(membershipTierMeta('VIP')).toEqual({ label: 'VIP', tone: 'success' })
    expect(membershipTierMeta('SVIP')).toEqual({ label: 'SVIP', tone: 'warning' })
  })

  it('localizes profile completeness with the spec wording', () => {
    // 规范图的状态说明用的是「草稿 / 已建档」，后台文案跟规范图对齐。
    expect(profileStatusMeta('DRAFT')).toEqual({ label: '草稿', tone: 'warning' })
    expect(profileStatusMeta('COMPLETED')).toEqual({ label: '已建档', tone: 'success' })
  })

  it('localizes account status separately from completeness', () => {
    expect(accountStatusMeta('ACTIVE')).toEqual({ label: '正常', tone: 'info' })
    expect(accountStatusMeta('SUSPENDED')).toEqual({ label: '已停用', tone: 'danger' })
    expect(accountStatusMeta('CLOSED')).toEqual({ label: '已注销', tone: 'neutral' })
  })

  it('localizes payment order and activation code statuses', () => {
    expect(paymentOrderStatusMeta('CREATED')).toEqual({ label: '待支付', tone: 'warning' })
    expect(paymentOrderStatusMeta('PAID')).toEqual({ label: '已支付', tone: 'success' })
    expect(paymentOrderStatusMeta('CLOSED')).toEqual({ label: '已关闭', tone: 'neutral' })
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

  it('derives age from the birth date', () => {
    const today = new Date('2026-08-22T00:00:00')
    expect(ageLabel('1992-03-30', today)).toBe('34')
    // 生日还没到，按前一岁算。
    expect(ageLabel('1992-12-31', today)).toBe('33')
    expect(ageLabel('2026-08-22', today)).toBe('0')
  })

  it('keeps age empty for missing, unparsable or future birth dates', () => {
    const today = new Date('2026-08-22T00:00:00')
    expect(ageLabel(null, today)).toBe('—')
    expect(ageLabel('', today)).toBe('—')
    expect(ageLabel('不是日期', today)).toBe('—')
    expect(ageLabel('2030-01-01', today)).toBe('—')
  })

  it('shortens the profile number for the table', () => {
    expect(shortProfileNo('a1b2c3d4-e5f6-7890-abcd-ef1234567890')).toBe('A1B2C3D4')
  })

  it('tolerates a missing profile number', () => {
    // 接口真的漏发过这个字段（uuid 列没映射上），在 null 上 slice 会让整页白屏。
    expect(shortProfileNo(null)).toBe('—')
    expect(shortProfileNo(undefined)).toBe('—')
    expect(shortProfileNo('')).toBe('—')
  })

  it('renders blanks as a dash', () => {
    expect(orDash(null)).toBe('—')
    expect(orDash(undefined)).toBe('—')
    expect(orDash('')).toBe('—')
    expect(orDash(0)).toBe('0')
    expect(orDash('上海')).toBe('上海')
  })

  it('trims timestamps down to minutes', () => {
    expect(minuteLabel('2024-05-10T14:30:45+08:00')).toMatch(/^2024-05-10 \d{2}:\d{2}$/)
    expect(minuteLabel(null)).toBe('—')
    expect(minuteLabel('不是时间')).toBe('不是时间')
  })
})
