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

const DUREE_MIN_JOURS = 15;

const schema = z
  .object({
    dateDebut: z.string().min(1, "La date de départ est obligatoire"),
    dateFin:   z.string().min(1, "La date de retour est obligatoire"),
    motif:     z.string().max(500).optional(),
    backupIdentifiantExterne: z.string().optional(),
  })
  .refine((d) => new Date(d.dateFin) >= new Date(d.dateDebut), {
    message: "La date de retour doit être après la date de départ",
    path: ["dateFin"],
  })
  .refine(
    (d) => {
      if (!d.dateDebut || !d.dateFin) return true;
      const diff = Math.ceil(
        (new Date(d.dateFin).getTime() - new Date(d.dateDebut).getTime()) /
          (1000 * 60 * 60 * 24)
      ) + 1;
      return diff >= DUREE_MIN_JOURS;
    },
    {
      message: `La mission doit durer au minimum ${DUREE_MIN_JOURS} jours`,
      path: ["dateFin"],
    }
  );

type FormData = z.infer<typeof schema>;

function calculerJours(dateDebut: string, dateFin: string): number {
  if (!dateDebut || !dateFin) return 0;
  const diff = Math.ceil(
    (new Date(dateFin).getTime() - new Date(dateDebut).getTime()) /
      (1000 * 60 * 60 * 24)
  ) + 1;
  return Math.max(0, diff);
}

export default function MissionLonguePage() {
  const router = useRouter();
  const [apiError, setApiError] = useState<string | null>(null);

  const {
    register, handleSubmit, watch, setValue,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({ resolver: zodResolver(schema) });

  const [dateDebut, dateFin] = watch(["dateDebut", "dateFin"]);
  const nombreJours = calculerJours(dateDebut, dateFin);
  const dureeValide = nombreJours >= DUREE_MIN_JOURS;

  async function onSubmit(data: FormData) {
    setApiError(null);
    const jours = calculerJours(data.dateDebut, data.dateFin);
    try {
      const res = await apiClient.post("/api/v5/demandes", {
        type:        "MISSION_LONGUE",
        dateDebut:   data.dateDebut,
        dateFin:     data.dateFin,
        nombreJours: jours,
        objetMission: data.motif ?? null,
        backupIdentifiantExterne: data.backupIdentifiantExterne,
      });
      // MISSION_LONGUE est dans TYPES_AVEC_JUSTIFICATIF → dépôt ordre de mission requis
      router.push(`/${res.data.id}/justificatif`);
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { code?: string } } })
        ?.response?.data?.code;
      if (code === "DUREE_INSUFFISANTE_MISSION_LONGUE")
        setApiError(`La mission doit durer au minimum ${DUREE_MIN_JOURS} jours.`);
      else if (code === "CIRCUIT_NON_DETERMINE")
        setApiError("Votre grade ne correspond à aucun circuit configuré. Contactez l'administrateur RH.");
      else
        setApiError("Une erreur est survenue. Veuillez réessayer.");
    }
  }

  return (
    <FormPageLayout
      image="/Image_africaine5_resize.png"
      accentColor="#2C2C2C"
      badge="Nouvelle demande"
      title="Mission longue durée"
      subtitle="Déplacement professionnel étendu (≥ 15 jours). Déclenche une validation renforcée incluant la Direction Générale pour les agents."
      icon="✈️"
    >
      <div className="grid grid-cols-2 gap-4">
        <FormField
          id="dateDebut"
          label="Date de départ"
          type="date"
          error={errors.dateDebut}
          {...register("dateDebut")}
        />
        <FormField
          id="dateFin"
          label="Date de retour"
          type="date"
          error={errors.dateFin}
          {...register("dateFin")}
        />
      </div>

      {/* Calcul durée en temps réel */}
      {nombreJours > 0 && (
        <div
          className="flex items-center justify-between rounded-lg border px-4 py-3 transition-colors"
          style={{
            borderColor: dureeValide ? "#2C2C2C" : "#DC2626",
            background:  dureeValide ? "#F6F6F6"  : "#FEF2F2",
          }}
        >
          <div className="flex items-center gap-3">
            <span className="text-xl">{dureeValide ? "✈️" : "⚠️"}</span>
            <div>
              <p
                className="text-xs uppercase tracking-wider font-ui font-semibold"
                style={{ color: dureeValide ? "#2C2C2C" : "#DC2626" }}
              >
                Durée calculée
              </p>
              <p
                className="text-lg font-heading font-bold"
                style={{ color: dureeValide ? "#2C2C2C" : "#DC2626" }}
              >
                {nombreJours} jour{nombreJours > 1 ? "s" : ""}
              </p>
            </div>
          </div>
          {!dureeValide && (
            <span className="text-xs text-secondary-600 font-medium">
              Minimum {DUREE_MIN_JOURS} jours requis
            </span>
          )}
        </div>
      )}

      <FormField
        id="motif"
        label="Objet / informations complémentaires (optionnel)"
        placeholder="ex: Formation réglementaire UEMOA — Dakar"
        error={errors.motif}
        {...register("motif")}
      />

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

      {/* Info ordre de mission */}
      <div className="flex items-start gap-3 rounded-lg border border-neutral-200 bg-neutral-50 px-4 py-3">
        <span className="text-lg mt-0.5">📎</span>
        <p className="text-xs text-neutral-600 leading-relaxed">
          Un <strong>ordre de mission</strong> vous sera demandé à l&apos;étape suivante.
          Préparez-le en format PDF ou image.
        </p>
      </div>

      {/* Avertissement DG */}
      <div className="flex items-start gap-3 rounded-lg border border-primary-200 bg-primary-50 px-4 py-3">
        <span className="text-lg mt-0.5">⚠️</span>
        <p className="text-xs text-primary-600 leading-relaxed">
          Les missions longue durée nécessitent une{" "}
          <strong>validation Direction Générale</strong> en plus du circuit RH standard.
        </p>
      </div>

      {apiError && (
        <Alert variant="destructive">
          <AlertDescription>{apiError}</AlertDescription>
        </Alert>
      )}

      <Button
        type="button"
        disabled={isSubmitting || !dureeValide}
        className="h-12 text-base mt-2"
        onClick={handleSubmit(onSubmit)}
      >
        {isSubmitting ? "Envoi en cours…" : "Continuer →"}
      </Button>
    </FormPageLayout>
  );
}
