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
