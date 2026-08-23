<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { onHide, onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { useCourseStore } from '@/stores/course'
import { renderCourseMarkdown } from '@/utils/courseMarkdown'
import { courseTypeLabel, formatCourseDuration } from '@/utils/course'
import { goBackOr, goToPage } from '@/adapters/navigation'
import AppButton from '@/components/AppButton.vue'

const course = useCourseStore()
const courseId = ref(0)

const detail = computed(() => course.detail)
const isVideo = computed(() => detail.value?.contentType === 'VIDEO')
const bodyHtml = computed(() => renderCourseMarkdown(detail.value?.contentMarkdown))

onLoad((query) => {
  courseId.value = Number(query?.id ?? 0)
  if (courseId.value > 0) {
    void course.loadDetail(courseId.value)
  }
})

/**
 * 阻止复制。
 *
 * <p><b>这一层是劝退，不是防盗。</b>它能挡住顺手长按选中、右键复制、拖走图片，
 * 也就是绝大多数普通用户会做的动作。但以下方式全都绕得过，且成本极低：
 * <ul>
 *   <li>开发者工具直接看 DOM 里的正文；</li>
 *   <li>直接调 <code>GET /guest/courses/{id}</code> 拿 JSON（会员账号本来就有权限）；</li>
 *   <li>截图后 OCR；</li>
 *   <li>禁用 JavaScript，这些监听器就不会挂上。</li>
 * </ul>
 * 视频同理：播放地址是 2 小时签名 URL，拿到手就能在有效期内转发下载。
 * 所以**不要把这一层当成安全边界**——真要防内容外流，只能靠水印加账号追责。</p>
 */
function blockEvent(event: Event): void {
  event.preventDefault()
}

const GUARDED_EVENTS = ['copy', 'cut', 'selectstart', 'dragstart', 'contextmenu'] as const

let guardsInstalled = false

/**
 * 挂 / 摘监听器用的是 **uni-app 的页面生命周期**，不是 Vue 的 `onMounted` / `onUnmounted`。
 *
 * <p>踩过的坑：uni-app H5 会缓存页面组件，从课程详情跳走时 `onUnmounted` **不一定触发**，
 * 于是这些 document 级的监听器就一直挂着——用户回到档案页想复制自己的档案编号会复制不了。
 * 用 `onShow` / `onHide` + `onUnload` 才能保证「只在本页可见时生效」。
 * `onUnmounted` 仍然保留做兜底。</p>
 */
function installGuards(): void {
  if (guardsInstalled) {
    return
  }
  GUARDED_EVENTS.forEach((name) => document.addEventListener(name, blockEvent))
  guardsInstalled = true
}

function removeGuards(): void {
  if (!guardsInstalled) {
    return
  }
  GUARDED_EVENTS.forEach((name) => document.removeEventListener(name, blockEvent))
  guardsInstalled = false
}

onShow(installGuards)
onHide(removeGuards)
onUnload(() => {
  removeGuards()
  course.clearDetail()
})
onUnmounted(removeGuards)

function back(): void {
  goBackOr('/pages/course/index')
}

function toVip(): void {
  goToPage('/pages/vip/index')
}

function retry(): void {
  if (courseId.value > 0) {
    void course.loadDetail(courseId.value)
  }
}
</script>

<template>
  <view class="detail-page">
    <view class="topbar">
      <view class="back" @tap="back">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7" /></svg>
        <text>返回</text>
      </view>
    </view>

    <view v-if="course.detailLoading" class="state">正在加载…</view>

    <!-- 需要会员：这是正常分支，不是错误 -->
    <view v-else-if="course.detailLocked" class="paywall">
      <text class="paywall-title">这节课需要会员</text>
      <text class="paywall-text">
        开通会员后可查看全部课程的正文与视频。已经开通过的话，回到会员页刷新一下状态即可。
      </text>
      <AppButton block @tap="toVip">去开通会员</AppButton>
      <view class="paywall-back" @tap="back"><text>先看看别的课</text></view>
    </view>

    <view v-else-if="course.detailError" class="state">
      <text>{{ course.detailError }}</text>
      <view class="state-action" @tap="retry"><text>重试</text></view>
    </view>

    <template v-else-if="detail">
      <view class="head">
        <text class="title">{{ detail.title }}</text>
        <text v-if="detail.subtitle" class="subtitle">{{ detail.subtitle }}</text>
        <view class="meta">
          <text class="tag">{{ courseTypeLabel(detail.contentType) }}</text>
          <text v-if="detail.collectionName" class="meta-text">{{ detail.collectionName }}</text>
          <text v-if="detail.authorName" class="meta-text">{{ detail.authorName }}</text>
          <text v-if="formatCourseDuration(detail.videoDurationSeconds)" class="meta-text">
            {{ formatCourseDuration(detail.videoDurationSeconds) }}
          </text>
        </view>
      </view>

      <!--
        视频。uni-app 的 <video> 在 H5 上渲染成原生 <video>：
        show-fullscreen-btn 留着（横屏看课体验更好），download 本来就不提供。
        进度拖动依赖 Range 请求，COS 的签名 GET 支持，所以可以拖。
      -->
      <video
        v-if="isVideo && detail.videoUrl"
        class="player"
        :src="detail.videoUrl"
        :show-center-play-btn="true"
        :enable-progress-gesture="true"
        object-fit="contain"
        controls
      />

      <view v-if="detail.summary" class="summary">
        <text>{{ detail.summary }}</text>
      </view>

      <!--
        正文。v-html 而不是 rich-text：rich-text 会把链接的 href 过滤掉
        （见 utils/courseMarkdown.ts 的说明）。内容已由 DOMPurify 净化。
        no-copy 这个类只是劝退，绕过成本极低，别当安全边界。
      -->
      <view v-if="bodyHtml" class="body no-copy" v-html="bodyHtml" />
      <view v-else-if="!isVideo" class="state">这节课还没有正文</view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.detail-page {
  min-height: 100vh;
  padding: 0 $ds-space-4 $ds-space-8;
  background: $ds-white;
}

.topbar {
  height: 96rpx;
  display: flex;
  align-items: center;
}
.back {
  display: flex;
  align-items: center;
  gap: $ds-space-1;
  color: $ds-graphite;
  @include ds-body-2;
}
.back svg {
  width: 34rpx;
  height: 34rpx;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.head {
  padding: $ds-space-2 0 $ds-space-4;
}
.title {
  display: block;
  color: $ds-ink;
  @include ds-h3;
  font-weight: 600;
}
.subtitle {
  display: block;
  margin-top: $ds-space-1;
  color: $ds-gray;
  @include ds-body-2;
}
.meta {
  margin-top: $ds-space-2;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: $ds-space-2;
}
.tag {
  padding: 0 $ds-space-2;
  border-radius: $ds-radius-xs;
  background: $ds-info-soft;
  color: $ds-info-ink;
  @include ds-caption;
}
.meta-text {
  color: $ds-gray;
  @include ds-caption;
}

.player {
  width: 100%;
  height: 420rpx;
  border-radius: $ds-radius-sm;
  background: #000;
}

.summary {
  margin: $ds-space-3 0;
  padding: $ds-space-3;
  border-radius: $ds-radius-sm;
  background: $ds-porcelain;
  color: $ds-graphite;
  @include ds-body-2;
}

.body {
  margin-top: $ds-space-4;
  color: $ds-graphite;
  @include ds-body-1;
}

/* 劝退层：挡住长按选中、右键与图片拖拽。可绕过，见脚本里的说明。 */
.no-copy {
  user-select: none;
  -webkit-user-select: none;
  -webkit-touch-callout: none;
}
.no-copy :deep(img) {
  max-width: 100%;
  pointer-events: none;
}

/*
 * 正文标题必须显式给字号：浏览器默认 h1 是 2em，在手机阅读视图里会比页面标题还大，
 * 一篇课程里出现两级「更大的标题」很难看。这里按 token 的层级收敛，
 * 且都不超过页面标题（ds-h3）。
 */
.body :deep(h1) {
  margin: $ds-space-4 0 $ds-space-2;
  color: $ds-ink;
  font-size: 38rpx;
  line-height: 54rpx;
  font-weight: 600;
}
.body :deep(h2) {
  margin: $ds-space-4 0 $ds-space-2;
  color: $ds-ink;
  font-size: 34rpx;
  line-height: 48rpx;
  font-weight: 600;
}
.body :deep(h3),
.body :deep(h4),
.body :deep(h5),
.body :deep(h6) {
  margin: $ds-space-3 0 $ds-space-2;
  color: $ds-ink;
  font-size: 32rpx;
  line-height: 46rpx;
  font-weight: 600;
}
.body :deep(p) {
  margin: 0 0 $ds-space-3;
}
.body :deep(ul),
.body :deep(ol) {
  margin: 0 0 $ds-space-3;
  padding-left: $ds-space-4;
}
.body :deep(blockquote) {
  margin: 0 0 $ds-space-3;
  padding: $ds-space-2 $ds-space-3;
  border-left: 6rpx solid $ds-line;
  background: $ds-porcelain;
  color: $ds-gray;
}
.body :deep(pre) {
  margin: 0 0 $ds-space-3;
  padding: $ds-space-3;
  border-radius: $ds-radius-xs;
  background: $ds-porcelain;
  overflow-x: auto;
}
.body :deep(code) {
  font-family: $ds-font-num;
}
.body :deep(a) {
  color: $ds-info-ink;
}
.body :deep(table) {
  width: 100%;
  margin-bottom: $ds-space-3;
  border-collapse: collapse;
}
.body :deep(th),
.body :deep(td) {
  padding: $ds-space-2;
  border: $ds-hairline solid $ds-line;
  text-align: left;
}

.paywall {
  margin-top: $ds-space-7;
  padding: $ds-space-5 $ds-space-4;
  display: flex;
  flex-direction: column;
  gap: $ds-space-3;
  border-radius: $ds-radius-md;
  background: $ds-porcelain;
  text-align: center;
}
.paywall-title {
  color: $ds-ink;
  @include ds-h3;
  font-weight: 600;
}
.paywall-text {
  color: $ds-gray;
  @include ds-body-2;
}
.paywall-back {
  color: $ds-gray;
  @include ds-body-2;
}

.state {
  padding: $ds-space-7 $ds-space-4;
  text-align: center;
  color: $ds-gray;
  @include ds-body-2;
}
.state-action {
  margin-top: $ds-space-2;
  color: $ds-ink;
  font-weight: 600;
}
</style>
