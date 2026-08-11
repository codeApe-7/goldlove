<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ name: string; size?: number }>(), { size: 18 })
const glyph = computed(() => ({
  home: '⌂',
  status: '◉',
  user: '♙',
  shield: '◇',
  document: '▤',
  lock: '▣',
  help: '?',
  headset: '◡',
  chevron: '›',
  check: '✓',
  close: '×',
  clock: '◷',
  camera: '□',
}[props.name] ?? '·'))
</script>

<template><text class="app-icon" :style="{ fontSize: `${size}px` }" aria-hidden="true">{{ glyph }}</text></template>

<style scoped>
.app-icon {
  width: 1.4em;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: currentColor;
  font-family: Arial, sans-serif;
  line-height: 1;
}
</style>
