import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { createAuthGuard } from './index'

vi.mock('@/api/admin', () => ({
  login: vi.fn(),
  logout: vi.fn(),
}))

function makeRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/login',
        name: 'login',
        component: { template: '<div />' },
        meta: { guestOnly: true },
      },
      {
        path: '/dashboard',
        name: 'dashboard',
        component: { template: '<div />' },
        meta: { requiresAuth: true },
      },
    ],
  })
  router.beforeEach(createAuthGuard(router))
  return router
}

describe('router guard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
  })

  it('redirects anonymous users to login for protected routes', async () => {
    const router = makeRouter()
    await router.push('/dashboard')
    expect(router.currentRoute.value.name).toBe('login')
  })

  it('allows authenticated users and bounces them away from login', async () => {
    const auth = useAuthStore()
    auth.session = { id: 1, username: 'op', displayName: 'Operator' }
    const router = makeRouter()
    await router.push('/dashboard')
    expect(router.currentRoute.value.name).toBe('dashboard')
    await router.push('/login')
    expect(router.currentRoute.value.name).toBe('dashboard')
  })
})
