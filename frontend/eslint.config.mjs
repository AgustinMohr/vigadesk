// ESLint flat config usando Neostandard (el sucesor moderno de StandardJS),
// pensado para ESLint 9 y compatible con TypeScript de fabrica.
// Opciones elegidas:
//  - ts: true   → analiza archivos .ts/.tsx con reglas de TypeScript.
//  - semi: true → respeta el punto y coma (estilo "semistandard"), no lo borra.
//  - env        → browser (localStorage/window en el cliente) + node (route.ts).
//  - ignores    → archivos generados que no queremos lintar.
import neostandard from 'neostandard';

export default neostandard({
  ts: true,
  semi: true,
  env: ['browser', 'node'],
  ignores: [
    '.next/**',
    'out/**',
    'build/**',
    'next-env.d.ts',
    'public/**',
  ],
});
