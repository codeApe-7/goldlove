import { defineStore } from 'pinia'
import * as api from '@/api'
import { h5SessionAdapter } from '@/adapters/session'
import type { GuestSession } from '@/types'

export const useAuthStore = defineStore('guest-auth', {
  state: () => ({
    session: (h5SessionAdapter.read() as GuestSession | null) ?? null,
  }),
  getters: {
    isAuthenticated: (state) => state.session !== null,
  },
  actions: {
    async activate(phone: string, initialCredential: string, newPassword: string): Promise<void> {
      const session = await api.activate(phone, initialCredential, newPassword)
      this.session = session
      h5SessionAdapter.write(session)
    },
    async login(phone: string, password: string): Promise<void> {
      const session = await api.login(phone, password)
      this.session = session
      h5SessionAdapter.write(session)
    },
    async logout(): Promise<void> {
      try {
        await api.logout()
      } finally {
        this.session = null
        h5SessionAdapter.clear()
      }
    },
    clearSession(): void {
      this.session = null
      h5SessionAdapter.clear()
    },
  },
})
