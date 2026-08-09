const SESSION_KEY = 'guest-session'

export interface SessionAdapter {
  read(): unknown | null
  write(value: unknown): void
  clear(): void
}

export const h5SessionAdapter: SessionAdapter = {
  read() {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    try {
      return JSON.parse(raw)
    } catch {
      sessionStorage.removeItem(SESSION_KEY)
      return null
    }
  },
  write(value) {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(value))
  },
  clear() {
    sessionStorage.removeItem(SESSION_KEY)
  },
}
