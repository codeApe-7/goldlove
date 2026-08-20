import { defineStore } from 'pinia'
import * as api from '@/api'
import { requestPayment, type ChannelPaymentOutcome } from '@/adapters/payment'
import { h5SessionAdapter } from '@/adapters/session'
import type {
  GuestSession,
  IssuedRegistrationToken,
  OnlineOrder,
  OnlineOrderStatus,
  OnlinePaymentSettings,
} from '@/types'

const PENDING_KEY = 'guest-pending-registration'
const PENDING_ORDER_KEY = 'guest-pending-order'
const PHONE_DRAFT_KEY = 'guest-order-phone'

export interface PendingRegistration {
  outTradeNo: string
  token: string
  expiresAt: string
  phone: string
}

/** 注册令牌一次性且短时有效，只放在会话存储里，不写入地址栏。 */
export const pendingRegistrationStore = {
  read(): PendingRegistration | null {
    const raw = sessionStorage.getItem(PENDING_KEY)
    if (!raw) return null
    try {
      const parsed = JSON.parse(raw) as PendingRegistration
      return parsed.token && parsed.outTradeNo ? parsed : null
    } catch {
      sessionStorage.removeItem(PENDING_KEY)
      return null
    }
  },
  write(value: PendingRegistration): void {
    sessionStorage.setItem(PENDING_KEY, JSON.stringify(value))
  },
  clear(): void {
    sessionStorage.removeItem(PENDING_KEY)
  },
}

export interface PendingOrder {
  outTradeNo: string
  phone: string
}

/**
 * 已下单但未完成支付的订单。易支付是整页跳转去支付宝，回跳时内存状态已丢，
 * 而 out_trade_no 是这笔订单唯一的句柄——不落会话存储就只能指望平台回带参数。
 */
export const pendingOrderStore = {
  read(): PendingOrder | null {
    const raw = sessionStorage.getItem(PENDING_ORDER_KEY)
    if (!raw) return null
    try {
      const parsed = JSON.parse(raw) as PendingOrder
      return parsed.outTradeNo ? parsed : null
    } catch {
      sessionStorage.removeItem(PENDING_ORDER_KEY)
      return null
    }
  },
  write(value: PendingOrder): void {
    sessionStorage.setItem(PENDING_ORDER_KEY, JSON.stringify(value))
  },
  clear(): void {
    sessionStorage.removeItem(PENDING_ORDER_KEY)
  },
}

/**
 * 用户输入但尚未下单的手机号。微信渠道下单前要先整页跳转做网页授权，
 * 回跳后页面已重新加载，不暂存就会丢掉用户刚填的号码。
 */
export const phoneDraftStore = {
  read(): string {
    return sessionStorage.getItem(PHONE_DRAFT_KEY) ?? ''
  },
  write(phone: string): void {
    sessionStorage.setItem(PHONE_DRAFT_KEY, phone)
  },
  clear(): void {
    sessionStorage.removeItem(PHONE_DRAFT_KEY)
  },
}

export const usePaymentStore = defineStore('guest-online-payment', {
  state: () => ({
    settings: null as OnlinePaymentSettings | null,
    order: null as OnlineOrder | null,
    status: null as OnlineOrderStatus | null,
    pending: pendingRegistrationStore.read(),
    pendingOrder: pendingOrderStore.read(),
    lastOutcome: null as ChannelPaymentOutcome | null,
  }),
  getters: {
    amountLabel: (state) =>
      state.settings ? `¥${(state.settings.amountMinor / 100).toFixed(2)}` : '',
    paid: (state) => state.status?.status === 'PAID',
    readyToRegister: (state) => state.pending !== null,
    /** 下单时收下的手机号，注册页据此预填且只读。 */
    orderedPhone: (state) => state.pending?.phone ?? state.pendingOrder?.phone ?? '',
  },
  actions: {
    async loadSettings(): Promise<OnlinePaymentSettings> {
      this.settings = await api.onlinePaymentSettings()
      return this.settings
    },

    /**
     * 下单。手机号由后端预检（格式、是否已有账号）并钉在订单上。
     * 订单号立刻落会话存储——易支付整页跳转后内存状态不复存在。
     */
    async createOrder(
      phone: string,
      authorizationCode: string,
      authorizationDocumentVersion: string,
    ): Promise<OnlineOrder> {
      const order = await api.createOnlineOrder(phone, authorizationCode, authorizationDocumentVersion)
      this.order = order
      this.status = null
      const pendingOrder: PendingOrder = { outTradeNo: order.outTradeNo, phone }
      pendingOrderStore.write(pendingOrder)
      this.pendingOrder = pendingOrder
      return order
    },

    /**
     * 按渠道调起支付。后端复用了已支付订单时（paid）不再调起支付，直接领注册令牌。
     */
    async pay(): Promise<ChannelPaymentOutcome> {
      if (!this.order) throw new Error('请先创建支付订单')
      if (this.order.paid) {
        await this.obtainRegistrationToken()
        this.lastOutcome = 'success'
        return 'success'
      }
      if (!this.order.payParameters) throw new Error('支付参数缺失，请重新下单')
      const outcome = await requestPayment(this.order.payParameters)
      this.lastOutcome = outcome
      if (outcome === 'success') {
        await this.obtainRegistrationToken()
      }
      return outcome
    },

    async refreshStatus(outTradeNo?: string): Promise<OnlineOrderStatus> {
      const target = outTradeNo ?? this.order?.outTradeNo ?? this.pendingOrder?.outTradeNo
      if (!target) throw new Error('缺少商户订单号')
      this.status = await api.onlineOrderStatus(target)
      return this.status
    },

    /**
     * 支付成功后签发注册令牌。回调可能晚到，因此先查一次状态做补偿。
     */
    async obtainRegistrationToken(outTradeNo?: string): Promise<IssuedRegistrationToken> {
      const target = outTradeNo ?? this.order?.outTradeNo ?? this.pendingOrder?.outTradeNo
      if (!target) throw new Error('缺少商户订单号')
      await this.refreshStatus(target)
      const issued = await api.issueRegistrationToken(target)
      const pending: PendingRegistration = {
        outTradeNo: target,
        token: issued.token,
        expiresAt: issued.expiresAt,
        phone: this.pendingOrder?.outTradeNo === target ? this.pendingOrder.phone : '',
      }
      pendingRegistrationStore.write(pending)
      this.pending = pending
      return issued
    },

    /** 提交手机号与密码完成建档注册，成功后直接持有访客会话。 */
    async register(phone: string, password: string): Promise<GuestSession> {
      const pending = this.pending ?? pendingRegistrationStore.read()
      if (!pending) throw new Error('注册令牌已失效，请重新获取')
      const session = await api.registerOnline(pending.token, phone, password)
      pendingRegistrationStore.clear()
      pendingOrderStore.clear()
      phoneDraftStore.clear()
      this.pending = null
      this.pendingOrder = null
      h5SessionAdapter.write(session)
      return session
    },

    clearPending(): void {
      pendingRegistrationStore.clear()
      pendingOrderStore.clear()
      phoneDraftStore.clear()
      this.pending = null
      this.pendingOrder = null
    },
  },
})
