"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { congeAnnuelSchema, type CongeAnnuelData } from "@/lib/schemas/absence";
import { FormField } from "@/components/FormField";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Checkbox } from "@/components/ui/checkbox";
import FormPageLayout from "@/components/FormPageLayout";
import apiClient from "@/lib/api/client";
import { BackupSelector } from "@/components/BackupSelector";

export default function CongeAnnuelPage() {
  const router = useRouter();
  const [apiError, setApiError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<CongeAnnuelData>({ resolver: zodResolver(congeAnnuelSchema) });

  const estPremiereFraction = watch("estPremiereFraction");

  async function onSubmit(data: CongeAnnuelData) {
    setApiError(null);
    try {
      const res = await apiClient.post("/api/v5/demandes", {
        type:                    "CONGE_ANNUEL",
        dateDebut:               data.dateDebut,
        dateFin:                 data.dateFin,
        backupIdentifiantExterne: data.backupIdentifiantExterne,
        numeroFraction:          data.numeroFraction ?? null,
        estPremiereFraction:     data.estPremiereFraction ?? null,
      });
      router.push(`/${res.data.id}/preview`);
    } catch (err: unknown) {
      const status = (err as any)?.response?.status;
      const code = (err as any)?.response?.data?.code;
      const message = (err as any)?.response?.data?.message;
      if (status === 401) {
        setApiError("Votre session a expiré. Veuillez vous reconnecter.");
      } else if (code === "CIRCUIT_NON_DETERMINE") {
        setApiError("Votre grade ne correspond à aucun circuit de validation. Contactez l'administrateur RH.");
      } else if (code === "DUREE_INSUFFISANTE_CONGE_ANNUEL" && message) {
        setApiError(message);
      } else {
        setApiError("Une erreur est survenue lors de la création. Veuillez réessayer.");
      }
    }
  }

  return (
    <FormPageLayout
      image="/Image_africaine6_resize.png"
      accentColor="#C41E22"
      badge="Nouvelle demande"
      title="Congé annuel"
      subtitle="Votre repos annuel réglementaire. Indiquez vos dates souhaitées — la validation suivra le circuit RH configuré."
      icon={
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="icon icon-tabler icons-tabler-outline icon-tabler-beach">
          <path stroke="none" d="M0 0h24v24H0z" fill="none" />
          <path d="M17.553 16.75a7.5 7.5 0 0 0 -10.606 0" />
          <path d="M18 3.804a6 6 0 0 0 -8.196 2.196l10.392 6a6 6 0 0 0 -2.196 -8.196" />
          <path d="M16.732 10c1.658 -2.87 2.225 -5.644 1.268 -6.196c-.957 -.552 -3.075 1.326 -4.732 4.196" />
          <path d="M15 9l-3 5.196" />
          <path d="M3 19.25a2.4 2.4 0 0 1 1 -.25a2.4 2.4 0 0 1 2 1a2.4 2.4 0 0 0 2 1a2.4 2.4 0 0 0 2 -1a2.4 2.4 0 0 1 2 -1a2.4 2.4 0 0 1 2 1a2.4 2.4 0 0 0 2 1a2.4 2.4 0 0 0 2 -1a2.4 2.4 0 0 1 2 -1a2.4 2.4 0 0 1 1 .25" />
        </svg>
      }
    >
      {/* Back-up obligatoire */}
      <div className="flex flex-col gap-1.5 relative">
        <label htmlFor="backupIdentifiantExterne" className="text-sm font-medium text-neutral-700">
          Identifiant du Back-up (collègue de même grade)
        </label>
        
        <BackupSelector 
          value={watch("backupIdentifiantExterne")} 
          onChange={(val) => setValue("backupIdentifiantExterne", val, { shouldValidate: true })}
          placeholder="— Obligatoire : rechercher un Back-up —"
        />
        
        {errors.backupIdentifiantExterne && (
          <p className="text-xs text-secondary-500">{errors.backupIdentifiantExterne.message}</p>
        )}
      </div>

      {/* Info Back-up */}
      <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
        <span className="text-lg mt-0.5">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" className="icon icon-tabler icons-tabler-outline icon-tabler-users">
            <path stroke="none" d="M0 0h24v24H0z" fill="none" />
            <path d="M5 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0" />
            <path d="M3 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2" />
            <path d="M16 3.13a4 4 0 0 1 0 7.75" />
            <path d="M21 21v-2a4 4 0 0 0 -3 -3.85" />
          </svg>
        </span>
        <p className="text-xs text-amber-700 leading-relaxed">
          Le Back-up est un <strong>collègue de même grade</strong> qui assurera
          la continuité de votre poste pendant votre absence.
          Il devra valider votre demande en première étape du circuit.
        </p>
      </div>

      {/* Dates */}
      <div className="grid grid-cols-2 gap-4">
        <FormField
          id="dateDebut"
          label="Date de début"
          type="date"
          error={errors.dateDebut}
          {...register("dateDebut")}
        />
        <FormField
          id="dateFin"
          label="Date de fin"
          type="date"
          error={errors.dateFin}
          {...register("dateFin")}
        />
      </div>

      {/* Fraction — optionnel */}
      <FormField
        id="numeroFraction"
        label="Numéro de fraction (optionnel)"
        type="number"
        min="1"
        placeholder="ex : 1, 2, 3…"
        error={errors.numeroFraction}
        {...register("numeroFraction", { valueAsNumber: true })}
      />

      {/* Première fraction */}
      <label className="flex items-center gap-3 cursor-pointer select-none">
        <Checkbox
          id="estPremiereFraction"
          checked={estPremiereFraction ?? false}
          onCheckedChange={(checked) =>
            setValue("estPremiereFraction", checked === true)
          }
        />
        <span className="text-sm font-medium text-neutral-700">
          Il s’agit de ma première fraction de congé pour cet exercice
        </span>
      </label>

      {/* Erreur API */}
      {apiError && (
        <Alert variant="destructive">
          <AlertDescription>{apiError}</AlertDescription>
        </Alert>
      )}

      <Button
        type="button"
        disabled={isSubmitting}
        className="h-12 text-base mt-2"
        onClick={handleSubmit(onSubmit)}
      >
        {isSubmitting ? "Envoi en cours…" : "Continuer vers l’aperçu →"}
      </Button>
    </FormPageLayout>
  );
}
