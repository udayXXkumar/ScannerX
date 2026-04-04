import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        secure: false,
        ws: false,
        rewrite: (path) => path
      },
      '/ws': {
        target: 'ws://127.0.0.1:8080',
        ws: true,
        rewrite: (path) => path
      }
    }
  }
})
