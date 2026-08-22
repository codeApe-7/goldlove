<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import AppSheet from './AppSheet.vue'
import AppWheelPicker from './AppWheelPicker.vue'
import {
  dateWheelColumns,
  formatDateValue,
  indexesToParts,
  parseDateValue,
  partsToIndexes,
} from '@/utils/datePicker'

/** 规范图 3.9 · 日期选择器（出生日期）：年 / 月 / 日三列底部弹层。 */
const props = withDefaults(
  defineProps<{
    modelValue: string
    title?: string
    placeholder?: string
    disabled?: boolean
    state?: 'default' | 'error'
    message?: string
    /** 由调用方注入当前年份，组件本身不读时钟，方便测试与 SSR。 */
    currentYear?: number
  }>(),
  {
    title: '请选择出生日期',
    placeholder: '请选择出生日期',
    disabled: false,
    state: 'default',
    message: '',
    currentYear: 0,
  },
)

const emit = defineEmits<{ 'update:modelValue': [string] }>()

const open = ref(false)
const draftIndexes = ref<number[]>([0, 0, 0])

const year = computed(() => (props.currentYear > 0 ? props.currentYear : new Date().getFullYear()))
const draftParts = computed(() => indexesToParts(draftIndexes.value, year.value))
const wheelColumns = computed(() => {
  const columns = dateWheelColumns(draftParts.value, year.value)
  return [columns.years, columns.months, columns.days]
})

function show(): void {
  if (props.disabled) {
    return
  }
  draftIndexes.value = partsToIndexes(parseDateValue(props.modelValue, year.value), year.value)
  open.value = true
}

function onWheelChange(value: number[]): void {
  // 先归一化再回写：从 31 天的月份滚到 2 月时日索引会越界。
  draftIndexes.value = partsToIndexes(indexesToParts(value, year.value), year.value)
}

function confirm(): void {
  emit('update:modelValue', formatDateValue(draftParts.value))
  open.value = false
}
</script>

<template>
  <view class="app-date-field" :class="[`tone-${disabled ? 'disabled' : state}`]">
    <view class="trigger" @tap="show">
      <text class="value" :class="{ placeholder: modelValue === '' }">
        {{ modelValue === '' ? placeholder : modelValue }}
      </text>
      <AppIcon name="calendar" :size="18" class="arrow" />
    </view>
    <text v-if="message" class="message">{{ message }}</text>

    <AppSheet :visible="open" :title="title" @cancel="open = false" @confirm="confirm">
      <AppWheelPicker
        :columns="wheelColumns"
        :value="draftIndexes"
        @update:value="onWheelChange"
      />
    </AppSheet>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.app-date-field {
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
  @include ds-tabular;
  @include ds-truncate;
  color: $ds-ink;
}

.value.placeholder {
  color: $ds-placeholder;
  font-family: inherit;
}

.arrow {
  color: $ds-gray;
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
</style>
