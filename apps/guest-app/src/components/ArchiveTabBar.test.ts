import { describe, expect, it } from 'vitest'
import appSource from '../App.vue?raw'
import tabBarSource from './ArchiveTabBar.vue?raw'
import profilePageSource from '../pages/profile/index.vue?raw'
import minePageSource from '../pages/mine/index.vue?raw'

describe('ArchiveTabBar H5 integration', () => {
  it('renders the constrained custom footer on both tab pages', () => {
    expect(profilePageSource).toContain('<ArchiveTabBar current="profile" />')
    expect(minePageSource).toContain('<ArchiveTabBar current="mine" />')
  })

  it('carries exactly the two remaining tabs', () => {
    // 审核环节拆掉后「状态」页不复存在，栅格也要跟着从 3 列改成 2 列。
    expect(tabBarSource).toContain("type ArchiveTabKey = 'profile' | 'mine'")
    expect(tabBarSource).not.toContain('/pages/status/index')
    expect(tabBarSource).toContain('grid-template-columns: repeat(2, 1fr);')
  })

  it('removes the native full-viewport H5 tab bar and placeholder', () => {
    expect(appSource).toMatch(/uni-tabbar\s*\{[^}]*display:\s*none\s*!important;/s)
    expect(appSource).toMatch(
      /\.uni-app--showtabbar\s+uni-page-wrapper\s*\{[^}]*height:\s*100%\s*!important;/s,
    )
    expect(appSource).toMatch(
      /\.uni-app--showtabbar\s+uni-page-wrapper::after\s*\{[^}]*display:\s*none\s*!important;/s,
    )
  })
})
