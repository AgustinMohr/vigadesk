// ==============================================================
// BUTTON — componente reutilizable
// --------------------------------------------------------------
// La idea: en vez de escribir el <button> con sus clases de Tailwind en
// cada parte de la app, definimos un botón con "variantes". Así los
// botones se ven iguales en todos lados y cambiarlos es tocar UN lugar.
//
// COMPONENTE CONTROLADO POR PROPS:
//  - children: el texto/ícono que va dentro del botón.
//  - onClick: función que se llama al hacer click.
//  - type: "button" (default) o "submit" (para formularios).
//  - variant: define los colores (primary/secondary/danger).
//  - disabled: si está deshabilitado.
// Todas tienen valores por defecto, así que se pueden omitir.
//
// CONCEPTO CLAVE — COMPONENTE CONTENEDOR: `children` es una prop especial
// de React que recibe los nodos JSX anidados entre las etiquetas.
// Tipar como `React.ReactNode` permite texto, elementos, arrays o null.
//
// CONCEPTO CLAVE — Tailwind y JIT: el compilador de Tailwind escanea los
// archivos buscando clases COMPLETAS en el código. Por eso las variantes
// van en un Record con strings literales y NO se arman con template
// strings (`\`bg-${color}\``). Si la clase no está escrita literalmente,
// Tailwind la descarta del CSS final y el botón se ve sin estilos.
// ==============================================================

// Unión de variantes disponibles. Como `Variant` es una unión de
// literales, `variantStyles` DEBE tener una entrada para cada una:
// TypeScript lo garantiza en compilación (Record<Variant, string>).
type Variant = 'primary' | 'secondary' | 'danger';

/**
 * Props (contrato) del componente Button.
 *
 * @typedef {Object} ButtonProps
 * @property {React.ReactNode} children - Contenido del botón
 *   (texto, ícono, o JSX anidado).
 * @property {() => void} [onClick] - Handler del click (opcional).
 * @property {'button' | 'submit'} [type] - "submit" se usa dentro de
 *   <form> (dispara el onSubmit sin JS).
 * @property {Variant} [variant] - Variante visual (default: primary).
 * @property {boolean} [disabled] - Bloquea clicks y estilos (default: false).
 */
type ButtonProps = {
  children: React.ReactNode;
  onClick?: () => void;
  type?: 'button' | 'submit';
  variant?: Variant;
  disabled?: boolean;
};

// Mapa variante → clases de Tailwind. Tailwind lee estos strings
// literalmente (no puede "armar" clases dinámicamente, por eso
// se escriben completas en un Record).
const variantStyles: Record<Variant, string> = {
  primary:
    'bg-slate-900 text-white hover:bg-slate-800',
  secondary:
    'border border-slate-300 text-slate-700 hover:bg-slate-100',
  danger:
    'bg-red-600 text-white hover:bg-red-700',
};

/**
 * Botón simple y reutilizable con variantes de estilo.
 *
 * Es un componente DUMB (solo vista): no tiene lógica propia, todo lo que
 * hace viene por props. Es la base visual de las tablas, forms y modales.
 *
 * @param {ButtonProps} props - Ver ButtonProps.
 * @returns {JSX.Element} El elemento <button> con la variante aplicada.
 */
export default function Button ({
  children,
  onClick,
  type = 'button',
  variant = 'primary',
  disabled = false,
}: ButtonProps) {
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      // Template literal: combina clases base (comunes a todas las
      // variantes) + las de la variante activa. `disabled:opacity-50` es
      // un modificador de estado de Tailwind: se aplica solo si disabled.
      className={`rounded-md px-4 py-2 text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed ${variantStyles[variant]}`}
    >
      {children}
    </button>
  );
}
