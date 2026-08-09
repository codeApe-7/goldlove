import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { setUnauthorizedHandler } from '@/api/request'
import { useAuthStore } from '@/stores/auth'

export function createApp() {
  const app = createSSRApp(App)
  const pinia = createPinia()
  app.use(pinia)

  setUnauthorizedHandler(() => {
    const auth = useAuthStore()
    auth.clearSession()
    uni.reLaunch({ url: '/pages/auth/index' })
  })

  return {
    app,
  }
}
