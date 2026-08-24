import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import {
  abortCourseVideoUpload,
  activateAccount,
  archiveCourse,
  completeCourseVideoUpload,
  courseDetail,
  createCourse,
  createCourseCollection,
  deleteCourse,
  exportProfiles,
  generateActivationCode,
  listActivationCodes,
  listCourseCollections,
  listCourses,
  listPaymentOrders,
  listProfiles,
  paymentSetting,
  profileCounts,
  profileDetail,
  publishCourse,
  revokeActivationCode,
  startCourseVideoUpload,
  suspendAccount,
  updateCourse,
  updateCourseCollection,
  updatePaymentSetting,
  uploadCourseImage,
  uploadCourseMarkdown,
  uploadCourseVideoPart,
} from './admin'

describe('admin api', () => {
  afterEach(() => vi.restoreAllMocks())

  function ok(data: unknown): never {
    return {
      data: { success: true, code: 'OK', message: '成功', data, requestId: 'r' },
    } as never
  }

  it('listProfiles forwards filters as query params', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(ok({ items: [], page: 1, size: 20, total: 0 }))

    await listProfiles({ keyword: '138', status: 'DRAFT', paidOnly: true, page: 2, size: 20 })

    expect(http.get).toHaveBeenCalledWith('/admin/profiles', {
      params: { keyword: '138', status: 'DRAFT', paidOnly: true, page: 2, size: 20 },
    })
  })

  it('profileCounts hits the counts endpoint', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(
      ok({ total: 2, draft: 1, completed: 1, suspended: 0, paid: 0 }),
    )

    await expect(profileCounts({ keyword: '138' })).resolves.toMatchObject({ total: 2 })
    expect(http.get).toHaveBeenCalledWith('/admin/profiles/counts', {
      params: { keyword: '138' },
    })
  })

  it('profileDetail reads a single profile by id', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(ok({ id: 7, phone: '13800138000' }))

    await expect(profileDetail(7)).resolves.toMatchObject({ phone: '13800138000' })
    expect(http.get).toHaveBeenCalledWith('/admin/profiles/7')
  })

  it('listPaymentOrders forwards filters', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(ok({ items: [], page: 1, size: 20, total: 0 }))

    await listPaymentOrders({ status: 'PAID', page: 1, size: 20 })

    expect(http.get).toHaveBeenCalledWith('/admin/payment-orders', {
      params: { status: 'PAID', page: 1, size: 20 },
    })
  })

  it('generateActivationCode posts the bound phone and tier', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(
      ok({ id: 1, code: 'LOVE-7K2M-9XQP-4T8B', boundPhone: '13800138000' }),
    )

    await expect(
      generateActivationCode({ boundPhone: '13800138000', grantedTier: 'VIP', note: null }),
    ).resolves.toMatchObject({ code: 'LOVE-7K2M-9XQP-4T8B' })
    expect(http.post).toHaveBeenCalledWith('/admin/activation-codes', {
      boundPhone: '13800138000',
      grantedTier: 'VIP',
      note: null,
    })
  })

  it('listActivationCodes and revokeActivationCode hit the expected paths', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(ok({ items: [], page: 1, size: 20, total: 0 }))
    vi.spyOn(http, 'post').mockResolvedValue(ok(null))

    await listActivationCodes({ status: 'UNUSED', page: 1, size: 20 })
    await revokeActivationCode(42)

    expect(http.get).toHaveBeenCalledWith('/admin/activation-codes', {
      params: { status: 'UNUSED', page: 1, size: 20 },
    })
    expect(http.post).toHaveBeenCalledWith('/admin/activation-codes/42/revoke')
  })

  it('exportProfiles asks for a blob and joins ids with commas', async () => {
    const blob = new Blob(['﻿档案编号'], { type: 'text/csv' })
    vi.spyOn(http, 'get').mockResolvedValue({ data: blob } as never)

    await expect(exportProfiles({ status: 'DRAFT' }, [7, 9])).resolves.toBe(blob)
    // axios 默认会把数组序列化成 ids[]=7&ids[]=9，Spring 的 List<Long> 收不到；
    // 逗号串它会自己拆开。
    expect(http.get).toHaveBeenCalledWith('/admin/profiles/export', {
      params: { status: 'DRAFT', ids: '7,9' },
      responseType: 'blob',
    })
  })

  it('exportProfiles omits ids when nothing is selected', async () => {
    vi.spyOn(http, 'get').mockResolvedValue({ data: new Blob() } as never)

    await exportProfiles({ status: 'DRAFT' })

    expect(http.get).toHaveBeenCalledWith('/admin/profiles/export', {
      params: { status: 'DRAFT' },
      responseType: 'blob',
    })
  })

  it('suspendAccount and activateAccount post the reason', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(ok({ accountId: 3, status: 'SUSPENDED' }))

    await expect(suspendAccount(3, '资料不实')).resolves.toMatchObject({ status: 'SUSPENDED' })
    expect(http.post).toHaveBeenCalledWith('/admin/accounts/3/suspend', { reason: '资料不实' })

    await activateAccount(3, null)
    expect(http.post).toHaveBeenCalledWith('/admin/accounts/3/activate', { reason: null })
  })

  it('paymentSetting reads the current amount', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(
      ok({ vipUpgradeAmountMinor: 12_800, managedInAdmin: true }),
    )

    await expect(paymentSetting()).resolves.toMatchObject({ vipUpgradeAmountMinor: 12_800 })
    expect(http.get).toHaveBeenCalledWith('/admin/payment-settings')
  })

  it('updatePaymentSetting puts the amount in minor units', async () => {
    vi.spyOn(http, 'put').mockResolvedValue(ok({ vipUpgradeAmountMinor: 12_800 }))

    await expect(updatePaymentSetting(12_800)).resolves.toMatchObject({
      vipUpgradeAmountMinor: 12_800,
    })
    expect(http.put).toHaveBeenCalledWith('/admin/payment-settings', {
      vipUpgradeAmountMinor: 12_800,
    })
  })
})

