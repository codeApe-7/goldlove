<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { usePaymentStore, pendingOrderStore, phoneDraftStore } from '@/stores/payment'
import * as api from '@/api'
import { readQueryParam, redirectTo } from '@/adapters/wechat'
import AppIcon from '@/components/AppIcon.vue'
import BrandMark from '@/components/BrandMark.vue'
import type { AuthorizationDocumentView } from '@/types'

const payment = usePaymentStore()
const authorizationDocument = ref<AuthorizationDocumentView | null>(null)
const agreed = ref(false)
const phone = ref('')
const loading = ref(false)
const busyLabel = ref('')
const error = ref('')
const documentExpanded = ref(false)

const phoneValid = computed(() => /^\d{11}$/.test(phone.value.trim().replace(/[\s-]/g, '')))
const canPay = computed(
  () => agreed.value && phoneValid.value && !loading.value && authorizationDocument.value !== null,
)
const isXpay = computed(() => payment.settings?.channelType === 'XPAY_ALIPAY')

function toast(message: string, icon: 'none' | 'success' = 'none'): void {
  uni.showToast({ title: message, icon })
}

function describe(cause: unknown): string {
  return cause instanceof Error ? cause.message : '操作失败'
}

onMounted(async () => {
  loading.value = true
  busyLabel.value = '加载中'
  // 网页授权/收银台回跳后页面已重新加载，先取回用户此前填的手机号。
  phone.value = phoneDraftStore.read()
  try {
    authorizationDocument.value = await api.currentAuthorizationDocument()
    await payment.loadSettings()
  } catch (cause) {
    error.value = describe(cause)
  } finally {
    loading.value = false
    busyLabel.value = ''
  }

  // 微信网页授权回跳带 code；易支付 return_url 回跳带 out_trade_no。
  // 平台未回带 out_trade_no 时退回会话存储——它是这笔订单唯一的句柄。
  const code = readQueryParam('code')
  if (code && authorizationDocument.value && !isXpay.value) {
    agreed.value = true
    await orderAndPay(code)
  }
  const resumable = readQueryParam('out_trade_no') ?? pendingOrderStore.read()?.outTradeNo
  if (resumable && isXpay.value) {
    agreed.value = true
    await resumeUnfinishedPayment(resumable)
  }
})

async function orderAndPay(authorizationCode: string): Promise<void> {
  if (!authorizationDocument.value) return
  loading.value = true
  busyLabel.value = '正在创建订单'
  error.value = ''
  try {
    const order = await payment.createOrder(
      phone.value.trim(),
      authorizationCode,
      authorizationDocument.value.version,
    )
    if (order.paid) {
      // 该手机号此前已付款但未注册，后端复用了那笔订单，不再收钱。
      busyLabel.value = '正在恢复已支付订单'
      await payment.pay()
      toast('已找到您此前的付款', 'success')
      uni.redirectTo({ url: '/pages/register/index' })
      return
    }
    if (isXpay.value) {
      // 易支付跳转支付宝收银台，支付结果通过 return_url 回跳后查单补偿。
      await payment.pay()
      return
    }
    busyLabel.value = '正在调起支付'
    const outcome = await payment.pay()
    if (outcome === 'success') {
      toast('支付成功', 'success')
      uni.redirectTo({ url: '/pages/register/index' })
      return
    }
    error.value = outcome === 'cancel' ? '您已取消支付，可重新发起' : '支付未完成，请重试'
  } catch (cause) {
    error.value = describe(cause)
  } finally {
    loading.value = false
    busyLabel.value = ''
  }
}

/** 未授权渠道（微信）先跳网页授权；无授权前置渠道（易支付）直接下单。 */
function startPayment(): void {
  if (!agreed.value) {
    toast('请先阅读并同意授权书')
    return
  }
  if (!phoneValid.value) {
    toast('请输入 11 位手机号')
    return
  }
  if (!payment.settings) {
    toast('支付渠道尚未就绪')
    return
  }
  if (isXpay.value) {
    void orderAndPay('')
    return
  }
  if (!payment.settings.authorizeUrl) {
    toast('支付渠道尚未就绪')
    return
  }
  // 微信要先整页跳走做网页授权，手机号必须先落盘才能在回跳后拿回来。
  phoneDraftStore.write(phone.value.trim())
  redirectTo(payment.settings.authorizeUrl)
}

/** 支付已完成但页面中断时，用订单号补偿领取注册令牌。 */
async function resumeUnfinishedPayment(outTradeNo?: string): Promise<void> {
  const target = outTradeNo ?? payment.order?.outTradeNo
  if (!target) {
    toast('没有待恢复的订单')
    return
  }
  loading.value = true
  busyLabel.value = '正在查询支付结果'
  try {
    const status = await payment.refreshStatus(target)
    if (status.status !== 'PAID') {
      error.value = '尚未收到支付成功结果，请稍后再试'
      return
    }
    await payment.obtainRegistrationToken(target)
    uni.redirectTo({ url: '/pages/register/index' })
  } catch (cause) {
    error.value = describe(cause)
  } finally {
    loading.value = false
    busyLabel.value = ''
  }
}
</script>

