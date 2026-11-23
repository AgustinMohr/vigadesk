import path from 'node:path';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Pruebas unitarias y de componente (jsdom). Las e2e viven en /e2e y las corre Playwright.
export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./tests/setup.ts'],
    include: ['tests/unit/**/*.test.{ts,tsx}'],
    coverage: { provider: 'v8', include: ['src/services/http.ts', 'src/lib/**', 'src/hooks/**', 'src/app/api/**'] },
  },
});