describe('课程目录接口', () => {
  afterEach(() => vi.restoreAllMocks())

  function ok(data: unknown): never {
    return {
      data: { success: true, code: 'OK', message: '成功', data, requestId: 'r' },
    } as never
  }

  it('列表不分页，后端直接给数组', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(ok([{ id: 1, name: '情绪与认知' }]))

    await expect(listCourseCollections()).resolves.toMatchObject([{ name: '情绪与认知' }])
    expect(http.get).toHaveBeenCalledWith('/admin/course-collections')
  })

  it('新增目录 POST 到集合路径', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(ok({ id: 5, name: '形象与状态' }))

    await createCourseCollection({ name: '形象与状态', description: '穿搭与状态', sortOrder: 40 })

    expect(http.post).toHaveBeenCalledWith('/admin/course-collections', {
      name: '形象与状态',
      description: '穿搭与状态',
      sortOrder: 40,
    })
  })

  it('改目录用 PATCH，隐藏也是改 status', async () => {
    vi.spyOn(http, 'patch').mockResolvedValue(ok({ id: 5, status: 'HIDDEN' }))

    await expect(updateCourseCollection(5, { status: 'HIDDEN' })).resolves.toMatchObject({
      status: 'HIDDEN',
    })
    // 目录没有 DELETE：下面挂着课程，删了会留孤儿，隐藏走 status。
    expect(http.patch).toHaveBeenCalledWith('/admin/course-collections/5', { status: 'HIDDEN' })
  })
})

describe('课程接口', () => {
  afterEach(() => vi.restoreAllMocks())

  function ok(data: unknown): never {
    return {
      data: { success: true, code: 'OK', message: '成功', data, requestId: 'r' },
    } as never
  }

  it('列表把筛选条件当 query 下发', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(ok({ items: [], page: 1, size: 20, total: 0 }))

    await listCourses({ collectionId: 3, contentType: 'VIDEO', status: 'DRAFT', keyword: '沟通', page: 2, size: 20 })

    expect(http.get).toHaveBeenCalledWith('/admin/courses', {
      params: { collectionId: 3, contentType: 'VIDEO', status: 'DRAFT', keyword: '沟通', page: 2, size: 20 },
    })
  })

  it('详情、发布、下架、删除各自的路径与方法', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(ok({ id: 7, title: '如何识别情绪' }))
    vi.spyOn(http, 'post').mockResolvedValue(ok({ id: 7, status: 'PUBLISHED' }))
    vi.spyOn(http, 'delete').mockResolvedValue(ok(null))

    await expect(courseDetail(7)).resolves.toMatchObject({ title: '如何识别情绪' })
    await publishCourse(7)
    await archiveCourse(7)
    await deleteCourse(7)

    expect(http.get).toHaveBeenCalledWith('/admin/courses/7')
    expect(http.post).toHaveBeenCalledWith('/admin/courses/7/publish')
    expect(http.post).toHaveBeenCalledWith('/admin/courses/7/archive')
    expect(http.delete).toHaveBeenCalledWith('/admin/courses/7')
  })

  it('新建 POST 集合路径，改动 PUT 带 expectedVersion', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(ok({ id: 7, version: 0 }))
    vi.spyOn(http, 'put').mockResolvedValue(ok({ id: 7, version: 1 }))

    const draft = {
      collectionId: 3,
      title: '如何识别情绪',
      contentType: 'ARTICLE' as const,
      contentMarkdown: '# 正文',
      sortOrder: 10,
    }
    await createCourse(draft)
    await updateCourse(7, { ...draft, expectedVersion: 0 })

    expect(http.post).toHaveBeenCalledWith('/admin/courses', draft)
    // 乐观锁：版本不匹配后端返回 409 COURSE_VERSION_CONFLICT。
    expect(http.put).toHaveBeenCalledWith('/admin/courses/7', { ...draft, expectedVersion: 0 })
  })
})

