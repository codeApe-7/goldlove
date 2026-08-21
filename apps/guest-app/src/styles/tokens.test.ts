import { describe, expect, it } from 'vitest'
import tokensSource from './tokens.scss?raw'

/**
 * 这些断言锁住《H5 表单组件与下拉样式规范 v1.0》第 1 区块标注的原始数值。
 * 规范图按 375pt 设计稿标注 px，换算基准 1 设计 px = 2rpx。
 * 任何一档被改动都会在这里失败，避免 token 层随后续改动悄悄漂移。
 */
describe('设计系统 token 与规范图一致', () => {
  it('九个色值加品牌金按规范定义', () => {
    const expected = [
      ['$ds-ink', '#0e0f12'],
      ['$ds-graphite', '#2b2d30'],
      ['$ds-gray', '#8a8d94'],
      ['$ds-porcelain', '#f5f4f2'],
      ['$ds-line', '#e6e4e1'],
      ['$ds-success', '#22c55e'],
      ['$ds-warning', '#f59e0b'],
      ['$ds-error', '#ef4444'],
      ['$ds-info', '#3b82f6'],
      ['$ds-gold', '#c9a96a'],
    ]
    for (const [name, value] of expected) {
      expect(tokensSource).toContain(`${name}: ${value};`)
    }
  })

  it('六档排版的字号与行高按 1px = 2rpx 换算', () => {
    const expected = [
      ['ds-h1', '68rpx', '88rpx', '700'],
      ['ds-h2', '56rpx', '72rpx', '500'],
      ['ds-h3', '40rpx', '56rpx', '500'],
      ['ds-body-1', '32rpx', '48rpx', '400'],
      ['ds-body-2', '28rpx', '40rpx', '400'],
      ['ds-caption', '24rpx', '32rpx', '400'],
    ]
    for (const [mixin, size, lineHeight, weight] of expected) {
      const block = mixinBody(tokensSource, mixin)
      expect(block, `缺少 @mixin ${mixin}`).not.toBe('')
      expect(block).toContain(`font-size: ${size};`)
      expect(block).toContain(`line-height: ${lineHeight};`)
      expect(block).toContain(`font-weight: ${weight};`)
    }
  })

  it('间距按 4pt 基准给出九档', () => {
    const expected = ['8rpx', '16rpx', '24rpx', '32rpx', '40rpx', '48rpx', '64rpx', '80rpx', '128rpx']
    expected.forEach((value, index) => {
      expect(tokensSource).toContain(`$ds-space-${index + 1}: ${value};`)
    })
  })

  it('圆角给出 4/8/16/20/24px 五档', () => {
    const expected = [
      ['$ds-radius-xs', '8rpx'],
      ['$ds-radius-sm', '16rpx'],
      ['$ds-radius-md', '32rpx'],
      ['$ds-radius-lg', '40rpx'],
      ['$ds-radius-xl', '48rpx'],
    ]
    for (const [name, value] of expected) {
      expect(tokensSource).toContain(`${name}: ${value};`)
    }
  })

  it('阴影只给三档，且不定义第四档', () => {
    expect(tokensSource).toContain('$ds-shadow-soft: 0 2rpx 4rpx rgba(0, 0, 0, 0.06);')
    expect(tokensSource).toContain('$ds-shadow-mid: 0 8rpx 24rpx rgba(0, 0, 0, 0.08);')
    expect(tokensSource).toContain('$ds-shadow-deep: 0 16rpx 48rpx rgba(0, 0, 0, 0.12);')
    expect(tokensSource.match(/\$ds-shadow-[a-z]+:/g)).toHaveLength(3)
  })

  it('中文字体栈按规范排序并留系统兜底', () => {
    const stack = tokensSource.slice(tokensSource.indexOf('$ds-font-cn:'))
    const order = ['HarmonyOS Sans SC', 'Source Han Sans SC', 'Noto Sans SC', 'PingFang SC']
    let cursor = -1
    for (const family of order) {
      const found = stack.indexOf(family)
      expect(found, `字体栈缺少 ${family}`).toBeGreaterThan(cursor)
      cursor = found
    }
    expect(stack).toContain('sans-serif')
  })

  it('token 文件本身不产出任何 CSS 规则', () => {
    // tokens.scss 会被十几个组件各自 @use，一旦含裸选择器就会重复打包。
    const withoutComments = tokensSource.replace(/\/\/[^\n]*/g, '')
    const declarationsOutsideMixins = withoutComments
      .replace(/@mixin[\s\S]*?\n\}/g, '')
      .split('\n')
      .filter((line: string) => line.trim().endsWith('{'))
    expect(declarationsOutsideMixins).toEqual([])
  })
})

/** 取出指定 mixin 的函数体，用于逐条核对字号档位。 */
function mixinBody(source: string, name: string): string {
  const match = source.match(new RegExp(`@mixin\\s+${name}\\s*\\{([\\s\\S]*?)\\n\\}`))
  return match ? match[1] : ''
}
