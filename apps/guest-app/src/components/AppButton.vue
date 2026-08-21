<script setup lang="ts">
/** 规范图 2 · 按钮：主 / 次 / 文字三种，各带默认、按下、禁用三态。 */
withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'text'
    size?: 'md' | 'sm'
    block?: boolean
    disabled?: boolean
  }>(),
  { variant: 'primary', size: 'md', block: false, disabled: false },
)

const emit = defineEmits<{ tap: [] }>()

function onTap(): void {
  emit('tap')
}
</script>

<template>
  <button
    class="app-button"
    :class="[`variant-${variant}`, `size-${size}`, { block }]"
    :disabled="disabled"
    @tap="onTap"
  >
    <slot />
  </button>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.app-button {
  min-width: 176rpx;
  padding: 0 $ds-space-5;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: $ds-hairline solid transparent;
  border-radius: $ds-radius-sm;
  font-family: inherit;
  font-weight: 600;
  transition: background-color $ds-transition, border-color $ds-transition, color $ds-transition;
}

.size-md {
  height: $ds-control-height;
  font-size: 30rpx;
  line-height: $ds-control-height;
}

.size-sm {
  height: $ds-control-height-sm;
  font-size: 26rpx;
  line-height: $ds-control-height-sm;
}

.block {
  width: 100%;
  display: flex;
}

.variant-primary {
  background: $ds-ink;
  color: $ds-white;
}

.variant-primary:active {
  background: $ds-graphite;
}

.variant-secondary {
  border-color: $ds-line;
  background: $ds-white;
  color: $ds-ink;
}

.variant-secondary:active {
  border-color: $ds-gray;
  background: $ds-porcelain;
}

.variant-text {
  min-width: 0;
  padding: 0 $ds-space-2;
  background: transparent;
  color: $ds-graphite;
}

.variant-text:active {
  color: $ds-ink;
}

// 禁用态用实色而非透明度：规范图里禁用按钮是灰底灰字，不是主色淡化。
.app-button[disabled] {
  background: $ds-disabled-bg;
  border-color: $ds-disabled-bg;
  color: $ds-disabled-ink;
}

.variant-text[disabled] {
  background: transparent;
  border-color: transparent;
}
</style>
