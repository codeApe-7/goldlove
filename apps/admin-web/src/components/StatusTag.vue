<script setup lang="ts">
import { computed } from 'vue'

/**
 * 状态标签。规范图 1.4 给了五档语义色，这里的 tone 名字对着语义，
 * 而不是 Element Plus 的 type——EP 的 'primary' 是蓝、'info' 是灰，
 * 直接暴露它的命名会让调用方每次都要想一遍映射。
 */
type Tone = 'info' | 'success' | 'warning' | 'danger' | 'neutral'

const props = defineProps<{ label: string; tone: Tone }>()

const EL_TYPE: Record<Tone, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
  info: 'primary',
  success: 'success',
  warning: 'warning',
  danger: 'danger',
  neutral: 'info',
}

const elType = computed(() => EL_TYPE[props.tone] ?? 'info')
</script>

<template>
  <el-tag :type="elType" effect="light" round class="archive-status-tag">
    {{ label }}
  </el-tag>
</template>
