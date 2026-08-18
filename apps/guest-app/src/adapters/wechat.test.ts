import { beforeEach, describe, expect, it, vi } from 'vitest'
import { isWechatBrowser, readQueryParam, wechatPayAdapter } from './wechat'
import type { JsapiPayParameters } from '@/types'

const PARAMETERS: JsapiPayParameters = {
  appId: 'wx-app-1',
  timeStamp: '1755500000',
  nonceStr: 'nonce-1',
  packageValue: 'prepay_id=wx-prepay-1',
  signType: 'RSA',
  paySign: 'sign-1',
}

describe('wechat adapter', () => {
  beforeEach(() => {
    delete (window as unknown as Record<string, unknown>).WeixinJSBridge
    delete (globalThis as unknown as Record<string, unknown>).uni
  })

  it('detects the WeChat built-in browser from the user agent', () => {
    expect(isWechatBrowser('Mozilla/5.0 ... MicroMessenger/8.0.49')).toBe(true)
    expect(isWechatBrowser('Mozilla/5.0 ... Safari/605.1.15')).toBe(false)
    expect(isWechatBrowser('')).toBe(false)
  })

  it('reads the authorization code out of the query string', () => {
    expect(readQueryParam('code', '?code=abc123&state=s1')).toBe('abc123')
    expect(readQueryParam('state', 'code=abc123&state=s%2F1')).toBe('s/1')
    expect(readQueryParam('code', '?state=s1')).toBeNull()
    expect(readQueryParam('code', '')).toBeNull()
  })

  it('renames packageValue to package when invoking the JSAPI bridge', async () => {
    const invoke = vi.fn(
      (_api: string, _payload: Record<string, string>, callback: (r: { err_msg: string }) => void) =>
        callback({ err_msg: 'get_brand_wcpay_request:ok' }),
    )
    ;(window as unknown as Record<string, unknown>).WeixinJSBridge = { invoke }

    const outcome = await wechatPayAdapter.requestPayment(PARAMETERS)

    expect(outcome).toBe('success')
    expect(invoke).toHaveBeenCalledWith(
      'getBrandWCPayRequest',
      {
        appId: 'wx-app-1',
        timeStamp: '1755500000',
        nonceStr: 'nonce-1',
        package: 'prepay_id=wx-prepay-1',
        signType: 'RSA',
        paySign: 'sign-1',
      },
      expect.any(Function),
    )
  })

  it('maps bridge cancel and failure replies', async () => {
    const reply = (errMsg: string) => {
      ;(window as unknown as Record<string, unknown>).WeixinJSBridge = {
        invoke: (
          _api: string,
          _payload: Record<string, string>,
          callback: (r: { err_msg: string }) => void,
        ) => callback({ err_msg: errMsg }),
      }
    }

    reply('get_brand_wcpay_request:cancel')
    expect(await wechatPayAdapter.requestPayment(PARAMETERS)).toBe('cancel')
    reply('get_brand_wcpay_request:fail')
    expect(await wechatPayAdapter.requestPayment(PARAMETERS)).toBe('fail')
  })

  it('prefers uni.requestPayment when running inside a mini program', async () => {
    const requestPayment = vi.fn((options: Record<string, unknown>) => {
      ;(options.success as () => void)()
    })
    ;(globalThis as unknown as Record<string, unknown>).uni = { requestPayment }

    expect(await wechatPayAdapter.requestPayment(PARAMETERS)).toBe('success')
    expect(requestPayment).toHaveBeenCalledWith(
      expect.objectContaining({ provider: 'wxpay', package: 'prepay_id=wx-prepay-1' }),
    )
  })

  it('maps a mini program cancel error to cancel', async () => {
    ;(globalThis as unknown as Record<string, unknown>).uni = {
      requestPayment: (options: Record<string, unknown>) => {
        ;(options.fail as (error: { errMsg: string }) => void)({
          errMsg: 'requestPayment:fail cancel',
        })
      },
    }

    expect(await wechatPayAdapter.requestPayment(PARAMETERS)).toBe('cancel')
  })
})
