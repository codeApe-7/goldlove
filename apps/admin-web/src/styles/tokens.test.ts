import { describe, expect, it } from 'vitest'
import tokens from './tokens.css?raw'
import theme from './theme.css?raw'

/**
 * 令牌保真断言。规范图第 1 区块给的是具体数值，改动这些数值等于改变设计，
 * 应该是一次有意识的决定而不是顺手调的副作用，所以在这里逐条锁住。
 */
describe('后台设计令牌', () => {
  it('色彩与规范图 1.1 / 1.4 一致', () => {
    const colors: Record<string, string> = {
      '--ds-sidebar': '#0f1115',
      '--ds-surface': '#ffffff',
      '--ds-line': '#e5e7eb',
      '--ds-primary': '#1d4ed8',
      '--ds-success': '#16a34a',
      '--ds-warning': '#f59e0b',
      '--ds-danger': '#dc2626',
      '--ds-neutral': '#6b7280',
      '--ds-disabled': '#d1d5db',
    }
    for (const [token, value] of Object.entries(colors)) {
      expect(tokens).toContain(`${token}: ${value};`)
    }
  })

  it('字号与字重与规范图 1.3 一致', () => {
    expect(tokens).toContain('--ds-h1-size: 28px;')
    expect(tokens).toContain('--ds-h1-weight: 700;')
    expect(tokens).toContain('--ds-h2-size: 20px;')
    expect(tokens).toContain('--ds-h2-weight: 600;')
    expect(tokens).toContain('--ds-h3-size: 16px;')
    expect(tokens).toContain('--ds-h3-weight: 600;')
    expect(tokens).toContain('--ds-body-size: 14px;')
    expect(tokens).toContain('--ds-caption-size: 12px;')
    expect(tokens).toContain('Inter, "PingFang SC"')
  })

  it('圆角与阴影与规范图 1.2 一致', () => {
    expect(tokens).toContain('--ds-radius-card: 8px;')
    expect(tokens).toContain('--ds-radius-control: 6px;')
    expect(tokens).toContain('--ds-shadow-card: 0 1px 2px rgba(16, 24, 40, 0.04);')
    expect(tokens).toContain('--ds-shadow-overlay: 0 8px 24px rgba(16, 24, 40, 0.08);')
  })

  it('Element Plus 主色连派生色一起覆盖', () => {
    // 只改 --el-color-primary 会留下 EP 自带的浅蓝：plain / disabled / hover
    // 用的是 light-N 与 dark-2，漏一个就会在界面上露出两种蓝。
    expect(theme).toContain('--el-color-primary: var(--ds-primary);')
    for (const step of [3, 5, 7, 8, 9]) {
      expect(theme).toContain(`--el-color-primary-light-${step}:`)
    }
    expect(theme).toContain('--el-color-primary-dark-2:')
  })

  it('字号与圆角接到了 Element Plus 的基准变量上', () => {
    expect(theme).toContain('--el-font-size-base: var(--ds-body-size);')
    expect(theme).toContain('--el-border-radius-base: var(--ds-radius-control);')
  })
})
