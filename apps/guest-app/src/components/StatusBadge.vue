<script setup lang="ts">
import { computed } from 'vue'
import { guestStatusMeta } from '@/utils/presentation'
import type { GuestStatusTone } from '@/utils/presentation'

/** 规范图 6 · 状态标签：成功 / 警告 / 错误 / 中性 / 提示五档。 */
export type StatusBadgeTone = GuestStatusTone | 'info'

const props = withDefaults(
  defineProps<{
    /** 传档案状态码时自动映射文案与色调。 */
    status?: string
    /** 直接指定色调与文案，用于档案状态之外的通用场景。 */
    tone?: StatusBadgeTone
    label?: string
  }>(),
  { status: '', tone: undefined, label: '' },
)

const resolved = computed<{ tone: StatusBadgeTone; label: string }>(() => {
  if (props.tone && props.label) {
    return { tone: props.tone, label: props.label }
  }
  const meta = guestStatusMeta(props.status)
  return { tone: props.tone ?? meta.tone, label: props.label || meta.label }
})
</script>

<template><text class="status-badge" :class="`tone-${resolved.tone}`">{{ resolved.label }}</text></template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.status-badge {
  padding: 5rpx $ds-space-2;
  border-radius: $ds-radius-xs;
  @include ds-caption;
  font-weight: 600;
}

.tone-neutral { color: $ds-neutral-ink; background: $ds-neutral-soft; }
.tone-warning { color: $ds-warning-ink; background: $ds-warning-soft; }
.tone-success { color: $ds-success-ink; background: $ds-success-soft; }
.tone-danger { color: $ds-error-ink; background: $ds-error-soft; }
.tone-info { color: $ds-info-ink; background: $ds-info-soft; }
</style>
