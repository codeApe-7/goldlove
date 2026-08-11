import type { ProfilePhotoView } from '@/types'

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
        if (response.statusCode === 401) {
          unauthorizedHandler()
          reject(new Error('请先登录'))
          return
        }
        if (response.statusCode === 403 && envelope?.code === 'AUTH_ACCOUNT_INACTIVE') {
          unauthorizedHandler()
          reject(new Error(envelope.message || '账号已停用'))
          return
        }
        if (!envelope || envelope.success === false) {
          reject(new Error(envelope?.message || '请求失败'))
          return
        }
        resolve(envelope.data)
      },
      fail: (error) => reject(new Error(error.errMsg || '网络错误')),
    })
  })
}

export function uploadPhoto(
  source: PhotoUploadSource | string,
  category: 'AVATAR' | 'LIFE',
): Promise<ApiEnvelope<ProfilePhotoView>> {
  return new Promise((resolve, reject) => {
    const options: UniApp.UploadFileOption = {
      url: BASE_URL + '/guest/profile/photo-uploads',
      name: 'file',
      formData: { category },
      header: bearerHeader(),
      success: (response) => {
        try {
          const envelope = JSON.parse(response.data) as ApiEnvelope<ProfilePhotoView>
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
    uni.uploadFile(options)
  })
}
