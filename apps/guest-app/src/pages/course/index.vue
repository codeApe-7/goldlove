<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { onReachBottom } from '@dcloudio/uni-app'
import { useCourseStore } from '@/stores/course'
import { courseEmptyMessage, courseTypeLabel, formatCourseDuration } from '@/utils/course'
import ArchiveTabBar from '@/components/ArchiveTabBar.vue'
import type { GuestCourseListItem } from '@/types'

const course = useCourseStore()

const showEmpty = computed(
  () => !course.listLoading && course.items.length === 0 && course.listError === '',
)

onMounted(async () => {
  await course.loadCollections()
  await course.refresh()
})

// 列表页是 tabBar 页，下拉到底继续拉——H5 上没有翻页器。
onReachBottom(() => {
  if (course.hasMore) {
    void course.loadMore()
  }
})

function openCourse(item: GuestCourseListItem): void {
  // 锁着的课也让它进详情页：详情页会显示升级引导，比在列表上弹一个 toast 更说得清。
  uni.navigateTo({ url: `/pages/course/detail?id=${item.id}` })
}
</script>

<template>
  <view class="archive-page course-page">
    <!-- 不再放页内大标题：档案页与我的页都靠原生导航栏的标题，这里保持一致，
         只留一句说明本页规则的副标题。 -->
    <view class="page-head">
      <text class="page-subtitle">按合集循序渐进，会员可看全部正文与视频</text>
    </view>

    <!-- 合集筛选：横向滚动，第一项是「全部」 -->
    <scroll-view class="collection-bar" scroll-x :show-scrollbar="false">
      <view class="collection-row">
        <view
          class="collection-chip"
          :class="{ 'is-active': course.activeCollectionId === null }"
          @tap="course.selectCollection(null)"
        >
          全部
        </view>
        <view
          v-for="item in course.collections"
          :key="item.id"
          class="collection-chip"
          :class="{ 'is-active': course.activeCollectionId === item.id }"
          @tap="course.selectCollection(item.id)"
        >
          {{ item.name }}
        </view>
      </view>
    </scroll-view>

    <view v-if="course.listError" class="notice is-error">
      <text>{{ course.listError }}</text>
      <text class="notice-action" @tap="course.refresh()">重试</text>
    </view>

    <view class="course-list">
      <view
        v-for="item in course.items"
        :key="item.id"
        class="course-card"
        @tap="openCourse(item)"
      >
        <view class="cover">
          <image
            v-if="item.coverPreviewUrl"
            class="cover-image"
            :src="item.coverPreviewUrl"
            mode="aspectFill"
          />
          <view v-else class="cover-placeholder">
            <text>{{ courseTypeLabel(item.contentType) }}</text>
          </view>
          <!-- 锁标只是提示；真正的门禁在详情接口上 -->
          <view v-if="item.locked" class="lock" aria-label="需要会员">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M7 10.5V8a5 5 0 0 1 10 0v2.5" />
              <rect x="5" y="10.5" width="14" height="9.5" rx="2" class="lock-body" />
            </svg>
          </view>
        </view>

        <view class="card-body">
          <text class="card-title">{{ item.title }}</text>
          <text v-if="item.subtitle" class="card-subtitle">{{ item.subtitle }}</text>
          <view class="card-meta">
            <text class="tag">{{ courseTypeLabel(item.contentType) }}</text>
            <text v-if="item.collectionName" class="meta-text">{{ item.collectionName }}</text>
            <text v-if="formatCourseDuration(item.videoDurationSeconds)" class="meta-text">
              {{ formatCourseDuration(item.videoDurationSeconds) }}
            </text>
            <text v-if="item.authorName" class="meta-text">{{ item.authorName }}</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="course.listLoading" class="hint">正在加载…</view>
    <view v-else-if="showEmpty" class="empty">
      <text>{{ courseEmptyMessage(course.activeCollectionName) }}</text>
    </view>
    <view v-else-if="!course.hasMore && course.items.length > 0" class="hint">没有更多了</view>

    <ArchiveTabBar current="course" />
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

