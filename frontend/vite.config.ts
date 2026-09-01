import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist',
    // Don't ship .map files to production. The React source isn't a
    // secret, but there's no upside to bundling it; if a prod-only
    // bug ever shows up, the operator can build once with
    // sourcemap: true and re-deploy to get a stack trace.
    sourcemap: false,
    target: 'es2022',
  },
  server: {
    // Proxy /api to the backend during dev so the SPA can call the
    // real server without CORS gymnastics. Port 8080 matches the
    // Spring Boot default and the run.bat / docker-compose ports.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
})
