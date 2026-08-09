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
// ==============================================================

type Variant = 'primary' | 'secondary' | 'danger';

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
      className={`rounded-md px-4 py-2 text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed ${variantStyles[variant]}`}
    >
      {children}
    </button>
  );
}
