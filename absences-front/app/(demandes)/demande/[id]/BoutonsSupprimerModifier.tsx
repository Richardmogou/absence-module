"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import apiClient from "@/lib/api/client";
import { Pencil, Trash2 } from "lucide-react";

interface Props {
  id: string;
  peutModifier: boolean;
  peutSupprimer: boolean;
}

export default function BoutonsSupprimerModifier({ id, peutModifier, peutSupprimer }: Props) {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [erreur, setErreur]   = useState<string | null>(null);

  async function supprimer() {
    if (!confirm("Êtes-vous sûr de vouloir supprimer cette demande ?")) return;
    setLoading(true);
    setErreur(null);
    try {
      await apiClient.delete(`/api/v5/demandes/${id}`);
      router.push("/?deleted=1");
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { code?: string } } })?.response?.data?.code;
      setErreur(
        code === "SUPPRESSION_IMPOSSIBLE"
          ? "Cette demande ne peut pas être supprimée dans son état actuel."
          : "Une erreur est survenue lors de la suppression."
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-3">
      {peutModifier && (
        <Button asChild variant="outline" className="gap-2">
          <a href={`/demande/${id}/modifier`}><Pencil size={14} /> Modifier</a>
        </Button>
      )}
      {peutSupprimer && (
        <Button
          variant="outline"
          disabled={loading}
          onClick={supprimer}
          className="gap-2 border-secondary-300 text-secondary-600 hover:bg-secondary-50"
        >
          {loading ? "Suppression…" : <><Trash2 size={14} /> Supprimer</>}
        </Button>
      )}
      {erreur && <p className="text-xs text-secondary-500 w-full">{erreur}</p>}
    </div>
  );
}
