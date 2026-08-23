import { afterEach, describe, expect, it, vi } from 'vitest'
import { request, setUnauthorizedHandler } from './request'

function stubRequest(handler: (options: UniApp.RequestOptions) => void) {
  vi.stubGlobal('uni', {
    request: vi.fn((options: UniApp.RequestOptions) => {
      handler(options)
      return { abort: vi.fn() }
    }),
  })
}

describe('request', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    setUnauthorizedHandler(() => undefined)
    sessionStorage.clear()
  })

  it('unwraps envelope and builds url from base', async () => {
    stubRequest((options) => {
      expect(options.url).toBe('/api/v1/guest/auth/me')
      options.success?.({
        statusCode: 200,
        header: {},
        cookies: [],
        data: { success: true, code: 'OK', message: '成功', data: { id: 1 }, requestId: 'r' },
      } as UniApp.RequestSuccessCallbackResult)
    })
    await expect(request<{ id: number }>({ url: '/guest/auth/me' })).resolves.toEqual({ id: 1 })
  })

  it('rejects business failure with message', async () => {
    stubRequest((options) => {
      options.success?.({
        statusCode: 200,
        header: {},
        cookies: [],
        data: { success: false, code: 'X', message: '失败', data: null, requestId: 'r' },
      } as UniApp.RequestSuccessCallbackResult)
    })
    await expect(request<never>({ url: '/x' })).rejects.toThrow('失败')
  })

  it('invokes unauthorized handler on 401', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    stubRequest((options) => {
      options.success?.({
        statusCode: 401,
        header: {},
        cookies: [],
        data: { code: 'AUTH_NOT_LOGGED_IN' },
      } as UniApp.RequestSuccessCallbackResult)
    })
    await expect(request<never>({ url: '/x' })).rejects.toBeTruthy()
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('does not treat a wrong password as an expired session', async () => {
    // 输错密码时后端返回 401「手机号或密码错误」。以前这里被当成会话过期：
    // 文案换成「请先登录」，还会 reLaunch 回登录页——人本来就在登录页，
    // 结果页面一闪，什么原因都没看到。
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    stubRequest((options) => {
      options.success?.({
        statusCode: 401,
        header: {},
        cookies: [],
        data: { success: false, code: 'AUTH_INVALID_CREDENTIALS', message: '手机号或密码错误' },
      } as UniApp.RequestSuccessCallbackResult)
    })
    await expect(request<never>({ url: '/guest/auth/login' })).rejects.toThrow('手机号或密码错误')
    expect(handler).not.toHaveBeenCalled()
  })

  it('falls back to a human sentence when the body carries no message', async () => {
    stubRequest((options) => {
      options.success?.({
        statusCode: 429,
        header: {},
        cookies: [],
        data: null,
      } as unknown as UniApp.RequestSuccessCallbackResult)
    })
    await expect(request<never>({ url: '/x' })).rejects.toThrow('操作过于频繁，请稍后再试')
  })

  it('attaches bearer token from stored session', async () => {
    sessionStorage.setItem(
      'guest-session',
      JSON.stringify({
        accountId: 7,
        status: 'ACTIVE',
        accessToken: 'tok-1',
        expiresIn: 2592000,
      }),
    )
    stubRequest((options) => {
      expect(options.header).toMatchObject({ Authorization: 'Bearer tok-1' })
      options.success?.({
        statusCode: 200,
        header: {},
        cookies: [],
        data: { success: true, code: 'OK', message: '成功', data: null, requestId: 'r' },
      } as UniApp.RequestSuccessCallbackResult)
    })
    await request<null>({ url: '/guest/profile/draft' })
  })

  it('invokes unauthorized handler on 403 AUTH_ACCOUNT_INACTIVE', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    stubRequest((options) => {
      options.success?.({
        statusCode: 403,
        header: {},
        cookies: [],
        data: {
          success: false,
          code: 'AUTH_ACCOUNT_INACTIVE',
          message: '账号已停用',
          data: null,
          requestId: 'r',
        },
      } as UniApp.RequestSuccessCallbackResult)
    })
    await expect(request<never>({ url: '/x' })).rejects.toThrow('账号已停用')
    expect(handler).toHaveBeenCalledTimes(1)
  })
})

/**
 * 非响应壳的响应必须当失败。
 *
 * 线上真实事故：后端重启窗口里 nginx 返回它自带的 502 HTML 页，
 * `response.data` 是个字符串。原来只判 `success === false`，字符串的 `.success`
 * 是 undefined（不等于 false），于是 resolve 了 `envelope.data`——也就是 undefined。
 * 调用方拿到 undefined 再读字段，报的是「Cannot read properties of undefined (reading 'status')」，
 * 完全指不到「服务在重启」这个真实原因。
 */
describe('非响应壳的响应', () => {
  const notEnvelope = (statusCode: number, data: unknown) => {
    stubRequest((options) => {
      options.success?.({
        statusCode, header: {}, cookies: [], data,
      } as UniApp.RequestSuccessCallbackResult)
    })
  }

  it('nginx 的 502 HTML 页要 reject，而不是 resolve(undefined)', async () => {
    notEnvelope(502, '<html><head><title>502 Bad Gateway</title></head><body></body></html>')
    await expect(request<never>({ url: '/guest/vip-payments/orders/x' }))
      .rejects.toThrow('服务暂时不可用，请稍后重试')
  })

  it('200 但不是响应壳（代理返回纯文本）也要 reject', async () => {
    notEnvelope(200, 'OK')
    await expect(request<never>({ url: '/x' })).rejects.toThrow(/无法识别的响应/)
  })

  it('缺 success 字段的 JSON（Spring 默认错误体）要 reject', async () => {
    // {"timestamp":...,"status":502,"error":"Bad Gateway","path":"..."} 没有 data 键
    notEnvelope(502, { timestamp: '2026-08-23T13:10:51Z', status: 502, error: 'Bad Gateway', path: '/x' })
    await expect(request<never>({ url: '/x' })).rejects.toThrow('服务暂时不可用，请稍后重试')
  })

  it('success 为 true 但 data 是 undefined 时不会静默通过', async () => {
    notEnvelope(200, { success: true, code: 'OK', message: '成功', requestId: 'r' })
    await expect(request<never>({ url: '/x' })).resolves.toBeUndefined()
  })

  it('空响应体要 reject', async () => {
    notEnvelope(500, '')
    await expect(request<never>({ url: '/x' })).rejects.toThrow('服务出错了，请稍后重试')
  })
})
