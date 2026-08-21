/** 出生日期滚轮的列生成与索引换算。规范图 3.9 是年 / 月 / 日三列。 */

export interface DateParts {
  year: number
  month: number
  day: number
}

export interface DateWheelColumns {
  years: string[]
  months: string[]
  days: string[]
}

const YEAR_SPAN = 100
// 出生日期的默认落点。停在当年对婚恋档案毫无意义，往前 30 年更接近真实取值。
const DEFAULT_AGE = 30
const ISO_DATE = /^(\d{4})-(\d{1,2})-(\d{1,2})$/

export function daysInMonth(year: number, month: number): number {
  // 下个月的第 0 天就是本月最后一天，闰年由 Date 自己算。
  return new Date(Date.UTC(year, month, 0)).getUTCDate()
}

export function birthYearRange(currentYear: number): { min: number; max: number } {
  return { min: currentYear - YEAR_SPAN, max: currentYear }
}

export function dateWheelColumns(parts: DateParts, currentYear: number): DateWheelColumns {
  const { min, max } = birthYearRange(currentYear)
  const years: string[] = []
  for (let year = min; year <= max; year += 1) {
    years.push(`${year}年`)
  }
  return {
    years,
    months: Array.from({ length: 12 }, (_, index) => `${pad(index + 1)}月`),
    days: Array.from(
      { length: daysInMonth(parts.year, parts.month) },
      (_, index) => `${pad(index + 1)}日`,
    ),
  }
}

export function partsToIndexes(
  parts: DateParts,
  currentYear: number,
): [number, number, number] {
  const { min } = birthYearRange(currentYear)
  return [parts.year - min, parts.month - 1, parts.day - 1]
}

export function indexesToParts(
  indexes: readonly number[],
  currentYear: number,
): DateParts {
  const { min, max } = birthYearRange(currentYear)
  const year = clamp(min + (indexes[0] ?? 0), min, max)
  const month = clamp((indexes[1] ?? 0) + 1, 1, 12)
  // 从 31 天的月份滚到 2 月时日列会变短，滚轮回传的索引可能已经越界。
  const day = clamp((indexes[2] ?? 0) + 1, 1, daysInMonth(year, month))
  return { year, month, day }
}

export function clampDay(parts: DateParts): DateParts {
  const lastDay = daysInMonth(parts.year, parts.month)
  return parts.day <= lastDay ? parts : { ...parts, day: lastDay }
}

export function parseDateValue(value: string, currentYear: number): DateParts {
  const match = ISO_DATE.exec(value.trim())
  if (!match) {
    return { year: currentYear - DEFAULT_AGE, month: 1, day: 1 }
  }
  const { min, max } = birthYearRange(currentYear)
  return clampDay({
    year: clamp(Number(match[1]), min, max),
    month: clamp(Number(match[2]), 1, 12),
    day: Math.max(1, Number(match[3])),
  })
}

export function formatDateValue(parts: DateParts): string {
  return `${parts.year}-${pad(parts.month)}-${pad(parts.day)}`
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}
