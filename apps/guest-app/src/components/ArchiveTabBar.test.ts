import { describe, expect, it } from 'vitest'
import appSource from '../App.vue?raw'
import tabBarSource from './ArchiveTabBar.vue?raw'
import profilePageSource from '../pages/profile/index.vue?raw'
import minePageSource from '../pages/mine/index.vue?raw'
import coursePageSource from '../pages/course/index.vue?raw'

describe('ArchiveTabBar H5 integration', () => {
  it('renders the constrained custom footer on every tab page', () => {
    expect(profilePageSource).toContain('<ArchiveTabBar current="profile" />')
    expect(coursePageSource).toContain('<ArchiveTabBar current="course" />')
    expect(minePageSource).toContain('<ArchiveTabBar current="mine" />')
  })

  it('carries the three tabs', () => {
    // 审核环节拆掉后「状态」页不复存在；课程模块上线后补回第三格，栅格跟着回到 3 列。
    expect(tabBarSource).toContain("type ArchiveTabKey = 'profile' | 'course' | 'mine'")
    expect(tabBarSource).not.toContain('/pages/status/index')
    expect(tabBarSource).toContain("url: '/pages/course/index'")
    expect(tabBarSource).toContain('grid-template-columns: repeat(3, 1fr);')
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
