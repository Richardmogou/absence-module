"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import apiClient from "@/lib/api/client";
import { Trash2 } from "lucide-react";

export function BoutonSupprimerCircuit({ circuitId }: { circuitId: string }) {
  const router  = useRouter();
  const [confirm, setConfirm] = useState(false);
  const [loading, setLoading] = useState(false);

  async function supprimer() {
    setLoading(true);
    try {
      await apiClient.delete(`/api/v5/admin/circuits/${circuitId}`);
      router.refresh();
    } finally {
      setLoading(false);
      setConfirm(false);
    }
  }

  if (confirm) {
    return (
      <div className="flex items-center gap-2">
        <span className="text-xs text-secondary-500 font-ui">Confirmer ?</span>
        <Button
          size="sm"
          variant="destructive"
          disabled={loading}
          onClick={supprimer}
        >
          {loading ? "…" : "Oui, supprimer"}
        </Button>
        <Button
          size="sm"
          variant="ghost"
          disabled={loading}
          onClick={() => setConfirm(false)}
        >
          Annuler
        </Button>
      </div>
    );
  }

  return (
    <Button
      size="sm"
      variant="ghost"
      className="text-neutral-400 hover:text-secondary-500"
      onClick={() => setConfirm(true)}
    >
      <Trash2 size={16} />
    </Button>
  );
}
