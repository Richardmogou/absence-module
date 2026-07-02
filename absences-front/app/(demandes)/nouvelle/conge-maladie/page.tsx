"use client";

import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useState } from "react";
import { FormField } from "@/components/FormField";
import { Button } from "@/components/ui/button";
import FormPageLayout from "@/components/FormPageLayout";
import apiClient from "@/lib/api/client";
import { BackupSelector } from "@/components/BackupSelector";

const schema = z
  .object({
    dateDebut: z.string().min(1, "La date de début est obligatoire"),
    dateFin:   z.string().min(1, "La date de fin est obligatoire"),
    backupIdentifiantExterne: z.string().optional(),
  })
  .refine((d) => new Date(d.dateFin) >= new Date(d.dateDebut), {
    message: "La date de fin doit être après la date de début",
    path: ["dateFin"],
  });

type FormData = z.infer<typeof schema>;

export default function CongeMaladiePage() {
  const router = useRouter();
  const [erreurApi, setErreurApi] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({ resolver: zodResolver(schema) });

  async function onSubmit(data: FormData) {
    setErreurApi(null);
    try {
      const res = await apiClient.post("/api/v5/demandes", {
        type:      "CONGE_MALADIE",
        dateDebut: data.dateDebut,
        dateFin:   data.dateFin,
        backupIdentifiantExterne: data.backupIdentifiantExterne,
      });
      // Redirige vers la page justificatif dédiée (certificat médical obligatoire)
      router.push(`/${res.data.id}/justificatif`);
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { code?: string } } })
        ?.response?.data?.code;
      if (code === "CHEVAUCHEMENT_DATES")
        setErreurApi("Une demande existe déjà sur cette période.");
      else
        setErreurApi("Une erreur est survenue. Veuillez réessayer.");
    }
  }

  return (
    <FormPageLayout
      image="/Image_Afrique3_resize.png"
      accentColor="#1A1A2E"
      badge="Nouvelle demande"
      title="Congé maladie"
      subtitle="Arrêt médical certifié. Joignez votre certificat médical à l'étape suivante — il sera transmis automatiquement au service RH."
      icon="🏥"
    >
      {/* Back-up optionnel */}
      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-neutral-700">
          Back-up (collègue de même grade) — optionnel
        </label>
        <BackupSelector
          value={watch("backupIdentifiantExterne")}
          onChange={(val) => setValue("backupIdentifiantExterne", val)}
        />
        {errors.backupIdentifiantExterne && (
          <p className="text-xs text-secondary-500">{errors.backupIdentifiantExterne.message}</p>
        )}
      </div>

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

      {/* Info certificat */}
      <div className="flex items-start gap-3 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3">
        <span className="text-lg mt-0.5">📎</span>
        <p className="text-xs text-blue-700 leading-relaxed">
          Un <strong>certificat médical</strong> vous sera demandé à l&apos;étape suivante.
          Préparez-le en format PDF ou image.
        </p>
      </div>

      {erreurApi && (
        <p className="text-xs text-secondary-500 font-medium">{erreurApi}</p>
      )}

      <Button
        type="button"
        disabled={isSubmitting}
        className="h-12 text-base mt-2"
        onClick={handleSubmit(onSubmit)}
      >
        {isSubmitting ? "Envoi en cours…" : "Continuer →"}
      </Button>
    </FormPageLayout>
  );
}
