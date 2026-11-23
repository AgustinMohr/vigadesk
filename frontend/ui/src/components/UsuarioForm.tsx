// ==============================================================
// FORMULARIO DE USUARIO (modal crear/editar)
// --------------------------------------------------------------
// Mismo patrón que ObraForm.tsx (ver ahí la teoría de INPUTS CONTROLADOS
// y del doble modo crear/editar). Particularidades:
//  - Recibe un `usuario` opcional: si llega → EDITAR (los campos se
//    inicializan con sus datos); si no → CREAR.
//  - La contraseña es obligatoria al crear; al editar se deja vacía
//    para conservar la existente (se envía `undefined`).
//  - Carga la lista de clientes del <select> al montarse (igual que
//    ProductoForm carga categorías).
//
// SEGURIDAD EN LA U: por eso el campo password es `type='password'`: el
// navegador enmascara los caracteres y evita que terceros lean la
// contraseña por encima del hombro (shoulder surfing).
// ==============================================================

'use client';

import { useEffect, useState } from 'react';
import type { UsuarioCliente } from '@/services/usuarioService';
import { clienteService, type Cliente } from '@/services/clienteService';
import Button from './Button';
import Modal from './Modal';
import { getErrorMessage } from '@/lib/http/api-error';
import Field from './Field';

/**
 * PROPS del modal de usuario de cliente.
 *
 * @typedef {Object} UsuarioFormProps
 * @property {UsuarioCliente} [usuario] - Si existe → edición; si no → creación.
 * @property {(data: Partial<UsuarioCliente>) => Promise<void>} onSubmit -
 *   Callback con los datos; el padre persiste y cierra el modal.
 * @property {() => void} onCancel - Cierra el modal sin guardar.
 */
type UsuarioFormProps = {
  usuario?: UsuarioCliente;
  onSubmit: (data: Partial<UsuarioCliente>) => Promise<void>;
  onCancel: () => void;
};

/**
 * Modal de formulario para crear o editar un usuario de cliente.
 *
 * El usuario SIEMPRE pertenece a un cliente (empresa): el <select> de
 * clientes se carga desde la API al montarse y es obligatorio elegir uno.
 * La contraseña: obligatoria al crear; en edición se deja vacía para no
 * pisar la existente (si el user la borra, `undefined` deja la vieja).
 *
 * @param {UsuarioFormProps} props - Ver UsuarioFormProps.
 * @returns {JSX.Element} El modal con el formulario de usuario.
 */
export default function UsuarioForm ({
  usuario,
  onSubmit,
  onCancel,
}: UsuarioFormProps) {
  // Lista de clientes (empresas) para el select. Se puebla en useEffect.
  const [clientes, setClientes] = useState<Cliente[]>([]);
  // Estado de cada campo del form.
  const [nombre, setNombre] = useState(usuario?.nombre ?? '');
  const [apellido, setApellido] = useState(usuario?.apellido ?? '');
  const [dni, setDni] = useState(usuario?.dni ?? '');
  const [email, setEmail] = useState(usuario?.email ?? '');
  // La contraseña SIEMPRE arranca vacía (incluso al editar: así no se
  // muestra la guardada —el backend no la expone de todos modos).
  const [password, setPassword] = useState('');
  // El select guarda el id del cliente como string (los selects devuelven
  // texto); se convierte a Number() recién al buscar el cliente.
  const [clienteId, setClienteId] = useState(usuario?.cliente?.id?.toString() ?? '');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const isEditing = !!usuario;

  // Efecto de montaje: carga los clientes UNA vez. Si falla, silencia el
  // error (quedaría el select vacío, sin crash).
  useEffect(() => {
    clienteService.getAll()
      .then((data) => setClientes(data))
      .catch(() => {});
  }, []);

  /**
   * Valida y arma el payload del usuario, delegando el guardado al padre.
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!nombre.trim() || !email.trim()) {
      setError('Nombre y email son obligatorios.');
      return;
    }
    // La contraseña es obligatoria SOLO al crear (en edición, vacía = no
    // se toca la existente).
    if (!isEditing && !password.trim()) {
      setError('La contraseña es obligatoria al crear el usuario.');
      return;
    }
    // Política de contraseñas del backend: al menos 12 caracteres (al crear, o si se cambia al editar).
    if (password.trim() && password.trim().length < 12) {
      setError('La contraseña debe tener al menos 12 caracteres.');
      return;
    }

    // Resuelve el objeto Cliente completo a partir del id seleccionado.
    // `.find()` recorre el array de clientes y devuelve el primero que
    // cumple la condición (o undefined si no hay match).
    const cliente = clientes.find((c) => c.id === Number(clienteId));
    if (!cliente) {
      setError('Debe seleccionar un cliente.');
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit({
        nombre: nombre.trim(),
        apellido: apellido.trim() || undefined,
        dni: dni.trim() || undefined,
        email: email.trim(),
        // `password.trim() || undefined`: en edición, vacío → undefined →
        // el backend conserva la contraseña guardada.
        password: password.trim() || undefined,
        // `cliente.id!` — el non-null assertion: le decimos a TS "confirmo
        // que no es undefined" (ya validamos que el .find() encontró al
        // cliente, y todo cliente de la lista tiene id).
        cliente: { id: cliente.id!, nombre: cliente.nombre },
      });
    } catch (err) {
      setError(getErrorMessage(err, 'Ocurrió un error al guardar el usuario.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    // Overlay de modal: fondo oscuro + caja blanca centrada.
    <Modal titulo={<>{isEditing ? 'Editar usuario' : 'Nuevo usuario'}</>} onClose={onCancel}>

      <form onSubmit={handleSubmit} className='space-y-4'>
        <Field label='Nombre' required>
          <input
            type='text'
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
            className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
          />
        </Field>

        <div className='grid grid-cols-2 gap-4'>
          <Field label='Apellido'>
            <input
              type='text'
              value={apellido}
              onChange={(e) => setApellido(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </Field>
          <Field label='DNI'>
            <input
              type='text'
              inputMode='numeric'
              value={dni}
              onChange={(e) => setDni(e.target.value)}
              className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
            />
          </Field>
        </div>

        <Field label='Email' required>
          <input
            type='email'
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
          />
        </Field>

        {/* La contraseña es obligatoria SOLO en creación */}
        <Field label='Contraseña' required={!isEditing}>
          <input
            type='password' // el navegador enmascara los caracteres
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder={isEditing ? 'Dejar vacía para conservar' : 'Mínimo 12 caracteres'}
            className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
          />
        </Field>

        {/* Select de clientes cargado desde la API */}
        <Field label='Cliente' required>
          <select
            value={clienteId}
            onChange={(e) => setClienteId(e.target.value)}
            className='mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none'
          >
            <option value=''>Seleccionar cliente</option>
            {clientes.map((c) => (
              // value puede ser number | undefined (Cliente.id es
              // opcional); en la práctica los listados siempre traen id.
              <option key={c.id} value={c.id}>
                {c.nombre}
              </option>
            ))}
          </select>
        </Field>

        {error && <p className='text-sm text-red-600'>{error}</p>}

        <div className='flex justify-end gap-2 pt-2'>
          <Button variant='secondary' onClick={onCancel} disabled={submitting}>
            Cancelar
          </Button>
          <Button type='submit' disabled={submitting}>
            {submitting ? 'Guardando…' : 'Guardar'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