<template>
  <view class="payment-page">
    <view class="hero">
      <BrandMark light />
      <text class="hero-title">建档服务</text>
      <text class="hero-price">{{ payment.amountLabel || '—' }}</text>
      <text class="hero-sub">{{ payment.settings?.orderDescription || '真实 · 严谨 · 安全 · 专属' }}</text>
    </view>

    <view class="surface">
      <view class="steps">
        <view class="step"><text class="index">1</text><text>填写手机号并同意授权书</text></view>
        <view class="step"><text class="index">2</text><text>支付建档费用</text></view>
        <view class="step"><text class="index">3</text><text>设置密码完成注册</text></view>
      </view>

      <label class="field-group">
        <text>手机号</text>
        <input
          v-model="phone"
          class="field"
          type="number"
          maxlength="11"
          placeholder="登录账号，付款后用它完成注册"
        />
      </label>

      <view v-if="authorizationDocument" class="document">
        <view class="document-head" @tap="documentExpanded = !documentExpanded">
          <text class="document-title">
            {{ authorizationDocument.title }}（{{ authorizationDocument.version }}）
          </text>
          <AppIcon name="chevron" :size="18" />
        </view>
        <scroll-view v-if="documentExpanded" class="document-body" scroll-y>
          <text>{{ authorizationDocument.content }}</text>
        </scroll-view>
        <text v-else class="document-hint">点击展开全文</text>
      </view>

      <label class="agree-row">
        <checkbox :checked="agreed" @tap="agreed = !agreed" />
        <text>我已阅读并同意上述授权书，并知悉付款后方可建档</text>
      </label>

      <text v-if="error" class="error">{{ error }}</text>

      <button class="archive-button-primary submit" :disabled="!canPay" @tap="startPayment">
        {{ loading ? busyLabel || '处理中' : `${isXpay ? '支付宝支付' : '微信支付'} ${payment.amountLabel}` }}
      </button>
      <text v-if="payment.order" class="resume" @tap="resumeUnfinishedPayment">已完成支付？点此恢复</text>

      <view class="notice">
        <AppIcon name="lock" :size="16" />
        <view>
          <strong>支付金额由服务端确定</strong>
          <text>手机号将加密存储，仅用于登录与去重；建档注册即成为 VIP 会员。</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.payment-page {
  min-height: 100vh;
  margin: 0 auto;
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
  background: #ffffff;
}
.hero {
  height: 420rpx;
  padding-top: calc(80rpx + env(safe-area-inset-top));
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #15181c;
  color: #ffffff;
}
.hero-title {
  margin-top: 18rpx;
  color: #ead4a7;
  font-family: "Songti SC", serif;
  font-size: 34rpx;
  letter-spacing: 6rpx;
}
.hero-price {
  margin-top: 12rpx;
  color: #ffffff;
  font-size: 62rpx;
  font-weight: 600;
}
.hero-sub {
  margin-top: 10rpx;
  color: #c2aa7d;
  font-size: 21rpx;
  letter-spacing: 3rpx;
}
.surface {
  position: relative;
  z-index: 2;
  margin: -80rpx 28rpx 0;
  padding: 28rpx 26rpx 24rpx;
  border: 1rpx solid #e5e3df;
  border-radius: 22rpx;
  background: #ffffff;
  box-shadow: 0 18rpx 48rpx rgba(13, 13, 15, 0.09);
}
.steps {
  margin-bottom: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.step {
  display: flex;
  align-items: center;
  gap: 14rpx;
  color: #4a4b4f;
  font-size: 23rpx;
}
.step .index {
  width: 34rpx;
  height: 34rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 99rpx;
  background: #f2f0ec;
  color: #0d0d0f;
  font-size: 19rpx;
  font-weight: 600;
}
.document {
  margin-bottom: 20rpx;
  padding: 18rpx;
  border: 1rpx solid #e5e3df;
  border-radius: 12rpx;
}
.document-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}
.document-title {
  font-size: 22rpx;
  font-weight: 600;
}
.document-body {
  max-height: 420rpx;
  margin-top: 12rpx;
}
.document-body text {
  color: #55565a;
  font-size: 20rpx;
  line-height: 1.7;
  white-space: pre-wrap;
}
.document-hint {
  display: block;
  margin-top: 8rpx;
  color: #929397;
  font-size: 19rpx;
}
.agree-row {
  display: flex;
  align-items: flex-start;
  gap: 10rpx;
  margin-bottom: 18rpx;
  color: #4a4b4f;
  font-size: 21rpx;
  line-height: 1.6;
}
.field-group {
  display: block;
  margin-bottom: 20rpx;
}
.field-group > text {
  display: block;
  margin-bottom: 10rpx;
  font-size: 23rpx;
  font-weight: 600;
}
.field {
  width: 100%;
  height: 76rpx;
  padding: 0 22rpx;
  border: 1rpx solid #dfddd9;
  border-radius: 10rpx;
  font-size: 25rpx;
}
.error {
  display: block;
  margin-bottom: 14rpx;
  color: #b3261e;
  font-size: 21rpx;
}
.submit {
  width: 100%;
}
.resume {
  display: block;
  margin-top: 14rpx;
  color: #85868a;
  text-align: center;
  font-size: 20rpx;
  text-decoration: underline;
}
.notice {
  margin-top: 22rpx;
  padding: 18rpx;
  display: flex;
  gap: 14rpx;
  border: 1rpx solid #e5e3df;
  border-radius: 12rpx;
}
.notice.warn {
  margin-top: 0;
  margin-bottom: 22rpx;
  border-color: #e6d5b8;
  background: #fdf8ee;
}
.notice strong,
.notice text { display: block; }
.notice strong { font-size: 21rpx; }
.notice text { margin-top: 5rpx; color: #929397; font-size: 19rpx; line-height: 1.5; }
@media screen and (min-width: 431px) {
  .payment-page { max-width: 430px; }
}
</style>
