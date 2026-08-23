import { describe, expect, it } from 'vitest'
import {
  ALLOWED_VIDEO_CONTENT_TYPES,
  MAX_VIDEO_PARTS,
  isSupportedVideoType,
  normalizeDurationSeconds,
  overallUploadPercent,
  planVideoParts,
  probeVideoDurationSeconds,
  videoContentType,
} from './videoUpload'

const MiB = 1024 * 1024

describe('planVideoParts', () => {
  it('切成等长块，最后一块只留剩下的字节', () => {
    const parts = planVideoParts(20 * MiB, 8 * MiB)

    expect(parts).toEqual([
      { partNumber: 1, start: 0, end: 8 * MiB, size: 8 * MiB },
      { partNumber: 2, start: 8 * MiB, end: 16 * MiB, size: 8 * MiB },
      { partNumber: 3, start: 16 * MiB, end: 20 * MiB, size: 4 * MiB },
    ])
  })

  it('恰好整除时不多出一个空块', () => {
    const parts = planVideoParts(16 * MiB, 8 * MiB)

    expect(parts).toHaveLength(2)
    expect(parts[1]).toEqual({ partNumber: 2, start: 8 * MiB, end: 16 * MiB, size: 8 * MiB })
  })

  it('比一块还小的文件就是一块，覆盖整个文件', () => {
    const parts = planVideoParts(3, 8 * MiB)

    expect(parts).toEqual([{ partNumber: 1, start: 0, end: 3, size: 3 }])
  })

  it('分块号从 1 开始，块首尾相接、总长等于文件大小', () => {
    // COS 的 partNumber 从 1 开始；块之间漏一个字节，合并出来的视频就是坏的。
    const total = 7 * MiB + 13
    const parts = planVideoParts(total, MiB)

    expect(parts[0].partNumber).toBe(1)
    expect(parts.at(-1)?.end).toBe(total)
    parts.forEach((part, index) => {
      expect(part.partNumber).toBe(index + 1)
      expect(part.start).toBe(index === 0 ? 0 : parts[index - 1].end)
      expect(part.size).toBe(part.end - part.start)
    })
    expect(parts.reduce((sum, part) => sum + part.size, 0)).toBe(total)
  })

  it('空文件与非法大小返回空计划', () => {
    expect(planVideoParts(0, 8 * MiB)).toEqual([])
    expect(planVideoParts(-1, 8 * MiB)).toEqual([])
    expect(planVideoParts(Number.NaN, 8 * MiB)).toEqual([])
  })

  it('分块大小非法时直接抛错，而不是死循环', () => {
    expect(() => planVideoParts(MiB, 0)).toThrow(/分块大小/)
    expect(() => planVideoParts(MiB, -8)).toThrow(/分块大小/)
    expect(() => planVideoParts(MiB, Number.NaN)).toThrow(/分块大小/)
  })

  it('块数超过 COS 上限时抛错', () => {
    // 后端会按 8 MiB 下发分块大小，2 GiB 只有 256 块；这条挡的是分块大小被改小的情况。
    expect(() => planVideoParts((MAX_VIDEO_PARTS + 1) * 1024, 1024)).toThrow(/10000/)
    expect(planVideoParts(MAX_VIDEO_PARTS * 1024, 1024)).toHaveLength(MAX_VIDEO_PARTS)
  })

  it('2 GiB 的文件按 8 MiB 切成 256 块', () => {
    const parts = planVideoParts(2 * 1024 * MiB, 8 * MiB)

    expect(parts).toHaveLength(256)
    expect(parts.at(-1)?.end).toBe(2 * 1024 * MiB)
  })
})

