<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import AppSheet from './AppSheet.vue'
import AppWheelPicker from './AppWheelPicker.vue'
import { isPrivateValue } from '@/utils/presentation'

/** 规范图 3.7 · 底部弹层单列滚轮选择器（年薪 / 收入等区间选择）。 */
const props = withDefaults(
  defineProps<{
    modelValue: string
    options: string[]
    title: string
    placeholder?: string
    disabled?: boolean
    state?: 'default' | 'error'
    message?: string
    /** 选中「保密」这类值时只显示该值本身并加锁标，不展示具体区间。 */
    maskPrivate?: boolean
    privateHint?: string
  }>(),
  {
    placeholder: '请选择',
    disabled: false,
    state: 'default',
    message: '',
    maskPrivate: false,
    privateHint: '已设为保密，档案中不展示具体数值',
  },
)

const emit = defineEmits<{ 'update:modelValue': [string] }>()

const open = ref(false)
const draftIndex = ref(0)

const wheelColumns = computed(() => [props.options])
const wheelValue = computed(() => [draftIndex.value])
const isPrivate = computed(() => props.maskPrivate && isPrivateValue(props.modelValue))

function show(): void {
  if (props.disabled) {
    return
  }
  // 打开时对齐当前值；值不在选项里（存量数据）就从头开始。
  const current = props.options.indexOf(props.modelValue)
  draftIndex.value = current >= 0 ? current : 0
  open.value = true
}

function onWheelChange(value: number[]): void {
  draftIndex.value = value[0] ?? 0
}

function confirm(): void {
  const picked = props.options[draftIndex.value]
  if (picked !== undefined) {
    emit('update:modelValue', picked)
  }
  open.value = false
}
</script>

<template>
  <view class="app-wheel-select" :class="[`tone-${disabled ? 'disabled' : state}`, { 'is-private': isPrivate }]">
    <view class="trigger" @tap="show">
      <AppIcon v-if="isPrivate" name="lock" :size="16" class="lock" />
      <text class="value" :class="{ placeholder: modelValue === '' }">
        {{ modelValue === '' ? placeholder : modelValue }}
      </text>
      <view class="arrow"><AppIcon name="chevron" :size="18" /></view>
    </view>
    <text v-if="isPrivate" class="private-hint">{{ privateHint }}</text>
    <text v-else-if="message" class="message">{{ message }}</text>

    <AppSheet :visible="open" :title="title" @cancel="open = false" @confirm="confirm">
      <AppWheelPicker
        :columns="wheelColumns"
        :value="wheelValue"
        @update:value="onWheelChange"
      />
    </AppSheet>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.app-wheel-select {
  width: 100%;
}

.trigger {
  min-height: $ds-control-height;
  padding: 0 $ds-space-3;
  display: flex;
  align-items: center;
  gap: $ds-space-2;
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-sm;
  background: $ds-white;
}

.value {
  min-width: 0;
  flex: 1;
  @include ds-body-1;
  @include ds-truncate;
  color: $ds-ink;
}

.value.placeholder {
  color: $ds-placeholder;
}

.arrow {
  flex: none;
  display: flex;
  align-items: center;
  color: $ds-gray;
  transform: rotate(90deg);
}

.message {
  display: block;
  margin-top: $ds-space-1;
  @include ds-caption;
  color: $ds-gray;
}

.tone-error .trigger {
  border-color: $ds-error;
}

.tone-error .message {
  color: $ds-error;
}

.tone-disabled .trigger {
  background: $ds-disabled-bg;
}

.tone-disabled .value {
  color: $ds-disabled-ink;
}

// 保密态：加锁标 + 收敛字重，视觉上与具体数值区分开。
.lock {
  flex: none;
  color: $ds-gray;
}

.is-private .trigger {
  background: $ds-porcelain;
}

.is-private .value {
  color: $ds-graphite;
  font-weight: 500;
}

.private-hint {
  display: block;
  margin-top: $ds-space-1;
  @include ds-caption;
  color: $ds-gray;
}
</style>
