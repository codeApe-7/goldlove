import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import * as api from '@/api'

vi.mock('@/api', () => ({
  login: vi.fn(),
  logout: vi.fn(),
}))

describe('guest auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    vi.clearAllMocks()
  })

  it('login stores session, logout clears it', async () => {
    vi.mocked(api.login).mockResolvedValue({
      accountId: 1,
      status: 'ACTIVE',
      accessToken: 'tok-1',
      expiresIn: 2592000,
    })
    const store = useAuthStore()
    await store.login('13800138000', 'secret')
    expect(store.isAuthenticated).toBe(true)
    await store.logout()
    expect(store.isAuthenticated).toBe(false)
    expect(api.logout).toHaveBeenCalledTimes(1)
  })

  it('login persists bearer token in session', async () => {
    vi.mocked(api.login).mockResolvedValue({
      accountId: 1,
      status: 'ACTIVE',
      accessToken: 'tok-1',
      expiresIn: 2592000,
    })
    const store = useAuthStore()
    await store.login('13800138000', 'secret')
    expect(JSON.parse(sessionStorage.getItem('guest-session')!)).toMatchObject({
      accessToken: 'tok-1',
      expiresIn: 2592000,
    })
  })
})
