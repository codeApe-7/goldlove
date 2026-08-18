import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePaymentStore, pendingRegistrationStore } from './payment'
import * as api from '@/api'
import { wechatPayAdapter } from '@/adapters/wechat'
import type { OnlineOrder, OnlineOrderStatus, OnlinePaymentSettings } from '@/types'

vi.mock('@/api', () => ({
  onlinePaymentSettings: vi.fn(),
  createOnlineOrder: vi.fn(),
  onlineOrderStatus: vi.fn(),
  issueRegistrationToken: vi.fn(),
  registerOnline: vi.fn(),
}))

vi.mock('@/adapters/wechat', () => ({
  wechatPayAdapter: { requestPayment: vi.fn() },
}))

const SETTINGS: OnlinePaymentSettings = {
  appId: 'wx-app-1',
  amountMinor: 100,
  orderDescription: '婚恋智能档案库建档服务',
  authorizeUrl: 'https://open.weixin.qq.com/connect/oauth2/authorize?appid=wx-app-1',
  state: 'state-1',
}

const ORDER: OnlineOrder = {
  outTradeNo: 'OTN-STORE-1',
  amountMinor: 100,
  authorizationDocumentVersion: 'v0.3',
  payParameters: {
    appId: 'wx-app-1',
    timeStamp: '1755500000',
    nonceStr: 'nonce-1',
    packageValue: 'prepay_id=wx-prepay-1',
    signType: 'RSA',
    paySign: 'sign-1',
  },
}

const PAID_STATUS: OnlineOrderStatus = {
  outTradeNo: 'OTN-STORE-1',
  status: 'PAID',
  amountMinor: 100,
  registered: false,
}

describe('guest online payment store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    vi.clearAllMocks()
  })

  it('formats the server-side amount for display', async () => {
    vi.mocked(api.onlinePaymentSettings).mockResolvedValue(SETTINGS)
    const store = usePaymentStore()

    await store.loadSettings()

    expect(store.amountLabel).toBe('¥1.00')
    expect(store.settings?.authorizeUrl).toContain('oauth2/authorize')
  })

  it('creates an order from the authorization code without sending an amount', async () => {
    vi.mocked(api.createOnlineOrder).mockResolvedValue(ORDER)
    const store = usePaymentStore()

    await store.createOrder('code-1', 'v0.3')

    expect(api.createOnlineOrder).toHaveBeenCalledWith('code-1', 'v0.3')
    expect(store.order?.outTradeNo).toBe('OTN-STORE-1')
  })

  it('issues and persists a one-time registration token after a successful payment', async () => {
    vi.mocked(api.createOnlineOrder).mockResolvedValue(ORDER)
    vi.mocked(api.onlineOrderStatus).mockResolvedValue(PAID_STATUS)
    vi.mocked(api.issueRegistrationToken).mockResolvedValue({
      token: 'reg-token-1',
      expiresAt: '2026-08-19T00:30:00+08:00',
    })
    vi.mocked(wechatPayAdapter.requestPayment).mockResolvedValue('success')
    const store = usePaymentStore()
    await store.createOrder('code-1', 'v0.3')

    const outcome = await store.pay()

    expect(outcome).toBe('success')
    expect(wechatPayAdapter.requestPayment).toHaveBeenCalledWith(ORDER.payParameters)
    expect(store.readyToRegister).toBe(true)
    expect(pendingRegistrationStore.read()).toMatchObject({
      outTradeNo: 'OTN-STORE-1',
      token: 'reg-token-1',
    })
  })

  it('does not issue a token when the user cancels or the payment fails', async () => {
    vi.mocked(api.createOnlineOrder).mockResolvedValue(ORDER)
    const store = usePaymentStore()
    await store.createOrder('code-1', 'v0.3')

    vi.mocked(wechatPayAdapter.requestPayment).mockResolvedValue('cancel')
    expect(await store.pay()).toBe('cancel')
    vi.mocked(wechatPayAdapter.requestPayment).mockResolvedValue('fail')
    expect(await store.pay()).toBe('fail')

    expect(api.issueRegistrationToken).not.toHaveBeenCalled()
    expect(store.readyToRegister).toBe(false)
  })

  it('refuses to pay before an order exists', async () => {
    const store = usePaymentStore()
    await expect(store.pay()).rejects.toThrow('请先创建支付订单')
  })

  it('queries order status before issuing a token so a late notification is compensated', async () => {
    vi.mocked(api.onlineOrderStatus).mockResolvedValue(PAID_STATUS)
    vi.mocked(api.issueRegistrationToken).mockResolvedValue({
      token: 'reg-token-2',
      expiresAt: '2026-08-19T00:30:00+08:00',
    })
    const store = usePaymentStore()

    await store.obtainRegistrationToken('OTN-STORE-1')

    expect(api.onlineOrderStatus).toHaveBeenCalledWith('OTN-STORE-1')
    expect(store.paid).toBe(true)
  })

  it('submits the stored token on register, then clears it and keeps the session', async () => {
    pendingRegistrationStore.write({
      outTradeNo: 'OTN-STORE-1',
      token: 'reg-token-3',
      expiresAt: '2026-08-19T00:30:00+08:00',
    })
    vi.mocked(api.registerOnline).mockResolvedValue({
      accountId: 42,
      status: 'ACTIVE',
      accessToken: 'tok-online',
      expiresIn: 2592000,
    })
    const store = usePaymentStore()

    const session = await store.register('13800138000', 'online-pass-2026')

    expect(api.registerOnline).toHaveBeenCalledWith('reg-token-3', '13800138000', 'online-pass-2026')
    expect(session.accountId).toBe(42)
    expect(pendingRegistrationStore.read()).toBeNull()
    expect(store.readyToRegister).toBe(false)
    expect(JSON.parse(sessionStorage.getItem('guest-session')!)).toMatchObject({
      accessToken: 'tok-online',
    })
  })

  it('refuses to register without a stored token', async () => {
    const store = usePaymentStore()
    await expect(store.register('13800138000', 'online-pass-2026')).rejects.toThrow(
      '注册令牌已失效，请重新获取',
    )
    expect(api.registerOnline).not.toHaveBeenCalled()
  })

  it('drops a corrupted pending entry instead of throwing', () => {
    sessionStorage.setItem('guest-pending-registration', '{not-json')
    expect(pendingRegistrationStore.read()).toBeNull()
    sessionStorage.setItem('guest-pending-registration', '{"outTradeNo":"OTN-1"}')
    expect(pendingRegistrationStore.read()).toBeNull()
  })
})
