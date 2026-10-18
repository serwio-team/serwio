import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Lokalnie: zapytania /api, /login i /logout idą przez proxy do backendu na porcie 8080.
// Dzięki temu frontend i backend mają ten sam adres, a cookie JSESSIONID działa bez CORS.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
      '/login': { target: 'http://localhost:8080', changeOrigin: false },
      '/logout': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
});
