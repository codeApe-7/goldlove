import axios from 'axios'

export interface ApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data: T
  requestId: string
}

/**
 * 面向界面的接口错误。
 *
 * axios 默认抛的是 `Request failed with status code 401` —— 这句话对使用后台的人
 * 毫无意义（「登录失败：Request failed with status code 401」）。所有请求错误都在
 * 拦截器里归一成这个类型：message 优先取后端返回的中文说明，code 与 status 留给
 * 调用方按需分支。
 */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly status: number,
    readonly requestId?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** 后端没给说明时按状态码兜底。宁可说「服务暂时不可用」，也不要抛出英文技术串。 */
const FALLBACK_BY_STATUS: Record<number, string> = {
  400: '请求参数有误，请检查后重试',
  401: '登录已过期，请重新登录',
  403: '没有权限执行这个操作',
  404: '请求的内容不存在',
  405: '请求方式不受支持',
  409: '数据已被其他人修改，请刷新后重试',
  413: '内容过大，请缩小后重试',
  415: '不支持的内容类型',
  429: '操作过于频繁，请稍后再试',
  500: '服务出错了，请稍后重试',
  502: '服务暂时不可用，请稍后重试',
  503: '服务暂时不可用，请稍后重试',
  504: '服务响应超时，请稍后重试',
}

let unauthorizedHandler: () => void = () => undefined

export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler
}

export const http = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
})
http.interceptors.response.use(
  (response) => {
    const envelope = response.data as ApiEnvelope<unknown>
    if (envelope && envelope.success === false) {
      return Promise.reject(new ApiError(
        envelope.message || '请求失败',
        envelope.code || 'UNKNOWN',
        response.status,
        envelope.requestId,
      ))
    }
    return response
  },
  async (error) => {
    const status: number = error?.response?.status ?? 0
    const url: string = error?.config?.url ?? ''

    if (status === 0) {
      // 压根没拿到响应：断网、被墙、服务没起来、请求被取消。
      return Promise.reject(new ApiError(
        axios.isCancel?.(error) ? '请求已取消' : '连不上服务器，请检查网络后重试',
        'NETWORK_ERROR',
        0,
      ))
    }

    const envelope = await readEnvelope(error?.response?.data)
    // 登录接口的 401 是「账号或密码不对」，不是「会话过期」，不能触发跳登录页。
    if (status === 401 && !url.includes('/auth/login')) {
      unauthorizedHandler()
    }
    return Promise.reject(new ApiError(
      envelope?.message || FALLBACK_BY_STATUS[status] || `请求失败（${status}）`,
      envelope?.code || 'HTTP_' + status,
      status,
      envelope?.requestId,
    ))
  },
)

/**
 * 从响应体里取出统一响应壳。
 *
 * 导出接口用 `responseType: 'blob'`，出错时响应体也是 Blob，不读成文本就只能拿到
 * `[object Blob]`。jsdom 的 Blob 没有 text()，所以退回 FileReader。
 */
async function readEnvelope(body: unknown): Promise<ApiEnvelope<unknown> | null> {
  if (!body) {
    return null
  }
  if (typeof body === 'object' && !(body instanceof Blob)) {
    const envelope = body as ApiEnvelope<unknown>
    return typeof envelope.message === 'string' || typeof envelope.code === 'string'
      ? envelope
      : null
  }
  if (body instanceof Blob) {
    try {
      return JSON.parse(await blobText(body)) as ApiEnvelope<unknown>
    } catch {
      return null
    }
  }
  if (typeof body === 'string') {
    try {
      return JSON.parse(body) as ApiEnvelope<unknown>
    } catch {
      return null
    }
  }
  return null
}

function blobText(blob: Blob): Promise<string> {
  if (typeof blob.text === 'function') {
    return blob.text()
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(reader.error ?? new Error('读取响应失败'))
    reader.readAsText(blob)
  })
}

export async function unwrap<T>(
  promise: Promise<{ data: ApiEnvelope<T> }>,
): Promise<T> {
  const response = await promise
  return response.data.data
}
