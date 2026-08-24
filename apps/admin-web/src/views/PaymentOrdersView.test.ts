import { describe, expect, it } from 'vitest'
import ledgerSource from './PaymentOrdersView.vue?raw'

/**
 * 源码断言，与 PaymentSettingsView.test.ts 同一路子：锁住「决定过一次、不该被顺手改掉」的地方。
 *
 * 这一页的存在意义之一是对账，而对账靠的是渠道侧订单号——线上真的遇到过一笔订单
 * 在渠道那边查不到，而库里当时没有任何能拿去渠道后台查的编号，只能靠时间和金额瞎猜。
 */
describe('支付订单台账', () => {
  it('列出渠道侧订单号，未支付的订单也要能看到', () => {
    expect(ledgerSource).toContain('label="支付平台单号"')
    expect(ledgerSource).toContain('row.channelTradeNo')
  })

  it('没有渠道单号时明确显示占位，而不是留一片空白让人以为界面坏了', () => {
    expect(ledgerSource).toContain('v-else class="muted"')
  })

  it('页头说清这个字段是干什么用的', () => {
    expect(ledgerSource).toContain('去渠道后台查这笔单子')
  })

  it('三种订单状态都可筛选', () => {
    expect(ledgerSource).toContain('value="CREATED"')
    expect(ledgerSource).toContain('value="PAID"')
    // CLOSED 现在真的会被写进库了（订单超时未付即关闭），筛选项不能少。
    expect(ledgerSource).toContain('value="CLOSED"')
  })
})
