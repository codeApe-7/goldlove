<script setup lang="ts">
/** 规范图 3.7–3.9 共用的底部弹层外壳：遮罩 + 取消 / 标题 / 确定。 */
withDefaults(
  defineProps<{
    visible: boolean
    title: string
    confirmLabel?: string
    cancelLabel?: string
  }>(),
  { confirmLabel: '确定', cancelLabel: '取消' },
)

const emit = defineEmits<{ cancel: []; confirm: [] }>()
</script>

<template>
  <view v-if="visible" class="app-sheet">
    <view class="mask" @tap="emit('cancel')" />
    <view class="panel">
      <view class="head">
        <text class="action" @tap="emit('cancel')">{{ cancelLabel }}</text>
        <text class="title">{{ title }}</text>
        <text class="action confirm" @tap="emit('confirm')">{{ confirmLabel }}</text>
      </view>
      <view class="body"><slot /></view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.app-sheet {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 100;
}

.mask {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: $ds-mask;
}

.panel {
  position: absolute;
  right: 50%;
  bottom: 0;
  width: 100%;
  max-width: $ds-viewport-max;
  padding-bottom: env(safe-area-inset-bottom);
  transform: translateX(50%);
  border-radius: $ds-radius-md $ds-radius-md 0 0;
  background: $ds-white;
  box-shadow: $ds-shadow-deep;
}

.head {
  height: 96rpx;
  padding: 0 $ds-space-4;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $ds-space-3;
  border-bottom: $ds-hairline solid $ds-line;
}

.action {
  flex: none;
  @include ds-body-2;
  color: $ds-gray;
}

.action.confirm {
  color: $ds-ink;
  font-weight: 600;
}

.title {
  min-width: 0;
  flex: 1;
  @include ds-body-2;
  @include ds-truncate;
  color: $ds-ink;
  text-align: center;
  font-weight: 500;
}
</style>
