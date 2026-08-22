<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    meta?: string
    /**
     * 是否裁掉超出圆角的内容。默认裁，让子行的分隔线贴合圆角；
     * 但内联展开的下拉面板是绝对定位的，被裁掉就只能看到最上面几个选项，
     * 所以含下拉控件的卡片必须传 :clip="false"。
     */
    clip?: boolean
  }>(),
  { clip: true },
)
</script>

<template>
  <view class="section-wrap">
    <view v-if="title || meta" class="section-label">
      <text>{{ title }}</text><text v-if="meta">{{ meta }}</text>
    </view>
    <view class="section-card" :class="{ clip }"><slot /></view>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.section-wrap { margin-bottom: $ds-space-3; }
.section-label {
  margin-bottom: $ds-space-2;
  padding: 0 $ds-space-1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: $ds-graphite;
  @include ds-body-1;
  font-weight: 600;
}
.section-label text:last-child:not(:first-child) {
  @include ds-body-2;
  color: $ds-gray;
  font-weight: 400;
}
.section-card {
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-md;
  background: $ds-white;
}
.section-card.clip { overflow: hidden; }
</style>
