import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useProfileStore } from './profile'
import * as api from '@/api'
import type { ProfilePhotoView } from '@/types'

vi.mock('@/api', () => ({
  getDraft: vi.fn(),
  listPhotos: vi.fn(),
  saveDraft: vi.fn(),
}))

describe('guest profile store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    vi.clearAllMocks()
  })

  it('loads draft and photos together', async () => {
    vi.mocked(api.getDraft).mockResolvedValue({ profileNo: 'p1', status: 'DRAFT' } as never)
    vi.mocked(api.listPhotos).mockResolvedValue([photo('AVATAR', 'a')])
    const store = useProfileStore()
    await store.load()
    expect(store.draft?.profileNo).toBe('p1')
    expect(store.photos.map((item) => item.objectKey)).toEqual(['a'])
  })

  it('addUploaded replaces avatar and appends life uploads', () => {
    const store = useProfileStore()
    store.photos = [
      { objectKey: 'a', category: 'AVATAR', previewUrl: 'https://cos/a' },
      { objectKey: 'b', category: 'LIFE', previewUrl: 'https://cos/b' },
    ]

    store.addUploaded({ objectKey: 'c', category: 'AVATAR', previewUrl: 'https://cos/c' })
    store.addUploaded({ objectKey: 'd', category: 'LIFE', previewUrl: 'https://cos/d' })

    expect(store.photos.map((item) => item.objectKey)).toEqual(['b', 'c', 'd'])
  })

  it('removePhoto only changes local collection', () => {
    const store = useProfileStore()
    store.photos = [
      { objectKey: 'a', category: 'AVATAR', previewUrl: 'https://cos/a' },
      { objectKey: 'b', category: 'LIFE', previewUrl: 'https://cos/b' },
    ]

    store.removeByObjectKey('a')

    expect(store.photos.map((item) => item.objectKey)).toEqual(['b'])
    expect(api.listPhotos).not.toHaveBeenCalled()
    expect(api.saveDraft).not.toHaveBeenCalled()
  })

  it('save sends the photo collection then refreshes persisted photos', async () => {
    vi.mocked(api.saveDraft).mockResolvedValue({ profileNo: 'p1', status: 'DRAFT' } as never)
    vi.mocked(api.listPhotos).mockResolvedValue([photo('AVATAR', 'a')])
    const store = useProfileStore()
    store.photos = [
      { objectKey: 'a', category: 'AVATAR', previewUrl: 'https://cos/a' },
      { objectKey: 'b', category: 'LIFE', previewUrl: 'https://cos/b' },
    ]

    await store.save({ gender: '男' })

    expect(api.saveDraft).toHaveBeenCalledWith({
      gender: '男',
      photos: { avatar: 'a', life: ['b'] },
    })
    expect(api.listPhotos).toHaveBeenCalledTimes(1)
    expect(store.photos.map((item) => item.objectKey)).toEqual(['a'])
  })
})

function photo(category: 'AVATAR' | 'LIFE', objectKey: string): ProfilePhotoView {
  return {
    id: 1,
    category,
    objectKey,
    sortOrder: 0,
    previewUrl: `https://cos/${objectKey}`,
    createdAt: '2026-08-09T00:00:00+08:00',
  }
}
