import { describe, expect, it } from 'vitest'
import vipSource from './index.vue?raw'
import storeSource from '../../stores/payment.ts?raw'

/**
 * 源码断言。这一页出过一次真实事故：上午下的单，晚上再进来就再也查不到了——
 * 订单号只存在回跳参数和 `sessionStorage` 里，两者都是一次性 / 单标签页的。
 * 下面几条锁的就是「订单永远找得回来」这件事，别被后来的顺手编辑撤回。
 */
describe('会员页的订单接续', () => {
  it('接续订单号走 store 的 resumeTarget，而不是页面里自己读会话存储', () => {
    expect(vipSource).toContain("vip.resumeTarget(readQueryParam('out_trade_no'))")
    // 页面不再直接碰 pendingOrderStore：三个来源的优先级只在 store 里定义一次。
    expect(vipSource).not.toContain('pendingOrderStore')
  })

  it('resumeTarget 的兜底是服务端订单列表', () => {
    expect(storeSource).toContain('const orders = await this.loadOrders()')
    expect(storeSource).toContain("orders.find((order) => order.status === 'CREATED')")
  })

  it('页面加载后会拉一次订单列表', () => {
    expect(vipSource).toContain('await vip.loadOrders()')
    expect(vipSource).toContain('v-for="item in vip.orders"')
  })

  it('列表展示金额、状态、时间与支付平台单号', () => {
    expect(vipSource).toContain('moneyLabel(item.amountMinor)')
    expect(vipSource).toContain('orderStatusOf(item.status)')
    expect(vipSource).toContain('timeLabel(item.createdAt)')
    expect(vipSource).toContain('item.channelTradeNo')
  })

  it('点任一笔订单都能查最新状态', () => {
    expect(vipSource).toContain('@tap="checkOrder(item)"')
  })

  it('订单已关闭时说清要重新下单，而不是含糊的「尚未收到支付结果」', () => {
    expect(vipSource).toContain("status.status === 'CLOSED'")
    expect(vipSource).toContain('这笔订单已超时关闭，请重新下单支付')
  })

  it('能不能继续付只看后端给的 status，前端不自己比时间', () => {
    // 后端在返回列表前已经把过期订单转成 CLOSED；两边各自拿本地时间比一次必然漂移。
    expect(storeSource).toContain("state.orders.filter((order) => order.status === 'CREATED')")
    expect(vipSource).not.toContain('Date.now()')
  })
})
