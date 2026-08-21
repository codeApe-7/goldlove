<script setup lang="ts">
import AppIcon from './AppIcon.vue'

/** 规范图 3.10 · 并排格子单选（性别）：图标 + 文案 + 单选圈 + 底部说明。 */
const props = withDefaults(
  defineProps<{
    modelValue: string
    options: string[]
    /** 选项 → 图标名，缺省则该格不显示图标。 */
    icons?: Record<string, string>
    hint?: string
    disabled?: boolean
  }>(),
  { icons: () => ({}), hint: '', disabled: false },
)

const emit = defineEmits<{ 'update:modelValue': [string] }>()

function pick(option: string): void {
  if (props.disabled) {
    return
  }
  emit('update:modelValue', option)
}
</script>

<template>
  <view class="app-radio-tiles">
    <view class="tiles" :class="{ disabled }">
      <view
        v-for="option in options"
        :key="option"
        class="tile"
        :class="{ active: option === modelValue }"
        role="radio"
        :aria-checked="option === modelValue"
        @tap="pick(option)"
      >
        <AppIcon v-if="icons[option]" :name="icons[option]" :size="22" class="mark" />
        <text class="label">{{ option }}</text>
        <view class="radio"><view v-if="option === modelValue" class="radio-dot" /></view>
      </view>
    </view>
    <text v-if="hint" class="hint">{{ hint }}</text>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.app-radio-tiles {
  width: 100%;
}

.tiles {
  display: flex;
  gap: $ds-space-2;
}

.tile {
  min-width: 0;
  flex: 1;
  padding: $ds-space-3 $ds-space-2;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $ds-space-2;
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-sm;
  background: $ds-white;
  transition: border-color $ds-transition, box-shadow $ds-transition;
}

.mark {
  color: $ds-gray;
}

.label {
  @include ds-body-2;
  @include ds-truncate;
  max-width: 100%;
  color: $ds-graphite;
}

.radio {
  width: 32rpx;
  height: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: $ds-hairline solid #c9c7c2;
  border-radius: 50%;
}

.radio-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: $ds-ink;
}

.tile.active {
  border-color: $ds-ink;
  box-shadow: $ds-shadow-soft;
}

.tile.active .mark,
.tile.active .label {
  color: $ds-ink;
}

.tile.active .label {
  font-weight: 600;
}

.tile.active .radio {
  border-color: $ds-ink;
}

.tiles.disabled .tile {
  background: $ds-disabled-bg;
}

.tiles.disabled .label,
.tiles.disabled .mark {
  color: $ds-disabled-ink;
}

.hint {
  display: block;
  margin-top: $ds-space-2;
  @include ds-caption;
  color: $ds-gray;
}
</style>
