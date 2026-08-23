import { ref, type Ref } from 'vue'
import {
  abortCourseVideoUpload,
  completeCourseVideoUpload,
  startCourseVideoUpload,
  uploadCourseVideoPart,
} from '@/api/admin'
import {
  isSupportedVideoType,
  overallUploadPercent,
  planVideoParts,
  probeVideoDurationSeconds,
  videoContentType,
  type VideoPartPlan,
} from '@/utils/videoUpload'
import type {
  CourseVideoAsset,
  CourseVideoPartResult,
  CourseVideoUploadSession,
} from '@/types'

export type CourseVideoUploadPhase = 'idle' | 'uploading' | 'failed' | 'done'

/** 上传成功后交给表单的东西。`durationSeconds` 只登记，不参与任何校验。 */
export interface CourseVideoUploadResult {
  objectKey: string
  sizeBytes: number
  contentType: string
  durationSeconds: number | null
  filename: string
  /** 签名回放地址，供保存前预览。后端在 complete 时一并签发。 */
  previewUrl: string
}

/**
 * 外部依赖收在一个对象里，测试可以整体替掉。
 * 默认实现就是 `api/admin` 的那四个函数加上 `<video>` 时长探测。
 */
export interface CourseVideoUploadDeps {
  start(payload: {
    filename: string
    contentType: string
    totalBytes: number
  }): Promise<CourseVideoUploadSession>
  uploadPart(
    uploadId: string,
    partNumber: number,
    chunk: Blob,
    onProgress: (loadedBytes: number) => void,
  ): Promise<CourseVideoPartResult>
  complete(uploadId: string, parts: CourseVideoPartResult[]): Promise<CourseVideoAsset>
  abort(uploadId: string): Promise<null>
  probeDuration(file: Blob): Promise<number | null>
}

const defaultDeps: CourseVideoUploadDeps = {
  start: (payload) => startCourseVideoUpload(payload),
  uploadPart: (uploadId, partNumber, chunk, onProgress) =>
    uploadCourseVideoPart(uploadId, partNumber, chunk, onProgress),
  complete: (uploadId, parts) => completeCourseVideoUpload(uploadId, parts),
  abort: (uploadId) => abortCourseVideoUpload(uploadId),
  probeDuration: (file) => probeVideoDurationSeconds(file),
}

/**
 * 视频分块上传的编排与界面状态。
 *
 * <p>三个刻意的行为，都是为了「传了一半别白传」：
 * <ul>
 *   <li>某一块失败**不中止会话**——已传的分块留在 COS 上，{@link CourseVideoUpload.retry}
 *       从失败那一块继续，而不是从头再来；</li>
 *   <li>{@code complete} 失败时重试**不重传分块**，只重新合并；</li>
 *   <li>开会话就失败时没有会话可续，重试是整件事重来。</li>
 * </ul>
 * 反过来，用户主动取消或者中途换了文件，旧会话必须中止，否则 COS 上会一直躺着半截分块。</p>
 */
