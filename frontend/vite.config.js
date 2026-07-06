import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 빌드 산출물을 Spring 정적 리소스 경로로 내보낸다 → 하나의 jar 로 프론트+백 배포.
export default defineConfig({
  plugins: [react()],
  // sockjs-client 가 참조하는 global 을 브라우저에서 브릿지
  define: { global: 'globalThis' },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    // 로컬 dev(vite :5173)에서 백엔드(:8080)로 WebSocket 프록시
    proxy: {
      '/ws': { target: 'http://localhost:8080', ws: true, changeOrigin: true },
    },
  },
});
