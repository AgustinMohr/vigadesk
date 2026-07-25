export type UserRole = "ADMIN" | "GUEST";

export type User = {
  email: string;
  nombre: string;
  rol: UserRole;
};

export type TestUser = {
  email: string;
  password: string;
  user: User;
};
