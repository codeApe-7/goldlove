import { defineStore } from 'pinia'
import * as api from '@/api'
import { requestPayment, type ChannelPaymentOutcome } from '@/adapters/payment'
import type {
  MembershipView,
  OnlineOrder,
  OnlineOrderListItem,
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
    orders: [] as OnlineOrderListItem[],
    lastOutcome: null as ChannelPaymentOutcome | null,
  }),
  getters: {
    amountLabel: (state) =>
      state.settings ? `¥${(state.settings.amountMinor / 100).toFixed(2)}` : '',
    tier: (state) => state.membership?.tier ?? 'FREE',
    isVip: (state) => state.membership?.tier === 'VIP' || state.membership?.tier === 'SVIP',
    paid: (state) => state.status?.status === 'PAID',
    /** 还能继续付的订单。后端已把过期的转成 CLOSED，这里不必自己比时间。 */
    payableOrders: (state) => state.orders.filter((order) => order.status === 'CREATED'),
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

    /** 本人的订单列表。 */
    async loadOrders(): Promise<OnlineOrderListItem[]> {
      const orders = await api.vipOrders()
      this.orders = Array.isArray(orders) ? orders : []
      return this.orders
    },

    /**
     * 需要接续确认的订单号，没有则返回空串。
     *
     * 顺序是回跳参数 → 会话存储 → **服务端最近一笔未支付订单**。前两个都是一次性的、
     * 单标签页的：关掉标签页或重新登录就没了。最后这个才是重新登录后还找得回来的来源，
     * 也正是「上午下的单，晚上再进来就再也查不到」的补丁。
     */
    async resumeTarget(fromReturnUrl?: string | null): Promise<string> {
      const local = fromReturnUrl || pendingOrderStore.read()
      if (local) return local
      const orders = await this.loadOrders()
      return orders.find((order) => order.status === 'CREATED')?.outTradeNo ?? ''
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
      const result = await api.vipOrderStatus(target)
      // 钱相关的路径上不裸读字段。传输层现在会把「不是响应壳」的响应挡在外面，
      // 但万一后端返回了一个合法响应壳却没带 data，这里也要给一句人话，
      // 而不是让调用方吃一个「Cannot read properties of undefined」——
      // 那种报错指不到真实原因，线上排查过一次。
      if (!result || typeof result.status !== 'string') {
        throw new Error('没拿到支付状态，请稍后重新查询')
      }
      this.status = result
      if (result.status === 'PAID') {
        pendingOrderStore.clear()
        await this.loadMembership()
      } else if (result.status === 'CLOSED') {
        // 这笔已经付不了了：清掉句柄，别让它在每次进页面时都被重新查一遍。
        pendingOrderStore.clear()
      }
      return result
    },

    /** 用激活码升级，成功后直接拿到新的会员状态。 */
    async redeem(code: string): Promise<MembershipView> {
      this.membership = await api.redeemActivationCode(code)
      return this.membership
    },
  },
})
