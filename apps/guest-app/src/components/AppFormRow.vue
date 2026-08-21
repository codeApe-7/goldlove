<script setup lang="ts">
/** 规范图 4 · 表单区块的一行：标签 + 必填星号 + 选填标记 + 控件 + 说明。 */
withDefaults(
  defineProps<{
    label: string
    required?: boolean
    optional?: boolean
    hint?: string
    /** stacked 用于下拉、级联等需要整宽展开的控件；inline 用于短输入。 */
    layout?: 'stacked' | 'inline'
  }>(),
  { required: false, optional: false, hint: '', layout: 'stacked' },
)
</script>

<template>
  <view class="app-form-row" :class="`layout-${layout}`">
    <view class="head">
      <text class="label">{{ label }}<text v-if="required" class="required"> *</text></text>
      <text v-if="optional && !required" class="optional">选填</text>
    </view>
    <view class="control"><slot /></view>
    <text v-if="hint" class="hint">{{ hint }}</text>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.app-form-row {
  padding: $ds-space-3 0;
  border-bottom: $ds-hairline solid #edebe7;
}

.app-form-row:last-child {
  border-bottom: 0;
}

.head {
  display: flex;
  align-items: center;
  gap: $ds-space-1;
}

.label {
  @include ds-body-2;
  color: $ds-graphite;
  font-weight: 500;
}

.required {
  color: $ds-error;
}

.optional {
  @include ds-caption;
  color: $ds-placeholder;
}

.control {
  margin-top: $ds-space-2;
}

.hint {
  display: block;
  margin-top: $ds-space-1;
  @include ds-caption;
  color: $ds-gray;
}

// 行内布局把标签与控件挤到同一行，用于「我的」这类紧凑列表。
.layout-inline {
  display: flex;
  align-items: center;
  gap: $ds-space-4;
}

.layout-inline .head {
  flex: none;
}

.layout-inline .control {
  min-width: 0;
  flex: 1;
  margin-top: 0;
}
</style>
