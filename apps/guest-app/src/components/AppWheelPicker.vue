<script setup lang="ts">
/**
 * 规范图 3.7 / 3.9 的滚轮本体。用 picker-view 而不是原生 <picker>：
 * 原生 picker 在 H5 会渲染 uni-app 自带的弹层，取消 / 确定与中间高亮条都无法定制。
 */
defineProps<{
  columns: string[][]
  value: number[]
}>()

const emit = defineEmits<{ 'update:value': [number[]] }>()

function onChange(event: Event): void {
  const detail = (event as Event & { detail: { value: number[] } }).detail
  emit('update:value', detail.value)
}
</script>

<template>
  <picker-view
    class="wheel"
    :value="value"
    indicator-class="wheel-indicator"
    mask-class="wheel-mask"
    @change="onChange"
  >
    <picker-view-column v-for="(column, index) in columns" :key="index">
      <view v-for="item in column" :key="item" class="wheel-item">{{ item }}</view>
    </picker-view-column>
  </picker-view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.wheel {
  width: 100%;
  height: 440rpx;
}

.wheel-item {
  height: $ds-sheet-row-height;
  display: flex;
  align-items: center;
  justify-content: center;
  @include ds-body-1;
  @include ds-tabular;
  color: $ds-graphite;
}
</style>

<!-- indicator-class / mask-class 由 uni-app 注入到组件内部节点上，scoped 选择器命中不到。 -->
<style lang="scss">
@use '@/styles/tokens.scss' as *;

.wheel-indicator {
  height: $ds-sheet-row-height;
  border-top: $ds-hairline solid $ds-line;
  border-bottom: $ds-hairline solid $ds-line;
  background: rgba(14, 15, 18, 0.03);
}

.wheel-mask {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(255, 255, 255, 0.55)),
    linear-gradient(0deg, rgba(255, 255, 255, 0.95), rgba(255, 255, 255, 0.55));
}
</style>
