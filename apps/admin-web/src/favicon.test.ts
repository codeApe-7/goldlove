import { describe, expect, it } from 'vitest'
import html from '../index.html?raw'
import favicon from '../public/favicon.svg?raw'

/**
 * 标签页图标用站内标识本身。
 *
 * 这两条锁的是「掉了也没人会注意到」的东西：index.html 平时没人动，
 * 某次依赖升级或模板重生成把 link 标签冲掉，页面照常跑、测试照常绿，
 * 只是标签页悄悄变回浏览器默认的空白图标。
 */
describe('浏览器标签页图标', () => {
  it('index.html 指向 public/ 下的 favicon.svg', () => {
    expect(html).toContain('<link rel="icon" type="image/svg+xml" href="/favicon.svg" />')
  })

  it('图标就是 BrandMark 的七根金色竖条', () => {
    // 与 components/BrandMark.vue 同形。颜色对不上就说明有人只改了一边。
    expect(favicon).toContain('#c9a96a')
    expect(favicon.match(/<rect /g) ?? []).toHaveLength(7)
    // 高度比例 48/72/94/66/94/72/48 —— 中间高、两侧低，左右对称。
    const heights = [...favicon.matchAll(/height="([\d.]+)"/g)].map((m) => Number(m[1]))
    expect(heights).toEqual([...heights].reverse())
    expect(Math.max(...heights)).toBe(heights[2])
  })
})
