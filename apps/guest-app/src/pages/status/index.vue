<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import * as api from '@/api'

const status = ref<{
  status: string
  pendingRevisionId: number | null
  currentApprovedRevisionId: number | null
} | null>(null)

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
  <view class="page">
    <view class="card">
      <text class="status-title">{{ status?.status ?? '加载中' }}</text>
      <text v-if="status?.status === 'PENDING_REVIEW'" class="status-hint">
        档案审核中，请耐心等待
      </text>
      <text v-else-if="status?.status === 'APPROVED'" class="status-hint">
        档案已通过审核
      </text>
      <text v-else-if="status?.status === 'CHANGES_REQUESTED'" class="status-hint">
        档案被退回，请修改后重新提交
      </text>
      <text v-else class="status-hint">档案尚未提交，请先完善档案</text>
      <button v-if="status?.status === 'CHANGES_REQUESTED'" class="submit" @tap="editAgain">
        去修改
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  padding: 30rpx;
}
.card {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 60rpx 30rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.status-title {
  font-size: 40rpx;
  font-weight: 700;
  color: #46323a;
}
.status-hint {
  margin-top: 20rpx;
  font-size: 28rpx;
  color: #8b7a80;
  text-align: center;
}
.submit {
  margin-top: 40rpx;
  width: 320rpx;
  background: #b4556d;
  color: #ffffff;
  border-radius: 999rpx;
}
</style>
