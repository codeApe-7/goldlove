<script setup lang="ts">
defineProps<{ title: string; description?: string }>()
</script>

<template>
  <header class="page-heading">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
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
  gap: 24px;
  margin-bottom: 20px;
}
h1 {
  margin: 0;
  color: var(--archive-ink);
  font-size: 23px;
  line-height: 1.25;
  letter-spacing: -0.02em;
}
p {
  margin: 7px 0 0;
  color: var(--archive-muted);
  font-size: 13px;
}
.page-heading-actions {
  flex: none;
}
</style>
