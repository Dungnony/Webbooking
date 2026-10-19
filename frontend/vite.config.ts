import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/proxy': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/proxy/, ''),
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Support chat WebSocket (STOMP); in production VITE_WS_URL points straight at the backend.
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
})
