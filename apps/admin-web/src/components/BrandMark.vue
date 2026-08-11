<script setup lang="ts">
withDefaults(defineProps<{ compact?: boolean; light?: boolean }>(), {
  compact: false,
  light: false,
})
</script>

<template>
  <span class="brand-mark" :class="{ compact, light }" aria-hidden="true">
    <i v-for="index in 7" :key="index" />
  </span>
</template>

<style scoped>
.brand-mark {
  width: 34px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: var(--archive-gold);
}
.brand-mark.compact {
  width: 24px;
  height: 30px;
  gap: 1px;
}
.brand-mark.light {
  color: #f0d7a5;
}
.brand-mark i {
  width: 2px;
  height: 48%;
  border-radius: 999px;
  background: currentColor;
}
.brand-mark i:nth-child(2),
.brand-mark i:nth-child(6) {
  height: 72%;
}
.brand-mark i:nth-child(3),
.brand-mark i:nth-child(5) {
  height: 94%;
}
.brand-mark i:nth-child(4) {
  height: 66%;
}
</style>
