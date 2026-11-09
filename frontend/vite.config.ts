import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // В dev запросы /api идут через proxy на gateway, поэтому браузеру CORS не мешает
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
});
