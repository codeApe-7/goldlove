<script setup lang="ts">
import { computed } from 'vue'
import { REGION_OPTIONS, formatRegionPath } from '@/data/regions'

/**
 * 所在地区级联筛选（规范图 3.5）。
 *
 * 档案的 city 存的是「北京市 / 东城区」这样拼好的文本，不是区划码，所以：
 * - checkStrictly 让每一级都能单独选中（只选省份就按省筛）
 * - 选中路径经 formatRegionPath 折叠后作为前缀下发，与存库格式对齐
 */
const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

// 反向解析：把「广东省 / 深圳市」还原成级联的路径。
// 直辖市折叠过中间层，这里补回来，否则级联面板定位不到已选项。
const path = computed<string[]>(() => {
  const segments = props.modelValue
    .split('/')
    .map((segment) => segment.trim())
    .filter((segment) => segment !== '')
  if (segments.length === 0) {
    return []
  }
  const province = REGION_OPTIONS.find((option) => option.value === segments[0])
  if (!province) {
    return []
  }
  if (segments.length === 1) {
    return segments
  }
  const hasCity = province.children?.some((city) => city.value === segments[1])
  return hasCity ? segments : [segments[0], segments[0], ...segments.slice(1)]
})

function onChange(value: unknown): void {
  emit('update:modelValue', Array.isArray(value) ? formatRegionPath(value as string[]) : '')
}
</script>

<template>
  <el-cascader
    :model-value="path"
    :options="REGION_OPTIONS"
    :props="{ checkStrictly: true }"
    placeholder="全部地区"
    clearable
    filterable
    style="width: 220px"
    @update:model-value="onChange"
  />
</template>
