<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import { addCustomTag, availableTags, removeTag, toggleTag } from '@/utils/tagSelection'

/** 规范图 3.11 · 多选标签：已选（可删除）/ 未选（可选择）/ 添加自定义。 */
const props = withDefaults(
  defineProps<{
    modelValue: string[]
    catalog: string[]
    limit?: number
    placeholder?: string
  }>(),
  { limit: 8, placeholder: '输入标签后回车添加' },
)

const emit = defineEmits<{ 'update:modelValue': [string[]] }>()

const composing = ref(false)
const draft = ref('')
const error = ref('')

const unselected = computed(() => availableTags(props.catalog, props.modelValue))

function toggle(tag: string): void {
  error.value = ''
  const next = toggleTag(props.modelValue, tag)
  if (next.length > props.limit) {
    error.value = `最多只能选 ${props.limit} 个标签`
    return
  }
  emit('update:modelValue', next)
}

function remove(tag: string): void {
  error.value = ''
  emit('update:modelValue', removeTag(props.modelValue, tag))
}

function onDraft(event: Event): void {
  draft.value = (event as Event & { detail: { value: string } }).detail.value
}

function submitCustom(): void {
  const result = addCustomTag(props.modelValue, draft.value, props.limit)
  error.value = result.error
  if (result.error === '') {
    emit('update:modelValue', result.selected)
    draft.value = ''
    composing.value = false
  }
}
</script>

<template>
  <view class="app-tag-select">
    <view class="group">
      <text class="group-title">已选（可删除）</text>
      <view class="chips">
        <view v-for="tag in modelValue" :key="tag" class="chip selected" @tap="remove(tag)">
          <text>{{ tag }}</text>
          <AppIcon name="close" :size="12" />
        </view>
        <text v-if="modelValue.length === 0" class="empty">还没有选择标签</text>
      </view>
    </view>

    <view v-if="unselected.length > 0" class="group">
      <text class="group-title">未选（可选择）</text>
      <view class="chips">
        <view v-for="tag in unselected" :key="tag" class="chip" @tap="toggle(tag)">
          <text>{{ tag }}</text>
        </view>
      </view>
    </view>

    <view class="group">
      <text class="group-title">添加自定义</text>
      <view v-if="composing" class="custom">
        <input
          class="custom-input"
          :value="draft"
          :placeholder="placeholder"
          placeholder-class="custom-placeholder"
          confirm-type="done"
          @input="onDraft"
          @confirm="submitCustom"
        />
        <text class="custom-action" @tap="submitCustom">添加</text>
      </view>
      <view v-else class="chip dashed" @tap="composing = true">
        <AppIcon name="plus" :size="12" />
        <text>添加自定义标签</text>
      </view>
    </view>

    <text v-if="error" class="error">{{ error }}</text>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.app-tag-select {
  width: 100%;
}

.group + .group {
  margin-top: $ds-space-3;
}

.group-title {
  display: block;
  margin-bottom: $ds-space-2;
  @include ds-caption;
  color: $ds-gray;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: $ds-space-2;
}

.chip {
  min-height: 60rpx;
  padding: 0 $ds-space-3;
  display: flex;
  align-items: center;
  gap: $ds-space-1;
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-pill;
  background: $ds-white;
  @include ds-body-2;
  color: $ds-graphite;
}

.chip:active {
  background: $ds-porcelain;
}

.chip.selected {
  border-color: $ds-ink;
  background: $ds-ink;
  color: $ds-white;
}

.chip.dashed {
  border-style: dashed;
  border-color: #c9c7c2;
  color: $ds-gray;
}

.empty {
  @include ds-body-2;
  color: $ds-placeholder;
}

.custom {
  padding: 0 $ds-space-3;
  display: flex;
  align-items: center;
  gap: $ds-space-2;
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-sm;
  background: $ds-white;
}

.custom-input {
  flex: 1;
  height: $ds-control-height-sm;
  @include ds-body-2;
  color: $ds-ink;
}

.custom-placeholder {
  color: $ds-placeholder;
}

.custom-action {
  flex: none;
  @include ds-body-2;
  color: $ds-ink;
  font-weight: 600;
}

.error {
  display: block;
  margin-top: $ds-space-2;
  @include ds-caption;
  color: $ds-error;
}
</style>
