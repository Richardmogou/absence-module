"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import apiClient from "@/lib/api/client";
import { Search } from "lucide-react";

export default function BoutonInstruction({ id, type }: { id: string; type?: string }) {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [erreur, setErreur]   = useState<string | null>(null);
  const [dateDebut, setDateDebut] = useState("");

  const estMaternite = type === "CONGE_MATERNITE";

  async function instruire() {
    if (estMaternite && !dateDebut) {
      setErreur("Veuillez saisir la date de début du congé maternité.");
      return;
    }
    setLoading(true);
    setErreur(null);
    try {
      await apiClient.post(
        `/api/v5/demandes/${id}/instruction`,
        estMaternite ? { dateDebut } : {},
      );
      router.push(`/demande/${id}?success=1`);
      router.refresh();
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { code?: string } } })
        ?.response?.data?.code;
      if (code === "JUSTIFICATIF_REQUIS")
        setErreur("Le justificatif est obligatoire avant transmission à la DRH.");
      else if (code === "REQUETE_INVALIDE")
        setErreur("La date de début est obligatoire pour un congé maternité.");
      else if (code === "TRANSITION_ILLEGALE")
        setErreur("La demande n'est pas dans l'état attendu pour cette action.");
      else
        setErreur("Une erreur est survenue. Veuillez réessayer.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex flex-col gap-2">
      {estMaternite && (
        <div className="flex flex-col gap-1.5">
          <label htmlFor="dateDebutMaternite" className="text-sm font-medium text-neutral-700">
            Date de début du congé maternité
          </label>
          <input
            id="dateDebutMaternite"
            type="date"
            value={dateDebut}
            onChange={(e) => setDateDebut(e.target.value)}
            className="h-10 w-full rounded-md border border-neutral-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
          <p className="text-xs text-neutral-500">
            La date de fin (+14 semaines / 98 jours) sera calculée automatiquement.
          </p>
        </div>
      )}

      <Button
        onClick={instruire}
        disabled={loading || (estMaternite && !dateDebut)}
        className="gap-2"
        style={{ background: "#7C3AED" }}
      >
        {loading ? "Transmission…" : <><Search size={16} /> Transmettre à la DRH</>}
      </Button>
      {erreur && <p className="text-xs text-secondary-500">{erreur}</p>}
    </div>
  );
}
