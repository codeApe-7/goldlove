/**
 * 视频分块上传里「能算清楚」的那部分：切片计划、总进度、时长探测。
 *
 * 单独抽成纯函数是因为这几处的边界最容易错——末块少算一个字节，合并出来的视频就是坏的；
 * 进度不夹住上界，进度条会冲到 120%。上传编排与状态在
 * `composables/useCourseVideoUpload.ts`，接口调用在 `api/admin.ts`。
 */

/** COS Multipart Upload 的分块数上限。超了要报错，不能静默截断成前 10000 块。 */
export const MAX_VIDEO_PARTS = 10_000

/**
 * 视频类型白名单，与后端 course 模块一致。
 * 前端这道只是少一次白跑的上传——后端不信客户端声明的 Content-Type，会自己再验一遍。
 */
export const ALLOWED_VIDEO_CONTENT_TYPES = ['video/mp4', 'video/quicktime', 'video/webm'] as const

/** 一块分块的位置。start / end 直接喂给 `File.prototype.slice`。 */
export interface VideoPartPlan {
  /** COS 的分块号从 1 开始，不是 0。 */
  partNumber: number
  start: number
  end: number
  size: number
}

/**
 * 按后端下发的 `partSizeBytes` 排出切片计划。
 * 分块大小由后端决定（它压在 multipart 上限之下），前端不自己挑。
 */
export function planVideoParts(totalBytes: number, partSizeBytes: number): VideoPartPlan[] {
  const partSize = Math.floor(partSizeBytes)
  if (!Number.isFinite(partSize) || partSize <= 0) {
    throw new Error('分块大小必须是正整数，请刷新后重试')
  }
  const total = Math.floor(totalBytes)
  if (!Number.isFinite(total) || total <= 0) {
    return []
  }
  const count = Math.ceil(total / partSize)
  if (count > MAX_VIDEO_PARTS) {
    throw new Error(`按 ${partSize} 字节切片会产生 ${count} 块，超过分块上限 ${MAX_VIDEO_PARTS} 块`)
  }
  const parts: VideoPartPlan[] = []
  for (let index = 0; index < count; index += 1) {
    const start = index * partSize
    const end = Math.min(start + partSize, total)
    parts.push({ partNumber: index + 1, start, end, size: end - start })
  }
  return parts
}

/**
 * 总进度百分比 = 已传完的块 + 当前块传了多少。
 *
 * `currentPartLoaded` 必须拿 `currentPartSize` 夹住：axios 的 `loaded` 算的是整个请求体，
 * 含 multipart 边界与 partNumber 字段，一定比分块本身大。
 */
export function overallUploadPercent(input: {
  uploadedBytes: number
  currentPartLoaded: number
  currentPartSize: number
  totalBytes: number
}): number {
  const total = positive(input.totalBytes)
  if (total <= 0) {
    return 0
  }
  const currentPartSize = positive(input.currentPartSize)
  const current = Math.min(positive(input.currentPartLoaded), currentPartSize)
  const sent = Math.min(positive(input.uploadedBytes) + current, total)
  return Math.floor((sent / total) * 100)
}

function positive(value: number): number {
  return Number.isFinite(value) && value > 0 ? value : 0
}

/** 类型串可能带参数（`video/mp4; codecs=avc1`），比对前先切掉分号后面的部分。 */
export function isSupportedVideoType(contentType: string | null | undefined): boolean {
  if (!contentType) {
    return false
  }
  const essence = contentType.split(';')[0].trim().toLowerCase()
  return (ALLOWED_VIDEO_CONTENT_TYPES as readonly string[]).includes(essence)
}

/** 扩展名 → 类型。浏览器偶尔给不出 File.type（macOS 上选 .mov 就会），按扩展名补一个。 */
const CONTENT_TYPE_BY_EXTENSION: Record<string, string> = {
  mp4: 'video/mp4',
  mov: 'video/quicktime',
  webm: 'video/webm',
}

/** 取文件的视频类型：先信浏览器，浏览器不给就看扩展名，都认不出返回空串让白名单去拒。 */
export function videoContentType(file: File): string {
  if (file.type) {
    return file.type
  }
  const parts = file.name.split('.')
  if (parts.length < 2) {
    return ''
  }
  return CONTENT_TYPE_BY_EXTENSION[parts.pop()?.toLowerCase() ?? ''] ?? ''
}

/**
 * 把 `<video>` 读出来的秒数收成整秒。
 *
 * 拿不到就返回 null：时长只是展示用的登记项，不做任何校验，库里也允许为空
 * （只有一条 `> 0` 的数据合理性 CHECK）。流式媒体的 duration 是 Infinity，也归到留空。
 */
export function normalizeDurationSeconds(raw: number | null | undefined): number | null {
  if (raw === null || raw === undefined || !Number.isFinite(raw)) {
    return null
  }
  const rounded = Math.round(raw)
  return rounded > 0 ? rounded : null
}

export interface DurationProbeDeps {
  createVideo?: () => HTMLVideoElement
  createObjectUrl?: (blob: Blob) => string
  revokeObjectUrl?: (url: string) => void
  /** 元数据迟迟不来时的兜底。时长不重要，不能为它把上传流程卡住。 */
  timeoutMs?: number
}

/**
 * 用 `<video>` 元素的 loadedmetadata 事件读时长。
 * 任何失败都兑现成 null——上传照常进行，只是这条课没有时长。
 */
export function probeVideoDurationSeconds(
  file: Blob,
  deps: DurationProbeDeps = {},
): Promise<number | null> {
  const createVideo = deps.createVideo ?? (() => document.createElement('video'))
  const createObjectUrl = deps.createObjectUrl ?? ((blob: Blob) => URL.createObjectURL(blob))
  const revokeObjectUrl = deps.revokeObjectUrl ?? ((url: string) => URL.revokeObjectURL(url))
  const timeoutMs = deps.timeoutMs ?? 8_000

  return new Promise<number | null>((resolve) => {
    let objectUrl: string | null = null
    let settled = false

    const finish = (seconds: number | null): void => {
      if (settled) {
        return
      }
      settled = true
      if (objectUrl !== null) {
        try {
          revokeObjectUrl(objectUrl)
        } catch {
          // 撤销失败只是漏一个临时 URL，不影响结果。
        }
      }
      resolve(seconds)
    }

    const timer = setTimeout(() => finish(null), timeoutMs)
    const done = (seconds: number | null): void => {
      clearTimeout(timer)
      finish(seconds)
    }

    try {
      objectUrl = createObjectUrl(file)
      const video = createVideo()
      video.addEventListener('loadedmetadata', () => done(normalizeDurationSeconds(video.duration)))
      video.addEventListener('error', () => done(null))
      video.preload = 'metadata'
      video.src = objectUrl
    } catch {
      // jsdom 与老浏览器没有 createObjectURL：拿不到时长就留空。
      done(null)
    }
  })
}
