<script setup lang="ts">
defineProps<{ label: string; tone: 'info' | 'warning' | 'success' | 'danger' }>()
</script>

<template>
  <el-tag :type="tone" effect="light" round class="archive-status-tag">
    {{ label }}
  </el-tag>
</template>
