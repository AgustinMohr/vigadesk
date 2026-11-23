import { defineConfig } from '@playwright/test';

// Prueba end to end contra el sistema completo ya levantado (docker compose del backend y del frontend).
// Variables: E2E_BASE_URL (por defecto http://localhost, el HAProxy), E2E_ADMIN_EMAIL y E2E_ADMIN_PASSWORD
// (el administrador inicial: BOOTSTRAP_ADMIN_EMAIL y BOOTSTRAP_ADMIN_PASSWORD del .env).
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost',
    trace: 'retain-on-failure',
  },
});
