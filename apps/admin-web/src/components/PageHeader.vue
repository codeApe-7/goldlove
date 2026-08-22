<script setup lang="ts">
/** 页面标题区。标题走令牌的 H1（28/700），副标题走辅助字号。 */
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <header class="page-heading">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.actions" class="page-heading-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-heading {
  min-height: 48px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--ds-space-5);
  margin-bottom: var(--ds-space-4);
}
h1 {
  margin: 0;
  color: var(--ds-text);
  font-size: var(--ds-h1-size);
  font-weight: var(--ds-h1-weight);
  line-height: 1.25;
  letter-spacing: -0.02em;
}
p {
  margin: var(--ds-space-2) 0 0;
  color: var(--ds-text-muted);
  font-size: var(--ds-body-size);
}
.page-heading-actions {
  flex: none;
  display: flex;
  gap: var(--ds-space-2);
}
</style>
