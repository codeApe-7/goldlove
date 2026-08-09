import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from '@/api/http'
import { approveReview, listReviews, rejectReview, reviewDetail } from '@/api/admin'

describe('review api', () => {
  afterEach(() => vi.restoreAllMocks())

  function ok(): never {
    return {
      data: { success: true, code: 'OK', message: '成功', data: {}, requestId: 'r' },
    } as never
  }

  it('listReviews passes filters and pagination', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(ok())
    await listReviews({ page: 2, size: 20, status: 'PENDING', deadline: 'OVERDUE' })
    expect(http.get).toHaveBeenCalledWith('/admin/profile-reviews', {
      params: { page: 2, size: 20, status: 'PENDING', deadline: 'OVERDUE' },
    })
  })

  it('reviewDetail targets revision', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(ok())
    await reviewDetail(7)
    expect(http.get).toHaveBeenCalledWith('/admin/profile-reviews/7')
  })

  it('approveReview posts expectedVersion', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(ok())
    await approveReview(7, 0)
    expect(http.post).toHaveBeenCalledWith('/admin/profile-reviews/7/approve', {
      expectedVersion: 0,
    })
  })

  it('rejectReview posts reason and comment', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(ok())
    await rejectReview(7, 0, 'CONTENT_INCOMPLETE', '请补充职业信息')
    expect(http.post).toHaveBeenCalledWith('/admin/profile-reviews/7/reject', {
      expectedVersion: 0,
      reasonCode: 'CONTENT_INCOMPLETE',
      comment: '请补充职业信息',
    })
  })
})
