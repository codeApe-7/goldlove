import { afterEach, describe, expect, it, vi } from 'vitest'
import { uploadPhoto } from './request'

describe('uploadPhoto', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uploads file with category and unwraps envelope', async () => {
    vi.stubGlobal('uni', {
      uploadFile: vi.fn((options: UniApp.UploadFileOption) => {
        expect(options.name).toBe('file')
        expect(options.formData).toEqual({ category: 'AVATAR' })
        expect(options.url).toBe('/api/v1/guest/profile/photos')
        options.success?.({
          statusCode: 200,
          data: JSON.stringify({
            success: true,
            code: 'OK',
            message: '成功',
            data: { id: 1, category: 'AVATAR' },
            requestId: 'r',
          }),
        } as UniApp.UploadFileSuccessCallbackResult)
      }),
    })
    const result = await uploadPhoto('/tmp/a.png', 'AVATAR')
    expect(result.data).toMatchObject({ id: 1, category: 'AVATAR' })
  })
})
