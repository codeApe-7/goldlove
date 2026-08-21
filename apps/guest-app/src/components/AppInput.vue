<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from './AppIcon.vue'

/**
 * 规范图 2 · 输入框：默认 / 聚焦 / 错误 / 成功 / 禁用五态，
 * 另含字数计数器、前置图标、说明文案、密码明文切换与多行输入。
 */
const props = withDefaults(
  defineProps<{
    modelValue: string
    type?: 'text' | 'number' | 'digit' | 'password' | 'textarea'
    placeholder?: string
    maxlength?: number
    state?: 'default' | 'error' | 'success'
    disabled?: boolean
    message?: string
    hint?: string
    icon?: string
    rows?: number
  }>(),
  {
    type: 'text',
    placeholder: '请输入内容',
    maxlength: 0,
    state: 'default',
    disabled: false,
    message: '',
    hint: '',
    icon: '',
    rows: 4,
  },
)

const emit = defineEmits<{ 'update:modelValue': [string] }>()

const focused = ref(false)
const passwordVisible = ref(false)

// 计数器只在设了上限时出现，规范图里是「已输入/上限」。
const counter = computed(() =>
  props.maxlength > 0 ? `${props.modelValue.length}/${props.maxlength}` : '',
)

const inputType = computed(() => {
  if (props.type === 'password') {
    return passwordVisible.value ? 'text' : 'password'
  }
  return props.type === 'number' ? 'number' : props.type
})

const tone = computed(() => {
  if (props.disabled) return 'disabled'
  if (props.state !== 'default') return props.state
  return focused.value ? 'focused' : 'default'
})

// uni-app 把 input / textarea 的事件声明成裸 Event，取值只能断言后再读 detail，
// 与 pages/profile/index.vue 里 onBooleanChange 的既有写法一致。
function onInput(event: Event): void {
  const detail = (event as Event & { detail: { value: string } }).detail
  emit('update:modelValue', detail.value)
}
</script>

<template>
  <view class="app-input" :class="`tone-${tone}`">
    <view class="field" :class="{ multiline: type === 'textarea' }">
      <AppIcon v-if="icon" :name="icon" :size="18" class="lead" />
      <textarea
        v-if="type === 'textarea'"
        class="control area"
        :value="modelValue"
        :placeholder="placeholder"
        :maxlength="maxlength > 0 ? maxlength : -1"
        :disabled="disabled"
        :style="{ height: `${rows * 44}rpx` }"
        placeholder-class="control-placeholder"
        @input="onInput"
        @focus="focused = true"
        @blur="focused = false"
      />
      <input
        v-else
        class="control"
        :value="modelValue"
        :type="inputType"
        :password="type === 'password' && !passwordVisible"
        :placeholder="placeholder"
        :maxlength="maxlength > 0 ? maxlength : -1"
        :disabled="disabled"
        placeholder-class="control-placeholder"
        @input="onInput"
        @focus="focused = true"
        @blur="focused = false"
      />
      <text v-if="counter && type !== 'textarea'" class="counter archive-tabular">{{ counter }}</text>
      <AppIcon v-if="state === 'success'" name="check" :size="18" class="trail success-mark" />
      <view
        v-if="type === 'password'"
        class="trail toggle"
        @tap="passwordVisible = !passwordVisible"
      >
        <AppIcon :name="passwordVisible ? 'eye' : 'eye-off'" :size="18" />
      </view>
    </view>

    <view v-if="type === 'textarea' && counter" class="area-footer">
      <text class="hint">{{ hint }}</text>
      <text class="counter archive-tabular">{{ counter }}</text>
    </view>

    <text v-if="message" class="message">{{ message }}</text>
    <text v-else-if="hint && type !== 'textarea'" class="hint">{{ hint }}</text>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.app-input {
  width: 100%;
}

.field {
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

.field.multiline {
  padding: $ds-space-3;
  align-items: flex-start;
}

.control {
  min-width: 0;
  flex: 1;
  height: $ds-control-height;
  @include ds-body-1;
  color: $ds-ink;
  background: transparent;
}

.control.area {
  width: 100%;
  height: auto;
  @include ds-body-2;
  line-height: 44rpx;
}

.control-placeholder {
  color: $ds-placeholder;
}

.lead {
  color: $ds-gray;
}

.counter {
  flex: none;
  @include ds-caption;
  color: $ds-placeholder;
}

.trail {
  flex: none;
  display: flex;
  align-items: center;
  color: $ds-gray;
}

.success-mark {
  color: $ds-success;
}

.area-footer {
  margin-top: $ds-space-1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $ds-space-2;
}

.message,
.hint {
  display: block;
  margin-top: $ds-space-1;
  @include ds-caption;
}

.hint {
  color: $ds-gray;
}

.message {
  color: $ds-gray;
}

// ---- 五态 ----

.tone-focused .field {
  @include ds-focus-ring;
}

.tone-error .field {
  border-color: $ds-error;
}

.tone-error .message {
  color: $ds-error;
}

.tone-success .field {
  border-color: $ds-success;
}

.tone-success .message {
  color: $ds-success-ink;
}

.tone-disabled .field {
  border-color: $ds-line;
  background: $ds-disabled-bg;
}

.tone-disabled .control {
  color: $ds-disabled-ink;
}
</style>
