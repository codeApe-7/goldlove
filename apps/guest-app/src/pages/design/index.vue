<script setup lang="ts">
import BrandMark from '@/components/BrandMark.vue'
import FoundationsSection from './sections/FoundationsSection.vue'
import ControlsSection from './sections/ControlsSection.vue'
import SelectorsSection from './sections/SelectorsSection.vue'
import PickersSection from './sections/PickersSection.vue'
import FormBlockSection from './sections/FormBlockSection.vue'
import UploaderSection from './sections/UploaderSection.vue'
import StatusSection from './sections/StatusSection.vue'
</script>

<template>
  <view class="design-page">
    <view class="hero">
      <BrandMark light />
      <text class="hero-title">H5 表单组件与下拉样式规范</text>
      <text class="hero-sub">私密会员制 · 高度可信赖的婚恋信息平台 · 设计系统规范 v1.0</text>
    </view>

    <view class="body">
      <FoundationsSection />
      <ControlsSection />
      <SelectorsSection />
      <PickersSection />
      <FormBlockSection />
      <UploaderSection />
      <StatusSection />

      <text class="tail">以上每一项都对应规范图的同名区块，可逐条比对验收。</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.design-page {
  width: 100%;
  min-height: 100vh;
  margin: 0 auto;
  padding-bottom: calc(#{$ds-space-8} + env(safe-area-inset-bottom));
  background: $ds-porcelain;
}

.hero {
  padding: calc(#{$ds-space-8} + env(safe-area-inset-top)) $ds-space-4 $ds-space-7;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #15181c;
  color: $ds-white;
}

.hero-title {
  margin-top: $ds-space-2;
  color: #ead4a7;
  font-family: $ds-font-serif;
  font-size: 40rpx;
  letter-spacing: 4rpx;
  text-align: center;
}

.hero-sub {
  margin-top: $ds-space-2;
  @include ds-caption;
  color: #c2aa7d;
  text-align: center;
}

.body {
  padding: $ds-space-4;
}

.tail {
  display: block;
  margin-top: $ds-space-6;
  @include ds-caption;
  color: $ds-gray;
  text-align: center;
}

@media screen and (min-width: 431px) {
  .design-page { max-width: $ds-viewport-max; }
}
</style>

<!--
  预览页专用的骨架样式故意不加 scoped：子区块组件复用这些 .spec-* 类，
  逐个组件重复声明会把同样的规则打包七遍。
-->
<style lang="scss">
@use '@/styles/tokens.scss' as *;

.spec-block {
  margin-bottom: $ds-space-6;
}

.spec-block__title {
  display: block;
  margin-bottom: $ds-space-3;
  @include ds-h3;
  color: $ds-ink;
}

.spec-group {
  margin-bottom: $ds-space-4;
  padding: $ds-space-4;
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-md;
  background: $ds-white;
  box-shadow: $ds-shadow-soft;
}

.spec-group__title {
  display: block;
  margin-bottom: $ds-space-3;
  @include ds-body-2;
  color: $ds-ink;
  font-weight: 600;
}

.spec-row {
  margin-bottom: $ds-space-4;
}

.spec-row:last-child {
  margin-bottom: 0;
}

.spec-label {
  display: block;
  margin-bottom: $ds-space-2;
  @include ds-caption;
  color: $ds-gray;
}

.spec-inline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: $ds-space-2;
}

.spec-note {
  display: block;
  margin-top: $ds-space-2;
  @include ds-caption;
  color: $ds-placeholder;
  line-height: 34rpx;
}
</style>