/*
 * 布局全部交给全局 .archive-page（theme.scss）：它给的是 margin: 0 auto、
 * 桌面宽度下的 max-width + 阴影，以及给 tabBar 留位的下内边距。
 * 档案页、我的页、会员页都用它——这里自己写一套 padding 与 background 就会
 * 在桌面上铺满整屏，和 tabBar 那条居中的 430px 对不上。本页只补自己的差异。
 */

.page-head {
  padding: $ds-space-3 0 $ds-space-4;
}
.page-subtitle {
  display: block;
  color: $ds-gray;
  @include ds-caption;
}

/*
 * 合集筛选条要贴着屏幕边缘滚动，而 .archive-page 有左右内边距。
 * 用负 margin 抵掉再把 padding 加回来，是「在有内边距的容器里做通栏滚动」的常规做法——
 * 比把整页内边距清零、再给每个兄弟节点补一遍要稳，也不会被后面的 padding 简写覆盖掉。
 */
.collection-bar {
  width: auto;
  margin: 0 (-$ds-space-4) $ds-space-4;
  padding: 0 $ds-space-4;
  white-space: nowrap;
}
.collection-row {
  display: inline-flex;
  gap: $ds-space-2;
  /* 滚到最右端时最后一个标签不要贴着屏幕边 */
  padding: 0 $ds-space-4 $ds-space-1 0;
}
.collection-chip {
  padding: 0 $ds-space-3;
  height: $ds-control-height-sm;
  display: inline-flex;
  align-items: center;
  border: $ds-hairline solid $ds-line;
  border-radius: $ds-radius-pill;
  background: $ds-white;
  color: $ds-graphite;
  @include ds-body-2;
}
.collection-chip.is-active {
  border-color: $ds-ink;
  background: $ds-ink;
  color: $ds-white;
  font-weight: 500;
}

.notice {
  margin-bottom: $ds-space-3;
  padding: $ds-space-3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-radius: $ds-radius-sm;
  @include ds-body-2;
}
.notice.is-error {
  background: $ds-error-soft;
  color: $ds-error-ink;
}
.notice-action {
  font-weight: 600;
}

.course-list {
  display: flex;
  flex-direction: column;
  gap: $ds-space-3;
}

.course-card {
  display: flex;
  gap: $ds-space-3;
  padding: $ds-space-3;
  border-radius: $ds-radius-md;
  background: $ds-white;
  box-shadow: $ds-shadow-soft;
}
.course-card:active {
  opacity: 0.7;
}

.cover {
  position: relative;
  flex-shrink: 0;
  width: 200rpx;
  height: 140rpx;
  border-radius: $ds-radius-sm;
  overflow: hidden;
  background: $ds-porcelain;
}
.cover-image {
  width: 100%;
  height: 100%;
}
.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $ds-gray;
  @include ds-caption;
}
.lock {
  position: absolute;
  top: $ds-space-1;
  right: $ds-space-1;
  width: 44rpx;
  height: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $ds-radius-pill;
  background: rgba(14, 15, 18, 0.62);
}
.lock svg {
  width: 26rpx;
  height: 26rpx;
  fill: none;
  stroke: $ds-white;
  stroke-width: 2;
  stroke-linecap: round;
}
.lock .lock-body {
  fill: $ds-white;
  stroke: none;
}

.card-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: $ds-space-1;
}
.card-title {
  color: $ds-ink;
  font-weight: 500;
  @include ds-body-1;
  @include ds-truncate;
}
.card-subtitle {
  color: $ds-gray;
  @include ds-body-2;
  @include ds-truncate;
}
.card-meta {
  margin-top: auto;
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

.hint,
.empty {
  padding: $ds-space-5 $ds-space-4;
  text-align: center;
  color: $ds-gray;
  @include ds-body-2;
}
</style>
