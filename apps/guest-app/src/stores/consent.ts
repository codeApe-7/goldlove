import { defineStore } from 'pinia'
import * as api from '@/api'
import type { ConsentView } from '@/types'

export const useConsentStore = defineStore('guest-consent', {
  state: () => ({
    current: null as ConsentView | null,
    loaded: false,
  }),
  actions: {
    async load(): Promise<void> {
      this.current = await api.currentConsent()
      this.loaded = true
    },
    async accept(version: string): Promise<ConsentView> {
      this.current = await api.acceptConsent(version, 'guest-consent')
      return this.current
    },
  },
})
