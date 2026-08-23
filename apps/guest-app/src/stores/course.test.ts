import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useCourseStore, VIP_REQUIRED_CODE } from './course'
import { ApiError } from '@/api/request'
import * as api from '@/api'
import type { GuestCourseListItem } from '@/types'

vi.mock('@/api', () => ({
  courseCollections: vi.fn(),
  courses: vi.fn(),
  courseDetail: vi.fn(),
}))

function listItem(id: number, locked = true): GuestCourseListItem {
  return {
    id,
    collectionId: 1,
    collectionName: '情绪与认知',
    title: `第 ${id} 课`,
    subtitle: null,
    summary: null,
    contentType: 'ARTICLE',
    authorName: null,
    videoDurationSeconds: null,
    coverPreviewUrl: null,
    publishedAt: null,
    locked,
  }
}

describe('课程 store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('分页是往后拼接，不是替换', async () => {
    vi.mocked(api.courses)
      .mockResolvedValueOnce({ items: [listItem(1)], page: 1, size: 10, total: 2 })
      .mockResolvedValueOnce({ items: [listItem(2)], page: 2, size: 10, total: 2 })
    const store = useCourseStore()

    await store.loadMore()
    expect(store.items.map((item) => item.id)).toEqual([1])
    expect(store.hasMore).toBe(true)

    await store.loadMore()
    expect(store.items.map((item) => item.id)).toEqual([1, 2])
    expect(store.hasMore).toBe(false)
  })

  it('拉满之后不再发请求', async () => {
    vi.mocked(api.courses).mockResolvedValue({
      items: [listItem(1)], page: 1, size: 10, total: 1,
    })
    const store = useCourseStore()

    await store.loadMore()
    await store.loadMore()

    expect(api.courses).toHaveBeenCalledTimes(1)
  })

  it('切合集从第一页重新拉', async () => {
    vi.mocked(api.courses).mockResolvedValue({
      items: [listItem(1)], page: 1, size: 10, total: 1,
    })
    const store = useCourseStore()
    await store.loadMore()

    await store.selectCollection(3)

    expect(api.courses).toHaveBeenLastCalledWith({ collectionId: 3, page: 1, size: 10 })
    expect(store.items.map((item) => item.id)).toEqual([1])
  })

  it('选同一个合集不重复请求', async () => {
    vi.mocked(api.courses).mockResolvedValue({ items: [], page: 1, size: 10, total: 0 })
    const store = useCourseStore()

    await store.selectCollection(null)

    expect(api.courses).not.toHaveBeenCalled()
  })

  it('免费账号的 403 落到升级引导，不算错误', async () => {
    // 这一条是门禁在前端的落点：报错弹窗会让用户以为是 bug，
    // 而实际上他要做的事是去开会员。
    vi.mocked(api.courseDetail).mockRejectedValue(
      new ApiError('这节课需要会员才能观看', 403, VIP_REQUIRED_CODE),
    )
    const store = useCourseStore()

    await store.loadDetail(11)

    expect(store.detailLocked).toBe(true)
    expect(store.detailError).toBe('')
    expect(store.detail).toBeNull()
  })

  it('其余失败照常报错', async () => {
    vi.mocked(api.courseDetail).mockRejectedValue(new Error('连不上服务器，请检查网络后重试'))
    const store = useCourseStore()

    await store.loadDetail(11)

    expect(store.detailLocked).toBe(false)
    expect(store.detailError).toBe('连不上服务器，请检查网络后重试')
  })

  it('会员能拿到正文', async () => {
    vi.mocked(api.courseDetail).mockResolvedValue({
      ...listItem(11, false),
      contentMarkdown: '# 正文',
      videoUrl: null,
    })
    const store = useCourseStore()

    await store.loadDetail(11)

    expect(store.detail?.contentMarkdown).toBe('# 正文')
    expect(store.detailLocked).toBe(false)
  })

  it('合集拉失败不影响课程列表', async () => {
    vi.mocked(api.courseCollections).mockRejectedValue(new Error('合集炸了'))
    vi.mocked(api.courses).mockResolvedValue({
      items: [listItem(1)], page: 1, size: 10, total: 1,
    })
    const store = useCourseStore()

    await store.loadCollections()
    await store.loadMore()

    // 不筛合集就是全部，列表本身不依赖合集接口。
    expect(store.items).toHaveLength(1)
  })
})
