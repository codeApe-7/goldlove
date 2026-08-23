import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  useCourseVideoUpload,
  type CourseVideoUploadDeps,
} from './useCourseVideoUpload'
import * as api from '@/api/admin'

vi.mock('@/api/admin', () => ({
  startCourseVideoUpload: vi.fn(async () => ({
    uploadId: 'u-api',
    objectKey: 'course/video/api.mp4',
    partSizeBytes: 8,
  })),
  uploadCourseVideoPart: vi.fn(async (_uploadId: string, partNumber: number) => ({
    partNumber,
    etag: `"api-${partNumber}"`,
  })),
  completeCourseVideoUpload: vi.fn(async () => ({
    objectKey: 'course/video/api.mp4',
    sizeBytes: 12,
    contentType: 'video/mp4',
  })),
  abortCourseVideoUpload: vi.fn(async () => null),
}))

/** 20 字节的假视频。分块大小同样按字节给，切片数学在 utils/videoUpload.test.ts 里单独测过。 */
function videoFile(size = 20, name = '第一课.mp4', type = 'video/mp4'): File {
  return new File([new Uint8Array(size)], name, { type })
}

function deferred<T>(): { promise: Promise<T>; resolve(value: T): void; reject(reason: unknown): void } {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

/** 把所有已排队的微任务与 setTimeout(0) 放掉。 */
function flush(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

interface Recorder {
  parts: number[]
  completed: Array<Array<{ partNumber: number; etag: string }>>
  aborted: string[]
}

function fakeDeps(overrides: Partial<CourseVideoUploadDeps> = {}): {
  deps: CourseVideoUploadDeps
  recorder: Recorder
} {
  const recorder: Recorder = { parts: [], completed: [], aborted: [] }
  const deps: CourseVideoUploadDeps = {
    start: vi.fn(async () => ({ uploadId: 'u-1', objectKey: 'course/video/1.mp4', partSizeBytes: 8 })),
    uploadPart: vi.fn(async (_uploadId, partNumber, chunk, onProgress) => {
      recorder.parts.push(partNumber)
      onProgress(chunk.size)
      return { partNumber, etag: `"etag-${partNumber}"` }
    }),
    complete: vi.fn(async (_uploadId: string, parts: Array<{ partNumber: number; etag: string }>) => {
      recorder.completed.push(parts.map((part) => ({ ...part })))
      return { objectKey: 'course/video/1.mp4', sizeBytes: 20, contentType: 'video/mp4' }
    }),
    abort: vi.fn(async (uploadId) => {
      recorder.aborted.push(uploadId)
      return null
    }),
    probeDuration: vi.fn(async () => 725),
    ...overrides,
  }
  return { deps, recorder }
}

describe('useCourseVideoUpload', () => {
  beforeEach(() => vi.clearAllMocks())

  it('按后端下发的分块大小逐块上传，最后一块只传剩下的字节', async () => {
    const { deps, recorder } = fakeDeps()
    const upload = useCourseVideoUpload(deps)

    const result = await upload.upload(videoFile(20))

    expect(recorder.parts).toEqual([1, 2, 3])
    const chunkSizes = vi.mocked(deps.uploadPart).mock.calls.map((call) => (call[2] as Blob).size)
    expect(chunkSizes).toEqual([8, 8, 4])
    expect(deps.start).toHaveBeenCalledWith({
      filename: '第一课.mp4',
      contentType: 'video/mp4',
      totalBytes: 20,
    })
    // complete 必须按分块号顺序带上全部 etag，顺序错了 COS 合并出来的视频是坏的。
    expect(recorder.completed).toEqual([
      [
        { partNumber: 1, etag: '"etag-1"' },
        { partNumber: 2, etag: '"etag-2"' },
        { partNumber: 3, etag: '"etag-3"' },
      ],
    ])
    expect(result).toEqual({
      objectKey: 'course/video/1.mp4',
      sizeBytes: 20,
      contentType: 'video/mp4',
      durationSeconds: 725,
      filename: '第一课.mp4',
    })
    expect(upload.phase.value).toBe('done')
    expect(upload.percent.value).toBe(100)
    expect(upload.donePartCount.value).toBe(3)
    expect(upload.totalParts.value).toBe(3)
  })

  it('只有一块的文件也走同一条路', async () => {
    const { deps, recorder } = fakeDeps({
      start: vi.fn(async () => ({ uploadId: 'u-1', objectKey: 'k', partSizeBytes: 8 * 1024 * 1024 })),
    })
    const upload = useCourseVideoUpload(deps)

    await upload.upload(videoFile(5))

    expect(recorder.parts).toEqual([1])
    expect(upload.phase.value).toBe('done')
  })

  it('时长探测不到也照样上传——时长只登记，不校验', async () => {
    const { deps } = fakeDeps({ probeDuration: vi.fn(async () => null) })
    const upload = useCourseVideoUpload(deps)

    const result = await upload.upload(videoFile(20))

    expect(result?.durationSeconds).toBeNull()
    expect(upload.phase.value).toBe('done')
    expect(upload.errorMessage.value).toBe('')
  })

  it('总进度按已完成的块加当前块推进，且不冲过 100', async () => {
    const seen: number[] = []
    const { deps } = fakeDeps()
    const upload = useCourseVideoUpload(deps)
    vi.mocked(deps.uploadPart).mockImplementation(async (_uploadId, partNumber, chunk, onProgress) => {
      // axios 的 loaded 含 multipart 边界，一定比分块大；进度不能因此超过 100。
      onProgress(chunk.size + 512)
      seen.push(upload.percent.value)
      return { partNumber, etag: `"etag-${partNumber}"` }
    })

    await upload.upload(videoFile(20))

    expect(seen).toEqual([40, 80, 100])
  })

  it('某一块失败只停在那一块，重试从这一块继续而不是从头', async () => {
    const { deps, recorder } = fakeDeps()
    let failOnce = true
    vi.mocked(deps.uploadPart).mockImplementation(async (_uploadId, partNumber, _chunk, onProgress) => {
      recorder.parts.push(partNumber)
      if (partNumber === 2 && failOnce) {
        failOnce = false
        throw new Error('连不上服务器，请检查网络后重试')
      }
      onProgress(8)
      return { partNumber, etag: `"etag-${partNumber}"` }
    })
    const upload = useCourseVideoUpload(deps)

    await upload.upload(videoFile(20))

    expect(upload.phase.value).toBe('failed')
    expect(upload.errorMessage.value).toBe('连不上服务器，请检查网络后重试')
    expect(upload.donePartCount.value).toBe(1)
    expect(deps.complete).not.toHaveBeenCalled()
    // 失败时不中止会话：整段已传的分块要留着给重试用。
    expect(recorder.aborted).toEqual([])

    const result = await upload.retry()

    expect(recorder.parts).toEqual([1, 2, 2, 3])
    expect(result?.objectKey).toBe('course/video/1.mp4')
    expect(upload.phase.value).toBe('done')
    expect(recorder.completed).toEqual([
      [
        { partNumber: 1, etag: '"etag-1"' },
        { partNumber: 2, etag: '"etag-2"' },
        { partNumber: 3, etag: '"etag-3"' },
      ],
    ])
  })

  it('complete 失败时重试不重传分块', async () => {
    const { deps, recorder } = fakeDeps()
    vi.mocked(deps.complete)
      .mockRejectedValueOnce(new Error('服务出错了，请稍后重试'))
      .mockResolvedValueOnce({ objectKey: 'course/video/1.mp4', sizeBytes: 20, contentType: 'video/mp4' })
    const upload = useCourseVideoUpload(deps)

    await upload.upload(videoFile(20))
    expect(upload.phase.value).toBe('failed')
    expect(upload.errorMessage.value).toBe('服务出错了，请稍后重试')

    await upload.retry()

    expect(recorder.parts).toEqual([1, 2, 3])
    expect(deps.complete).toHaveBeenCalledTimes(2)
    expect(upload.phase.value).toBe('done')
  })

  it('开会话就失败时，重试是整件事重来', async () => {
    const { deps, recorder } = fakeDeps()
    vi.mocked(deps.start)
      .mockRejectedValueOnce(new Error('没有权限执行这个操作'))
      .mockResolvedValueOnce({ uploadId: 'u-2', objectKey: 'k', partSizeBytes: 8 })
    const upload = useCourseVideoUpload(deps)

    await upload.upload(videoFile(20))
    expect(upload.phase.value).toBe('failed')
    expect(upload.errorMessage.value).toBe('没有权限执行这个操作')
    expect(recorder.parts).toEqual([])

    await upload.retry()

    expect(deps.start).toHaveBeenCalledTimes(2)
    expect(recorder.parts).toEqual([1, 2, 3])
    expect(upload.phase.value).toBe('done')
  })

  it('取消时中止会话，并且不再往下传', async () => {
    const { deps, recorder } = fakeDeps()
    const pending = deferred<{ partNumber: number; etag: string }>()
    vi.mocked(deps.uploadPart).mockImplementation((_uploadId, partNumber) => {
      recorder.parts.push(partNumber)
      return pending.promise
    })
    const upload = useCourseVideoUpload(deps)

    const running = upload.upload(videoFile(20))
    await flush()
    expect(upload.phase.value).toBe('uploading')

    await upload.cancel()
    pending.resolve({ partNumber: 1, etag: '"etag-1"' })
    await expect(running).resolves.toBeNull()

    expect(recorder.aborted).toEqual(['u-1'])
    expect(recorder.parts).toEqual([1])
    expect(deps.complete).not.toHaveBeenCalled()
    expect(upload.phase.value).toBe('idle')
    expect(upload.percent.value).toBe(0)
    expect(upload.result.value).toBeNull()
  })

  it('传完之后取消不再调 abort——会话已经结束了', async () => {
    const { deps, recorder } = fakeDeps()
    const upload = useCourseVideoUpload(deps)

    await upload.upload(videoFile(20))
    await upload.cancel()

    expect(recorder.aborted).toEqual([])
    expect(upload.phase.value).toBe('idle')
  })

  it('失败后换一个文件，先把上一个会话中止掉', async () => {
    const { deps, recorder } = fakeDeps()
    vi.mocked(deps.uploadPart).mockRejectedValueOnce(new Error('连不上服务器，请检查网络后重试'))
    vi.mocked(deps.start)
      .mockResolvedValueOnce({ uploadId: 'u-1', objectKey: 'k1', partSizeBytes: 8 })
      .mockResolvedValueOnce({ uploadId: 'u-2', objectKey: 'k2', partSizeBytes: 8 })
    const upload = useCourseVideoUpload(deps)

    await upload.upload(videoFile(20, '旧课.mp4'))
    expect(upload.phase.value).toBe('failed')

    await upload.upload(videoFile(20, '新课.mp4'))

    expect(recorder.aborted).toEqual(['u-1'])
    expect(upload.filename.value).toBe('新课.mp4')
    expect(upload.phase.value).toBe('done')
  })

  it('中止请求本身失败也不拦住界面', async () => {
    const { deps } = fakeDeps({ abort: vi.fn(async () => { throw new Error('连不上服务器') }) })
    vi.mocked(deps.uploadPart).mockRejectedValueOnce(new Error('传不上去'))
    const upload = useCourseVideoUpload(deps)

    await upload.upload(videoFile(20))
    await expect(upload.cancel()).resolves.toBeUndefined()

    expect(upload.phase.value).toBe('idle')
  })

  it('格式不在白名单里就不开会话', async () => {
    const { deps } = fakeDeps()
    const upload = useCourseVideoUpload(deps)

    await expect(upload.upload(videoFile(20, '第一课.avi', 'video/x-msvideo'))).resolves.toBeNull()

    expect(deps.start).not.toHaveBeenCalled()
    expect(upload.phase.value).toBe('failed')
    expect(upload.errorMessage.value).toContain('MP4')
  })

  it('空文件不开会话', async () => {
    const { deps } = fakeDeps()
    const upload = useCourseVideoUpload(deps)

    await expect(upload.upload(videoFile(0))).resolves.toBeNull()

    expect(deps.start).not.toHaveBeenCalled()
    expect(upload.phase.value).toBe('failed')
    expect(upload.errorMessage.value).toContain('空')
  })

  it('分块大小算不出切片计划时报错而不是死循环', async () => {
    const { deps } = fakeDeps({
      start: vi.fn(async () => ({ uploadId: 'u-1', objectKey: 'k', partSizeBytes: 0 })),
    })
    const upload = useCourseVideoUpload(deps)

    await expect(upload.upload(videoFile(20))).resolves.toBeNull()

    expect(upload.phase.value).toBe('failed')
    expect(upload.errorMessage.value).toContain('分块大小')
    expect(deps.uploadPart).not.toHaveBeenCalled()
  })

  it('上传途中又选了别的文件时，先开的那个会话被中止且不会 complete', async () => {
    const { deps, recorder } = fakeDeps()
    const firstPart = deferred<{ partNumber: number; etag: string }>()
    vi.mocked(deps.start)
      .mockResolvedValueOnce({ uploadId: 'u-1', objectKey: 'k1', partSizeBytes: 8 })
      .mockResolvedValueOnce({ uploadId: 'u-2', objectKey: 'k2', partSizeBytes: 8 })
    vi.mocked(deps.uploadPart).mockImplementationOnce((_uploadId, partNumber) => {
      recorder.parts.push(partNumber)
      return firstPart.promise
    })
    const upload = useCourseVideoUpload(deps)

    const first = upload.upload(videoFile(20, '旧课.mp4'))
    await flush()
    const second = upload.upload(videoFile(20, '新课.mp4'))
    firstPart.resolve({ partNumber: 1, etag: '"etag-1"' })

    await expect(first).resolves.toBeNull()
    const result = await second


    expect(recorder.aborted).toEqual(['u-1'])
    expect(recorder.completed).toHaveLength(1)
    expect(result?.filename).toBe('新课.mp4')
    expect(upload.phase.value).toBe('done')
  })

  it('不传依赖时接的就是 api/admin 那几个函数', async () => {
    const upload = useCourseVideoUpload()

    const result = await upload.upload(videoFile(12))

    expect(api.startCourseVideoUpload).toHaveBeenCalledWith({
      filename: '第一课.mp4',
      contentType: 'video/mp4',
      totalBytes: 12,
    })
    expect(api.uploadCourseVideoPart).toHaveBeenCalledTimes(2)
    expect(api.completeCourseVideoUpload).toHaveBeenCalledWith('u-api', [
      { partNumber: 1, etag: '"api-1"' },
      { partNumber: 2, etag: '"api-2"' },
    ])
    expect(result?.objectKey).toBe('course/video/api.mp4')
  })
})
