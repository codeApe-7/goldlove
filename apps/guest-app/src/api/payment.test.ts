import { describe, expect, it, vi } from 'vitest'
import * as api from './index'
import type { OnlineOrderListItem } from '@/types'

/** 记录最后一次 uni.request 的入参，接口层的断言全落在 url / method 上。 */
function stubRequest(reply: { statusCode: number; data: unknown }) {
  const calls: UniApp.RequestOptions[] = []
  vi.stubGlobal('uni', {
    request: vi.fn((options: UniApp.RequestOptions) => {
      calls.push(options)
      options.success?.({
        statusCode: reply.statusCode,
        header: {},
        cookies: [],
        data: reply.data,
      } as UniApp.RequestSuccessCallbackResult)
      return { abort: vi.fn() }
    }),
  })
  return calls
}

function envelope<T>(data: T) {
  return { success: true, code: 'OK', message: '成功', data, requestId: 'r' }
}

const ORDER: OnlineOrderListItem = {
  outTradeNo: '26dOYK_HMYHMceJ1veXjsjeCnl2QVJRI',
  status: 'CREATED',
  amountMinor: 9900,
  channelTradeNo: '20260823225910918724',
  createdAt: '2026-08-23T10:36:32Z',
  paidAt: null,
  expiresAt: '2026-08-23T11:06:32Z',
}

describe('VIP 支付接口', () => {
  it('订单列表是 GET，且不带任何查询参数', async () => {
    const calls = stubRequest({ statusCode: 200, data: envelope([ORDER]) })

    const orders = await api.vipOrders()

    expect(calls[0].url).toBe('/api/v1/guest/vip-payments/orders')
    expect(calls[0].method ?? 'GET').toBe('GET')
    expect(orders[0].channelTradeNo).toBe('20260823225910918724')
  })

  it('下单是 POST 到同一路径，不传金额也不传账号', async () => {
    const calls = stubRequest({
      statusCode: 200,
      data: envelope({ outTradeNo: 'OTN-1', amountMinor: 9900, payParameters: null }),
    })

    await api.createVipOrder()

    expect(calls[0].url).toBe('/api/v1/guest/vip-payments/orders')
    expect(calls[0].method).toBe('POST')
    expect(calls[0].data).toBeUndefined()
  })

  /**
   * 商户订单号是 Base64URL，可能以 `-` 开头、也可能含 `--`（线上真的出现过
   * `-2tw5Ff3UGOAgvGjzQ8ScAoSKQ5S--bh`）。拼进路径时必须转义，不能裸接。
   */
  it('查单把订单号转义后拼进路径', async () => {
    const calls = stubRequest({
      statusCode: 200,
      data: envelope({ ...ORDER, membershipGranted: false }),
    })

    await api.vipOrderStatus('-2tw5Ff3UGOAgvGjzQ8ScAoSKQ5S--bh')

    expect(calls[0].url)
      .toBe('/api/v1/guest/vip-payments/orders/-2tw5Ff3UGOAgvGjzQ8ScAoSKQ5S--bh')
  })
})
