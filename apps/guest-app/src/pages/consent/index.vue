<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import * as api from '@/api'
import { useConsentStore } from '@/stores/consent'
import type { AuthorizationDocumentView } from '@/types'

const consent = useConsentStore()
const document = ref<AuthorizationDocumentView | null>(null)
const agreed = ref(false)
const submitting = ref(false)

onLoad(async () => {
  try {
    document.value = await api.currentAuthorizationDocument()
  } catch {
    uni.showToast({ title: '授权书加载失败', icon: 'none' })
  }
})

function toggleAgree(event: { detail: { value: boolean } }): void {
  agreed.value = event.detail.value
}

async function confirm(): Promise<void> {
  if (!agreed.value || !document.value) {
    uni.showToast({ title: '请先勾选同意', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await consent.accept(document.value.version)
    uni.switchTab({ url: '/pages/profile/index' })
  } catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : '同意失败',
      icon: 'none',
    })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="page consent-page">
    <scroll-view scroll-y class="doc">
      <text class="doc-title">{{ document?.title ?? '付费建档与直播内容授权书' }}</text>
      <text class="doc-content">{{ document?.content }}</text>
    </scroll-view>
    <view class="agree-row">
      <switch :checked="agreed" @change="toggleAgree" color="#B4556D" />
      <text>我已阅读并同意本授权书</text>
    </view>
    <button class="submit" :disabled="submitting" @tap="confirm">同意并继续</button>
  </view>
</template>

<style lang="scss" scoped>
.consent-page {
  min-height: 100vh;
  padding: 30rpx;
  background: #f7f5f2;
  display: flex;
  flex-direction: column;
}
.doc {
  flex: 1;
  background: #ffffff;
  border-radius: 24rpx;
  padding: 30rpx;
  margin-bottom: 24rpx;
}
.doc-title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: #46323a;
  margin-bottom: 20rpx;
}
.doc-content {
  font-size: 28rpx;
  line-height: 1.8;
  color: #3b3034;
  white-space: pre-wrap;
}
.agree-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 20rpx;
}
.submit {
  background: #b4556d;
  color: #ffffff;
  border-radius: 999rpx;
}
</style>
