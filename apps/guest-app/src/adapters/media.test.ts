import { afterEach, describe, expect, it, vi } from 'vitest'
import { choosePhotos, hasChooseMediaApi } from './media'

function stubUni(overrides: Record<string, unknown> = {}): void {
  const base = {
    showToast: vi.fn(),
  }
  vi.stubGlobal('uni', { ...base, ...overrides })
}

describe('hasChooseMediaApi', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('is false on H5 runtime where chooseMedia is absent', () => {
    stubUni()
    expect(hasChooseMediaApi()).toBe(false)
  })

  it('is true on platforms exposing chooseMedia (WeChat/App)', () => {
    stubUni({ chooseMedia: vi.fn() })
    expect(hasChooseMediaApi()).toBe(true)
  })
})

describe('choosePhotos', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('falls back to chooseImage on H5 and returns the raw File objects', async () => {
    const file = new File(['x'], 'photo.png', { type: 'image/png' })
    const chooseImage = vi.fn().mockResolvedValue({
      tempFilePaths: ['blob:http://localhost/1'],
      tempFiles: [file],
    })
    stubUni({ chooseImage })

    const photos = await choosePhotos(3)

    expect(chooseImage).toHaveBeenCalledWith({
      count: 3,
      sourceType: ['album', 'camera'],
    })
    expect(photos).toEqual([{ file }])
  })

  it('uses chooseMedia on supported platforms and returns tempFilePath', async () => {
    const chooseMedia = vi.fn().mockResolvedValue({
      tempFiles: [{ tempFilePath: '/tmp/a.png', fileType: 'image' }],
    })
    stubUni({ chooseMedia })

    const photos = await choosePhotos(1)

    expect(chooseMedia).toHaveBeenCalledWith({ count: 1, mediaType: ['image'] })
    expect(photos).toEqual([{ filePath: '/tmp/a.png' }])
  })
})
