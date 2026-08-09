<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useConsentStore } from '@/stores/consent'

const auth = useAuthStore()
const consent = useConsentStore()

onShow(async () => {
  try {
    await consent.load()
  } catch {
    // 未登录/会话失效由 request 统一处理
  }
})

function renewConsent(): void {
  uni.navigateTo({ url: '/pages/consent/index' })
}

async function logout(): Promise<void> {
  await auth.logout()
  uni.reLaunch({ url: '/pages/auth/index' })
}
</script>

<template>
  <view class="page">
    <view class="card">
      <text class="title">账号</text>
      <text class="row">账号 ID：{{ auth.session?.accountId }}</text>
    </view>
    <view class="card">
      <text class="title">授权</text>
      <text v-if="consent.current" class="row">
        有效期至 {{ consent.current.expiresAt }}（版本 {{ consent.current.authorizationDocumentVersion }}）
      </text>
      <text v-else class="row">暂无有效授权</text>
      <button class="link" @tap="renewConsent">查看/重新同意授权书</button>
    </view>
    <button class="logout" @tap="logout">退出登录</button>
  </view>
</template>

<style lang="scss" scoped>
.page {
  padding: 30rpx;
}
.card {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 30rpx;
  margin-bottom: 24rpx;
}
.title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #46323a;
  margin-bottom: 16rpx;
}
.row {
  display: block;
  font-size: 28rpx;
  color: #3b3034;
  margin-bottom: 12rpx;
}
.link {
  margin-top: 12rpx;
  background: #ffffff;
  color: #b4556d;
  border: 2rpx solid #b4556d;
  border-radius: 999rpx;
}
.logout {
  margin-top: 30rpx;
  background: #ffffff;
  color: #b4556d;
  border: 2rpx solid #b4556d;
  border-radius: 999rpx;
}
</style>
