/**
 * 金额换算。传输与存储一律用「分」（后端、库、支付渠道都是），界面按「元」录入。
 * 两个方向都过一次四舍五入——浮点乘除会差出一分钱。
 */

/** 分 → 元。 */
export function minorToYuan(amountMinor: number): number {
  return Math.round(amountMinor) / 100
}

/**
 * 元 → 分。必须四舍五入而不是截断：`129.99 * 100` 在浮点里是
 * `12998.999999999998`，取整截断会少收一分钱。
 */
export function yuanToMinor(amountYuan: number): number {
  return Math.round(amountYuan * 100)
}
