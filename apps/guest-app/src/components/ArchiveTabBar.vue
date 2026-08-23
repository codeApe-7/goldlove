<script setup lang="ts">
import { onMounted } from 'vue'

type ArchiveTabKey = 'profile' | 'course' | 'mine'

const props = defineProps<{ current: ArchiveTabKey }>()

const items: Array<{ key: ArchiveTabKey; label: string; url: string }> = [
  { key: 'profile', label: '档案', url: '/pages/profile/index' },
  { key: 'course', label: '课程', url: '/pages/course/index' },
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
          <!-- 课程：一本翻开的书 -->
          <svg v-else-if="item.key === 'course'" viewBox="0 0 24 24">
            <path v-if="current === item.key" class="icon-fill" d="M11.05 5.9C9.7 4.9 7.9 4.35 5.6 4.25a1.2 1.2 0 0 0-1.25 1.2v11.2c0 .64.5 1.17 1.14 1.2 2.02.1 3.58.58 4.72 1.4a1.3 1.3 0 0 0 1.53 0c1.14-.82 2.7-1.3 4.72-1.4a1.2 1.2 0 0 0 1.14-1.2V5.45a1.2 1.2 0 0 0-1.25-1.2c-2.3.1-4.1.65-5.45 1.65Z" />
            <template v-else>
              <path d="M12 6.6C10.7 5.5 8.85 4.9 6.4 4.8a.9.9 0 0 0-.95.9v10.6c0 .48.37.88.85.9 2.1.1 3.75.6 4.95 1.5M12 6.6c1.3-1.1 3.15-1.7 5.6-1.8a.9.9 0 0 1 .95.9v10.6a.9.9 0 0 1-.85.9c-2.1.1-3.75.6-4.95 1.5" />
              <path d="M12 6.6v13.1" />
            </template>
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
  grid-template-columns: repeat(3, 1fr);
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
