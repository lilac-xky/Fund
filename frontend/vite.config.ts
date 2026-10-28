import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
    // 本项目不使用软链接，跳过 realpath 解析，避免 Windows 上为此额外起子进程
    preserveSymlinks: true,
  },
  server: {
    // 开发期把 /api 转发到本地后端，避免前端另配一套地址
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:9090',
        changeOrigin: true,
      },
    },
  },
  build: {
    // 产物留在前端自身的 dist/，不写进后端 static；发布时按需另行拷贝
    outDir: 'dist',
    emptyOutDir: true,
  },
})
