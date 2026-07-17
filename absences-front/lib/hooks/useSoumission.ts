import { useState } from "react";
import { useRouter } from "next/navigation";
import apiClient from "@/lib/api/client";

export function useSoumission() {
  const router = useRouter();
  const [createdDemandeId, setCreatedDemandeId] = useState<string | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const soumettre = async (
    payload: any,
    justificatifConfig?: { file: File | null; typePiece: string },
    skipSoumission?: boolean
  ) => {
    setApiError(null);
    setIsSubmitting(true);
    try {
      let id = createdDemandeId;
      if (!id) {
        // Etape 1 : Création
        const res = await apiClient.post("/api/v5/demandes", payload);
        id = res.data.id;
        setCreatedDemandeId(id);

        // Etape 2 : Justificatif
        if (justificatifConfig && justificatifConfig.file) {
          const formData = new FormData();
          formData.append("typePiece", justificatifConfig.typePiece);
          formData.append("fichier", justificatifConfig.file);
          await apiClient.post(`/api/v5/demandes/${id}/justificatif`, formData);
        }
      }

      // Etape 3 : Soumission (si on n'est pas en mode brouillon)
      if (!skipSoumission) {
        await apiClient.post(`/api/v5/demandes/${id}/soumettre`);
      }

      router.push(`/demande/${id}?success=1`);
    } catch (err: any) {
      const status = err?.response?.status;
      const code = err?.response?.data?.code;
      const message = err?.response?.data?.message;

      if (status === 409 && code === "DOUBLON_DETECTE") {
        // Rejet strict : aucune confirmation possible, erreur bloquante.
        setApiError("Une demande existe déjà sur cette période. Soumission impossible (doublon interdit).");
      } else if (status === 422 && code === "CIRCUIT_NON_DETERMINE") {
        setApiError("Votre grade ne correspond à aucun circuit de validation configuré. Contactez l'administrateur RH.");
      } else if (code === "DUREE_INSUFFISANTE_CONGE_ANNUEL") {
        setApiError(message || "Durée insuffisante pour ce congé.");
      } else if (code === "DUREE_INSUFFISANTE_MISSION_LONGUE") {
        setApiError("La mission longue doit durer au minimum 15 jours.");
      } else if (code === "MOTIF_INCONNU") {
        setApiError("Ce motif n'est pas reconnu par le référentiel RH.");
      } else if (message) {
        // Toute autre erreur métier renvoyée par l'API (VALIDATION_ERREUR,
        // REQUETE_INVALIDE, DEMANDE_INTROUVABLE, etc.) : on affiche son message réel.
        setApiError(message);
      } else {
        // Ni réponse ni message exploitable (API injoignable, timeout, 500 sans corps).
        setApiError("Une erreur est survenue lors de la création. Veuillez réessayer.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return {
    soumettre,
    apiError,
    isSubmitting,
  };
}
