<script setup lang="ts">
withDefaults(defineProps<{ compact?: boolean; light?: boolean }>(), {
  compact: false,
  light: false,
})
</script>

<template>
  <view class="brand-mark" :class="{ compact, light }" aria-hidden="true">
    <text v-for="index in 7" :key="index" />
  </view>
</template>

<style lang="scss" scoped>
.brand-mark {
  width: 72rpx;
  height: 94rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5rpx;
  color: #c9a96a;

  &.compact {
    width: 48rpx;
    height: 62rpx;
    gap: 3rpx;
  }

  &.light {
    color: #f1d7a3;
  }

  text {
    width: 4rpx;
    height: 48%;
    border-radius: 99rpx;
    background: currentColor;
  }

  text:nth-child(2), text:nth-child(6) { height: 72%; }
  text:nth-child(3), text:nth-child(5) { height: 94%; }
  text:nth-child(4) { height: 66%; }
}
</style>
