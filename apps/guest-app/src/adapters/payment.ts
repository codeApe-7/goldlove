import type { PayParameters } from '@/types'

export type ChannelPaymentOutcome = 'success' | 'cancel' | 'fail' | 'redirect'

/**
 * 调起支付。易支付整页跳转支付宝收银台，支付结果通过 return_url 回跳后查单补偿，
 * 所以这里返回 redirect 而不是 success——真正的结果要等回来查单才知道。
 */
export async function requestPayment(parameters: PayParameters): Promise<ChannelPaymentOutcome> {
  if (!parameters.jumpUrl) return 'fail'
  window.location.href = parameters.jumpUrl
  return 'redirect'
}
