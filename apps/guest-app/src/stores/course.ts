import { defineStore } from 'pinia'
import * as api from '@/api'
import { apiErrorCode } from '@/api/request'
import type { GuestCourseCollectionView, GuestCourseDetail, GuestCourseListItem } from '@/types'

const PAGE_SIZE = 10

/** 后端在免费账号取详情时返回的错误码。 */
export const VIP_REQUIRED_CODE = 'COURSE_VIP_REQUIRED'

interface CourseState {
  collections: GuestCourseCollectionView[]
  /** null = 全部目录。 */
  activeCollectionId: number | null
  items: GuestCourseListItem[]
  page: number
  total: number
  listLoading: boolean
  listError: string
  detail: GuestCourseDetail | null
  detailLoading: boolean
  detailError: string
  /** 详情因为「不是会员」而打不开。这不是错误，是要引导升级的正常分支。 */
  detailLocked: boolean
}

export const useCourseStore = defineStore('guest-course', {
  state: (): CourseState => ({
    collections: [],
    activeCollectionId: null,
    items: [],
    page: 0,
    total: 0,
    listLoading: false,
    listError: '',
    detail: null,
    detailLoading: false,
    detailError: '',
    detailLocked: false,
  }),
  getters: {
    /** 还有没有下一页，用于 onReachBottom 决定要不要继续拉。 */
    hasMore: (state) => state.items.length < state.total,
    activeCollectionName: (state) =>
      state.collections.find((item) => item.id === state.activeCollectionId)?.name ?? null,
  },
  actions: {
    async loadCollections(): Promise<void> {
      try {
        this.collections = await api.courseCollections()
      } catch (error) {
        // 合集拉不到不该把整页打死：课程列表本身不依赖它（不筛就是全部）。
        this.listError = error instanceof Error ? error.message : '合集加载失败'
      }
    },

    /** 从第一页重新拉。切换合集与下拉刷新都走这里。 */
    async refresh(): Promise<void> {
      this.items = []
      this.page = 0
      this.total = 0
      await this.loadMore()
    },

    async loadMore(): Promise<void> {
      if (this.listLoading) {
        return
      }
      if (this.page > 0 && this.items.length >= this.total) {
        return
      }
      this.listLoading = true
      this.listError = ''
      try {
        const next = this.page + 1
        const page = await api.courses({
          collectionId: this.activeCollectionId,
          page: next,
          size: PAGE_SIZE,
        })
        // 拼接而不是替换：H5 上是「加载更多」而不是翻页器。
        this.items = next === 1 ? page.items : [...this.items, ...page.items]
        this.page = next
        this.total = page.total
      } catch (error) {
        this.listError = error instanceof Error ? error.message : '课程加载失败'
      } finally {
        this.listLoading = false
      }
    },

    async selectCollection(collectionId: number | null): Promise<void> {
      if (this.activeCollectionId === collectionId) {
        return
      }
      this.activeCollectionId = collectionId
      await this.refresh()
    },

    /**
     * 拉详情。
     *
     * <p>403 `COURSE_VIP_REQUIRED` 不是错误，是「这节课要会员」——落到
     * {@link CourseState.detailLocked}，页面据此显示升级引导而不是红色报错。
     * 其余失败照常进 {@link CourseState.detailError}。</p>
     */
    async loadDetail(courseId: number): Promise<void> {
      this.detailLoading = true
      this.detailError = ''
      this.detailLocked = false
      this.detail = null
      try {
        this.detail = await api.courseDetail(courseId)
      } catch (error) {
        if (apiErrorCode(error) === VIP_REQUIRED_CODE) {
          this.detailLocked = true
        } else {
          this.detailError = error instanceof Error ? error.message : '课程加载失败'
        }
      } finally {
        this.detailLoading = false
      }
    },

    clearDetail(): void {
      this.detail = null
      this.detailError = ''
      this.detailLocked = false
      this.detailLoading = false
    },
  },
})
