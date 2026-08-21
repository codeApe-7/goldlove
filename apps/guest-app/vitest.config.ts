import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    // 打开 CSS 处理，否则 `import x from './a.scss?raw'` 会被 CSS 桩替换成空串，
    // 样式保真断言（src/styles/tokens.test.ts）就无从下手。
    css: true,
  },
})
