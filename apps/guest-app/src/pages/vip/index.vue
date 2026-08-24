<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useVipPaymentStore } from '@/stores/payment'
import { readQueryParam } from '@/adapters/returnParams'
import { goBackOr } from '@/adapters/navigation'
import type { OnlineOrderListItem } from '@/types'
import AppIcon from '@/components/AppIcon.vue'
import BrandMark from '@/components/BrandMark.vue'
import AppButton from '@/components/AppButton.vue'
import AppInput from '@/components/AppInput.vue'
import StatusBadge from '@/components/StatusBadge.vue'

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

const ORDER_STATUS: Record<string, { label: string; tone: 'success' | 'warning' | 'neutral' }> = {
  CREATED: { label: '待支付', tone: 'warning' },
  PAID: { label: '已支付', tone: 'success' },
  CLOSED: { label: '已关闭', tone: 'neutral' },
}

const tierLabel = computed(() => TIER_LABEL[vip.tier] ?? '普通用户')
const canRedeem = computed(() => code.value.trim() !== '' && !loading.value)

function orderStatusOf(status: string) {
  return ORDER_STATUS[status] ?? { label: status, tone: 'neutral' as const }
}

function moneyLabel(amountMinor: number): string {
  return `¥${(amountMinor / 100).toFixed(2)}`
}

/** 后端回的是 ISO 时间串，这里只展示到分钟，够用户认出「哪一笔」就行。 */
function timeLabel(value: string | null): string {
  if (!value) return ''
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return ''
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${parsed.getFullYear()}-${pad(parsed.getMonth() + 1)}-${pad(parsed.getDate())} `
    + `${pad(parsed.getHours())}:${pad(parsed.getMinutes())}`
}

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

  // 订单号的三个来源：回跳参数（一次性）、会话存储（单标签页）、服务端订单列表。
  // 只有最后一个在关掉标签页或重新登录之后还找得回来。
  try {
    const resumable = await vip.resumeTarget(readQueryParam('out_trade_no'))
    if (resumable) {
      await resumePayment(resumable)
    }
    await vip.loadOrders()
  } catch (cause) {
    error.value = describe(cause)
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
    } else if (status.status === 'CLOSED') {
      error.value = '这笔订单已超时关闭，请重新下单支付'
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

/** 列表里点某一笔：能付的继续付，不能付的只查状态。 */
async function checkOrder(item: OnlineOrderListItem): Promise<void> {
  notice.value = ''
  error.value = ''
  await resumePayment(item.outTradeNo)
  try {
    await vip.loadOrders()
  } catch {
    // 列表刷新失败无关紧要，状态已经查到了。
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
        <AppButton block :disabled="loading" @tap="startPayment">
          {{ loading ? busyLabel || '处理中' : `支付宝支付 ${vip.amountLabel}` }}
        </AppButton>
      </view>

      <view class="method">
        <text class="section-title">方式二 · 激活码</text>
        <AppInput v-model="code" placeholder="请输入激活码，如 LOVE-XXXX-XXXX-XXXX" />
        <view class="redeem">
          <AppButton variant="secondary" block :disabled="!canRedeem" @tap="redeem">
            使用激活码升级
          </AppButton>
        </view>
        <text class="hint">激活码在生成时已绑定手机号，只能由该手机号的账号使用</text>
      </view>

      <view v-if="vip.orders.length" class="method orders">
        <text class="section-title">我的订单</text>
        <view v-for="item in vip.orders" :key="item.outTradeNo" class="order" @tap="checkOrder(item)">
          <view class="order-head">
            <text class="order-money">{{ moneyLabel(item.amountMinor) }}</text>
            <StatusBadge
              :tone="orderStatusOf(item.status).tone"
              :label="orderStatusOf(item.status).label" />
          </view>
          <text class="order-meta">下单时间 {{ timeLabel(item.createdAt) }}</text>
          <text v-if="item.paidAt" class="order-meta">支付时间 {{ timeLabel(item.paidAt) }}</text>
          <text v-if="item.channelTradeNo" class="order-meta">
            支付平台单号 {{ item.channelTradeNo }}
          </text>
          <text class="order-action">
            {{ item.status === 'CREATED' ? '点击继续确认支付结果' : '点击查看最新状态' }}
          </text>
        </view>
        <text class="hint">
          订单超时未付会自动关闭，重新下单即可；对账时把「支付平台单号」提供给客服最快
        </text>
      </view>

      <text class="back" @tap="back">返回</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.vip-page {
  min-height: 100vh;
  margin: 0 auto;
  padding-bottom: calc(#{$ds-space-8} + env(safe-area-inset-bottom));
  background: $ds-white;
}
.brand-hero {
  height: 340rpx;
  padding-top: calc(76rpx + env(safe-area-inset-top));
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #15181c;
  color: $ds-white;
}
.brand-title {
  margin-top: $ds-space-2;
  color: #ead4a7;
  font-family: $ds-font-serif;
  font-size: 44rpx;
  letter-spacing: 7rpx;
}
.brand-sub {
  margin-top: $ds-space-2;
  @include ds-caption;
  color: #c2aa7d;
  letter-spacing: 4rpx;
}
.surface {
  position: relative;
  z-index: 2;
  margin: -92rpx $ds-space-4 0;
  padding: $ds-space-5 $ds-space-5 $ds-space-4;
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-md;
  background: $ds-white;
  box-shadow: $ds-shadow-deep;
}
.section-title {
  display: block;
  margin-bottom: $ds-space-2;
  @include ds-body-2;
  font-weight: 600;
}
.benefits {
  padding-bottom: $ds-space-4;
  border-bottom: $ds-hairline solid #eeece8;
}
.benefit {
  margin-bottom: $ds-space-2;
  display: flex;
  align-items: center;
  gap: $ds-space-2;
  @include ds-body-2;
  color: $ds-graphite;
}
.method {
  margin-top: $ds-space-4;
}
.redeem {
  margin-top: $ds-space-2;
}
.hint {
  display: block;
  margin-top: $ds-space-2;
  @include ds-caption;
  color: $ds-gray;
  line-height: 34rpx;
}
.orders {
  padding-top: $ds-space-4;
  border-top: $ds-hairline solid #eeece8;
}
.order {
  margin-bottom: $ds-space-2;
  padding: $ds-space-3;
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-sm;
  background: #fcfbf9;
}
.order-head {
  margin-bottom: $ds-space-1;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.order-money {
  @include ds-body-1;
  font-weight: 600;
}
.order-meta {
  display: block;
  @include ds-caption;
  color: $ds-gray;
  line-height: 34rpx;
  /* 平台单号是 20 位数字，窄屏必须能断行，否则会把卡片撑破。 */
  word-break: break-all;
}
.order-action {
  display: block;
  margin-top: $ds-space-1;
  @include ds-caption;
  color: $ds-graphite;
  text-decoration: underline;
}
.notice-text {
  display: block;
  margin-top: $ds-space-3;
  @include ds-body-2;
  color: $ds-success-ink;
}
.error {
  display: block;
  margin-top: $ds-space-3;
  @include ds-body-2;
  color: $ds-error;
}
.back {
  display: block;
  margin-top: $ds-space-4;
  @include ds-caption;
  color: $ds-gray;
  text-align: center;
  text-decoration: underline;
}
@media screen and (min-width: 431px) {
  .vip-page { max-width: $ds-viewport-max; }
}
</style>
