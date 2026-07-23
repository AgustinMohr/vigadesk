// Botón reutilizable con tres variantes: primary (oscuro), secondary (borde), danger (rojo)
// Soporta estado disabled con opacidad reducida
type Variant = "primary" | "secondary" | "danger";

type ButtonProps = {
  children: React.ReactNode;
  onClick?: () => void;
  type?: "button" | "submit";
  variant?: Variant;
  disabled?: boolean;
};

const variantStyles: Record<Variant, string> = {
  primary:
    "bg-slate-900 text-white hover:bg-slate-800",
  secondary:
    "border border-slate-300 text-slate-700 hover:bg-slate-100",
  danger:
    "bg-red-600 text-white hover:bg-red-700",
};

export default function Button({
  children,
  onClick,
  type = "button",
  variant = "primary",
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
