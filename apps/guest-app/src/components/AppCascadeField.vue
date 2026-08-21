<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import AppSheet from './AppSheet.vue'
import { REGIONS } from '@/data/regions'
import {
  cascadeColumns,
  formatRegionValue,
  normalizeSelection,
  parseRegionValue,
  selectCity,
  selectDistrict,
  selectProvince,
} from '@/utils/regionCascade'
import type { CascadeSelection, RegionNode } from '@/utils/regionCascade'

/** 规范图 3.8 · 级联选择器（所在城市 / 地区）：省份 → 城市 → 区/县三列并排列表。 */
const props = withDefaults(
  defineProps<{
    modelValue: string
    title?: string
    placeholder?: string
    disabled?: boolean
    state?: 'default' | 'error'
    message?: string
    tree?: RegionNode[]
  }>(),
  {
    title: '请选择所在城市 / 地区',
    placeholder: '请选择所在城市 / 地区',
    disabled: false,
    state: 'default',
    message: '',
    tree: () => REGIONS,
  },
)

const emit = defineEmits<{ 'update:modelValue': [string] }>()

const EMPTY: CascadeSelection = { province: '', city: '', district: '' }

const open = ref(false)
const draft = ref<CascadeSelection>({ ...EMPTY })

const columns = computed(() => cascadeColumns(props.tree, draft.value))
const canConfirm = computed(() => draft.value.district !== '')

// 全国数据下三列分别可达 34 / 21 / 38 项，打开时必须滚到已选项，否则用户看不到自己选过什么。
// scroll-into-view 要的是元素 id，用下标而不是名字，避免中文 id。
const anchors = computed(() => ({
  province: anchorOf('p', columns.value.provinces, draft.value.province),
  city: anchorOf('c', columns.value.cities, draft.value.city),
  district: anchorOf('d', columns.value.districts, draft.value.district),
}))

function anchorOf(prefix: string, names: string[], selected: string): string {
  const index = names.indexOf(selected)
  return index >= 0 ? `${prefix}-${index}` : ''
}

function show(): void {
  if (props.disabled) {
    return
  }
  // 存量档案的所在城市是自由文本，对不上数据集时 normalizeSelection 会清空。
  draft.value = normalizeSelection(props.tree, parseRegionValue(props.modelValue))
  open.value = true
}

function pickProvince(name: string): void {
  draft.value = selectProvince(props.tree, name, draft.value)
}

function pickCity(name: string): void {
  draft.value = selectCity(props.tree, draft.value, name)
}

function pickDistrict(name: string): void {
  draft.value = selectDistrict(draft.value, name)
}

function confirm(): void {
  if (!canConfirm.value) {
    uni.showToast({ title: '请选到区 / 县', icon: 'none' })
    return
  }
  emit('update:modelValue', formatRegionValue(draft.value))
  open.value = false
}
</script>

<template>
  <view class="app-cascade-field" :class="[`tone-${disabled ? 'disabled' : state}`]">
    <view class="trigger" @tap="show">
      <text class="value" :class="{ placeholder: modelValue === '' }">
        {{ modelValue === '' ? placeholder : modelValue }}
      </text>
      <view class="arrow"><AppIcon name="chevron" :size="18" /></view>
    </view>
    <text v-if="message" class="message">{{ message }}</text>

    <AppSheet :visible="open" :title="title" @cancel="open = false" @confirm="confirm">
      <view class="columns">
        <view class="column">
          <text class="column-head">省份</text>
          <scroll-view class="list" scroll-y :scroll-into-view="anchors.province">
            <view
              v-for="(name, index) in columns.provinces"
              :id="`p-${index}`"
              :key="name"
              class="cell"
              :class="{ active: name === draft.province }"
              @tap="pickProvince(name)"
            >{{ name }}</view>
          </scroll-view>
        </view>

        <view class="column">
          <text class="column-head">城市</text>
          <scroll-view class="list" scroll-y :scroll-into-view="anchors.city">
            <view
              v-for="(name, index) in columns.cities"
              :id="`c-${index}`"
              :key="name"
              class="cell"
              :class="{ active: name === draft.city }"
              @tap="pickCity(name)"
            >{{ name }}</view>
          </scroll-view>
        </view>

        <view class="column">
          <text class="column-head">区 / 县</text>
          <scroll-view class="list" scroll-y :scroll-into-view="anchors.district">
            <view
              v-for="(name, index) in columns.districts"
              :id="`d-${index}`"
              :key="name"
              class="cell"
              :class="{ active: name === draft.district }"
              @tap="pickDistrict(name)"
            >{{ name }}</view>
          </scroll-view>
        </view>
      </view>
    </AppSheet>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.app-cascade-field {
  width: 100%;
}

.trigger {
  min-height: $ds-control-height;
  padding: 0 $ds-space-3;
  display: flex;
  align-items: center;
  gap: $ds-space-2;
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-sm;
  background: $ds-white;
}

.value {
  min-width: 0;
  flex: 1;
  @include ds-body-1;
  @include ds-truncate;
  color: $ds-ink;
}

.value.placeholder {
  color: $ds-placeholder;
}

.arrow {
  flex: none;
  display: flex;
  align-items: center;
  color: $ds-gray;
  transform: rotate(90deg);
}

.message {
  display: block;
  margin-top: $ds-space-1;
  @include ds-caption;
  color: $ds-gray;
}

.columns {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
}

.column {
  min-width: 0;
  border-right: $ds-hairline solid $ds-line;
}

.column:last-child {
  border-right: 0;
}

.column-head {
  display: block;
  padding: $ds-space-2 $ds-space-3;
  @include ds-caption;
  color: $ds-gray;
  text-align: center;
  background: $ds-porcelain;
}

.list {
  height: 520rpx;
}

.cell {
  min-height: 76rpx;
  padding: $ds-space-2 $ds-space-2;
  display: flex;
  align-items: center;
  justify-content: center;
  @include ds-body-2;
  color: $ds-graphite;
  text-align: center;
}

.cell:active {
  background: $ds-porcelain;
}

.cell.active {
  background: $ds-porcelain;
  color: $ds-ink;
  font-weight: 600;
}

.tone-error .trigger {
  border-color: $ds-error;
}

.tone-error .message {
  color: $ds-error;
}

.tone-disabled .trigger {
  background: $ds-disabled-bg;
}

.tone-disabled .value {
  color: $ds-disabled-ink;
}
</style>
