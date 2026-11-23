import { expect, test, type APIRequestContext } from '@playwright/test';

const email = process.env.E2E_ADMIN_EMAIL;
const password = process.env.E2E_ADMIN_PASSWORD;

// Datos propios de esta corrida: no dependen de lo que ya haya en la base ni chocan con otras corridas.
const sufijo = Date.now().toString(36);
const clienteNombre = `Cliente E2E ${sufijo}`;
const obraDireccion = `Obra E2E ${sufijo}`;
const productoNombre = `Producto E2E ${sufijo}`;

async function api<T> (request: APIRequestContext, token: string, metodo: 'post', ruta: string, datos: unknown): Promise<T> {
  const respuesta = await request[metodo](ruta, { data: datos, headers: { Authorization: `Bearer ${token}` } });
  expect(respuesta.ok(), `${ruta} → ${respuesta.status()} ${await respuesta.text()}`).toBeTruthy();
  return respuesta.json() as Promise<T>;
}

test.describe('flujo principal', () => {
  test.skip(!email || !password, 'Definir E2E_ADMIN_EMAIL y E2E_ADMIN_PASSWORD');

  test.beforeAll(async ({ request }) => {
    const sesion = await request.post('/api/usuarios/login', { data: { email, password } });
    expect(sesion.ok(), 'login del administrador por API').toBeTruthy();
    const { token } = await sesion.json() as { token: string };

    const cliente = await api<{ id: number }>(request, token, 'post', '/api/clientes', {
      nombre: clienteNombre,
      correoElectronico: `e2e-${sufijo}@mail.com`,
      maximoDescubierto: 1_000_000,
    });
    await api(request, token, 'post', '/api/obras', {
      direccion: obraDireccion,
      lat: 0,
      lng: 0,
      presupuesto: 500_000,
      estado: 'HABILITADA',
      cliente: { id: cliente.id },
    });
    await api(request, token, 'post', '/api/productos', {
      nombre: productoNombre,
      precio: 1000,
      stockActual: 100,
      stockMinimo: 1,
      unidadMedida: 'UNIDAD',
    });
  });

  test('iniciar sesion, crear un pedido y ver su estado', async ({ page }) => {
    // 1. Inicio de sesion por la pantalla
    await page.goto('/login');
    await page.locator('input[name="email"]').fill(email!);
    await page.locator('input[name="password"]').fill(password!);
    await page.getByRole('button', { name: /entrar/i }).click();
    await expect(page).not.toHaveURL(/\/login/);

    // 2. Nuevo pedido: cliente, obra, un producto y su cantidad
    await page.goto('/pedidos');
    await page.getByRole('button', { name: 'Nuevo pedido' }).click();
    const formulario = page.locator('form').filter({ hasText: 'Nuevo pedido' }).or(page.getByRole('heading', { name: 'Nuevo pedido' }).locator('..'));
    const selects = formulario.locator('select');
    await selects.nth(0).selectOption({ label: clienteNombre });
    await expect(selects.nth(1)).toContainText(obraDireccion); // las obras se cargan al elegir el cliente
    await selects.nth(1).selectOption({ label: obraDireccion });
    await formulario.getByRole('button', { name: '+ Agregar producto' }).click();
    // el texto de la opcion incluye precio y stock: se elige por el valor de la opcion que contiene el nombre
    const valorProducto = await selects.nth(2).locator('option', { hasText: productoNombre }).getAttribute('value');
    await selects.nth(2).selectOption(valorProducto!);
    await formulario.locator('input[type="number"]').first().fill('3');
    await formulario.getByRole('button', { name: /crear|guardar/i }).click();

    // 3. El pedido aparece en la lista con su estado
    const fila = page.getByRole('row').filter({ hasText: clienteNombre });
    await expect(fila).toBeVisible({ timeout: 20_000 }); // la lista se recarga al guardar
    await expect(fila).toContainText(/ACEPTADO|EN_PREPARACION/);
  });
});
