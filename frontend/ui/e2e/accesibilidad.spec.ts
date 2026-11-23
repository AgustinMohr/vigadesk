import path from 'node:path';
import { expect, test, type Page } from '@playwright/test';

const email = process.env.E2E_ADMIN_EMAIL;
const password = process.env.E2E_ADMIN_PASSWORD;

const ANCHOS = [
  { nombre: 'movil', width: 375, height: 800 },
  { nombre: 'tablet', width: 768, height: 1000 },
  { nombre: 'escritorio', width: 1280, height: 800 },
];

async function iniciarSesion (page: Page) {
  await page.goto('/login');
  await page.waitForLoadState('networkidle'); // el formulario necesita estar hidratado
  await page.locator('input[name="email"]').fill(email!);
  await page.locator('input[name="password"]').fill(password!);
  await page.getByRole('button', { name: /entrar/i }).click();
  await expect(page).not.toHaveURL(/\/login/, { timeout: 20_000 }); // el primer login tras un rato sin uso puede tardar
}

/** Corre axe-core en el navegador real (con contraste de color y regiones) y devuelve las violaciones. */
async function violaciones (page: Page): Promise<string[]> {
  await page.addScriptTag({ path: path.join(process.cwd(), 'node_modules', 'axe-core', 'axe.min.js') });
  return page.evaluate(async () => {
    const axe = (window as unknown as { axe: { run: () => Promise<{ violations: Array<{ id: string; help: string; nodes: Array<{ target: string[] }> }> }> } }).axe;
    const resultado = await axe.run();
    return resultado.violations.map((v) => `${v.id}: ${v.help} → ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`);
  });
}

async function sinDesplazamientoHorizontal (page: Page) {
  const { scroll, ancho } = await page.evaluate(() => ({
    scroll: document.documentElement.scrollWidth,
    ancho: window.innerWidth,
  }));
  expect(scroll, `la pagina se desplaza horizontalmente (${scroll}px > ${ancho}px)`).toBeLessThanOrEqual(ancho);
}

for (const { nombre, width, height } of ANCHOS) {
  test.describe(`pantalla ${nombre} (${width}px)`, () => {
    test.use({ viewport: { width, height } });

    test('login: sin desplazamiento horizontal y sin violaciones de accesibilidad', async ({ page }) => {
      await page.goto('/login');
      await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible();

      await sinDesplazamientoHorizontal(page);
      expect(await violaciones(page)).toEqual([]);
    });

    test.describe('con sesion', () => {
      test.skip(!email || !password, 'Definir E2E_ADMIN_EMAIL y E2E_ADMIN_PASSWORD');

      for (const ruta of ['/dashboard', '/pedidos', '/productos', '/clientes']) {
        test(`${ruta}: sin desplazamiento horizontal y sin violaciones de accesibilidad`, async ({ page }) => {
          await iniciarSesion(page);
          await page.goto(ruta);
          await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
          await page.waitForLoadState('networkidle');

          await sinDesplazamientoHorizontal(page);
          expect(await violaciones(page)).toEqual([]);
        });
      }
    });
  });
}

test.describe('menu en pantallas chicas', () => {
  test.use({ viewport: { width: 375, height: 800 } });
  test.skip(!email || !password, 'Definir E2E_ADMIN_EMAIL y E2E_ADMIN_PASSWORD');

  test('el menu esta oculto, se abre con el boton, marca la ruta activa y se cierra con Esc', async ({ page }) => {
    await iniciarSesion(page);
    await page.goto('/pedidos');
    const menu = page.getByRole('navigation', { name: 'Principal' });
    await expect(menu).toBeHidden();

    await page.getByRole('button', { name: 'Abrir menú' }).click();
    await expect(menu).toBeVisible();
    await expect(menu.getByRole('link', { name: 'Pedidos' })).toHaveAttribute('aria-current', 'page');

    await page.keyboard.press('Escape');
    await expect(menu).toBeHidden();
  });
});

test.describe('marca', () => {
  test('el titulo de la pagina es VigaDesk', async ({ page }) => {
    await page.goto('/login');

    await expect(page).toHaveTitle(/VigaDesk/);
  });
});
