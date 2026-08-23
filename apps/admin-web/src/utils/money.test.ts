import { describe, expect, it } from 'vitest'
import { minorToYuan, yuanToMinor } from './money'

describe('金额换算', () => {
  it('分换算成元', () => {
    expect(minorToYuan(1)).toBe(0.01)
    expect(minorToYuan(100)).toBe(1)
    expect(minorToYuan(12_800)).toBe(128)
    expect(minorToYuan(10_000_000)).toBe(100_000)
  })

  it('元换算成分', () => {
    expect(yuanToMinor(0.01)).toBe(1)
    expect(yuanToMinor(128)).toBe(12_800)
    expect(yuanToMinor(599)).toBe(59_900)
  })

  it('浮点乘法的误差不会少收一分钱', () => {
    // 129.99 * 100 === 12998.999999999998，截断就是 12998。
    expect(yuanToMinor(129.99)).toBe(12_999)
    expect(yuanToMinor(0.29)).toBe(29)
    expect(yuanToMinor(1.1)).toBe(110)
  })

  it('来回换算保持原值', () => {
    for (const minor of [1, 29, 100, 12_999, 59_900, 10_000_000]) {
      expect(yuanToMinor(minorToYuan(minor))).toBe(minor)
    }
  })
})
