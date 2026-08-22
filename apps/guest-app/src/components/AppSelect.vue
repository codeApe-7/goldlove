<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from './AppIcon.vue'

/**
 * 规范图 3.1–3.6 · 内联下拉框：关闭态四状态 + 展开面板。
 * `searchable` 打开时面板顶部出现关键词过滤（规范 3.3）。
 */
const props = withDefaults(
  defineProps<{
    modelValue: string
    options: string[]
    placeholder?: string
    disabled?: boolean
    state?: 'default' | 'error'
    message?: string
    searchable?: boolean
    searchPlaceholder?: string
    footer?: string
  }>(),
  {
    placeholder: '请选择',
    disabled: false,
    state: 'default',
    message: '',
    searchable: false,
    searchPlaceholder: '搜索关键词',
    footer: '如未找到合适选项，可联系客服',
  },
)

const emit = defineEmits<{ 'update:modelValue': [string] }>()

const open = ref(false)
const keyword = ref('')

const visibleOptions = computed(() => {
  const trimmed = keyword.value.trim()
  if (!props.searchable || trimmed === '') {
    return props.options
  }
  return props.options.filter((option) => option.includes(trimmed))
})

const tone = computed(() => {
  if (props.disabled) return 'disabled'
  if (props.state === 'error') return 'error'
  return open.value ? 'focused' : 'default'
})

function toggle(): void {
  if (props.disabled) {
    return
  }
  open.value = !open.value
  if (open.value) {
    keyword.value = ''
  }
}

function pick(option: string): void {
  emit('update:modelValue', option)
  open.value = false
}

function onSearch(event: Event): void {
  keyword.value = (event as Event & { detail: { value: string } }).detail.value
}
</script>

<template>
  <view class="app-select" :class="`tone-${tone}`">
    <view class="trigger" @tap="toggle">
      <!-- 存量档案的值可能不在当前选项表里，照原样显示而不是清空。 -->
      <text class="value" :class="{ placeholder: modelValue === '' }">
        {{ modelValue === '' ? placeholder : modelValue }}
      </text>
      <view class="arrow" :class="{ open }"><AppIcon name="chevron" :size="18" /></view>
    </view>
    <text v-if="message" class="message">{{ message }}</text>

    <view v-if="open" class="scrim" @tap="open = false" />
    <view v-if="open" class="panel">
      <view v-if="searchable" class="search">
        <AppIcon name="search" :size="16" />
        <input
          class="search-input"
          :value="keyword"
          :placeholder="searchPlaceholder"
          placeholder-class="search-placeholder"
          @input="onSearch"
        />
      </view>

      <scroll-view class="options" scroll-y>
        <view
          v-for="option in visibleOptions"
          :key="option"
          class="option"
          :class="{ selected: option === modelValue }"
          @tap="pick(option)"
        >
          <text class="option-label">{{ option }}</text>
          <AppIcon v-if="option === modelValue" name="check" :size="16" />
        </view>
        <text v-if="visibleOptions.length === 0" class="empty">没有匹配的选项</text>
      </scroll-view>

      <text v-if="footer" class="footer">{{ footer }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.app-select {
  position: relative;
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
  transition: border-color $ds-transition, box-shadow $ds-transition;
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
  transition: transform $ds-transition;
}

.arrow.open {
  transform: rotate(-90deg);
}

.message {
  display: block;
  margin-top: $ds-space-1;
  @include ds-caption;
  color: $ds-gray;
}

// 点击面板以外任意处收起。uni-app 没有全局 click-outside，用透明层兜住。
.scrim {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 30;
}

.panel {
  position: absolute;
  top: calc(#{$ds-control-height} + #{$ds-space-1});
  right: 0;
  left: 0;
  z-index: 31;
  padding: $ds-space-2 0 0;
  overflow: hidden;
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-sm;
  background: $ds-white;
  box-shadow: $ds-shadow-deep;
}

.search {
  margin: 0 $ds-space-2 $ds-space-2;
  padding: 0 $ds-space-2;
  display: flex;
  align-items: center;
  gap: $ds-space-2;
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-xs;
  background: $ds-porcelain;
  color: $ds-gray;
}

.search-input {
  flex: 1;
  height: 68rpx;
  @include ds-body-2;
  color: $ds-ink;
}

.search-placeholder {
  color: $ds-placeholder;
}

.options {
  // 规范 3.2 的面板是把选项一次列全的。按 88rpx 一行给到 8 行，
  // 学历（6 项）与行业档（7 项）都不用滚；更长的列表（职业）才滚动，配合搜索用。
  max-height: 704rpx;
}

.option {
  min-height: $ds-sheet-row-height;
  padding: 0 $ds-space-3;
  display: flex;
  align-items: center;
  gap: $ds-space-2;
  color: $ds-graphite;
}

.option:active {
  background: $ds-porcelain;
}

.option-label {
  min-width: 0;
  flex: 1;
  @include ds-body-1;
  @include ds-truncate;
}

.option.selected {
  background: $ds-porcelain;
  color: $ds-ink;
}

.option.selected .option-label {
  font-weight: 600;
}

.empty {
  display: block;
  padding: $ds-space-5 $ds-space-3;
  @include ds-body-2;
  color: $ds-placeholder;
  text-align: center;
}

.footer {
  display: block;
  padding: $ds-space-2 $ds-space-3 $ds-space-3;
  border-top: $ds-hairline solid #f0eeea;
  @include ds-caption;
  color: $ds-placeholder;
}

// ---- 关闭态四状态 ----

.tone-focused .trigger {
  @include ds-focus-ring;
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
