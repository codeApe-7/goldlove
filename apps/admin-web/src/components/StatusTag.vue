<script setup lang="ts">
import { computed } from 'vue'
import { reviewStatusMeta, type ReviewStatus } from '@/utils/presentation'

const props = defineProps<{ status: ReviewStatus }>()
const meta = computed(() => reviewStatusMeta(props.status))
</script>

<template>
  <el-tag :type="meta.tone" effect="light" round class="archive-status-tag">
    {{ meta.label }}
  </el-tag>
</template>
