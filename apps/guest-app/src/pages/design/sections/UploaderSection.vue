<script setup lang="ts">
import AppUploader from '@/components/AppUploader.vue'
import type { UploaderItem } from '@/components/AppUploader.vue'

/** 规范图区块 5 · 图片上传：头像四态与生活照栅格。 */
// 预览页用 1×1 透明占位图，避免为陈列引入真实素材文件。
const PLACEHOLDER = 'data:image/svg+xml;utf8,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="120" height="120">'
  + '<rect width="120" height="120" fill="#d9d6d1"/>'
  + '<circle cx="60" cy="46" r="20" fill="#b3afa8"/>'
  + '<path d="M20 116c4-24 20-36 40-36s36 12 40 36z" fill="#b3afa8"/></svg>',
)

const EMPTY: UploaderItem[] = []
const UPLOADED: UploaderItem[] = [{ key: 'a', url: PLACEHOLDER }]
const UPLOADING: UploaderItem[] = [{ key: 'b', url: PLACEHOLDER, progress: 60 }]
const LIFE_PHOTOS: UploaderItem[] = [
  { key: 'l1', url: PLACEHOLDER },
  { key: 'l2', url: PLACEHOLDER },
  { key: 'l3', url: PLACEHOLDER },
  { key: 'l4', url: PLACEHOLDER, progress: 40 },
]

function noop(): void {
  uni.showToast({ title: '预览页不执行真实上传', icon: 'none' })
}
</script>

<template>
  <view class="spec-block">
    <text class="spec-block__title">5. 图片上传组件</text>

    <view class="spec-group">
      <text class="spec-group__title">头像上传</text>
      <view class="states">
        <view class="state">
          <text class="spec-label">空状态</text>
          <AppUploader mode="avatar" :items="EMPTY" footer="" @add="noop" />
        </view>
        <view class="state">
          <text class="spec-label">已上传</text>
          <AppUploader mode="avatar" :items="UPLOADED" footer="" @add="noop" @remove="noop" />
        </view>
        <view class="state">
          <text class="spec-label">上传中</text>
          <AppUploader mode="avatar" :items="UPLOADING" footer="" />
        </view>
      </view>
      <text class="spec-note">已上传态右上角为删除角标，右下角为重新选择的相机角标；上传中不可删除。</text>
    </view>

    <view class="spec-group">
      <text class="spec-group__title">生活照（最多 3 张）</text>
      <AppUploader :items="LIFE_PHOTOS" :max="6" @add="noop" @remove="noop" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.states {
  display: flex;
  flex-wrap: wrap;
  gap: $ds-space-4;
}

.state {
  min-width: 176rpx;
}
</style>
