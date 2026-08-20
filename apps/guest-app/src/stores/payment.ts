import { defineStore } from 'pinia'
import * as api from '@/api'
import { requestPayment, type ChannelPaymentOutcome } from '@/adapters/payment'
import type {
  MembershipView,
  OnlineOrder,
  OnlineOrderStatus,
  OnlinePaymentSettings,
} from '@/types'

const PENDING_ORDER_KEY = 'guest-pending-order'

/**
 * 已下单但未完成支付的订单号。易支付是整页跳转去支付宝，回跳时内存状态已丢，
 * 而 out_trade_no 是这笔订单唯一的句柄——不落会话存储就只能指望平台回带参数。
 */
export const pendingOrderStore = {
  read(): string {
    return sessionStorage.getItem(PENDING_ORDER_KEY) ?? ''
  },
  write(outTradeNo: string): void {
    sessionStorage.setItem(PENDING_ORDER_KEY, outTradeNo)
  },
  clear(): void {
    sessionStorage.removeItem(PENDING_ORDER_KEY)
  },
}

export const useVipPaymentStore = defineStore('guest-vip-payment', {
  state: () => ({
    settings: null as OnlinePaymentSettings | null,
    membership: null as MembershipView | null,
    order: null as OnlineOrder | null,
    status: null as OnlineOrderStatus | null,
    lastOutcome: null as ChannelPaymentOutcome | null,
  }),
  getters: {
    amountLabel: (state) =>
      state.settings ? `¥${(state.settings.amountMinor / 100).toFixed(2)}` : '',
    tier: (state) => state.membership?.tier ?? 'FREE',
    isVip: (state) => state.membership?.tier === 'VIP' || state.membership?.tier === 'SVIP',
    paid: (state) => state.status?.status === 'PAID',
  },
  actions: {
    async loadSettings(): Promise<OnlinePaymentSettings> {
      this.settings = await api.vipPaymentSettings()
      return this.settings
    },

    async loadMembership(): Promise<MembershipView> {
      this.membership = await api.membership()
      return this.membership
    },

    /** 下单。金额与账号都由后端决定；订单号立刻落会话存储，整页跳转后才找得回来。 */
    async createOrder(): Promise<OnlineOrder> {
      const order = await api.createVipOrder()
      this.order = order
      this.status = null
      pendingOrderStore.write(order.outTradeNo)
      return order
    },

    async pay(): Promise<ChannelPaymentOutcome> {
      if (!this.order) throw new Error('请先创建支付订单')
      if (!this.order.payParameters) throw new Error('支付参数缺失，请重新下单')
      const outcome = await requestPayment(this.order.payParameters)
      this.lastOutcome = outcome
      return outcome
    },

    /**
     * 查单。回调可能晚到或丢失，后端在本地仍为 CREATED 时会主动向渠道补偿查询。
     * 支付成功后会员已由后端授予，这里顺带刷新等级。
     */
    async refreshStatus(outTradeNo?: string): Promise<OnlineOrderStatus> {
      const target = outTradeNo || this.order?.outTradeNo || pendingOrderStore.read()
      if (!target) throw new Error('缺少商户订单号')
      this.status = await api.vipOrderStatus(target)
      if (this.status.status === 'PAID') {
        pendingOrderStore.clear()
        await this.loadMembership()
      }
      return this.status
    },

    /** 用激活码升级，成功后直接拿到新的会员状态。 */
    async redeem(code: string): Promise<MembershipView> {
      this.membership = await api.redeemActivationCode(code)
      return this.membership
    },
  },
})
