import { afterEach, describe, expect, it, vi } from 'vitest'
import { goBackOr, goToPage } from './navigation'
import vipPageSource from '../pages/vip/index.vue?raw'

function stubUni(overrides: Record<string, unknown> = {}) {
  const api = {
    navigateBack: vi.fn(),
    switchTab: vi.fn(),
    redirectTo: vi.fn(),
    ...overrides,
  }
  vi.stubGlobal('uni', api)
  return api
}

/** 页面栈深度：1 = 当前页就是栈底，没有上一级。 */
function stubPageStack(depth: number): void {
  vi.stubGlobal(
    'getCurrentPages',
    vi.fn(() => Array.from({ length: depth }, () => ({}))),
  )
}

describe('goBackOr', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('pops the page stack when the page was pushed by navigateTo', () => {
    const api = stubUni()
    stubPageStack(2)

    goBackOr('/pages/mine/index')

    expect(api.navigateBack).toHaveBeenCalledTimes(1)
    expect(api.switchTab).not.toHaveBeenCalled()
  })

  it('lands on the fallback tab page when the current page is the only one in the stack', () => {
    // 回归用例：易支付整页跳转回 #/pages/vip/index 后是全新文档，页面栈只剩会员页，
    // 裸 navigateBack 会静默失败 —— 这正是「点返回没反应」的现场。
    const api = stubUni()
    stubPageStack(1)

    goBackOr('/pages/mine/index')

    expect(api.navigateBack).not.toHaveBeenCalled()
    expect(api.switchTab).toHaveBeenCalledWith({ url: '/pages/mine/index' })
  })

  it('lands on the fallback when the runtime exposes no page stack at all', () => {
    const api = stubUni()

    goBackOr('/pages/mine/index')

    expect(api.navigateBack).not.toHaveBeenCalled()
    expect(api.switchTab).toHaveBeenCalledWith({ url: '/pages/mine/index' })
  })

  it('lands on the fallback when navigateBack itself fails', () => {
    const api = stubUni({
      navigateBack: vi.fn((options?: { fail?: (result: unknown) => void }) => {
        options?.fail?.({ errMsg: 'navigateBack:fail cannot navigate back at first page' })
      }),
    })
    stubPageStack(2)

    goBackOr('/pages/mine/index')

    expect(api.switchTab).toHaveBeenCalledWith({ url: '/pages/mine/index' })
  })
})

describe('goToPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uses switchTab for tabBar pages and redirectTo for the rest', () => {
    const api = stubUni()

    goToPage('/pages/profile/index')
    goToPage('/pages/auth/index')

    expect(api.switchTab).toHaveBeenCalledWith({ url: '/pages/profile/index' })
    expect(api.redirectTo).toHaveBeenCalledWith({ url: '/pages/auth/index' })
  })
})

describe('vip page back button', () => {
  it('routes through goBackOr instead of a bare navigateBack', () => {
    expect(vipPageSource).toContain("goBackOr('/pages/mine/index')")
    expect(vipPageSource).not.toContain('uni.navigateBack()')
  })
})
