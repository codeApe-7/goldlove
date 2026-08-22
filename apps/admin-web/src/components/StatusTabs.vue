<script setup lang="ts">
/**
 * 带计数的状态 tab（规范图 4 顶部）。计数由后端 /profiles/counts 一次返回，
 * 统计时忽略 tab 自身的条件，否则切换之后其他 tab 的数字会全变 0。
 */
export interface StatusTabItem {
  key: string
  label: string
  count?: number
}

defineProps<{ items: StatusTabItem[]; active: string }>()
defineEmits<{ change: [key: string] }>()
</script>

<template>
  <nav class="status-tabs" role="tablist">
    <button
      v-for="item in items"
      :key="item.key"
      type="button"
      role="tab"
      class="status-tab"
      :class="{ 'is-active': item.key === active }"
      :aria-selected="item.key === active"
      @click="$emit('change', item.key)"
    >
      {{ item.label }}
      <span v-if="item.count !== undefined" class="tabular">({{ item.count.toLocaleString() }})</span>
    </button>
  </nav>
</template>

<style scoped>
.status-tabs {
  display: flex;
  align-items: center;
  gap: var(--ds-space-5);
  margin-bottom: var(--ds-space-4);
  border-bottom: 1px solid var(--ds-line);
}
.status-tab {
  position: relative;
  padding: 0 0 10px;
  border: 0;
  background: none;
  color: var(--ds-text-muted);
  font-size: var(--ds-body-size);
  cursor: pointer;
}
.status-tab:hover {
  color: var(--ds-text);
}
.status-tab.is-active {
  color: var(--ds-primary);
  font-weight: 500;
}
.status-tab.is-active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  border-radius: 2px;
  background: var(--ds-primary);
  content: '';
}
.status-tab span {
  margin-left: var(--ds-space-1);
}
</style>
