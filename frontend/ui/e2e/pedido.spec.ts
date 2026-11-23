import { expect, test, type APIRequestContext } from '@playwright/test';

const email = process.env.E2E_ADMIN_EMAIL;
const password = process.env.E2E_ADMIN_PASSWORD;

// Datos propios de esta corrida: no dependen de lo que ya haya en la base ni chocan con otras corridas.
const sufijo = Date.now().toString(36);
const clienteNombre = `Cliente E2E ${sufijo}`;
const obraDireccion = `Obra E2E ${sufijo}`;
const productoNombre = `Producto E2E ${sufijo}`;

// Ids y token creados en beforeAll, para la prueba de doble envio por API
const datos = { token: '', clienteId: 0, obraId: 0, productoId: 0 };
const emailCliente = `cliente-${sufijo}@example.com`;
const claveCliente = 'clave-del-cliente-123';

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
    datos.token = token;

    const cliente = await api<{ id: number }>(request, token, 'post', '/api/clientes', {
      nombre: clienteNombre,
      correoElectronico: `e2e-${sufijo}@mail.com`,
      maximoDescubierto: 1_000_000,
    });
    const obra = await api<{ id: number }>(request, token, 'post', '/api/obras', {
      direccion: obraDireccion,
      lat: 0,
      lng: 0,
      presupuesto: 500_000,
      estado: 'HABILITADA',
      cliente: { id: cliente.id },
    });
    const producto = await api<{ id: number }>(request, token, 'post', '/api/productos', {
      nombre: productoNombre,
      precio: 1000,
      stockActual: 100,
      stockMinimo: 1,
      unidadMedida: 'UNIDAD',
    });
    // un usuario con rol CLIENTE de esa empresa, para probar el guard de rutas por rol
    await api(request, token, 'post', '/api/usuarios', {
      nombre: 'Usuario E2E',
      email: emailCliente,
      password: claveCliente,
      cliente: { id: cliente.id },
    });
    datos.clienteId = cliente.id;
    datos.obraId = obra.id;
    datos.productoId = producto.id;
  });

  test('un POST repetido con la misma Idempotency-Key (por el BFF) devuelve el mismo pedido', async ({ request }) => {
    const clave = `e2e-${sufijo}-${Date.now()}`;
    const pedido = { clienteId: datos.clienteId, obraId: datos.obraId, lineas: [{ productoId: datos.productoId, cantidad: 1 }] };
    const enviar = (cuerpo: unknown) => request.post('/api/pedidos', {
      data: cuerpo,
      headers: { Authorization: `Bearer ${datos.token}`, 'Idempotency-Key': clave },
    });

    const primero = await enviar(pedido);
    const segundo = await enviar(pedido);

    expect(primero.ok(), await primero.text()).toBeTruthy();
    expect(segundo.ok(), await segundo.text()).toBeTruthy();
    const a = await primero.json();
    const b = await segundo.json();
    expect(b.id).toBe(a.id);
    expect(b.numeroPedido).toBe(a.numeroPedido);

    // la misma clave con otro contenido es un conflicto, no un pedido nuevo
    const distinto = await enviar({ ...pedido, lineas: [{ productoId: datos.productoId, cantidad: 2 }] });
    expect(distinto.status()).toBe(409);
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
    const formulario = page.getByRole('dialog', { name: 'Nuevo pedido' });
    await formulario.getByRole('combobox', { name: /^Cliente/ }).selectOption({ label: clienteNombre });
    const obra = formulario.getByRole('combobox', { name: /^Obra/ });
    await expect(obra).toContainText(obraDireccion); // las obras se cargan al elegir el cliente
    await obra.selectOption({ label: obraDireccion });
    await formulario.getByRole('button', { name: '+ Agregar producto' }).click();
    // el texto de la opcion incluye el precio: se elige por el valor de la opcion que contiene el nombre
    const producto = formulario.getByRole('combobox', { name: 'Producto 1' });
    const valorProducto = await producto.locator('option', { hasText: productoNombre }).getAttribute('value');
    await producto.selectOption(valorProducto!);
    await formulario.getByRole('spinbutton', { name: /^Cantidad/ }).fill('3');
    await formulario.getByRole('button', { name: /crear pedido/i }).click();

    // 3. El pedido aparece en la lista con su estado
    const fila = page.getByRole('row').filter({ hasText: clienteNombre });
    await expect(fila).toBeVisible({ timeout: 20_000 }); // la lista se recarga al guardar
    await expect(fila).toContainText(/ACEPTADO|EN_PREPARACION/);
  });

  test('un CLIENTE que abre /usuarios ve "Sin acceso" y no se llama al backend de usuarios', async ({ page }) => {
    const pedidosAUsuarios: string[] = [];
    page.on('request', (r) => {
      const { pathname } = new URL(r.url());
      if (pathname === '/api/usuarios') pedidosAUsuarios.push(`${r.method()} ${pathname}`);
    });

    await page.goto('/login');
    await page.locator('input[name="email"]').fill(emailCliente);
    await page.locator('input[name="password"]').fill(claveCliente);
    await page.getByRole('button', { name: /entrar/i }).click();
    await expect(page).not.toHaveURL(/\/login/);

    await page.goto('/usuarios');

    await expect(page.getByRole('heading', { name: 'Sin acceso' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Usuarios' })).toHaveCount(0); // tampoco esta en el menu
    expect(pedidosAUsuarios).toEqual([]);
  });
});
