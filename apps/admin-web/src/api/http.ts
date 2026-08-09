import axios from 'axios'

export interface ApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data: T
  requestId: string
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
      return Promise.reject(new Error(envelope.message || '请求失败'))
    }
    return response
  },
  (error) => {
    if (error?.response?.status === 401) {
      unauthorizedHandler()
    }
    return Promise.reject(error)
  },
)

export async function unwrap<T>(
  promise: Promise<{ data: ApiEnvelope<T> }>,
): Promise<T> {
  const response = await promise
  return response.data.data
}
