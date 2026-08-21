<script setup lang="ts">
import { ref } from 'vue'
import AppCheckbox from '@/components/AppCheckbox.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import AppProgressMeter from '@/components/AppProgressMeter.vue'
import type { CompletionItem } from '@/components/AppProgressMeter.vue'

/** 规范图区块 6 · 复选框、授权勾选、状态标签、信息完成度。 */
const BADGES = [
  { tone: 'success', label: '已通过', kind: '成功' },
  { tone: 'warning', label: '待完善', kind: '警告' },
  { tone: 'danger', label: '未通过', kind: '错误' },
  { tone: 'neutral', label: '审核中', kind: '中性' },
  { tone: 'info', label: '可修改', kind: '提示' },
] as const

const COMPLETION_ITEMS: CompletionItem[] = [
  { label: '基本资料', tone: 'done' },
  { label: '社交账号', tone: 'done' },
  { label: '照片资料', tone: 'partial' },
  { label: '择偶标准', tone: 'todo' },
]

const unchecked = ref(false)
const checked = ref(true)
const consentTruthful = ref(true)
const consentReview = ref(true)
const consentShare = ref(false)
</script>

<template>
  <view class="spec-block">
    <text class="spec-block__title">6. 复选框 / 授权勾选 / 状态标签</text>

    <view class="spec-group">
      <text class="spec-group__title">复选框</text>
      <view class="stack">
        <AppCheckbox v-model="unchecked">默认未选</AppCheckbox>
        <AppCheckbox v-model="checked">选中</AppCheckbox>
        <AppCheckbox :model-value="false" disabled>禁用未选</AppCheckbox>
        <AppCheckbox :model-value="true" disabled>禁用已选</AppCheckbox>
      </view>
    </view>

    <view class="spec-group">
      <text class="spec-group__title">授权勾选（私密承诺）</text>
      <view class="stack">
        <AppCheckbox v-model="consentTruthful">
          本人承诺以上填写信息真实无误，愿意承担相应法律责任
        </AppCheckbox>
        <AppCheckbox v-model="consentReview">
          授权婚恋智能档案库对信息进行人工审核与真实性核验
        </AppCheckbox>
        <AppCheckbox v-model="consentShare">
          同意平台在匹配需要时，向对方展示我的部分信息
        </AppCheckbox>
      </view>
    </view>

    <view class="spec-group">
      <text class="spec-group__title">状态标签</text>
      <view class="badges">
        <view v-for="badge in BADGES" :key="badge.label" class="badge-row">
          <text class="badge-kind">{{ badge.kind }}</text>
          <StatusBadge :tone="badge.tone" :label="badge.label" />
        </view>
      </view>
    </view>

    <view class="spec-group">
      <text class="spec-group__title">信息完成度（进度条样式）</text>
      <AppProgressMeter
        :percent="65"
        title="资料完整度"
        hint="完善资料可获得更多匹配机会"
        :items="COMPLETION_ITEMS"
      />
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.stack {
  display: flex;
  flex-direction: column;
  gap: $ds-space-3;
}

.badges {
  display: flex;
  flex-direction: column;
  gap: $ds-space-2;
}

.badge-row {
  display: flex;
  align-items: center;
  gap: $ds-space-3;
}

.badge-kind {
  width: 72rpx;
  @include ds-caption;
  color: $ds-gray;
}
</style>
