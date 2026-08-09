import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useProfileStore } from './profile'
import * as api from '@/api'

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
})
