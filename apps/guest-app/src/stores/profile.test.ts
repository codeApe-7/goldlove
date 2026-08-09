import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useProfileStore } from './profile'
import * as api from '@/api'
import type { ProfilePhotoView } from '@/types'

vi.mock('@/api', () => ({
  getDraft: vi.fn(),
  listPhotos: vi.fn(),
  saveDraft: vi.fn(),
  deletePhoto: vi.fn(),
  submitProfile: vi.fn(),
}))

describe('guest profile store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    vi.clearAllMocks()
  })

  it('loads draft and photos together', async () => {
    vi.mocked(api.getDraft).mockResolvedValue({ profileNo: 'p1', status: 'DRAFT' } as never)
    vi.mocked(api.listPhotos).mockResolvedValue([])
    const store = useProfileStore()
    await store.load()
    expect(store.draft?.profileNo).toBe('p1')
    expect(store.photos).toEqual([])
  })

  it('submit reuses the same idempotency key on retry', async () => {
    vi.mocked(api.submitProfile).mockResolvedValue({
      id: 1,
      status: 'PENDING',
      reviewDeadlineAt: '',
    })
    const store = useProfileStore()
    await store.submit()
    const key = sessionStorage.getItem('profile-submission-key')
    expect(key).toBeTruthy()
    await store.submit()
    expect(api.submitProfile).toHaveBeenCalledTimes(2)
    expect(api.submitProfile).toHaveBeenLastCalledWith(key)
  })

  it('addPhoto replaces the existing avatar and keeps life photos', () => {
    const store = useProfileStore()
    store.photos = [photo(1, 'AVATAR'), photo(2, 'LIFE')]

    store.addPhoto(photo(3, 'AVATAR'))

    expect(store.photos.map((item) => item.id)).toEqual([2, 3])
  })

  it('addPhoto appends life photos', () => {
    const store = useProfileStore()
    store.photos = [photo(1, 'LIFE')]

    store.addPhoto(photo(2, 'LIFE'))

    expect(store.photos.map((item) => item.id)).toEqual([1, 2])
  })
})

function photo(id: number, category: 'AVATAR' | 'LIFE'): ProfilePhotoView {
  return {
    id,
    category,
    sha256: 'a'.repeat(64),
    sizeBytes: 1024,
    contentType: 'image/png',
    width: 100,
    height: 80,
    sortOrder: id,
    downloadUrl: `https://cos.example/${id}.png`,
    createdAt: '2026-08-09T00:00:00+08:00',
  }
}
