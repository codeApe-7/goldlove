import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useVipPaymentStore, pendingOrderStore } from './payment'
import * as api from '@/api'
import { requestPayment } from '@/adapters/payment'
import type { MembershipView, OnlineOrder, OnlineOrderStatus, OnlinePaymentSettings } from '@/types'

vi.mock('@/api', () => ({
  vipPaymentSettings: vi.fn(),
  createVipOrder: vi.fn(),
  vipOrderStatus: vi.fn(),
  membership: vi.fn(),
  redeemActivationCode: vi.fn(),
}))

vi.mock('@/adapters/payment', () => ({
  requestPayment: vi.fn(),
}))

const SETTINGS: OnlinePaymentSettings = {
  channelType: 'XPAY_ALIPAY',
  amountMinor: 9900,
  orderDescription: 'gold 智能档案库 VIP 会员',
}

const ORDER: OnlineOrder = {
  outTradeNo: 'OTN-VIP-1',
  amountMinor: 9900,
  payParameters: { channelType: 'XPAY_ALIPAY', jumpUrl: 'https://cashier.example/pay?o=1' },
}

const PAID_STATUS: OnlineOrderStatus = {
  outTradeNo: 'OTN-VIP-1',
  status: 'PAID',
  amountMinor: 9900,
  membershipGranted: true,
}

const FREE: MembershipView = {
  tier: 'FREE',
  creditMinor: 0,
  svipThresholdMinor: 59900,
  creditToNextTierMinor: 59900,
}

const VIP: MembershipView = {
  tier: 'VIP',
  creditMinor: 9900,
  svipThresholdMinor: 59900,
  creditToNextTierMinor: 50000,
}

describe('guest VIP payment store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    vi.clearAllMocks()
  })

  it('formats the server-side amount for display', async () => {
    vi.mocked(api.vipPaymentSettings).mockResolvedValue(SETTINGS)
    const store = useVipPaymentStore()

    await store.loadSettings()

    expect(store.amountLabel).toBe('¥99.00')
  })

  it('reports the current tier and whether it already counts as a member', async () => {
    vi.mocked(api.membership).mockResolvedValue(FREE)
    const store = useVipPaymentStore()

    await store.loadMembership()

    expect(store.tier).toBe('FREE')
    expect(store.isVip).toBe(false)
  })

  it('creates an order without sending an amount or an account', async () => {
    vi.mocked(api.createVipOrder).mockResolvedValue(ORDER)
    const store = useVipPaymentStore()

    await store.createOrder()

    expect(api.createVipOrder).toHaveBeenCalledWith()
    expect(store.order?.outTradeNo).toBe('OTN-VIP-1')
  })

  it('persists the order number so a full-page redirect cannot lose it', async () => {
    vi.mocked(api.createVipOrder).mockResolvedValue(ORDER)
    const store = useVipPaymentStore()

    await store.createOrder()

    // 易支付整页跳转支付宝，回跳后内存状态没了，out_trade_no 只能靠会话存储。
    expect(pendingOrderStore.read()).toBe('OTN-VIP-1')
  })

  it('hands the signed jump url to the channel adapter', async () => {
    vi.mocked(api.createVipOrder).mockResolvedValue(ORDER)
    vi.mocked(requestPayment).mockResolvedValue('redirect')
    const store = useVipPaymentStore()
    await store.createOrder()

    const outcome = await store.pay()

    expect(outcome).toBe('redirect')
    expect(requestPayment).toHaveBeenCalledWith(ORDER.payParameters)
  })

  it('refuses to pay before an order exists', async () => {
    const store = useVipPaymentStore()
    await expect(store.pay()).rejects.toThrow('请先创建支付订单')
  })

  it('clears the stored order and refreshes membership once the order is paid', async () => {
    pendingOrderStore.write('OTN-VIP-1')
    vi.mocked(api.vipOrderStatus).mockResolvedValue(PAID_STATUS)
    vi.mocked(api.membership).mockResolvedValue(VIP)
    const store = useVipPaymentStore()

    const status = await store.refreshStatus()

    expect(api.vipOrderStatus).toHaveBeenCalledWith('OTN-VIP-1')
    expect(status.status).toBe('PAID')
    expect(store.paid).toBe(true)
    expect(store.tier).toBe('VIP')
    expect(pendingOrderStore.read()).toBe('')
  })

  it('keeps the stored order while the payment is still pending', async () => {
    pendingOrderStore.write('OTN-VIP-1')
    vi.mocked(api.vipOrderStatus).mockResolvedValue({ ...PAID_STATUS, status: 'CREATED' })
    const store = useVipPaymentStore()

    await store.refreshStatus()

    expect(store.paid).toBe(false)
    expect(pendingOrderStore.read()).toBe('OTN-VIP-1')
    expect(api.membership).not.toHaveBeenCalled()
  })

  it('refuses to query status without an order number', async () => {
    const store = useVipPaymentStore()
    await expect(store.refreshStatus()).rejects.toThrow('缺少商户订单号')
  })

  it('upgrades through an activation code without touching the order flow', async () => {
    vi.mocked(api.redeemActivationCode).mockResolvedValue(VIP)
    const store = useVipPaymentStore()

    const membership = await store.redeem(' love-7k2m-9xqp-4t8b ')

    expect(api.redeemActivationCode).toHaveBeenCalledWith(' love-7k2m-9xqp-4t8b ')
    expect(membership.tier).toBe('VIP')
    expect(store.tier).toBe('VIP')
    expect(api.createVipOrder).not.toHaveBeenCalled()
  })
})

describe('查单拿不到状态时的兜底', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('响应壳合法但没带 data 时给人话，而不是 TypeError', async () => {
    // 传输层已经会挡掉「不是响应壳」的响应；这一条防的是「壳合法但 data 缺失」。
    vi.mocked(api.vipOrderStatus).mockResolvedValue(undefined as never)
    const store = useVipPaymentStore()
    await expect(store.refreshStatus('OTN-1')).rejects.toThrow('没拿到支付状态，请稍后重新查询')
  })

  it('status 不是字符串也当异常处理', async () => {
    vi.mocked(api.vipOrderStatus).mockResolvedValue({ outTradeNo: 'OTN-1' } as never)
    const store = useVipPaymentStore()
    await expect(store.refreshStatus('OTN-1')).rejects.toThrow('没拿到支付状态')
  })
})
