<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { usePaymentStore } from '@/stores/payment'
import * as api from '@/api'
import { isWechatBrowser, readQueryParam, redirectTo } from '@/adapters/wechat'
import AppIcon from '@/components/AppIcon.vue'
import BrandMark from '@/components/BrandMark.vue'
import type { AuthorizationDocumentView } from '@/types'

const payment = usePaymentStore()
const authorizationDocument = ref<AuthorizationDocumentView | null>(null)
const agreed = ref(false)
const loading = ref(false)
const busyLabel = ref('')
const error = ref('')
const insideWechat = ref(true)
const documentExpanded = ref(false)

const canPay = computed(() => agreed.value && !loading.value && authorizationDocument.value !== null)

function toast(message: string, icon: 'none' | 'success' = 'none'): void {
  uni.showToast({ title: message, icon })
}

function describe(cause: unknown): string {
  return cause instanceof Error ? cause.message : '操作失败'
}

onMounted(async () => {
  insideWechat.value = isWechatBrowser()
  loading.value = true
  busyLabel.value = '加载中'
  try {
    authorizationDocument.value = await api.currentAuthorizationDocument()
    await payment.loadSettings()
  } catch (cause) {
    error.value = describe(cause)
  } finally {
    loading.value = false
    busyLabel.value = ''
  }

  // 网页授权回跳把 code 带在地址上；uni-app H5 走 hash 路由，因此直接读 location。
  const code = readQueryParam('code')
  if (code && authorizationDocument.value) {
    agreed.value = true
    await orderAndPay(code)
  }
})

async function orderAndPay(authorizationCode: string): Promise<void> {
  if (!authorizationDocument.value) return
  loading.value = true
  busyLabel.value = '正在创建订单'
  error.value = ''
  try {
    await payment.createOrder(authorizationCode, authorizationDocument.value.version)
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

/** 未授权时先跳公众号网页授权，回跳地址由服务端固定。 */
function startPayment(): void {
  if (!agreed.value) {
    toast('请先阅读并同意授权书')
    return
  }
  if (!payment.settings) {
    toast('支付渠道尚未就绪')
    return
  }
  redirectTo(payment.settings.authorizeUrl)
}

/** 支付已完成但页面中断时，用订单号补偿领取注册令牌。 */
async function resumeUnfinishedPayment(): Promise<void> {
  const outTradeNo = payment.order?.outTradeNo
  if (!outTradeNo) {
    toast('没有待恢复的订单')
    return
  }
  loading.value = true
  busyLabel.value = '正在查询支付结果'
  try {
    const status = await payment.refreshStatus(outTradeNo)
    if (status.status !== 'PAID') {
      error.value = '尚未收到支付成功结果，请稍后再试'
      return
    }
    await payment.obtainRegistrationToken(outTradeNo)
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
      <view v-if="!insideWechat" class="notice warn">
        <AppIcon name="lock" :size="16" />
        <view>
          <strong>请在微信中打开本页面</strong>
          <text>线上建档使用微信支付，需要在微信内完成；也可联系客服走人工登记。</text>
        </view>
      </view>

      <view class="steps">
        <view class="step"><text class="index">1</text><text>阅读并同意授权书</text></view>
        <view class="step"><text class="index">2</text><text>微信支付建档费用</text></view>
        <view class="step"><text class="index">3</text><text>填写手机号完成注册</text></view>
      </view>

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
        {{ loading ? busyLabel || '处理中' : `微信支付 ${payment.amountLabel}` }}
      </button>
      <text v-if="payment.order" class="resume" @tap="resumeUnfinishedPayment">已完成支付？点此恢复</text>

      <view class="notice">
        <AppIcon name="lock" :size="16" />
        <view>
          <strong>支付金额由服务端确定</strong>
          <text>支付结果以微信支付通知为准；建档注册即成为 VIP 会员。</text>
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
