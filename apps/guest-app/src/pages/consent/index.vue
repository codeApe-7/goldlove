<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import * as api from '@/api'
import { useConsentStore } from '@/stores/consent'
import type { AuthorizationDocumentView } from '@/types'
import AppIcon from '@/components/AppIcon.vue'

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

function toggleAgree(): void {
  agreed.value = !agreed.value
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
  <view class="consent-page archive-page">
    <view class="document-head">
      <text class="doc-title">{{ document?.title ?? '婚恋智能档案库授权书' }}</text>
      <view class="doc-meta"><text>版本号：{{ document?.version ?? '—' }}</text><text>生效日期：{{ document?.effectiveAt?.slice(0, 10) ?? '—' }}</text></view>
      <text class="doc-lead">欢迎使用婚恋智能档案库服务。为保障您的隐私与安全，请仔细阅读以下授权内容。</text>
    </view>
    <scroll-view scroll-y class="doc-body">
      <text class="doc-content">{{ document?.content || '授权书正在加载，请稍候…' }}</text>
    </scroll-view>
    <view class="consent-actions">
      <view class="agree-row" @tap="toggleAgree">
        <view class="checkbox" :class="{ checked: agreed }"><AppIcon v-if="agreed" name="check" :size="13" /></view>
        <text>我已阅读并同意《授权书》全部内容</text>
      </view>
      <button class="archive-button-primary" :disabled="submitting || !agreed" @tap="confirm">同意并继续</button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.consent-page {
  height: 100vh;
  padding-bottom: 0;
  display: flex;
  flex-direction: column;
}
.document-head {
  padding: 28rpx 22rpx 24rpx;
  border: 1rpx solid #e5e3df;
  border-bottom: 0;
  border-radius: 18rpx 18rpx 0 0;
  background: #ffffff;
}
.doc-title {
  display: block;
  font-size: 31rpx;
  font-weight: 700;
  color: #0d0d0f;
}
.doc-meta {
  margin-top: 14rpx;
  display: flex;
  gap: 28rpx;
  color: #85868a;
  font-size: 19rpx;
}
.doc-lead {
  display: block;
  margin-top: 22rpx;
  color: #55565a;
  font-size: 22rpx;
  line-height: 1.65;
}
.doc-body {
  flex: 1;
  min-height: 0;
  padding: 0 22rpx 24rpx;
  border: 1rpx solid #e5e3df;
  border-top: 0;
  background: #ffffff;
}
.doc-content {
  font-size: 23rpx;
  line-height: 1.9;
  color: #343438;
  white-space: pre-wrap;
}
.consent-actions {
  margin: 20rpx -28rpx 0;
  padding: 20rpx 28rpx calc(22rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid #e5e3df;
  background: #ffffff;
}
.agree-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-bottom: 18rpx;
  color: #4f5054;
  font-size: 21rpx;
}
.checkbox {
  width: 28rpx;
  height: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid #b9babd;
  border-radius: 4rpx;
  color: #ffffff;
}
.checkbox.checked {
  border-color: #0d0d0f;
  background: #0d0d0f;
}
</style>