describe('overallUploadPercent', () => {
  it('已完成的块加上当前块的进度', () => {
    expect(
      overallUploadPercent({
        uploadedBytes: 8 * MiB,
        currentPartLoaded: 4 * MiB,
        currentPartSize: 8 * MiB,
        totalBytes: 24 * MiB,
      }),
    ).toBe(50)
  })

  it('没开始传是 0，全部传完是 100', () => {
    expect(
      overallUploadPercent({ uploadedBytes: 0, currentPartLoaded: 0, currentPartSize: 8 * MiB, totalBytes: 24 * MiB }),
    ).toBe(0)
    expect(
      overallUploadPercent({ uploadedBytes: 24 * MiB, currentPartLoaded: 0, currentPartSize: 0, totalBytes: 24 * MiB }),
    ).toBe(100)
  })

  it('当前块的 loaded 超过块本身时夹住，不会冲过 100', () => {
    // axios 的 loaded 是整个请求体（multipart 边界 + partNumber 字段），一定比分块大。
    expect(
      overallUploadPercent({
        uploadedBytes: 16 * MiB,
        currentPartLoaded: 8 * MiB + 4096,
        currentPartSize: 8 * MiB,
        totalBytes: 24 * MiB,
      }),
    ).toBe(100)
  })

  it('向下取整，不把 99.9% 说成 100%', () => {
    expect(
      overallUploadPercent({ uploadedBytes: 999, currentPartLoaded: 0, currentPartSize: 1, totalBytes: 1000 }),
    ).toBe(99)
  })

  it('总长为 0 或非法时返回 0', () => {
    expect(
      overallUploadPercent({ uploadedBytes: 10, currentPartLoaded: 0, currentPartSize: 0, totalBytes: 0 }),
    ).toBe(0)
    expect(
      overallUploadPercent({
        uploadedBytes: Number.NaN,
        currentPartLoaded: Number.NaN,
        currentPartSize: Number.NaN,
        totalBytes: Number.NaN,
      }),
    ).toBe(0)
  })

  it('负数当 0 处理', () => {
    expect(
      overallUploadPercent({ uploadedBytes: -100, currentPartLoaded: -1, currentPartSize: 8, totalBytes: 100 }),
    ).toBe(0)
  })
})

describe('isSupportedVideoType', () => {
  it('只认后端白名单里的三种', () => {
    expect(ALLOWED_VIDEO_CONTENT_TYPES).toEqual(['video/mp4', 'video/quicktime', 'video/webm'])
    ALLOWED_VIDEO_CONTENT_TYPES.forEach((type) => expect(isSupportedVideoType(type)).toBe(true))
    expect(isSupportedVideoType('VIDEO/MP4')).toBe(true)
    expect(isSupportedVideoType('video/mp4; codecs=avc1')).toBe(true)
  })

  it('空值与其他类型一律不认', () => {
    expect(isSupportedVideoType('video/avi')).toBe(false)
    expect(isSupportedVideoType('image/png')).toBe(false)
    expect(isSupportedVideoType('')).toBe(false)
    expect(isSupportedVideoType(null)).toBe(false)
    expect(isSupportedVideoType(undefined)).toBe(false)
  })
})

describe('videoContentType', () => {
  it('优先用浏览器给的 type', () => {
    expect(videoContentType(new File(['x'], '第一课.mp4', { type: 'video/mp4' }))).toBe('video/mp4')
  })

  it('浏览器没给 type 时按扩展名兜底', () => {
    // macOS 上选 .mov 有时拿到空 type，按扩展名补一个，别让用户以为格式不支持。
    expect(videoContentType(new File(['x'], '第一课.MOV', { type: '' }))).toBe('video/quicktime')
    expect(videoContentType(new File(['x'], 'a.mp4', { type: '' }))).toBe('video/mp4')
    expect(videoContentType(new File(['x'], 'a.webm', { type: '' }))).toBe('video/webm')
  })

  it('认不出来就返回空串，交给白名单去拒', () => {
    expect(videoContentType(new File(['x'], '第一课.avi', { type: '' }))).toBe('')
    expect(videoContentType(new File(['x'], '没有扩展名', { type: '' }))).toBe('')
  })
})

