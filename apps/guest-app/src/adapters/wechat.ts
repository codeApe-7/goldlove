import type { JsapiPayParameters } from '@/types'

export type PaymentOutcome = 'success' | 'cancel' | 'fail'

/** 微信 JSAPI 支付回执的成功标识。 */
const BRIDGE_OK = 'get_brand_wcpay_request:ok'
const BRIDGE_CANCEL = 'get_brand_wcpay_request:cancel'

interface WeixinJsBridge {
  invoke(
    api: 'getBrandWCPayRequest',
    payload: Record<string, string>,
    callback: (response: { err_msg?: string }) => void,
  ): void
}

interface BridgeWindow {
  WeixinJSBridge?: WeixinJsBridge
  navigator?: { userAgent?: string }
  location?: { search?: string; href?: string }
  addEventListener?(type: string, listener: () => void): void
}

function bridgeWindow(): BridgeWindow {
  return (typeof window === 'undefined' ? {} : window) as unknown as BridgeWindow
}

export function isWechatBrowser(userAgent?: string): boolean {
  const agent = userAgent ?? bridgeWindow().navigator?.userAgent ?? ''
  return /micromessenger/i.test(agent)
}

/** 从当前地址读取网页授权回跳带回的参数。 */
export function readQueryParam(name: string, search?: string): string | null {
  const raw = search ?? bridgeWindow().location?.search ?? ''
  const query = raw.startsWith('?') ? raw.slice(1) : raw
  for (const pair of query.split('&')) {
    if (!pair) continue
    const separator = pair.indexOf('=')
    const key = separator === -1 ? pair : pair.slice(0, separator)
    if (decodeURIComponent(key) !== name) continue
    return separator === -1 ? '' : decodeURIComponent(pair.slice(separator + 1).replace(/\+/g, ' '))
  }
  return null
}

export function redirectTo(url: string): void {
  const target = bridgeWindow().location
  if (target) target.href = url
}

/** 后端返回 packageValue，调起支付时字段名必须是 package。 */
function bridgePayload(parameters: JsapiPayParameters): Record<string, string> {
  return {
    appId: parameters.appId,
    timeStamp: parameters.timeStamp,
    nonceStr: parameters.nonceStr,
    package: parameters.packageValue,
    signType: parameters.signType,
    paySign: parameters.paySign,
  }
}

export interface WechatPayAdapter {
  requestPayment(parameters: JsapiPayParameters): Promise<PaymentOutcome>
}

/**
 * 小程序走 uni.requestPayment，微信内网页走 WeixinJSBridge。
 * 两者都只消费后端签名后的参数，前端不参与签名。
 */
export const wechatPayAdapter: WechatPayAdapter = {
  requestPayment(parameters) {
    const miniProgramPayment = miniProgramPaymentApi()
    if (miniProgramPayment) {
      return new Promise<PaymentOutcome>((resolve) => {
        miniProgramPayment({
          ...bridgePayload(parameters),
          provider: 'wxpay',
          success: () => resolve('success'),
          fail: (error: { errMsg?: string }) =>
            resolve(/cancel/i.test(error?.errMsg ?? '') ? 'cancel' : 'fail'),
        })
      })
    }
    return invokeBridge(parameters)
  },
}

/** uni 只在 uni-app 运行时存在，直接引用裸全局会抛 ReferenceError。 */
function miniProgramPaymentApi(): ((options: Record<string, unknown>) => void) | null {
  const runtime = (globalThis as unknown as {
    uni?: { requestPayment?: (options: Record<string, unknown>) => void }
  }).uni
  return typeof runtime?.requestPayment === 'function' ? runtime.requestPayment : null
}

function invokeBridge(parameters: JsapiPayParameters): Promise<PaymentOutcome> {
  return new Promise<PaymentOutcome>((resolve) => {
    const invoke = (): void => {
      const bridge = bridgeWindow().WeixinJSBridge
      if (!bridge) {
        resolve('fail')
        return
      }
      bridge.invoke('getBrandWCPayRequest', bridgePayload(parameters), (response) => {
        if (response?.err_msg === BRIDGE_OK) resolve('success')
        else if (response?.err_msg === BRIDGE_CANCEL) resolve('cancel')
        else resolve('fail')
      })
    }
    if (bridgeWindow().WeixinJSBridge) {
      invoke()
      return
    }
    // 页面比 JSBridge 先就绪时，等 WeixinJSBridgeReady 再调起。
    const target = bridgeWindow()
    if (typeof target.addEventListener === 'function') {
      target.addEventListener('WeixinJSBridgeReady', invoke)
    } else {
      resolve('fail')
    }
  })
}
