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

  it('uploads via files with the raw File on H5', async () => {
    vi.stubGlobal('uni', {
      uploadFile: vi.fn((options: UniApp.UploadFileOption) => {
        expect(options.files).toEqual([{ name: 'file', file: expect.any(File) }])
        expect(options.filePath).toBeUndefined()
        options.success?.({
          statusCode: 200,
          data: JSON.stringify({
            success: true,
            code: 'OK',
            message: '成功',
            data: { id: 2, category: 'LIFE' },
            requestId: 'r2',
          }),
        } as UniApp.UploadFileSuccessCallbackResult)
      }),
    })
    const result = await uploadPhoto(
      { file: new File(['y'], 'life.png', { type: 'image/png' }) },
      'LIFE',
    )
    expect(result.data).toMatchObject({ id: 2, category: 'LIFE' })
  })

  it('rejects when the response envelope reports failure', async () => {
    vi.stubGlobal('uni', {
      uploadFile: vi.fn((options: UniApp.UploadFileOption) => {
        options.success?.({
          statusCode: 409,
          data: JSON.stringify({
            success: false,
            code: 'PHOTO_COUNT_LIMIT_EXCEEDED',
            message: '头像最多 1 张，生活照最多 6 张',
            data: null,
            requestId: 'r3',
          }),
        } as UniApp.UploadFileSuccessCallbackResult)
      }),
    })

    await expect(
      uploadPhoto({ filePath: '/tmp/a.png' }, 'AVATAR'),
    ).rejects.toThrow('头像最多 1 张，生活照最多 6 张')
  })
})
