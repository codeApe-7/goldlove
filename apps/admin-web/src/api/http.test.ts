import { afterEach, describe, expect, it, vi } from 'vitest'
import { http, unwrap, setUnauthorizedHandler } from './http'

function mockAdapter(status: number, body: unknown) {
  http.defaults.adapter = async (config) => {
    if (status >= 400) {
      throw {
        response: { status, data: body, config },
        config,
      }
    }
    return {
      data: body,
      status,
      statusText: 'OK',
      headers: {},
      config,
    }
  }
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
    mockAdapter(200, { success: false, code: 'X', message: '失败', data: null, requestId: 'r2' })
    await expect(unwrap<never>(http.get('/x'))).rejects.toThrow('失败')
  })

  it('invokes unauthorized handler on 401', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    mockAdapter(401, { success: false, code: 'AUTH_NOT_LOGGED_IN', message: '', data: null, requestId: 'r3' })
    await expect(unwrap<never>(http.get('/x'))).rejects.toBeTruthy()
    expect(handler).toHaveBeenCalledTimes(1)
  })
})
