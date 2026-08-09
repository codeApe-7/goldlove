import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import * as api from '@/api/admin'

vi.mock('@/api/admin', () => ({
  login: vi.fn(),
  logout: vi.fn(),
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    vi.clearAllMocks()
  })

  it('logs in and stores session', async () => {
    vi.mocked(api.login).mockResolvedValue({ id: 1, username: 'op', displayName: 'Operator' })
    const store = useAuthStore()
    await store.login('op', 'secret')
    expect(store.isAuthenticated).toBe(true)
    expect(store.session?.username).toBe('op')
  })

  it('logout clears session', async () => {
    vi.mocked(api.login).mockResolvedValue({ id: 1, username: 'op', displayName: 'Operator' })
    const store = useAuthStore()
    await store.login('op', 'secret')
    await store.logout()
    expect(store.isAuthenticated).toBe(false)
    expect(api.logout).toHaveBeenCalledTimes(1)
  })
})
