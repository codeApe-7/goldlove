import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5174,
    host: '0.0.0.0',
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:1180', changeOrigin: true },
    },
  },
})
