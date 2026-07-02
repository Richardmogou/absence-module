"use client";

import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useState } from "react";
import { FormField } from "@/components/FormField";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import FormPageLayout from "@/components/FormPageLayout";
import apiClient from "@/lib/api/client";
import { BackupSelector } from "@/components/BackupSelector";

const schema = z.object({
  dateDebut: z.string().min(1, "La date de début est obligatoire"),
  backupIdentifiantExterne: z.string().optional(),
});

type FormData = z.infer<typeof schema>;

const DUREE_SEMAINES = 14;

function ajouterSemaines(dateStr: string): { fin: string; jours: number } {
  if (!dateStr) return { fin: "", jours: 0 };
  const d = new Date(dateStr);
  const jours = DUREE_SEMAINES * 7;
  d.setDate(d.getDate() + jours);
  return {
    fin: d.toLocaleDateString("fr-FR", { day: "2-digit", month: "long", year: "numeric" }),
    jours,
  };
}

export default function CongeMaterniteePage() {
  const router = useRouter();
  const [apiError, setApiError] = useState<string | null>(null);

  const {
    register, handleSubmit, watch, setValue,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({ resolver: zodResolver(schema) });

  const dateDebut = watch("dateDebut");
  const { fin: dateFinEstimee, jours } = ajouterSemaines(dateDebut);

  async function onSubmit(data: FormData) {
    setApiError(null);
    try {
      const res = await apiClient.post("/api/v5/demandes", {
        type:      "CONGE_MATERNITE",
        dateDebut: data.dateDebut,
        backupIdentifiantExterne: data.backupIdentifiantExterne,
        // dateFin ignorée — calculée par le backend (dateDebut + 14 semaines)
        // demandeurIdentifiantExterne lu depuis le JWT — ne pas envoyer
      });
      // CONGE_MATERNITE est dans TYPES_AVEC_JUSTIFICATIF → dépôt certificat grossesse requis
      router.push(`/${res.data.id}/justificatif`);
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { code?: string } } })
        ?.response?.data?.code;
      if (code === "CIRCUIT_NON_DETERMINE")
        setApiError("Votre grade ne correspond à aucun circuit configuré. Contactez l'administrateur RH.");
      else
        setApiError("Une erreur est survenue. Veuillez réessayer.");
    }
  }

  return (
    <FormPageLayout
      image="/Image_africaine6_resize.png"
      accentColor="#96751A"
      badge="Nouvelle demande"
      title="Congé maternité"
      subtitle="Congé naissance & maternité. La durée légale est fixée à 14 semaines — la date de fin est calculée automatiquement."
      icon="👶"
    >
      <FormField
        id="dateDebut"
        label="Date de début souhaitée"
        type="date"
        error={errors.dateDebut}
        {...register("dateDebut")}
      />

      {/* Calcul automatique durée */}
      {dateFinEstimee ? (
        <div className="rounded-xl border border-gold-200 bg-gold-50 overflow-hidden">
          <div className="px-4 py-3 flex items-center gap-3 border-b border-gold-200">
            <span className="text-xl">📅</span>
            <p className="text-xs text-gold-700 font-ui font-semibold uppercase tracking-wider">
              Durée légale calculée automatiquement
            </p>
          </div>
          <div className="grid grid-cols-3 divide-x divide-gold-200">
            <div className="flex flex-col items-center py-4 gap-1">
              <span className="text-xs text-gold-600 font-ui uppercase tracking-wide">Durée</span>
              <span className="font-heading text-2xl font-bold text-gold-700">{DUREE_SEMAINES}</span>
              <span className="text-xs text-gold-500">semaines</span>
            </div>
            <div className="flex flex-col items-center py-4 gap-1">
              <span className="text-xs text-gold-600 font-ui uppercase tracking-wide">Jours</span>
              <span className="font-heading text-2xl font-bold text-gold-700">{jours}</span>
              <span className="text-xs text-gold-500">calendaires</span>
            </div>
            <div className="flex flex-col items-center py-4 gap-1 px-2">
              <span className="text-xs text-gold-600 font-ui uppercase tracking-wide">Retour</span>
              <span className="font-heading text-sm font-bold text-gold-700 text-center leading-tight">
                {dateFinEstimee}
              </span>
            </div>
          </div>
        </div>
      ) : (
        <div className="flex items-center gap-3 rounded-lg border border-neutral-200 bg-neutral-50 px-4 py-3">
          <span className="text-lg">ℹ️</span>
          <p className="text-xs text-neutral-500">
            Sélectionnez une date de début pour voir la date de fin estimée.
          </p>
        </div>
      )}

      {/* Back-up optionnel */}
      <div className="flex flex-col gap-1.5 mt-2">
        <label className="text-sm font-medium text-neutral-700">
          Identifiant du Back-up (collègue de même grade)
        </label>
        <BackupSelector 
          value={watch("backupIdentifiantExterne")} 
          onChange={(val) => setValue("backupIdentifiantExterne", val)} 
        />
        {errors.backupIdentifiantExterne && (
          <p className="text-xs text-secondary-500">{errors.backupIdentifiantExterne.message}</p>
        )}
      </div>

      {/* Info justificatif requis */}
      <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
        <span className="text-lg mt-0.5">📎</span>
        <p className="text-xs text-amber-700 leading-relaxed">
          Un <strong>certificat de grossesse</strong> vous sera demandé à l&apos;étape suivante.
          Préparez-le en format PDF ou image.
        </p>
      </div>

      {/* Info légale */}
      <div className="flex items-start gap-3 rounded-lg border border-neutral-200 bg-neutral-50 px-4 py-3">
        <span className="text-lg mt-0.5">⚖️</span>
        <p className="text-xs text-neutral-500 leading-relaxed">
          Conformément au <strong>Code du travail</strong>, la durée du congé maternité
          est fixée à <strong>14 semaines</strong> (98 jours) non modulables.
        </p>
      </div>

      {apiError && (
        <Alert variant="destructive">
          <AlertDescription>{apiError}</AlertDescription>
        </Alert>
      )}

      <Button
        type="button"
        disabled={isSubmitting || !dateFinEstimee}
        className="h-12 text-base mt-2"
        onClick={handleSubmit(onSubmit)}
        style={{ background: "#96751A" }}
      >
        {isSubmitting ? "Envoi en cours…" : "Continuer →"}
      </Button>
    </FormPageLayout>
  );
}
