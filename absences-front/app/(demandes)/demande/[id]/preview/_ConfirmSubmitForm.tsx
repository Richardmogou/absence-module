"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import apiClient from "@/lib/api/client";

interface ConfirmFormProps {
  id: string;
  doublonDetecte: boolean;
}

export default function ConfirmSubmitForm({ id, doublonDetecte }: ConfirmFormProps) {
  const router = useRouter();
  const [apiError, setApiError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function soumettre() {
    setApiError(null);
    setIsSubmitting(true);
    try {
      await apiClient.post(`/api/v5/demandes/${id}/soumettre`);
      router.push(`/demande/${id}?success=1`);
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      const code   = (err as { response?: { data?: { code?: string } } })?.response?.data?.code;
      if (status === 422 && code === "CIRCUIT_NON_DETERMINE") {
        setApiError("Votre grade ne correspond à aucun circuit de validation configuré. Contactez l'administrateur RH.");
      } else if (status === 409 && code === "DOUBLON_DETECTE") {
        setApiError("Une demande existe déjà sur cette période. Soumission impossible (doublon interdit).");
      } else {
        setApiError("Une erreur est survenue. Veuillez réessayer.");
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="flex w-full flex-col gap-4">
      {/* Rejet strict : un doublon détecté bloque la soumission (pas de contournement). */}
      {doublonDetecte && (
        <Alert variant="destructive">
          <AlertDescription>
            Une demande existe déjà sur une période proche. La soumission est impossible (doublon interdit).
          </AlertDescription>
        </Alert>
      )}

      {apiError && (
        <Alert variant="destructive">
          <AlertDescription>{apiError}</AlertDescription>
        </Alert>
      )}

      <Button
        type="button"
        onClick={soumettre}
        disabled={isSubmitting || doublonDetecte}
        className="self-end"
      >
        {isSubmitting ? "Soumission…" : "Confirmer la soumission"}
      </Button>
    </div>
  );
}
