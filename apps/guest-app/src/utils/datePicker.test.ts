import { describe, expect, it } from 'vitest'
import {
  birthYearRange,
  clampDay,
  dateWheelColumns,
  daysInMonth,
  formatDateValue,
  indexesToParts,
  parseDateValue,
  partsToIndexes,
} from './datePicker'

describe('daysInMonth', () => {
  it('给出各月天数', () => {
    expect(daysInMonth(2026, 1)).toBe(31)
    expect(daysInMonth(2026, 4)).toBe(30)
    expect(daysInMonth(2026, 2)).toBe(28)
  })

  it('闰年二月是 29 天', () => {
    expect(daysInMonth(2024, 2)).toBe(29)
    expect(daysInMonth(2000, 2)).toBe(29)
    expect(daysInMonth(1900, 2)).toBe(28)
  })
})

describe('birthYearRange', () => {
  it('从当年往前推 100 年，上界是当年', () => {
    expect(birthYearRange(2026)).toEqual({ min: 1926, max: 2026 })
  })
})

describe('dateWheelColumns', () => {
  it('三列都带中文单位且日数跟随年月', () => {
    const columns = dateWheelColumns({ year: 2024, month: 2, day: 1 }, 2026)

    expect(columns.years[0]).toBe('1926年')
    expect(columns.years.at(-1)).toBe('2026年')
    expect(columns.months).toHaveLength(12)
    expect(columns.months[0]).toBe('01月')
    expect(columns.days).toHaveLength(29)
    expect(columns.days.at(-1)).toBe('29日')
  })

  it('非闰年二月只给 28 天', () => {
    expect(dateWheelColumns({ year: 2026, month: 2, day: 1 }, 2026).days).toHaveLength(28)
  })
})

describe('partsToIndexes / indexesToParts', () => {
  it('往返转换保持同一个日期', () => {
    const parts = { year: 1992, month: 3, day: 30 }
    const columns = dateWheelColumns(parts, 2026)

    const indexes = partsToIndexes(parts, 2026)
    expect(indexes).toEqual([66, 2, 29])
    expect(indexesToParts(indexes, 2026)).toEqual(parts)
    expect(columns.days[indexes[2]]).toBe('30日')
  })

  it('滚轮索引越界时收敛到当月最后一天', () => {
    // 从 1 月 31 日滚到 2 月，日列会缩短，索引 30 已经不存在。
    expect(indexesToParts([66, 1, 30], 2026)).toEqual({ year: 1992, month: 2, day: 29 })
  })
})

describe('clampDay', () => {
  it('把不存在的日期收敛到当月最后一天', () => {
    expect(clampDay({ year: 2026, month: 2, day: 31 })).toEqual({ year: 2026, month: 2, day: 28 })
    expect(clampDay({ year: 2024, month: 2, day: 31 })).toEqual({ year: 2024, month: 2, day: 29 })
  })

  it('合法日期原样返回', () => {
    expect(clampDay({ year: 2026, month: 3, day: 31 })).toEqual({ year: 2026, month: 3, day: 31 })
  })
})

describe('parseDateValue / formatDateValue', () => {
  it('解析 ISO 日期', () => {
    expect(parseDateValue('1992-03-30', 2026)).toEqual({ year: 1992, month: 3, day: 30 })
  })

  it('空值或非法值退回默认年份的 1 月 1 日', () => {
    expect(parseDateValue('', 2026)).toEqual({ year: 1996, month: 1, day: 1 })
    expect(parseDateValue('不是日期', 2026)).toEqual({ year: 1996, month: 1, day: 1 })
  })

  it('格式化补零', () => {
    expect(formatDateValue({ year: 1992, month: 3, day: 5 })).toBe('1992-03-05')
  })
})
