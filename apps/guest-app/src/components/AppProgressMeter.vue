<script setup lang="ts">
import AppIcon from './AppIcon.vue'

/** 规范图 6 · 信息完成度：百分比进度条 + 分项完成清单。 */
export interface CompletionItem {
  label: string
  tone: 'done' | 'partial' | 'todo'
}

withDefaults(
  defineProps<{
    percent: number
    title?: string
    hint?: string
    items?: CompletionItem[]
  }>(),
  { title: '资料完整度', hint: '', items: () => [] },
)

const TONE_ICON: Record<CompletionItem['tone'], string> = {
  done: 'check-circle',
  partial: 'dot',
  todo: 'circle',
}
</script>

<template>
  <view class="app-progress-meter">
    <view class="head">
      <text class="title">{{ title }}</text>
      <text class="percent archive-tabular">{{ percent }}%</text>
    </view>
    <view class="track">
      <view class="fill" :style="{ width: `${Math.min(100, Math.max(0, percent))}%` }" />
    </view>
    <text v-if="hint" class="hint">{{ hint }}</text>

    <view v-if="items.length > 0" class="items">
      <view v-for="item in items" :key="item.label" class="item" :class="`tone-${item.tone}`">
        <AppIcon :name="TONE_ICON[item.tone]" :size="16" />
        <text>{{ item.label }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.app-progress-meter {
  width: 100%;
}

.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $ds-space-2;
}

.title {
  @include ds-body-2;
  color: $ds-graphite;
  font-weight: 500;
}

.percent {
  @include ds-h3;
  color: $ds-ink;
}

.track {
  height: 10rpx;
  margin-top: $ds-space-2;
  overflow: hidden;
  border-radius: $ds-radius-pill;
  background: $ds-line;
}

.fill {
  height: 100%;
  border-radius: inherit;
  background: $ds-ink;
  transition: width $ds-transition;
}

.hint {
  display: block;
  margin-top: $ds-space-2;
  @include ds-caption;
  color: $ds-gray;
}

.items {
  margin-top: $ds-space-3;
  display: flex;
  flex-direction: column;
  gap: $ds-space-2;
}

.item {
  display: flex;
  align-items: center;
  gap: $ds-space-2;
  @include ds-body-2;
  color: $ds-graphite;
}

.tone-done { color: $ds-success; }
.tone-partial { color: $ds-warning; }
.tone-todo { color: $ds-placeholder; }

// 图标承担色调，文案统一用正文色，避免整行都染色。
.item text {
  color: $ds-graphite;
}

.tone-todo text {
  color: $ds-gray;
}
</style>
