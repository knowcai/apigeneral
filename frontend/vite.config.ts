import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/admin': 'http://localhost:8088',
      '/api': 'http://localhost:8088'
    }
  }
})
