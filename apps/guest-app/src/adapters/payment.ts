import type { PayParameters } from '@/types'
import { wechatPayAdapter } from './wechat'

export type ChannelPaymentOutcome = 'success' | 'cancel' | 'fail' | 'redirect'

/**
 * 按渠道分发调起支付：
 * - 易支付（XPAY_ALIPAY）跳转支付宝收银台，支付结果通过 return_url 回跳后查单补偿；
 * - 微信（WECHAT_JSAPI）走 WeixinJSBridge / uni.requestPayment。
 */
export async function requestPayment(parameters: PayParameters): Promise<ChannelPaymentOutcome> {
  if (parameters.channelType === 'XPAY_ALIPAY') {
    if (!parameters.jumpUrl) return 'fail'
    window.location.href = parameters.jumpUrl
    return 'redirect'
  }
  if (!parameters.wechatJsapi) return 'fail'
  return wechatPayAdapter.requestPayment(parameters.wechatJsapi)
}
