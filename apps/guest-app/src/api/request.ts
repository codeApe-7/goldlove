export interface ApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data: T
  requestId: string
}

export interface ProfilePhotoView {
  id: number
  category: 'AVATAR' | 'LIFE'
  sha256: string
  sizeBytes: number
  contentType: string
  width: number
  height: number
  sortOrder: number
  downloadUrl: string
  createdAt: string
}

const BASE_URL = '/api/v1'

let unauthorizedHandler: () => void = () => undefined

export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler
}

export function request<T>(options: UniApp.RequestOptions): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    uni.request({
      ...options,
      url: BASE_URL + options.url,
      header: { 'Content-Type': 'application/json', ...(options.header ?? {}) },
      success: (response) => {
        const envelope = response.data as ApiEnvelope<T>
        if (response.statusCode === 401) {
          unauthorizedHandler()
          reject(new Error('请先登录'))
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
  filePath: string,
  category: 'AVATAR' | 'LIFE',
): Promise<ApiEnvelope<ProfilePhotoView>> {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: BASE_URL + '/guest/profile/photos',
      filePath,
      name: 'file',
      formData: { category },
      success: (response) => {
        try {
          resolve(JSON.parse(response.data) as ApiEnvelope<ProfilePhotoView>)
        } catch {
          reject(new Error('上传响应无法解析'))
        }
      },
      fail: (error) => reject(new Error(error.errMsg || '上传失败')),
    })
  })
}