describe('课程素材接口', () => {
  afterEach(() => vi.restoreAllMocks())

  function ok(data: unknown): never {
    return {
      data: { success: true, code: 'OK', message: '成功', data, requestId: 'r' },
    } as never
  }

  function lastPost(): [string, unknown, Record<string, unknown> | undefined] {
    const mock = vi.mocked(http.post)
    return mock.mock.calls.at(-1) as never
  }

  it('图片走 multipart，不手写 Content-Type', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(ok({ objectKey: 'course/img/1.png', previewUrl: 'https://cos/1.png' }))
    const file = new File(['png'], '插图.png', { type: 'image/png' })

    await expect(uploadCourseImage(file)).resolves.toMatchObject({ previewUrl: 'https://cos/1.png' })

    const [url, body, config] = lastPost()
    expect(url).toBe('/admin/course-assets/images')
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('file')).toBe(file)
    // 手写 Content-Type 会顶掉浏览器自己加的 boundary，后端直接解不出分片。
    expect(config?.headers).toBeUndefined()
  })

  it('.md 上传返回正文文本，不落库', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(ok({ content: '# 标题', sizeBytes: 7 }))
    const file = new File(['# 标题'], '课程.md', { type: 'text/markdown' })

    await expect(uploadCourseMarkdown(file)).resolves.toMatchObject({ content: '# 标题' })

    const [url, body] = lastPost()
    expect(url).toBe('/admin/course-assets/markdown')
    expect((body as FormData).get('file')).toBe(file)
  })

  it('开视频上传会话时申报文件名、类型与总大小', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(
      ok({ uploadId: 'u-1', objectKey: 'course/video/1.mp4', partSizeBytes: 8 * 1024 * 1024 }),
    )

    await expect(
      startCourseVideoUpload({ filename: '第一课.mp4', contentType: 'video/mp4', totalBytes: 12_345 }),
    ).resolves.toMatchObject({ partSizeBytes: 8 * 1024 * 1024 })

    expect(http.post).toHaveBeenCalledWith('/admin/course-assets/videos/uploads', {
      filename: '第一课.mp4',
      contentType: 'video/mp4',
      totalBytes: 12_345,
    })
  })

  it('分块带 partNumber 与文件名，并把 loaded 回吐给调用方', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(ok({ partNumber: 2, etag: '"abc"' }))
    const loaded: number[] = []

    await expect(
      uploadCourseVideoPart('u-1', 2, new Blob(['chunk']), (bytes) => loaded.push(bytes)),
    ).resolves.toEqual({ partNumber: 2, etag: '"abc"' })

    const [url, body, config] = lastPost()
    expect(url).toBe('/admin/course-assets/videos/uploads/u-1/parts')
    expect((body as FormData).get('partNumber')).toBe('2')
    // Blob 必须带文件名，否则 Spring 收到的是普通表单字段而不是 MultipartFile。
    expect((body as FormData).get('file')).toBeInstanceOf(File)
    ;(config?.onUploadProgress as (event: { loaded: number }) => void)({ loaded: 4096 })
    expect(loaded).toEqual([4096])
  })

  it('complete 带上全部分块的 etag，abort 用 DELETE', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(ok({ objectKey: 'course/video/1.mp4', sizeBytes: 99 }))
    vi.spyOn(http, 'delete').mockResolvedValue(ok(null))

    await completeCourseVideoUpload('u-1', [
      { partNumber: 1, etag: '"a"' },
      { partNumber: 2, etag: '"b"' },
    ])
    await abortCourseVideoUpload('u-1')

    expect(http.post).toHaveBeenCalledWith('/admin/course-assets/videos/uploads/u-1/complete', {
      parts: [
        { partNumber: 1, etag: '"a"' },
        { partNumber: 2, etag: '"b"' },
      ],
    })
    expect(http.delete).toHaveBeenCalledWith('/admin/course-assets/videos/uploads/u-1')
  })
})
