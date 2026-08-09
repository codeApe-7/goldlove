import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useConsentStore } from './consent'
import * as api from '@/api'

vi.mock('@/api', () => ({
  currentConsent: vi.fn(),
  acceptConsent: vi.fn(),
}))

describe('guest consent store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads current consent and marks loaded', async () => {
    vi.mocked(api.currentConsent).mockResolvedValue({
      id: 1,
      authorizationDocumentVersion: 'v0.3',
      acceptedAt: '2030-07-01T00:00:00Z',
      expiresAt: '2031-07-01T00:00:00Z',
    })
    const store = useConsentStore()
    await store.load()
    expect(store.loaded).toBe(true)
    expect(store.current?.authorizationDocumentVersion).toBe('v0.3')
  })

  it('accepts current version and stores result', async () => {
    vi.mocked(api.acceptConsent).mockResolvedValue({
      id: 2,
      authorizationDocumentVersion: 'v0.3',
      acceptedAt: '2030-07-01T00:00:00Z',
      expiresAt: '2031-07-01T00:00:00Z',
    })
    const store = useConsentStore()
    const consent = await store.accept('v0.3')
    expect(consent.id).toBe(2)
    expect(store.current?.id).toBe(2)
    expect(api.acceptConsent).toHaveBeenCalledWith('v0.3', 'guest-h5')
  })
})
