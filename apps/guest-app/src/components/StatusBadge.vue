<script setup lang="ts">
import { computed } from 'vue'
import { guestStatusMeta } from '@/utils/presentation'

const props = defineProps<{ status: string }>()
const meta = computed(() => guestStatusMeta(props.status))
</script>

<template><text class="status-badge" :class="`tone-${meta.tone}`">{{ meta.label }}</text></template>

<style lang="scss" scoped>
.status-badge {
  padding: 5rpx 13rpx;
  border-radius: 8rpx;
  font-size: 21rpx;
  font-weight: 600;
}
.tone-neutral { color: #77787c; background: #efefed; }
.tone-warning { color: #a16207; background: #fff4d8; }
.tone-success { color: #15803d; background: #e4f7e9; }
.tone-danger { color: #dc2626; background: #fee9e9; }
</style>
