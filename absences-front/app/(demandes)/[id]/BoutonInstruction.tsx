"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import apiClient from "@/lib/api/client";
import { Search } from "lucide-react";

export default function BoutonInstruction({ id }: { id: string }) {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [erreur, setErreur]   = useState<string | null>(null);

  async function instruire() {
    setLoading(true);
    setErreur(null);
    try {
      await apiClient.post(`/api/v5/demandes/${id}/instruction`);
      router.push(`/${id}?success=1`);
      router.refresh();
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { code?: string } } })
        ?.response?.data?.code;
      if (code === "JUSTIFICATIF_REQUIS")
        setErreur("Le justificatif est obligatoire avant transmission à la DRH.");
      else if (code === "TRANSITION_ILLEGALE")
        setErreur("La demande n'est pas dans l'état attendu pour cette action.");
      else
        setErreur("Une erreur est survenue. Veuillez réessayer.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex flex-col gap-1.5">
      <Button
        onClick={instruire}
        disabled={loading}
        className="gap-2"
        style={{ background: "#7C3AED" }}
      >
        {loading ? "Transmission…" : <><Search size={16} /> Transmettre à la DRH</>}
      </Button>
      {erreur && <p className="text-xs text-secondary-500">{erreur}</p>}
    </div>
  );
}
