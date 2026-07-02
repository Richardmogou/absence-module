"use client";

import { useEffect } from "react";
import { signIn } from "next-auth/react";
import { useSearchParams } from "next/navigation";

export default function ConnexionPage() {
  const params      = useSearchParams();
  const callbackUrl = params.get("callbackUrl") ?? "/mon-espace";

  useEffect(() => {
    signIn("keycloak", { callbackUrl });
  }, [callbackUrl]);

  return (
    <div className="min-h-[calc(100vh-176px)] flex items-center justify-center">
      <div className="flex flex-col items-center gap-4 text-neutral-500">
        <div className="w-8 h-8 border-2 border-primary-500 border-t-transparent rounded-full animate-spin" />
        <p className="text-sm font-ui">Redirection vers le serveur de connexion…</p>
      </div>
    </div>
  );
}
