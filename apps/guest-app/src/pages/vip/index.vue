<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useVipPaymentStore } from '@/stores/payment'
import { readQueryParam } from '@/adapters/returnParams'
import { goBackOr } from '@/adapters/navigation'
import { pendingOrderStore } from '@/stores/payment'
import AppIcon from '@/components/AppIcon.vue'
import BrandMark from '@/components/BrandMark.vue'

const vip = useVipPaymentStore()
const code = ref('')
const loading = ref(false)
const busyLabel = ref('')
const error = ref('')
const notice = ref('')

const TIER_LABEL: Record<string, string> = {
  FREE: '普通用户',
  VIP: 'VIP 会员',
  SVIP: 'SVIP 会员',
}

const tierLabel = computed(() => TIER_LABEL[vip.tier] ?? '普通用户')
const canRedeem = computed(() => code.value.trim() !== '' && !loading.value)

function toast(message: string, icon: 'none' | 'success' = 'none'): void {
  uni.showToast({ title: message, icon })
}

function describe(cause: unknown): string {
  return cause instanceof Error ? cause.message : '操作失败'
}

onMounted(async () => {
  loading.value = true
  busyLabel.value = '加载中'
  try {
    await Promise.all([vip.loadMembership(), vip.loadSettings()])
  } catch (cause) {
    error.value = describe(cause)
  } finally {
    loading.value = false
    busyLabel.value = ''
  }

  // 收银台整页跳转回来后内存状态已丢：订单号优先取回跳参数，其次取会话存储。
  const resumable = readQueryParam('out_trade_no') || pendingOrderStore.read()
  if (resumable) {
    await resumePayment(resumable)
  }
})

/** 回调可能晚到或丢失，后端在本地仍未支付时会主动向渠道查单补偿。 */
async function resumePayment(outTradeNo: string): Promise<void> {
  loading.value = true
  busyLabel.value = '正在确认支付结果'
  try {
    const status = await vip.refreshStatus(outTradeNo)
    if (status.status === 'PAID') {
      notice.value = '支付已完成，会员权益已生效'
      toast('升级成功', 'success')
    } else {
      error.value = '尚未收到支付结果，稍后可重新查询'
    }
  } catch (cause) {
    error.value = describe(cause)
  } finally {
    loading.value = false
    busyLabel.value = ''
  }
}

async function startPayment(): Promise<void> {
  loading.value = true
  busyLabel.value = '正在创建订单'
  error.value = ''
  notice.value = ''
  try {
    await vip.createOrder()
    busyLabel.value = '正在跳转收银台'
    // 易支付整页跳转支付宝，之后本页会被重新加载，结果靠回跳查单。
    await vip.pay()
  } catch (cause) {
    error.value = describe(cause)
    loading.value = false
    busyLabel.value = ''
  }
}

async function redeem(): Promise<void> {
  loading.value = true
  busyLabel.value = '正在校验激活码'
  error.value = ''
  notice.value = ''
  try {
    const membership = await vip.redeem(code.value)
    code.value = ''
    notice.value = `已升级为 ${TIER_LABEL[membership.tier] ?? membership.tier}`
    toast('升级成功', 'success')
  } catch (cause) {
    error.value = describe(cause)
  } finally {
    loading.value = false
    busyLabel.value = ''
  }
}

function back(): void {
  // 支付回跳 / 直接打开本页时页面栈里没有上一级，必须兜底回「我的」——会员入口就在那。
  goBackOr('/pages/mine/index')
}
</script>

<template>
  <view class="vip-page archive-page">
    <view class="brand-hero">
      <BrandMark light />
      <text class="brand-title">{{ tierLabel }}</text>
      <text class="brand-sub">{{ vip.isVip ? '会员权益已生效' : '升级解锁会员权益' }}</text>
    </view>

    <view class="surface">
      <view class="benefits">
        <text class="section-title">会员权益</text>
        <view class="benefit"><AppIcon name="authorization" :size="16" /><text>VIP 会员身份标识</text></view>
        <view class="benefit"><AppIcon name="document" :size="16" /><text>档案优先进入匹配库</text></view>
        <view class="benefit"><AppIcon name="shield" :size="16" /><text>累计付费达标自动升 SVIP</text></view>
      </view>

      <text v-if="notice" class="notice-text">{{ notice }}</text>
      <text v-if="error" class="error">{{ error }}</text>

      <view class="method">
        <text class="section-title">方式一 · 在线支付</text>
        <button class="archive-button-primary submit" :disabled="loading" @tap="startPayment">
          {{ loading ? busyLabel || '处理中' : `支付宝支付 ${vip.amountLabel}` }}
        </button>
      </view>

      <view class="method">
        <text class="section-title">方式二 · 激活码</text>
        <input v-model="code" class="field" type="text" placeholder="请输入激活码，如 LOVE-XXXX-XXXX-XXXX" />
        <button class="archive-button-ghost submit" :disabled="!canRedeem" @tap="redeem">
          使用激活码升级
        </button>
        <text class="hint">激活码在生成时已绑定手机号，只能由该手机号的账号使用</text>
      </view>

      <text class="back" @tap="back">返回</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.vip-page {
  min-height: 100vh;
  margin: 0 auto;
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
  background: #ffffff;
}
.brand-hero {
  height: 340rpx;
  padding-top: calc(76rpx + env(safe-area-inset-top));
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #15181c;
  color: #ffffff;
}
.brand-title {
  margin-top: 18rpx;
  color: #ead4a7;
  font-family: "Songti SC", serif;
  font-size: 38rpx;
  letter-spacing: 7rpx;
}
.brand-sub {
  margin-top: 14rpx;
  color: #c2aa7d;
  font-size: 22rpx;
  letter-spacing: 4rpx;
}
.surface {
  position: relative;
  z-index: 2;
  margin: -92rpx 28rpx 0;
  padding: 30rpx 26rpx 24rpx;
  border: 1rpx solid #e5e3df;
  border-radius: 22rpx;
  background: #ffffff;
  box-shadow: 0 18rpx 48rpx rgba(13, 13, 15, 0.09);
}
.section-title {
  display: block;
  margin-bottom: 14rpx;
  font-size: 23rpx;
  font-weight: 600;
}
.benefits {
  padding-bottom: 22rpx;
  border-bottom: 1rpx solid #eeece8;
}
.benefit {
  margin-bottom: 12rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
  color: #55565a;
  font-size: 21rpx;
}
.method {
  margin-top: 24rpx;
}
.field {
  width: 100%;
  height: 76rpx;
  margin-bottom: 14rpx;
  padding: 0 22rpx;
  border: 1rpx solid #dfddd9;
  border-radius: 10rpx;
  font-size: 25rpx;
}
.submit { width: 100%; }
.hint {
  display: block;
  margin-top: 10rpx;
  color: #929397;
  font-size: 19rpx;
  line-height: 1.5;
}
.notice-text {
  display: block;
  margin-top: 18rpx;
  color: #1f7a4d;
  font-size: 21rpx;
}
.error {
  display: block;
  margin-top: 18rpx;
  color: #b3261e;
  font-size: 21rpx;
}
.back {
  display: block;
  margin-top: 24rpx;
  color: #85868a;
  text-align: center;
  font-size: 20rpx;
  text-decoration: underline;
}
@media screen and (min-width: 431px) {
  .vip-page { max-width: 430px; }
}
</style>
