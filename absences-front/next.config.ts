import type { NextConfig } from "next";

const API_UPSTREAM = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  turbopack: {
    resolveAlias: {
      "react-hook-form": "react-hook-form/dist/index.esm.mjs",
    },
  },
  // Proxy transparent : le navigateur appelle /api/v5/* sur le front (port 3000),
  // Next.js redirige vers le backend Spring Boot (port 8080, réseau Docker interne).
  // Cela contourne le problème NEXT_PUBLIC_* baked-in au build time.
  async rewrites() {
    return [
      {
        source: "/api/v5/:path*",
        destination: `${API_UPSTREAM}/api/v5/:path*`,
      },
    ];
  },
};

export default nextConfig;