describe('normalizeDurationSeconds', () => {
  it('四舍五入成整秒', () => {
    expect(normalizeDurationSeconds(725.4)).toBe(725)
    expect(normalizeDurationSeconds(725.5)).toBe(726)
    expect(normalizeDurationSeconds(1)).toBe(1)
  })

  it('拿不到时长就留空——时长只登记，不校验', () => {
    // 库里只有 `> 0` 的数据合理性 CHECK，允许为空；直播流式媒体的 duration 是 Infinity。
    expect(normalizeDurationSeconds(null)).toBeNull()
    expect(normalizeDurationSeconds(undefined)).toBeNull()
    expect(normalizeDurationSeconds(Number.NaN)).toBeNull()
    expect(normalizeDurationSeconds(Number.POSITIVE_INFINITY)).toBeNull()
    expect(normalizeDurationSeconds(0)).toBeNull()
    expect(normalizeDurationSeconds(0.4)).toBeNull()
    expect(normalizeDurationSeconds(-5)).toBeNull()
  })
})

/**
 * 造一个假的 video 元素：jsdom 不会真的解码媒体，事件由测试自己触发。
 *
 * `duration` 在 HTMLVideoElement 上是只读的，而这个假元素要让用例自己写进去，
 * 所以把它从类型里摘出来重新声明成可写的。
 */
type FakeVideo = Omit<HTMLVideoElement, 'duration'> & {
  duration: number
  fire(type: string): void
}

function fakeVideo(): FakeVideo {
  const listeners: Record<string, Array<() => void>> = {}
  return {
    preload: '',
    src: '',
    duration: Number.NaN,
    addEventListener(type: string, listener: () => void) {
      ;(listeners[type] ??= []).push(listener)
    },
    fire(type: string) {
      ;(listeners[type] ?? []).forEach((listener) => listener())
    },
  } as unknown as FakeVideo
}

describe('probeVideoDurationSeconds', () => {
  it('从 loadedmetadata 读 duration，并撤销临时 URL', async () => {
    const video = fakeVideo()
    const revoked: string[] = []
    const pending = probeVideoDurationSeconds(new Blob(['x']), {
      createVideo: () => video,
      createObjectUrl: () => 'blob:fake',
      revokeObjectUrl: (url) => revoked.push(url),
    })

    video.duration = 903.6
    video.fire('loadedmetadata')

    await expect(pending).resolves.toBe(904)
    expect(video.src).toBe('blob:fake')
    expect(video.preload).toBe('metadata')
    expect(revoked).toEqual(['blob:fake'])
  })

  it('解码失败返回 null，不抛错', async () => {
    const video = fakeVideo()
    const pending = probeVideoDurationSeconds(new Blob(['x']), {
      createVideo: () => video,
      createObjectUrl: () => 'blob:fake',
      revokeObjectUrl: () => undefined,
    })

    video.fire('error')

    await expect(pending).resolves.toBeNull()
  })

  it('浏览器不支持 createObjectURL 时返回 null', async () => {
    await expect(
      probeVideoDurationSeconds(new Blob(['x']), {
        createVideo: fakeVideo,
        createObjectUrl: () => {
          throw new Error('createObjectURL is not a function')
        },
      }),
    ).resolves.toBeNull()
  })

  it('元数据迟迟不来时超时返回 null，不把上传流程卡住', async () => {
    await expect(
      probeVideoDurationSeconds(new Blob(['x']), {
        createVideo: fakeVideo,
        createObjectUrl: () => 'blob:fake',
        revokeObjectUrl: () => undefined,
        timeoutMs: 0,
      }),
    ).resolves.toBeNull()
  })

  it('只兑现一次：先超时后又来了元数据也不会改结果', async () => {
    const video = fakeVideo()
    const revoked: string[] = []
    const pending = probeVideoDurationSeconds(new Blob(['x']), {
      createVideo: () => video,
      createObjectUrl: () => 'blob:fake',
      revokeObjectUrl: (url) => revoked.push(url),
      timeoutMs: 0,
    })

    await expect(pending).resolves.toBeNull()

    video.duration = 12
    video.fire('loadedmetadata')

    await expect(pending).resolves.toBeNull()
    expect(revoked).toEqual(['blob:fake'])
  })
})
