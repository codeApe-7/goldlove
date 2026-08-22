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
    <picker-view-column v-for="(column, columnIndex) in columns" :key="columnIndex">
      <!--
        picker-view 无法用 CSS 选中「居中那一项」，只能拿 value 里的下标自己标记。
        规范 3.7 的居中项是品牌黑且字号更大，上下未选中项是淡灰。
      -->
      <view
        v-for="(item, itemIndex) in column"
        :key="item"
        class="wheel-item"
        :class="{ 'is-active': itemIndex === value[columnIndex] }"
      >{{ item }}</view>
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
  @include ds-body-2;
  @include ds-tabular;
  color: $ds-placeholder;
  transition: color $ds-transition;
}

.wheel-item.is-active {
  @include ds-body-1;
  color: $ds-ink;
  font-weight: 500;
}
</style>

<!-- indicator-class / mask-class 由 uni-app 注入到组件内部节点上，scoped 选择器命中不到。 -->
<style lang="scss">
@use '@/styles/tokens.scss' as *;

.wheel-indicator {
  height: $ds-sheet-row-height;
  border-top: $ds-hairline solid $ds-line;
  border-bottom: $ds-hairline solid $ds-line;
}

// 未选中项已经是淡灰，遮罩只做很轻的淡出，避免整列糊成一片白。
.wheel-mask {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0)),
    linear-gradient(0deg, rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0));
}
</style>
