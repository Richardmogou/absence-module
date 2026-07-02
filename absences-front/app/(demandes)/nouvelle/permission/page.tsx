"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { FormField } from "@/components/FormField";
import { Button } from "@/components/ui/button";
import FormPageLayout from "@/components/FormPageLayout";
import apiClient from "@/lib/api/client";
import { BackupSelector } from "@/components/BackupSelector";

interface MotifPermission {
  codeMotif: string;
  libelle: string;
  dureeJours: number;
  justificatifRequis: boolean;
}

const schema = z.object({
  dateDebut:   z.string().min(1, "La date est obligatoire"),
  codeMotif:   z.string().min(1, "Le motif est obligatoire"),
  nombreJours: z.string().optional(),
  backupIdentifiantExterne: z.string().optional(),
});

type FormData = z.infer<typeof schema>;

export default function PermissionPage() {
  const router = useRouter();
  const [motifs, setMotifs]         = useState<MotifPermission[]>([]);
  const [loadingMotifs, setLoading] = useState(true);
  const [erreurApi, setErreurApi]   = useState<string | null>(null);

  const {
    register, handleSubmit, watch, setValue,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({ resolver: zodResolver(schema) });

  useEffect(() => {
    apiClient
      .get("/api/v5/referentiel/bareme-permission")
      .then((r) => setMotifs(r.data))
      .catch(() => setErreurApi("Impossible de charger les motifs. Rechargez la page."))
      .finally(() => setLoading(false));
  }, []);

  const codeMotifSelectionne = watch("codeMotif");
  const motifSelectionne     = motifs.find((m) => m.codeMotif === codeMotifSelectionne);
  const estAutreMotif        = codeMotifSelectionne === "AUTRE_MOTIF";
  const justificatifRequis   = motifSelectionne?.justificatifRequis ?? false;

  async function onSubmit(data: FormData) {
    setErreurApi(null);
    try {
      const res = await apiClient.post("/api/v5/demandes", {
        type:            "PERMISSION",
        dateDebut:       data.dateDebut,
        motifPermission: data.codeMotif,
        backupIdentifiantExterne: data.backupIdentifiantExterne,
        ...(estAutreMotif && data.nombreJours
          ? { nombreJours: Number(data.nombreJours) }
          : {}),
      });
      // justificatif requis → page upload dédiée, sinon → preview
      router.push(justificatifRequis
        ? `/${res.data.id}/justificatif`
        : `/${res.data.id}/preview`
      );
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { code?: string } } })
        ?.response?.data?.code;
      if (code === "MOTIF_INCONNU")
        setErreurApi("Ce motif n'est pas reconnu par le référentiel RH.");
      else if (code === "MOTIF_REQUIS")
        setErreurApi("Le motif de permission est obligatoire.");
      else
        setErreurApi("Une erreur est survenue. Veuillez réessayer.");
    }
  }

  return (
    <FormPageLayout
      image="/Image_africaine5_resize.png"
      accentColor="#B8932A"
      badge="Nouvelle demande"
      title="Permission"
      subtitle="Absence courte durée autorisée. Sélectionnez le motif réglementaire — la durée est fixée automatiquement par le barème RH."
      icon="📋"
    >
      <FormField
        id="dateDebut"
        label="Date de la permission"
        type="date"
        error={errors.dateDebut}
        {...register("dateDebut")}
      />

      {/* Select motif */}
      <div className="flex flex-col gap-1.5">
        <label htmlFor="codeMotif" className="text-sm font-medium text-neutral-700">
          Motif de permission
        </label>
        <select
          id="codeMotif"
          disabled={loadingMotifs}
          className="h-10 w-full rounded border border-neutral-300 bg-white px-3 py-2 text-sm text-primary-500 focus:outline-none focus:ring-2 focus:ring-gold-500 disabled:opacity-50 disabled:cursor-not-allowed"
          {...register("codeMotif")}
        >
          <option value="">
            {loadingMotifs ? "Chargement des motifs…" : "— Sélectionner un motif —"}
          </option>
          {motifs.map((m) => (
            <option key={m.codeMotif} value={m.codeMotif}>
              {m.libelle}
            </option>
          ))}
        </select>
        {errors.codeMotif && (
          <p className="text-xs text-secondary-500">{errors.codeMotif.message}</p>
        )}
      </div>

      {/* Durée accordée automatiquement */}
      {motifSelectionne && !estAutreMotif && (
        <div className="flex items-center justify-between rounded-lg border border-gold-200 bg-gold-50 px-4 py-3">
          <div className="flex items-center gap-3">
            <span className="text-xl">⏱</span>
            <div>
              <p className="text-xs text-gold-700 font-ui uppercase tracking-wider">Durée accordée</p>
              <p className="text-lg font-heading font-bold text-gold-600">
                {motifSelectionne.dureeJours === 1
                  ? "1 jour"
                  : `${motifSelectionne.dureeJours} jours`}
              </p>
            </div>
          </div>
          {/* Badge justificatif requis */}
          {justificatifRequis && (
            <span className="flex items-center gap-1.5 rounded-full border border-amber-300 bg-amber-50 px-2.5 py-1 text-xxs font-semibold text-amber-700">
              📎 Justificatif requis
            </span>
          )}
        </div>
      )}

      {/* Durée libre si AUTRE_MOTIF */}
      {estAutreMotif && (
        <FormField
          id="nombreJours"
          label="Durée souhaitée (jours)"
          type="number"
          min="1"
          placeholder="ex: 2"
          error={errors.nombreJours}
          {...register("nombreJours")}
        />
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

      {/* Info justificatif si requis */}
      {justificatifRequis && (
        <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
          <span className="text-lg mt-0.5">📎</span>
          <p className="text-xs text-amber-700 leading-relaxed">
            Ce motif requiert un <strong>justificatif</strong>. Vous pourrez le déposer
            à l&apos;étape suivante avant la soumission.
          </p>
        </div>
      )}

      {/* Erreur API */}
      {erreurApi && (
        <p className="text-xs text-secondary-500 font-medium">{erreurApi}</p>
      )}

      <Button
        type="button"
        disabled={isSubmitting || loadingMotifs}
        className="h-12 text-base mt-2"
        style={{ background: "#B8932A" }}
        onClick={handleSubmit(onSubmit)}
      >
        {isSubmitting ? "Envoi en cours…" : "Continuer →"}
      </Button>
    </FormPageLayout>
  );
}
