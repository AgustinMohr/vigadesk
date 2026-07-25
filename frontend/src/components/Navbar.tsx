"use client";

import Link from "next/link";
import { useAuth } from "@/lib/auth/AuthContext";

export default function Navbar() {
  const { user, logout } = useAuth();

  return (
    <nav className="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-6">
      <Link href="/dashboard" className="text-lg font-semibold text-slate-950">
        Mi Sistema
      </Link>

      <div className="flex items-center gap-4">
        <span className="text-sm text-slate-500">
          {user?.nombre}{" "}
          <span className="ml-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
            {user?.rol === "ADMIN" ? "Admin" : "Invitado"}
          </span>
        </span>
        <Link
          href="/login"
          onClick={(e) => {
            e.preventDefault();
            logout();
          }}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
        >
          Salir
        </Link>
      </div>
    </nav>
  );
}
