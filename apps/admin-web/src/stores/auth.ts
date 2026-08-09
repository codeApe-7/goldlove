import { defineStore } from 'pinia'
import * as api from '@/api/admin'
import type { AdminSession } from '@/types'

const SESSION_KEY = 'admin-session'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    session: readStoredSession(),
  }),
  getters: {
    isAuthenticated: (state) => state.session !== null,
  },
  actions: {
    async login(username: string, password: string): Promise<void> {
      const session = await api.login(username, password)
      this.session = session
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
    },
    async logout(): Promise<void> {
      try {
        await api.logout()
      } finally {
        this.session = null
        sessionStorage.removeItem(SESSION_KEY)
      }
    },
    clearSession(): void {
      this.session = null
      sessionStorage.removeItem(SESSION_KEY)
    },
  },
})

function readStoredSession(): AdminSession | null {
  const raw = sessionStorage.getItem(SESSION_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AdminSession
  } catch {
    sessionStorage.removeItem(SESSION_KEY)
    return null
  }
}
