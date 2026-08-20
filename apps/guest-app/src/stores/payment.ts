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

export interface PendingRegistration {
  outTradeNo: string
  token: string
  expiresAt: string
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

export const usePaymentStore = defineStore('guest-online-payment', {
  state: () => ({
    settings: null as OnlinePaymentSettings | null,
    order: null as OnlineOrder | null,
    status: null as OnlineOrderStatus | null,
    pending: pendingRegistrationStore.read(),
    lastOutcome: null as ChannelPaymentOutcome | null,
  }),
  getters: {
    amountLabel: (state) =>
      state.settings ? `¥${(state.settings.amountMinor / 100).toFixed(2)}` : '',
    paid: (state) => state.status?.status === 'PAID',
    readyToRegister: (state) => state.pending !== null,
  },
  actions: {
    async loadSettings(): Promise<OnlinePaymentSettings> {
      this.settings = await api.onlinePaymentSettings()
      return this.settings
    },

    /** 用网页授权 code 下单。金额与商品描述由后端决定。 */
    async createOrder(authorizationCode: string, authorizationDocumentVersion: string): Promise<OnlineOrder> {
      this.order = await api.createOnlineOrder(authorizationCode, authorizationDocumentVersion)
      this.status = null
      return this.order
    },

    /** 按渠道调起支付；微信成功或易支付回跳后立即换取一次性注册令牌。 */
    async pay(): Promise<ChannelPaymentOutcome> {
      if (!this.order) throw new Error('请先创建支付订单')
      const outcome = await requestPayment(this.order.payParameters)
      this.lastOutcome = outcome
      if (outcome === 'success') {
        await this.obtainRegistrationToken()
      }
      return outcome
    },

    async refreshStatus(outTradeNo?: string): Promise<OnlineOrderStatus> {
      const target = outTradeNo ?? this.order?.outTradeNo
      if (!target) throw new Error('缺少商户订单号')
      this.status = await api.onlineOrderStatus(target)
      return this.status
    },

    /**
     * 支付成功后签发注册令牌。回调可能晚到，因此先查一次状态做补偿。
     */
    async obtainRegistrationToken(outTradeNo?: string): Promise<IssuedRegistrationToken> {
      const target = outTradeNo ?? this.order?.outTradeNo
      if (!target) throw new Error('缺少商户订单号')
      await this.refreshStatus(target)
      const issued = await api.issueRegistrationToken(target)
      const pending: PendingRegistration = {
        outTradeNo: target,
        token: issued.token,
        expiresAt: issued.expiresAt,
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
      this.pending = null
      h5SessionAdapter.write(session)
      return session
    },

    clearPending(): void {
      pendingRegistrationStore.clear()
      this.pending = null
    },
  },
})
