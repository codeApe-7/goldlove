import { afterEach, describe, expect, it, vi } from 'vitest'
import * as api from './index'
import { ApiError, apiErrorCode, setUnauthorizedHandler } from './request'
import type { GuestCourseCollectionView, GuestCourseDetail, GuestCourseListItem, PageView } from '@/types'

/** 记录最后一次 uni.request 的入参，接口层的断言全落在 url / method 上。 */
function stubRequest(reply: { statusCode: number; data: unknown }) {
  const calls: UniApp.RequestOptions[] = []
  vi.stubGlobal('uni', {
    request: vi.fn((options: UniApp.RequestOptions) => {
      calls.push(options)
      options.success?.({
        statusCode: reply.statusCode,
        header: {},
        cookies: [],
        data: reply.data,
      } as UniApp.RequestSuccessCallbackResult)
      return { abort: vi.fn() }
    }),
  })
  return calls
}

function envelope<T>(data: T) {
  return { success: true, code: 'OK', message: '成功', data, requestId: 'r' }
}

const COLLECTION: GuestCourseCollectionView = {
  id: 1,
  name: '情绪与认知',
  description: '识别情绪，理解认知偏差',
  courseCount: 4,
}

const LIST_ITEM: GuestCourseListItem = {
  id: 11,
  collectionId: 1,
  collectionName: '情绪与认知',
  title: '情绪的第一课',
  subtitle: '看见情绪本身',
  summary: null,
  contentType: 'ARTICLE',
  authorName: '林老师',
  videoDurationSeconds: null,
  coverPreviewUrl: 'https://cos.example.com/cover.png?sign=1',
  publishedAt: '2026-08-20T10:00:00Z',
  locked: true,
}

const PAGE: PageView<GuestCourseListItem> = { items: [LIST_ITEM], page: 1, size: 10, total: 1 }

const DETAIL: GuestCourseDetail = {
  ...LIST_ITEM,
  locked: false,
  contentMarkdown: '# 正文',
  videoUrl: null,
}

describe('课程接口层', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    setUnauthorizedHandler(() => undefined)
    sessionStorage.clear()
  })

  it('合集列表走 GET /guest/courses/collections', async () => {
    const calls = stubRequest({ statusCode: 200, data: envelope([COLLECTION]) })

    const collections = await api.courseCollections()

    expect(calls[0]?.url).toBe('/api/v1/guest/courses/collections')
    expect(calls[0]?.method).toBeUndefined()
    expect(collections).toEqual([COLLECTION])
  })

  it('课程列表默认第 1 页，不带合集参数', async () => {
    const calls = stubRequest({ statusCode: 200, data: envelope(PAGE) })

    const page = await api.courses()

    expect(calls[0]?.url).toBe('/api/v1/guest/courses?page=1&size=10')
    expect(page.total).toBe(1)
  })

  it('筛了合集就带上 collectionId，页码与页长照传', async () => {
    const calls = stubRequest({ statusCode: 200, data: envelope(PAGE) })

    await api.courses({ collectionId: 3, page: 2, size: 20 })

    // 参数顺序固定为 collectionId → page → size，与不带合集时保持一致。
    expect(calls[0]?.url).toBe('/api/v1/guest/courses?collectionId=3&page=2&size=20')
  })

  it('合集传 null 等于不筛（不能把 "null" 拼进 query）', async () => {
    const calls = stubRequest({ statusCode: 200, data: envelope(PAGE) })

    await api.courses({ collectionId: null, page: 1, size: 10 })

    expect(calls[0]?.url).not.toContain('collectionId')
  })

  it('详情走 GET /guest/courses/{id}', async () => {
    const calls = stubRequest({ statusCode: 200, data: envelope(DETAIL) })

    const detail = await api.courseDetail(11)

    expect(calls[0]?.url).toBe('/api/v1/guest/courses/11')
    expect(detail.contentMarkdown).toBe('# 正文')
  })

  it('列表项不带正文与视频地址（后端口径，前端不要自己补）', async () => {
    stubRequest({ statusCode: 200, data: envelope(PAGE) })

    const page = await api.courses()

    expect(page.items[0]).not.toHaveProperty('contentMarkdown')
    expect(page.items[0]).not.toHaveProperty('videoUrl')
  })
})

describe('课程详情的 403 不能被当成会话失效', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    setUnauthorizedHandler(() => undefined)
  })

  it('抛出的错误带得住后端错误码', async () => {
    stubRequest({
      statusCode: 403,
      data: {
        success: false,
        code: 'COURSE_VIP_REQUIRED',
        message: '这节课需要会员权益',
        data: null,
        requestId: 'r',
      },
    })

    const cause = await api.courseDetail(11).then(() => null, (error: unknown) => error)

    expect(cause).toBeInstanceOf(ApiError)
    expect(apiErrorCode(cause)).toBe('COURSE_VIP_REQUIRED')
    expect((cause as ApiError).status).toBe(403)
    expect((cause as ApiError).message).toBe('这节课需要会员权益')
  })

  it('不会触发 401 处理器把人踢回登录页', async () => {
    // 403 + AUTH_ACCOUNT_INACTIVE 才是「账号停用」。课程门禁的 403 只是没买会员，
    // 走清会话那条路等于把人莫名其妙地登出。
    const unauthorized = vi.fn()
    setUnauthorizedHandler(unauthorized)
    stubRequest({
      statusCode: 403,
      data: {
        success: false,
        code: 'COURSE_VIP_REQUIRED',
        message: '这节课需要会员权益',
        data: null,
        requestId: 'r',
      },
    })

    await expect(api.courseDetail(11)).rejects.toThrow('这节课需要会员权益')
    expect(unauthorized).not.toHaveBeenCalled()
  })

  it('普通错误没有错误码时 apiErrorCode 给空串，不会误判成门禁', () => {
    expect(apiErrorCode(new Error('连不上服务器'))).toBe('')
    expect(apiErrorCode(null)).toBe('')
    expect(apiErrorCode(undefined)).toBe('')
    expect(apiErrorCode('COURSE_VIP_REQUIRED')).toBe('')
  })
})
