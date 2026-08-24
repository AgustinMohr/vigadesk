import type { NextConfig } from 'next';

const securityHeaders = [
  // Protege contra ataques MIME sniffing (forzar a interpretar el content-type real).
  { key: 'X-Content-Type-Options', value: 'nosniff' },
  // Controla la información de referrer que se envía a otros orígenes.
  { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
  // Evita que la app se incruste en iframes de terceros (clickjacking).
  { key: 'X-Frame-Options', value: 'SAMEORIGIN' },
  // Restringe las APIs del navegador disponibles en la página.
  {
    key: 'Permissions-Policy',
    value: 'camera=(), microphone=(), geolocation=()',
  },
  // CSP: solo recursos del mismo origen + inline scripts/styles de Next.js.
  {
    key: 'Content-Security-Policy',
    value:
      "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; frame-ancestors 'self'; base-uri 'self'; form-action 'self'",
  },
];

const nextConfig: NextConfig = {
  output: 'standalone',       // Build optimizado para Docker (copia solo lo necesario)
  reactCompiler: true,        // Habilita el compilador experimental de React
  async headers () {
    return [
      {
        source: '/:path*',
        headers: securityHeaders,
      },
    ];
  },
};

export default nextConfig;
