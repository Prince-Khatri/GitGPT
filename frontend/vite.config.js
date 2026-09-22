import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const api = env.VITE_DEV_API_TARGET || 'http://localhost:8080';
  const port = Number(env.VITE_DEV_PORT || 5173);
  return {
    plugins: [react()],
    server: {
      port,
      proxy: {
        '/api': { target: api, changeOrigin: true },
        '/oauth2': { target: api, changeOrigin: true },
        '/login': { target: api, changeOrigin: true },
        '/logout': { target: api, changeOrigin: true }
      }
    }
  };
});
