<script setup lang="ts">
defineProps<{ title?: string; meta?: string }>()
</script>

<template>
  <view class="section-wrap">
    <view v-if="title || meta" class="section-label">
      <text>{{ title }}</text><text v-if="meta">{{ meta }}</text>
    </view>
    <view class="section-card"><slot /></view>
  </view>
</template>

<style lang="scss" scoped>
.section-wrap { margin-bottom: 24rpx; }
.section-label {
  margin-bottom: 12rpx;
  padding: 0 6rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #1e1e22;
  font-size: 26rpx;
  font-weight: 600;
}
.section-label text:last-child:not(:first-child) {
  color: #8a8b8f;
  font-size: 22rpx;
  font-weight: 400;
}
.section-card {
  overflow: hidden;
  border: 1rpx solid #e5e3df;
  border-radius: 20rpx;
  background: #ffffff;
}
</style>
