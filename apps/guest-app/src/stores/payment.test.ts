import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePaymentStore, pendingRegistrationStore, pendingOrderStore } from './payment'
import * as api from '@/api'
import { requestPayment } from '@/adapters/payment'
import type { OnlineOrder, OnlineOrderStatus, OnlinePaymentSettings } from '@/types'

vi.mock('@/api', () => ({
  onlinePaymentSettings: vi.fn(),
  createOnlineOrder: vi.fn(),
  onlineOrderStatus: vi.fn(),
  issueRegistrationToken: vi.fn(),
  registerOnline: vi.fn(),
}))

vi.mock('@/adapters/payment', () => ({
  requestPayment: vi.fn(),
}))

const PHONE = '13800138000'

const SETTINGS: OnlinePaymentSettings = {
  channelType: 'WECHAT_JSAPI',
  amountMinor: 100,
  orderDescription: '婚恋智能档案库建档服务',
  authorizeUrl: 'https://open.weixin.qq.com/connect/oauth2/authorize?appid=wx-app-1',
  state: 'state-1',
}

const ORDER: OnlineOrder = {
  outTradeNo: 'OTN-STORE-1',
  amountMinor: 100,
  authorizationDocumentVersion: 'v0.3',
  paid: false,
  payParameters: {
    channelType: 'WECHAT_JSAPI',
    jumpUrl: null,
    wechatJsapi: {
      appId: 'wx-app-1',
      timeStamp: '1755500000',
      nonceStr: 'nonce-1',
      packageValue: 'prepay_id=wx-prepay-1',
      signType: 'RSA',
      paySign: 'sign-1',
    },
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

    await store.createOrder(PHONE, 'code-1', 'v0.3')

    expect(api.createOnlineOrder).toHaveBeenCalledWith(PHONE, 'code-1', 'v0.3')
    expect(store.order?.outTradeNo).toBe('OTN-STORE-1')
  })

  it('issues and persists a one-time registration token after a successful payment', async () => {
    vi.mocked(api.createOnlineOrder).mockResolvedValue(ORDER)
    vi.mocked(api.onlineOrderStatus).mockResolvedValue(PAID_STATUS)
    vi.mocked(api.issueRegistrationToken).mockResolvedValue({
      token: 'reg-token-1',
      expiresAt: '2026-08-19T00:30:00+08:00',
    })
    vi.mocked(requestPayment).mockResolvedValue('success')
    const store = usePaymentStore()
    await store.createOrder(PHONE, 'code-1', 'v0.3')

    const outcome = await store.pay()

    expect(outcome).toBe('success')
    expect(requestPayment).toHaveBeenCalledWith(ORDER.payParameters)
    expect(store.readyToRegister).toBe(true)
    expect(pendingRegistrationStore.read()).toMatchObject({
      outTradeNo: 'OTN-STORE-1',
      token: 'reg-token-1',
    })
  })

  it('does not issue a token when the user cancels or the payment fails', async () => {
    vi.mocked(api.createOnlineOrder).mockResolvedValue(ORDER)
    const store = usePaymentStore()
    await store.createOrder(PHONE, 'code-1', 'v0.3')

    vi.mocked(requestPayment).mockResolvedValue('cancel')
    expect(await store.pay()).toBe('cancel')
    vi.mocked(requestPayment).mockResolvedValue('fail')
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
      phone: PHONE,
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

  it('persists the order number and phone so a full-page redirect cannot lose them', async () => {
    vi.mocked(api.createOnlineOrder).mockResolvedValue(ORDER)
    const store = usePaymentStore()

    await store.createOrder(PHONE, '', 'v0.3')

    // 易支付整页跳转支付宝，回跳后内存状态没了，out_trade_no 只能靠会话存储。
    expect(pendingOrderStore.read()).toEqual({ outTradeNo: 'OTN-STORE-1', phone: PHONE })
    expect(store.orderedPhone).toBe(PHONE)
  })

  it('recovers the order number from session storage when the URL carries none', async () => {
    pendingOrderStore.write({ outTradeNo: 'OTN-STORE-1', phone: PHONE })
    vi.mocked(api.onlineOrderStatus).mockResolvedValue(PAID_STATUS)
    vi.mocked(api.issueRegistrationToken).mockResolvedValue({
      token: 'reg-token-4',
      expiresAt: '2026-08-19T00:30:00+08:00',
    })
    const store = usePaymentStore()

    await store.obtainRegistrationToken()

    expect(api.issueRegistrationToken).toHaveBeenCalledWith('OTN-STORE-1')
    expect(pendingRegistrationStore.read()).toMatchObject({ token: 'reg-token-4', phone: PHONE })
  })

  it('skips requesting payment when the backend reused an already paid order', async () => {
    vi.mocked(api.createOnlineOrder).mockResolvedValue({ ...ORDER, paid: true, payParameters: null })
    vi.mocked(api.onlineOrderStatus).mockResolvedValue(PAID_STATUS)
    vi.mocked(api.issueRegistrationToken).mockResolvedValue({
      token: 'reg-token-5',
      expiresAt: '2026-08-19T00:30:00+08:00',
    })
    const store = usePaymentStore()
    await store.createOrder(PHONE, '', 'v0.3')

    expect(await store.pay()).toBe('success')

    // 复用订单不再收钱，因此绝不能调起支付。
    expect(requestPayment).not.toHaveBeenCalled()
    expect(store.readyToRegister).toBe(true)
  })

  it('clears the order number and phone once registration succeeds', async () => {
    pendingOrderStore.write({ outTradeNo: 'OTN-STORE-1', phone: PHONE })
    pendingRegistrationStore.write({
      outTradeNo: 'OTN-STORE-1',
      token: 'reg-token-6',
      expiresAt: '2026-08-19T00:30:00+08:00',
      phone: PHONE,
    })
    vi.mocked(api.registerOnline).mockResolvedValue({
      accountId: 7,
      status: 'ACTIVE',
      accessToken: 'tok-online',
      expiresIn: 2592000,
    })
    const store = usePaymentStore()

    await store.register(PHONE, 'online-pass-2026')

    expect(pendingOrderStore.read()).toBeNull()
    expect(sessionStorage.getItem('guest-order-phone')).toBeNull()
    expect(store.orderedPhone).toBe('')
  })
})
