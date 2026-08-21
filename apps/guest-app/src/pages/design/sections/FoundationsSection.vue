<script setup lang="ts">
import BrandMark from '@/components/BrandMark.vue'

/** 规范图区块 1 · 基础样式：品牌、色彩、字体、间距、圆角、阴影。 */
const COLORS = [
  { hex: '#0E0F12', name: '品牌黑' },
  { hex: '#2B2D30', name: '石墨灰' },
  { hex: '#8A8D94', name: '中灰' },
  { hex: '#F5F4F2', name: '瓷白' },
  { hex: '#E6E4E1', name: '浅灰' },
  { hex: '#22C55E', name: '成功' },
  { hex: '#F59E0B', name: '警告' },
  { hex: '#EF4444', name: '错误' },
  { hex: '#3B82F6', name: '信息' },
]

const TYPES = [
  { key: 'h1', label: '标题 1', spec: '34px / Bold / 行高 44' },
  { key: 'h2', label: '标题 2', spec: '28px / Medium / 行高 36' },
  { key: 'h3', label: '标题 3', spec: '20px / Medium / 行高 28' },
  { key: 'body-1', label: '正文 1', spec: '16px / Regular / 行高 24' },
  { key: 'body-2', label: '正文 2', spec: '14px / Regular / 行高 20' },
  { key: 'caption', label: '辅助', spec: '12px / Regular / 行高 16' },
]

const SPACINGS = [4, 8, 12, 16, 20, 24, 32, 40, 64]
const RADII = [4, 8, 16, 20, 24]
const SHADOWS = [
  { key: 'soft', label: '浅阴影', spec: '0 1px 2px rgba(0,0,0,.06)' },
  { key: 'mid', label: '中阴影', spec: '0 4px 12px rgba(0,0,0,.08)' },
  { key: 'deep', label: '深阴影', spec: '0 8px 24px rgba(0,0,0,.12)' },
]
</script>

<template>
  <view class="spec-block">
    <text class="spec-block__title">1. 基础样式</text>

    <view class="spec-group">
      <text class="spec-group__title">品牌标识 / Brand</text>
      <view class="brand-plate">
        <BrandMark light />
        <view class="brand-copy">
          <text class="brand-name">婚恋智能档案库</text>
          <text class="brand-slogan">真实 · 严谨 · 安全 · 专属</text>
        </view>
      </view>
    </view>

    <view class="spec-group">
      <text class="spec-group__title">色彩规范 / Color</text>
      <view class="swatches">
        <view v-for="color in COLORS" :key="color.hex" class="swatch">
          <view class="chip" :style="{ background: color.hex }" />
          <text class="hex archive-tabular">{{ color.hex }}</text>
          <text class="name">{{ color.name }}</text>
        </view>
      </view>
      <text class="spec-note">品牌金 #C9A96A 仅用于品牌标识与深色背景上的强调，不参与表单控件配色。</text>
    </view>

    <view class="spec-group">
      <text class="spec-group__title">字体规范 / Typography</text>
      <view v-for="type in TYPES" :key="type.key" class="type-row">
        <text class="sample" :class="`sample-${type.key}`">{{ type.label }}</text>
        <text class="sample-spec">{{ type.spec }}</text>
      </view>
      <text class="spec-note">
        中文字体栈：HarmonyOS Sans SC → 思源黑体 → Noto Sans SC → 苹方，未预装时逐级兜底；
        数字启用等宽数字特性保证纵向对齐。
      </text>
    </view>

    <view class="spec-group">
      <text class="spec-group__title">间距规范 / Spacing</text>
      <view class="scale-row">
        <view v-for="size in SPACINGS" :key="size" class="scale-item">
          <view class="scale-box" :style="{ width: `${size * 2}rpx`, height: `${size * 2}rpx` }" />
          <text class="scale-label archive-tabular">{{ size }}</text>
        </view>
      </view>
      <text class="spec-note">基准：4pt</text>
    </view>

    <view class="spec-group">
      <text class="spec-group__title">圆角规范 / Radius</text>
      <view class="scale-row">
        <view v-for="size in RADII" :key="size" class="scale-item">
          <view class="radius-box" :style="{ 'border-radius': `${size * 2}rpx` }" />
          <text class="scale-label archive-tabular">{{ size }}px</text>
        </view>
      </view>
    </view>

    <view class="spec-group">
      <text class="spec-group__title">阴影规范 / Shadow（仅用于卡片、弹层）</text>
      <view class="shadow-row">
        <view v-for="shadow in SHADOWS" :key="shadow.key" class="shadow-item">
          <view class="shadow-box" :class="`shadow-${shadow.key}`" />
          <text class="scale-label">{{ shadow.label }}</text>
          <text class="shadow-spec">{{ shadow.spec }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.brand-plate {
  padding: $ds-space-4;
  display: flex;
  align-items: center;
  gap: $ds-space-3;
  border-radius: $ds-radius-sm;
  background: #15181c;
}

.brand-name {
  display: block;
  color: #ead4a7;
  font-family: $ds-font-serif;
  font-size: 34rpx;
  letter-spacing: 4rpx;
}

.brand-slogan {
  display: block;
  margin-top: $ds-space-1;
  @include ds-caption;
  color: #c2aa7d;
  letter-spacing: 3rpx;
}

.swatches {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: $ds-space-2;
}

.chip {
  width: 100%;
  height: 76rpx;
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-xs;
}

.hex,
.name {
  display: block;
  margin-top: 4rpx;
  font-size: 18rpx;
  line-height: 26rpx;
}

.hex {
  color: $ds-graphite;
}

.name {
  color: $ds-gray;
}

.type-row {
  margin-bottom: $ds-space-3;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $ds-space-3;
}

.sample {
  min-width: 0;
  color: $ds-ink;
}

.sample-h1 { @include ds-h1; }
.sample-h2 { @include ds-h2; }
.sample-h3 { @include ds-h3; }
.sample-body-1 { @include ds-body-1; }
.sample-body-2 { @include ds-body-2; }
.sample-caption { @include ds-caption; }

.sample-spec {
  flex: none;
  @include ds-caption;
  color: $ds-gray;
}

.scale-row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: $ds-space-3;
}

.scale-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $ds-space-1;
}

.scale-box {
  background: $ds-graphite;
}

.radius-box {
  width: 88rpx;
  height: 88rpx;
  border: $ds-hairline solid $ds-line;
  background: $ds-porcelain;
}

.scale-label {
  @include ds-caption;
  color: $ds-gray;
}

.shadow-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $ds-space-3;
}

.shadow-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $ds-space-1;
}

.shadow-box {
  width: 100%;
  height: 96rpx;
  border-radius: $ds-radius-sm;
  background: $ds-white;
}

.shadow-soft { box-shadow: $ds-shadow-soft; }
.shadow-mid { box-shadow: $ds-shadow-mid; }
.shadow-deep { box-shadow: $ds-shadow-deep; }

.shadow-spec {
  font-size: 17rpx;
  line-height: 24rpx;
  color: $ds-placeholder;
  text-align: center;
}
</style>
