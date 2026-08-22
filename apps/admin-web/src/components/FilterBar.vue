<script setup lang="ts">
/**
 * 筛选条外壳（规范图 4）。只负责「标签 + 控件」的排布与右侧的重置 / 查询，
 * 控件本身由调用方用 el-input / el-select / el-date-picker 直接放进 fields 插槽——
 * 这些 Element Plus 原生形态经 theme.css 令牌化后已经符合规范图，不必再包一层。
 */
defineProps<{ loading?: boolean }>()
defineEmits<{ search: []; reset: [] }>()
</script>

<template>
  <section class="filter-bar archive-panel">
    <div class="filter-fields">
      <slot name="fields" />
    </div>
    <div class="filter-actions">
      <el-button @click="$emit('reset')">重置</el-button>
      <el-button type="primary" :loading="loading" @click="$emit('search')">查询</el-button>
    </div>
  </section>
</template>

<style scoped>
.filter-bar {
  margin-bottom: var(--ds-space-4);
  padding: var(--ds-space-4);
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--ds-space-4);
  flex-wrap: wrap;
}
.filter-fields {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: flex-end;
  gap: var(--ds-space-4);
  flex-wrap: wrap;
}
.filter-actions {
  flex: none;
  display: flex;
  gap: var(--ds-space-2);
}
/* 每个字段是「12px 标签 + 控件」的竖向组合，标签用 :deep 让调用方少写样式 */
.filter-fields :deep(.filter-field) {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.filter-fields :deep(.filter-field > span) {
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
}
</style>
