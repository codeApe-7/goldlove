<script setup lang="ts">
import { Files } from '@element-plus/icons-vue'

/** 空状态（规范图 5 右侧）。文案要说清「为什么空」，而不只是「没有数据」。 */
withDefaults(defineProps<{ title?: string; hint?: string }>(), {
  title: '暂无数据',
  hint: '',
})
</script>

<template>
  <div class="empty-state">
    <el-icon class="empty-icon"><Files /></el-icon>
    <p class="empty-title">{{ title }}</p>
    <p v-if="hint" class="empty-hint">{{ hint }}</p>
    <div v-if="$slots.action" class="empty-action">
      <slot name="action" />
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  padding: var(--ds-space-6) var(--ds-space-4);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.empty-icon {
  font-size: 40px;
  color: var(--ds-disabled);
}
.empty-title {
  margin: var(--ds-space-3) 0 0;
  color: var(--ds-text-secondary);
  font-size: var(--ds-body-size);
}
.empty-hint {
  margin: 6px 0 0;
  color: var(--ds-text-muted);
  font-size: var(--ds-caption-size);
}
.empty-action {
  margin-top: var(--ds-space-4);
}
</style>
