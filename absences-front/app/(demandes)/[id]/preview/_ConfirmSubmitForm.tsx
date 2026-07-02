"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { useRouter } from "next/navigation";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import apiClient from "@/lib/api/client";

interface ConfirmFormProps {
  id: string;
  doublonDetecte: boolean;
}

interface FormValues {
  confirmDoublon: boolean;
}

export default function ConfirmSubmitForm({ id, doublonDetecte }: ConfirmFormProps) {
  const router = useRouter();
  const [apiError, setApiError] = useState<string | null>(null);

  const { watch, setValue, handleSubmit, formState: { isSubmitting } } = useForm<FormValues>({
    defaultValues: { confirmDoublon: false },
  });

  const confirmDoublon = watch("confirmDoublon");
  const isDisabled = isSubmitting || (doublonDetecte && !confirmDoublon);

  async function onSubmit(data: FormValues) {
    setApiError(null);
    try {
      await apiClient.post(`/api/v5/demandes/${id}/soumettre?confirmDoublon=${data.confirmDoublon}`);
      router.push(`/${id}?success=1`);
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number; data?: { code?: string } } })?.response?.status;
      const code   = (err as { response?: { data?: { code?: string } } })?.response?.data?.code;
      if (status === 422 && code === "CIRCUIT_NON_DETERMINE") {
        setApiError("Votre grade ne correspond à aucun circuit de validation configuré. Contactez l'administrateur RH.");
      } else if (status === 409 && code === "DOUBLON_DETECTE") {
        setApiError("Une demande similaire existe déjà sur cette période. Cochez la case de confirmation pour soumettre malgré le doublon.");
      } else {
        setApiError("Une erreur est survenue. Veuillez réessayer.");
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="flex w-full flex-col gap-4">
      {doublonDetecte && (
        <Alert variant="destructive">
          <AlertDescription>
            Une demande similaire existe déjà sur une période proche.
          </AlertDescription>
          <label className="mt-3 flex items-center gap-2 cursor-pointer text-sm font-medium text-secondary-700">
            <Checkbox
              id="confirmDoublon"
              checked={confirmDoublon}
              onCheckedChange={(checked) => setValue("confirmDoublon", checked === true)}
            />
            Je confirme vouloir soumettre malgré ce doublon
          </label>
        </Alert>
      )}

      {apiError && (
        <Alert variant="destructive">
          <AlertDescription>{apiError}</AlertDescription>
        </Alert>
      )}

      <Button type="submit" disabled={isDisabled} className="self-end">
        {isSubmitting ? "Soumission…" : "Confirmer la soumission"}
      </Button>
    </form>
  );
}
