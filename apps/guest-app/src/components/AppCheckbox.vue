<script setup lang="ts">
import AppIcon from './AppIcon.vue'

/** 规范图 6 · 复选框：未选 / 选中 / 禁用未选 / 禁用已选，支持长文本授权条款。 */
const props = withDefaults(
  defineProps<{
    modelValue: boolean
    disabled?: boolean
  }>(),
  { disabled: false },
)

const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

function toggle(): void {
  if (props.disabled) {
    return
  }
  emit('update:modelValue', !props.modelValue)
}
</script>

<template>
  <view
    class="app-checkbox"
    :class="{ checked: modelValue, disabled }"
    role="checkbox"
    :aria-checked="modelValue"
    :aria-disabled="disabled"
    @tap="toggle"
  >
    <view class="box">
      <AppIcon v-if="modelValue" name="check" :size="14" />
    </view>
    <text class="label"><slot /></text>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.app-checkbox {
  display: flex;
  align-items: flex-start;
  gap: $ds-space-2;
}

.box {
  flex: none;
  width: 36rpx;
  height: 36rpx;
  margin-top: 3rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: $ds-hairline solid #c9c7c2;
  border-radius: $ds-radius-xs;
  background: $ds-white;
  color: $ds-white;
  transition: background-color $ds-transition, border-color $ds-transition;
}

.label {
  flex: 1;
  @include ds-body-2;
  color: $ds-graphite;
}

.checked .box {
  border-color: $ds-ink;
  background: $ds-ink;
}

.disabled .box {
  border-color: $ds-line;
  background: $ds-disabled-bg;
  color: $ds-disabled-ink;
}

.disabled .label {
  color: $ds-disabled-ink;
}
</style>
