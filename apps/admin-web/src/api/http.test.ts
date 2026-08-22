import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, http, unwrap, setUnauthorizedHandler } from './http'

function mockAdapter(status: number, body: unknown) {
  http.defaults.adapter = async (config) => {
    if (status >= 400) {
      throw {
        response: { status, data: body, config },
        config,
      }
    }
    return { data: body, status, statusText: 'OK', headers: {}, config }
  }
}

function envelope(code: string, message: string) {
  return { success: false, code, message, data: null, requestId: 'r' }
}

describe('http api layer', () => {
  afterEach(() => {
    delete http.defaults.adapter
    setUnauthorizedHandler(() => undefined)
  })

  it('unwraps success envelope data', async () => {
    mockAdapter(200, { success: true, code: 'OK', message: '成功', data: { id: 1 }, requestId: 'r1' })
    await expect(unwrap<{ id: number }>(http.get('/x'))).resolves.toEqual({ id: 1 })
  })

  it('rejects business failure with message', async () => {
    mockAdapter(200, envelope('X', '失败'))
    await expect(unwrap<never>(http.get('/x'))).rejects.toThrow('失败')
  })

  it('surfaces the backend message instead of axios 的英文状态串', async () => {
    // 这是这层存在的理由：登录密码错时界面以前显示
    // 「Request failed with status code 401」，对使用后台的人毫无意义。
    mockAdapter(401, envelope('AUTH_INVALID_CREDENTIALS', '用户名或密码错误'))
    await expect(unwrap<never>(http.post('/admin/auth/login', {})))
      .rejects.toThrow('用户名或密码错误')
  })

  it('carries code and status on the error', async () => {
    mockAdapter(409, envelope('ACCOUNT_STATUS_CONFLICT', '账号状态已变化，请重试'))
    const error = await unwrap<never>(http.post('/x')).catch((e) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error.code).toBe('ACCOUNT_STATUS_CONFLICT')
    expect(error.status).toBe(409)
    expect(error.requestId).toBe('r')
  })

  it('falls back to a human sentence when the body carries no message', async () => {
    mockAdapter(500, '<html>502 Bad Gateway</html>')
    await expect(unwrap<never>(http.get('/x'))).rejects.toThrow('服务出错了，请稍后重试')

    mockAdapter(429, null)
    await expect(unwrap<never>(http.get('/x'))).rejects.toThrow('操作过于频繁，请稍后再试')
  })

  it('reports a network failure when no response came back', async () => {
    http.defaults.adapter = async () => {
      throw { message: 'Network Error' }
    }
    await expect(unwrap<never>(http.get('/x'))).rejects.toThrow('连不上服务器，请检查网络后重试')
  })

  it('reads the message out of a blob body（导出接口）', async () => {
    mockAdapter(400, new Blob([JSON.stringify(envelope('PROFILE_EXPORT_TOO_LARGE', '单次最多导出 5000 条'))]))
    await expect(http.get('/x', { responseType: 'blob' }))
      .rejects.toThrow('单次最多导出 5000 条')
  })

  it('invokes unauthorized handler on 401', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    mockAdapter(401, envelope('AUTH_NOT_LOGGED_IN', '请先登录'))
    await expect(unwrap<never>(http.get('/x'))).rejects.toThrow('请先登录')
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('does not treat a failed login as an expired session', async () => {
    // 登录接口的 401 是「密码不对」。跳一次登录页没坏处，但会把真正的
    // 会话过期语义搞混，也会在登录页上无谓地清一次本地状态。
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    mockAdapter(401, envelope('AUTH_INVALID_CREDENTIALS', '用户名或密码错误'))
    await expect(unwrap<never>(http.post('/admin/auth/login', {}))).rejects.toThrow()
    expect(handler).not.toHaveBeenCalled()
  })
})
