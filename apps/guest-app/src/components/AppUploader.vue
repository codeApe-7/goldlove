<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'

/**
 * 规范图 5 · 图片上传：头像单张与生活照栅格，
 * 覆盖空状态 / 已上传 / 上传中（进度环）/ 可删除四种形态。
 */
export interface UploaderItem {
  key: string
  url: string
  /** 0–100 表示上传中；不传表示已完成。 */
  progress?: number
}

const props = withDefaults(
  defineProps<{
    items: UploaderItem[]
    mode?: 'avatar' | 'grid'
    max?: number
    removable?: boolean
    emptyLabel?: string
    addLabel?: string
    footer?: string
  }>(),
  {
    mode: 'grid',
    max: 3,
    removable: true,
    emptyLabel: '上传头像',
    addLabel: '添加照片',
    footer: '支持 jpg / png，单张不超过 10MB',
  },
)

const emit = defineEmits<{ add: []; remove: [string] }>()

// 进度环半径 20、viewBox 48，周长固定，进度按 dashoffset 收缩。
const RING_CIRCUMFERENCE = 2 * Math.PI * 20

const avatar = computed(() => props.items[0])
const canAdd = computed(() => props.mode === 'avatar' || props.items.length < props.max)

function ringOffset(progress: number): number {
  const clamped = Math.min(100, Math.max(0, progress))
  return RING_CIRCUMFERENCE * (1 - clamped / 100)
}
</script>

<template>
  <view class="app-uploader" :class="`mode-${mode}`">
    <view v-if="mode === 'avatar'" class="avatar-slot">
      <view class="tile avatar" @tap="canAdd && emit('add')">
        <image v-if="avatar" :src="avatar.url" class="photo" mode="aspectFill" />
        <view v-else class="empty">
          <AppIcon name="user" :size="24" />
          <text>{{ emptyLabel }}</text>
        </view>

        <view v-if="avatar?.progress !== undefined" class="progress">
          <svg class="ring" viewBox="0 0 48 48">
            <circle class="ring-track" cx="24" cy="24" r="20" />
            <circle
              class="ring-value"
              cx="24"
              cy="24"
              r="20"
              :stroke-dasharray="RING_CIRCUMFERENCE"
              :stroke-dashoffset="ringOffset(avatar.progress)"
            />
          </svg>
          <text class="percent archive-tabular">{{ Math.round(avatar.progress) }}%</text>
          <text class="progress-label">上传中</text>
        </view>

        <view v-else-if="avatar" class="badge camera"><AppIcon name="camera" :size="12" /></view>
      </view>

      <view
        v-if="avatar && removable && avatar.progress === undefined"
        class="badge remove"
        @tap.stop="emit('remove', avatar.key)"
      >
        <AppIcon name="close" :size="12" />
      </view>
    </view>

    <view v-else class="grid">
      <view v-for="item in items" :key="item.key" class="tile">
        <image :src="item.url" class="photo" mode="aspectFill" />
        <view v-if="item.progress !== undefined" class="progress">
          <svg class="ring" viewBox="0 0 48 48">
            <circle class="ring-track" cx="24" cy="24" r="20" />
            <circle
              class="ring-value"
              cx="24"
              cy="24"
              r="20"
              :stroke-dasharray="RING_CIRCUMFERENCE"
              :stroke-dashoffset="ringOffset(item.progress)"
            />
          </svg>
          <text class="percent archive-tabular">{{ Math.round(item.progress) }}%</text>
        </view>
        <view
          v-else-if="removable"
          class="badge remove"
          @tap.stop="emit('remove', item.key)"
        >
          <AppIcon name="close" :size="12" />
        </view>
      </view>

      <view v-if="canAdd" class="tile add" @tap="emit('add')">
        <AppIcon name="plus" :size="20" />
        <text>{{ addLabel }}</text>
      </view>
    </view>

    <text v-if="footer" class="footer">{{ footer }}</text>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.app-uploader {
  width: 100%;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $ds-space-2;
}

.avatar-slot {
  position: relative;
  width: 176rpx;
}

.tile {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $ds-space-1;
  border-radius: $ds-radius-sm;
  background: $ds-porcelain;
  color: $ds-gray;
}

.tile.avatar {
  width: 176rpx;
  border-radius: $ds-radius-md;
}

.tile.add {
  border: $ds-hairline dashed #c9c7c2;
  background: $ds-white;
}

.tile.add text,
.empty text {
  @include ds-caption;
}

.photo {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $ds-space-1;
}

// ---- 上传中 ----

.progress {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2rpx;
  background: rgba(14, 15, 18, 0.5);
  color: $ds-white;
}

.ring {
  position: absolute;
  width: 96rpx;
  height: 96rpx;
  fill: none;
  stroke-linecap: round;
  transform: rotate(-90deg);
}

.ring-track {
  stroke: rgba(255, 255, 255, 0.28);
  stroke-width: 3;
}

.ring-value {
  stroke: $ds-white;
  stroke-width: 3;
  transition: stroke-dashoffset $ds-transition;
}

.percent {
  @include ds-body-2;
  font-weight: 600;
}

.progress-label {
  @include ds-caption;
  opacity: 0.85;
}

// ---- 角标 ----

.badge {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.badge.camera {
  right: $ds-space-1;
  bottom: $ds-space-1;
  width: 40rpx;
  height: 40rpx;
  border: 3rpx solid $ds-white;
  background: $ds-ink;
  color: $ds-white;
}

.badge.remove {
  top: 6rpx;
  right: 6rpx;
  z-index: 2;
  width: 36rpx;
  height: 36rpx;
  background: rgba(14, 15, 18, 0.72);
  color: $ds-white;
}

.footer {
  display: block;
  margin-top: $ds-space-2;
  @include ds-caption;
  color: $ds-gray;
}
</style>
