<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import * as api from '@/api'
import AppIcon from '@/components/AppIcon.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { guestStatusMeta } from '@/utils/presentation'

const status = ref<{
  status: string
  pendingRevisionId: number | null
  currentApprovedRevisionId: number | null
} | null>(null)

const statusCode = computed(() => status.value?.status ?? 'DRAFT')
const statusMeta = computed(() => guestStatusMeta(statusCode.value))
const progressStep = computed(() => {
  if (statusCode.value === 'APPROVED') return 3
  if (statusCode.value === 'PENDING_REVIEW') return 2
  return 1
})

onShow(async () => {
  try {
    status.value = await api.profileStatus()
  } catch {
    uni.showToast({ title: '状态加载失败', icon: 'none' })
  }
})

function editAgain(): void {
  uni.switchTab({ url: '/pages/profile/index' })
}
</script>

<template>
  <view class="archive-page status-page">
    <text class="archive-title">审核状态</text>
    <text class="archive-subtitle">查看您的档案提交与审核进度</text>

    <view class="status-overview">
      <view class="overview-top"><view><text>当前状态</text><strong>{{ statusMeta.label }}</strong></view><StatusBadge :status="statusCode" /></view>
      <view class="status-rail">
        <view v-for="(label, index) in ['提交档案', '审核中', '完成']" :key="label" class="rail-step" :class="{ active: progressStep >= index + 1 }">
          <text>{{ progressStep > index + 1 ? '✓' : index + 1 }}</text><small>{{ label }}</small>
        </view>
        <view class="rail-line"><i :style="{ width: `${((progressStep - 1) / 2) * 100}%` }" /></view>
      </view>
      <view v-if="status?.pendingRevisionId || status?.currentApprovedRevisionId" class="version-row">
        <text>档案版本</text><text class="archive-tabular">#{{ status.pendingRevisionId || status.currentApprovedRevisionId }}</text>
      </view>
    </view>

    <view class="result-card" :class="`tone-${statusMeta.tone}`">
      <view class="result-icon"><AppIcon :name="statusCode === 'APPROVED' ? 'check' : statusCode === 'CHANGES_REQUESTED' ? 'close' : statusCode === 'PENDING_REVIEW' ? 'clock' : 'document'" :size="21" /></view>
      <view class="result-copy"><view><strong>{{ statusMeta.label }}</strong><StatusBadge :status="statusCode" /></view><text>{{ statusMeta.description }}</text></view>
      <button v-if="statusCode === 'CHANGES_REQUESTED'" class="result-action" @tap="editAgain">修改并重新提交</button>
      <button v-else-if="statusCode === 'DRAFT'" class="result-action" @tap="editAgain">去完善档案</button>
    </view>

    <view class="privacy-tip"><AppIcon name="shield" :size="17" /><text>档案审核全程遵循隐私保护规范，审核结果以系统状态为准。</text></view>
  </view>
</template>

<style lang="scss" scoped>
.status-page { padding-top: 26rpx; }
.archive-title,
.archive-subtitle { display: block; }
.status-overview {
  margin-top: 30rpx;
  padding: 26rpx 24rpx 22rpx;
  border-radius: 22rpx;
  background: linear-gradient(145deg, #171a1e, #222529);
  color: #ffffff;
  box-shadow: 0 16rpx 32rpx rgba(13, 13, 15, 0.12);
}
.overview-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.overview-top text,
.overview-top strong { display: block; }
.overview-top text { color: #9fa1a5; font-size: 20rpx; }
.overview-top strong { margin-top: 7rpx; font-size: 34rpx; }
.status-rail {
  position: relative;
  margin-top: 34rpx;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
}
.rail-step {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  color: #6f7175;
}
.rail-step text {
  width: 32rpx;
  height: 32rpx;
  border: 4rpx solid #222529;
  border-radius: 50%;
  background: #5c5e62;
  text-align: center;
  font-size: 16rpx;
  line-height: 25rpx;
}
.rail-step small { margin-top: 8rpx; font-size: 18rpx; }
.rail-step.active { color: #e5e6e8; }
.rail-step.active text { background: #ffffff; color: #0d0d0f; }
.rail-line {
  position: absolute;
  top: 14rpx;
  left: 16.66%;
  width: 66.66%;
  height: 2rpx;
  background: #54565a;
}
.rail-line i { display: block; height: 100%; background: #ffffff; }
.version-row {
  margin-top: 24rpx;
  padding-top: 18rpx;
  display: flex;
  justify-content: space-between;
  border-top: 1rpx solid rgba(255,255,255,.12);
  color: #a7a9ac;
  font-size: 19rpx;
}
.result-card {
  margin-top: 22rpx;
  padding: 24rpx;
  border: 1rpx solid #e5e3df;
  border-radius: 20rpx;
  background: #ffffff;
}
.result-icon {
  width: 42rpx;
  height: 42rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #efefed;
}
.result-copy { margin-top: 16rpx; }
.result-copy > view { display: flex; align-items: center; justify-content: space-between; }
.result-copy strong { font-size: 29rpx; }
.result-copy > text { display: block; margin-top: 10rpx; color: #77787c; font-size: 22rpx; line-height: 1.6; }
.tone-success .result-icon { background: #ddf7e5; color: #15803d; }
.tone-danger .result-icon { background: #fee5e5; color: #dc2626; }
.tone-warning .result-icon { background: #fff1cf; color: #a16207; }
.result-action {
  height: 70rpx;
  margin-top: 20rpx;
  border: 1rpx solid #dfddd9;
  border-radius: 10rpx;
  background: #ffffff;
  color: #0d0d0f;
  font-size: 23rpx;
  line-height: 70rpx;
}
.privacy-tip {
  margin-top: 26rpx;
  padding: 20rpx;
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  border-radius: 14rpx;
  background: #eeece8;
  color: #77787c;
  font-size: 20rpx;
  line-height: 1.6;
}
</style>
