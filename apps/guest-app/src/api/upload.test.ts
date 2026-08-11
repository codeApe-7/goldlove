import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { uploadPhoto } from './request'

describe('uploadPhoto', () => {
  beforeEach(() => {
    sessionStorage.setItem(
      'guest-session',
      JSON.stringify({
        accountId: 7,
        status: 'ACTIVE',
        accessToken: 'tok-1',
        expiresIn: 2592000,
      }),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    sessionStorage.clear()
  })

  it('uploads file with category and unwraps envelope', async () => {
    vi.stubGlobal('uni', {
      uploadFile: vi.fn((options: UniApp.UploadFileOption) => {
        expect(options.name).toBe('file')
        expect(options.formData).toEqual({ category: 'AVATAR' })
        expect(options.url).toBe('/api/v1/guest/profile/photo-uploads')
        expect(options.header).toMatchObject({ Authorization: 'Bearer tok-1' })
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
            code: 'PHOTO_CONTENT_INVALID',
            message: '文件内容不能为空',
            data: null,
            requestId: 'r3',
          }),
        } as UniApp.UploadFileSuccessCallbackResult)
      }),
    })

    await expect(
      uploadPhoto({ filePath: '/tmp/a.png' }, 'AVATAR'),
    ).rejects.toThrow('文件内容不能为空')
  })
})
