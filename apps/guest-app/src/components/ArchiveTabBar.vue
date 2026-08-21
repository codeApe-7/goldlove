<script setup lang="ts">
import { onMounted } from 'vue'

type ArchiveTabKey = 'profile' | 'mine'

const props = defineProps<{ current: ArchiveTabKey }>()

const items: Array<{ key: ArchiveTabKey; label: string; url: string }> = [
  { key: 'profile', label: '档案', url: '/pages/profile/index' },
  { key: 'mine', label: '我的', url: '/pages/mine/index' },
]

onMounted(() => {
  uni.hideTabBar({ animation: false })
})

function navigate(item: (typeof items)[number]): void {
  if (item.key === props.current) return
  uni.switchTab({ url: item.url })
}
</script>

<template>
  <view class="archive-tabbar" role="navigation" aria-label="主导航">
    <view class="archive-tabbar__inner">
      <view
        v-for="item in items"
        :key="item.key"
        class="archive-tabbar__item"
        :class="{ 'is-active': current === item.key }"
        :aria-current="current === item.key ? 'page' : undefined"
        :aria-label="item.label"
        @tap="navigate(item)"
      >
        <view class="archive-tabbar__icon" aria-hidden="true">
          <svg v-if="item.key === 'profile'" viewBox="0 0 24 24">
            <path v-if="current === item.key" class="icon-fill" d="M3.7 10.15 12 3.5l8.3 6.65v9.6a.75.75 0 0 1-.75.75H14.5v-6h-5v6H4.45a.75.75 0 0 1-.75-.75z" />
            <path v-else d="m3.7 10.15 8.3-6.65 8.3 6.65M5.2 9.2v10.55c0 .41.34.75.75.75H9.5v-6h5v6h3.55c.41 0 .75-.34.75-.75V9.2" />
          </svg>
          <svg v-else viewBox="0 0 24 24">
            <path v-if="current === item.key" class="icon-fill" d="M12 11.6a4.35 4.35 0 1 0 0-8.7 4.35 4.35 0 0 0 0 8.7Zm0 1.9c-5.02 0-8.05 2.58-8.05 5.42 0 .65.53 1.18 1.18 1.18h13.74c.65 0 1.18-.53 1.18-1.18 0-2.84-3.03-5.42-8.05-5.42Z" />
            <template v-else>
              <circle cx="12" cy="7.25" r="4.15" />
              <path d="M4.2 20.1c.25-3.78 3.1-6.35 7.8-6.35s7.55 2.57 7.8 6.35" />
            </template>
          </svg>
        </view>
        <text>{{ item.label }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.archive-tabbar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 50%;
  z-index: 90;
  width: 100%;
  max-width: 430px;
  padding-bottom: env(safe-area-inset-bottom);
  transform: translateX(-50%);
  border-top: 1rpx solid #e5e3df;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 -4rpx 16rpx rgba(13, 13, 15, 0.025);
  backdrop-filter: blur(16px);
}

.archive-tabbar__inner {
  height: 100rpx;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
}

.archive-tabbar__item {
  min-width: 0;
  height: 100rpx;
  padding: 11rpx 0 9rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5rpx;
  color: #85868a;
  font-size: 19rpx;
  font-weight: 400;
  line-height: 1;
  -webkit-tap-highlight-color: transparent;
}

.archive-tabbar__item.is-active {
  color: #0d0d0f;
  font-weight: 600;
}

.archive-tabbar__icon {
  width: 38rpx;
  height: 38rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.archive-tabbar__icon svg {
  width: 36rpx;
  height: 36rpx;
  overflow: visible;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.55;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.archive-tabbar__icon .icon-fill {
  fill: currentColor;
  stroke: currentColor;
}

.archive-tabbar__item:active {
  opacity: 0.58;
}
</style>
