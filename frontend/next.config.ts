import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  output: 'standalone',       // Build optimizado para Docker (copia solo lo necesario)
  reactCompiler: true,        // Habilita el compilador experimental de React
};

export default nextConfig;
