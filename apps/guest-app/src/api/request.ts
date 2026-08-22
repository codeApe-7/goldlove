import type { PhotoUploadResult } from '@/types'

export interface ApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data: T
  requestId: string
}

export interface PhotoUploadSource {
  file?: File
  filePath?: string
}

const BASE_URL = '/api/v1'

/**
 * 登录与注册接口的 401 是「手机号或密码不对」，不是「会话过期」。
 * 走会话过期那条路会把后端的说明换成「请先登录」，还会把人 reLaunch 回登录页——
 * 他本来就在登录页，结果就是输错密码后页面一闪，什么原因都没看到。
 */
const CREDENTIAL_ENDPOINTS = ['/guest/auth/login', '/guest/auth/register']

/** 后端没给说明时按状态码兜底，别让界面显示空白或英文技术串。 */
const FALLBACK_BY_STATUS: Record<number, string> = {
  400: '请求参数有误，请检查后重试',
  403: '没有权限执行这个操作',
  404: '请求的内容不存在',
  409: '数据已变化，请刷新后重试',
  413: '内容过大，请缩小后重试',
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

function bearerHeader(): Record<string, string> {
  const raw = sessionStorage.getItem('guest-session')
  if (!raw) return {}
  try {
    const session = JSON.parse(raw) as { accessToken?: string }
    if (session.accessToken) return { Authorization: `Bearer ${session.accessToken}` }
  } catch {
    // 会话损坏时按未登录处理
  }
  return {}
}

export function request<T>(options: UniApp.RequestOptions): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    uni.request({
      ...options,
      url: BASE_URL + options.url,
      header: { 'Content-Type': 'application/json', ...bearerHeader(), ...(options.header ?? {}) },
      success: (response) => {
        const envelope = response.data as ApiEnvelope<T>
        const status = response.statusCode
        const isCredentialCheck = CREDENTIAL_ENDPOINTS.some((path) => options.url.startsWith(path))

        if (status === 401 && !isCredentialCheck) {
          unauthorizedHandler()
          reject(new Error(envelope?.message || '登录已过期，请重新登录'))
          return
        }
        if (status === 403 && envelope?.code === 'AUTH_ACCOUNT_INACTIVE') {
          unauthorizedHandler()
          reject(new Error(envelope.message || '账号已停用'))
          return
        }
        if (!envelope || envelope.success === false) {
          reject(new Error(
            envelope?.message || FALLBACK_BY_STATUS[status] || `请求失败（${status}）`,
          ))
          return
        }
        resolve(envelope.data)
      },
      fail: (error) => reject(new Error(error.errMsg || '连不上服务器，请检查网络后重试')),
    })
  })
}

export function uploadPhoto(
  source: PhotoUploadSource | string,
  category: 'AVATAR' | 'LIFE',
  onProgress?: (percent: number) => void,
): Promise<ApiEnvelope<PhotoUploadResult>> {
  return new Promise((resolve, reject) => {
    const options: UniApp.UploadFileOption = {
      url: BASE_URL + '/guest/profile/photo-uploads',
      name: 'file',
      formData: { category },
      header: bearerHeader(),
      success: (response) => {
        try {
          const envelope = JSON.parse(response.data) as ApiEnvelope<PhotoUploadResult>
          if (
            response.statusCode === 401 ||
            (response.statusCode === 403 && envelope.code === 'AUTH_ACCOUNT_INACTIVE')
          ) {
            unauthorizedHandler()
            reject(new Error(envelope.message || '请先登录'))
            return
          }
          if (envelope.success === false) {
            reject(new Error(envelope.message || '上传失败'))
            return
          }
          resolve(envelope)
        } catch {
          reject(new Error('上传响应无法解析'))
        }
      },
      fail: (error) => reject(new Error(error.errMsg || '上传失败')),
    }
    const resolved = typeof source === 'string' ? { filePath: source } : source
    if (resolved.file) {
      // H5：直接传 File 对象最可靠
      options.files = [{ name: 'file', file: resolved.file }]
    } else {
      options.filePath = resolved.filePath
    }
    // 带 success / fail 回调时 uni.uploadFile 返回的是 UploadTask，但类型声明里被
    // Promise 重载抢先命中，只能断言回来才能挂进度监听。
    const task = uni.uploadFile(options) as unknown as UniApp.UploadTask | undefined
    // 上传进度用于渲染进度环。部分平台不返回 UploadTask，拿不到就不显示百分比。
    if (onProgress && task?.onProgressUpdate) {
      task.onProgressUpdate((result) => onProgress(result.progress))
    }
  })
}
