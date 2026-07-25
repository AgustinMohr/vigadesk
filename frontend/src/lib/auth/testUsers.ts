import type { TestUser } from "./types";

export const testUsers: TestUser[] = [
  {
    email: "admin@example.com",
    password: "admin123",
    user: { email: "admin@example.com", nombre: "Administrador", rol: "ADMIN" },
  },
  {
    email: "guest@example.com",
    password: "guest123",
    user: { email: "guest@example.com", nombre: "Invitado", rol: "GUEST" },
  },
];