export function useCourseVideoUpload(deps: CourseVideoUploadDeps = defaultDeps) {
  const phase = ref<CourseVideoUploadPhase>('idle')
  const percent = ref(0)
  const errorMessage = ref('')
  const filename = ref('')
  const donePartCount = ref(0)
  const totalParts = ref(0)
  const result = ref<CourseVideoUploadResult | null>(null)

  // 下面这些不需要响应式：只有 percent 要在上传过程中被界面读到。
  let file: File | null = null
  let session: CourseVideoUploadSession | null = null
  let plans: VideoPartPlan[] = []
  let etags = new Map<number, string>()
  let uploadedBytes = 0
  let currentPartSize = 0
  let currentPartLoaded = 0
  let sessionFinished = false
  /**
   * 轮次号。每次 upload / retry / cancel 都会 +1，正在飞的那一轮发现号变了就安静退出。
   * 没有这个的话，用户取消或换文件之后，上一轮的回调还会继续推进度、甚至去 complete。
   */
  let runId = 0

  function recomputePercent(): void {
    percent.value = overallUploadPercent({
      uploadedBytes,
      currentPartLoaded,
      currentPartSize,
      totalBytes: file?.size ?? 0,
    })
  }

  /** 收拾上一轮：让它作废，并且把还没结束的会话中止掉。 */
  async function discard(): Promise<void> {
    const pendingSession = sessionFinished ? null : session
    runId += 1
    session = null
    sessionFinished = false
    if (pendingSession) {
      try {
        await deps.abort(pendingSession.uploadId)
      } catch {
        // 中止失败只是 COS 上多留一份没合并的分块，不该把界面卡在这里。
      }
    }
  }

  async function upload(selected: File): Promise<CourseVideoUploadResult | null> {
    await discard()
    const run = (runId += 1)

    file = selected
    filename.value = selected.name
    plans = []
    etags = new Map()
    uploadedBytes = 0
    currentPartSize = 0
    currentPartLoaded = 0
    donePartCount.value = 0
    totalParts.value = 0
    percent.value = 0
    result.value = null
    errorMessage.value = ''

    if (selected.size <= 0) {
      return fail('选到的视频是空文件，请重新选择')
    }
    if (!isSupportedVideoType(videoContentType(selected))) {
      return fail('只支持 MP4、MOV 与 WebM 视频')
    }
    return run_(run, true)
  }

  /**
   * 从上次断掉的地方继续。有会话就只补没传完的分块（或只重做合并），
   * 没会话说明连开会话都失败了，只能整件事重来。
   */
  async function retry(): Promise<CourseVideoUploadResult | null> {
    if (!file) {
      return null
    }
    const run = (runId += 1)
    return run_(run, session === null)
  }

  /** 用户主动取消。已经传完的会话不用中止——它已经结束了。 */
  async function cancel(): Promise<void> {
    const pendingSession = sessionFinished ? null : session
    runId += 1
    file = null
    session = null
    sessionFinished = false
    plans = []
    etags = new Map()
    uploadedBytes = 0
    currentPartSize = 0
    currentPartLoaded = 0
    phase.value = 'idle'
    percent.value = 0
    donePartCount.value = 0
    totalParts.value = 0
    result.value = null
    errorMessage.value = ''
    filename.value = ''
    if (pendingSession) {
      try {
        await deps.abort(pendingSession.uploadId)
      } catch {
        // 同 discard：中止失败不拦界面。
      }
    }
  }

  function fail(message: string): null {
    phase.value = 'failed'
    errorMessage.value = message
    return null
  }

  async function run_(
    run: number,
    openSession: boolean,
  ): Promise<CourseVideoUploadResult | null> {
    const current = file
    if (!current) {
      return null
    }
    phase.value = 'uploading'
    errorMessage.value = ''

    try {
      if (openSession) {
        const opened = await deps.start({
          filename: current.name,
          contentType: videoContentType(current),
          totalBytes: current.size,
        })
        if (run !== runId) {
          return null
        }
        session = opened
        // 分块大小由后端下发；算不出计划就直接报错，不要退化成死循环。
        plans = planVideoParts(current.size, opened.partSizeBytes)
        totalParts.value = plans.length
        etags = new Map()
        uploadedBytes = 0
        donePartCount.value = 0
      }

      const opened = session
      if (!opened) {
        return fail('上传会话已失效，请重新选择文件')
      }

      for (const plan of plans) {
        if (etags.has(plan.partNumber)) {
          continue
        }
        currentPartSize = plan.size
        currentPartLoaded = 0
        const chunk = current.slice(plan.start, plan.end)
        const part = await deps.uploadPart(
          opened.uploadId,
          plan.partNumber,
          chunk,
          (loadedBytes) => {
            if (run !== runId) {
              return
            }
            currentPartLoaded = loadedBytes
            recomputePercent()
          },
        )
        if (run !== runId) {
          return null
        }
        etags.set(plan.partNumber, part.etag)
        uploadedBytes += plan.size
        currentPartLoaded = 0
        currentPartSize = 0
        donePartCount.value = etags.size
        recomputePercent()
      }

      const orderedParts: CourseVideoPartResult[] = plans.map((plan) => ({
        partNumber: plan.partNumber,
        etag: etags.get(plan.partNumber) as string,
      }))
      const asset = await deps.complete(opened.uploadId, orderedParts)
      if (run !== runId) {
        return null
      }
      sessionFinished = true

      const durationSeconds = await deps.probeDuration(current)
      if (run !== runId) {
        return null
      }

      const value: CourseVideoUploadResult = {
        objectKey: asset.objectKey,
        sizeBytes: asset.sizeBytes,
        contentType: asset.contentType,
        durationSeconds,
        filename: current.name,
        previewUrl: asset.previewUrl,
      }
      result.value = value
      phase.value = 'done'
      percent.value = 100
      return value
    } catch (error) {
      // 已经被新一轮取代的失败不该覆盖界面状态。
      if (run !== runId) {
        return null
      }
      return fail(error instanceof Error ? error.message : '视频上传失败')
    }
  }

  return {
    phase: phase as Ref<CourseVideoUploadPhase>,
    percent,
    errorMessage,
    filename,
    donePartCount,
    totalParts,
    result,
    upload,
    retry,
    cancel,
  }
}
