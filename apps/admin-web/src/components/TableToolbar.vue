<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'

/**
 * 列表工具栏（规范图 4 下半）。左侧是勾选数量与批量操作，右侧是刷新与主操作。
 * 勾选数为 0 时批量区不显示——留一排点不动的灰按钮只会让人反复去点。
 */
defineProps<{ selectedCount: number }>()
defineEmits<{ refresh: [] }>()
</script>

<template>
  <div class="table-toolbar">
    <div class="toolbar-left">
      <template v-if="selectedCount > 0">
        <span class="selected">已选择 <b class="tabular">{{ selectedCount }}</b> 项</span>
        <slot name="batch" />
      </template>
      <span v-else class="hint"><slot name="hint" /></span>
    </div>
    <div class="toolbar-right">
      <el-button :icon="Refresh" circle aria-label="刷新列表" @click="$emit('refresh')" />
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ds-space-4);
  margin-bottom: var(--ds-space-3);
  min-height: var(--ds-control-height);
}
.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
}
.selected {
  color: var(--ds-text-secondary);
  font-size: var(--ds-body-size);
}
.selected b {
  color: var(--ds-primary);
}
.hint {
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
}
</style>
