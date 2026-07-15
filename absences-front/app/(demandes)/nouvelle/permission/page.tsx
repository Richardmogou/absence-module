"use client";

import { useEffect, useState, useRef } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { FormField } from "@/components/FormField";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import FormPageLayout from "@/components/FormPageLayout";
import apiClient from "@/lib/api/client";
import { BackupSelector } from "@/components/BackupSelector";
import { Paperclip, Timer, FileText, FolderOpen } from "lucide-react";
import { useSoumission } from "@/lib/hooks/useSoumission";

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
  const [motifs, setMotifs]         = useState<MotifPermission[]>([]);
  const [loadingMotifs, setLoading] = useState(true);
  const [localError, setLocalError]   = useState<string | null>(null);

  const {
    register, handleSubmit, watch, setValue,
    formState: { errors },
  } = useForm<FormData>({ resolver: zodResolver(schema) });

  const {
    soumettre,
    apiError,
    isSubmitting
  } = useSoumission();

  const fileRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);

  useEffect(() => {
    apiClient
      .get("/api/v5/referentiel/bareme-permission")
      .then((r) => setMotifs(r.data))
      .catch(() => setLocalError("Impossible de charger les motifs. Rechargez la page."))
      .finally(() => setLoading(false));
  }, []);

  const codeMotifSelectionne = watch("codeMotif");
  const motifSelectionne     = motifs.find((m) => m.codeMotif === codeMotifSelectionne);
  const estAutreMotif        = codeMotifSelectionne === "AUTRE_MOTIF";
  const justificatifRequis   = motifSelectionne?.justificatifRequis ?? false;

  async function onSubmit(data: FormData) {
    setLocalError(null);
    if (justificatifRequis && !file) {
      setLocalError("Le justificatif est obligatoire pour ce motif.");
      return;
    }
    
    await soumettre(
      {
        type:            "PERMISSION",
        dateDebut:       data.dateDebut,
        motifPermission: data.codeMotif,
        backupIdentifiantExterne: data.backupIdentifiantExterne,
        ...(estAutreMotif && data.nombreJours
          ? { nombreJours: Number(data.nombreJours) }
          : {}),
      },
      (justificatifRequis && file) ? { file, typePiece: "JUSTIFICATIF_PERMISSION" } : undefined
    );
  }

  return (
    <FormPageLayout
      image="/Image_africaine5_resize.png"
      accentColor="#B8932A"
      badge="Nouvelle demande"
      title="Permission"
      subtitle="Absence courte durée autorisée. Sélectionnez le motif réglementaire — la durée est fixée automatiquement par le barème RH."
      icon={
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="icon icon-tabler icons-tabler-outline icon-tabler-file-dots"><path stroke="none" d="M0 0h24v24H0z" fill="none" /><path d="M14 3v4a1 1 0 0 0 1 1h4" /><path d="M17 21h-10a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2h7l5 5v11a2 2 0 0 1 -2 2" /><path d="M9 14v.01" /><path d="M12 14v.01" /><path d="M15 14v.01" /></svg>
      }
    >
      <FormField
        id="dateDebut"
        label="Date de la permission"
        type="date"
        error={errors.dateDebut}
        {...register("dateDebut")}
      />

      {/* Select motif */}
      <div className="flex flex-col gap-1.5 mt-4">
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
        <div className="flex items-center justify-between rounded-lg border border-gold-200 bg-gold-50 px-4 py-3 mt-4">
          <div className="flex items-center gap-3">
            <Timer size={20} className="text-gold-600 flex-shrink-0" />
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
              <Paperclip size={12} /> Justificatif requis
            </span>
          )}
        </div>
      )}

      {/* Durée libre si AUTRE_MOTIF */}
      {estAutreMotif && (
        <div className="mt-4">
          <FormField
            id="nombreJours"
            label="Durée souhaitée (jours)"
            type="number"
            min="1"
            placeholder="ex: 2"
            error={errors.nombreJours}
            {...register("nombreJours")}
          />
        </div>
      )}

      {/* Back-up optionnel */}
      <div className="flex flex-col gap-1.5 mt-4">
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
        <div className="flex flex-col gap-2 mt-4">
          <label className="text-sm font-medium text-neutral-700">Justificatif (Obligatoire)</label>
          <div
            className="relative flex flex-col items-center justify-center rounded-xl border-2 border-dashed px-6 py-6 text-center transition-colors cursor-pointer"
            style={{
              borderColor: file ? "#B8932A" : "#D1D5DB",
              background:  file ? "#B8932A0A" : "#FAFAFA",
            }}
          >
            <input
              ref={fileRef}
              type="file"
              accept=".pdf,.jpg,.jpeg,.png"
              className="absolute inset-0 opacity-0 cursor-pointer"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
            <div className="flex flex-col items-center gap-3 pointer-events-none">
              {file ? (
                <>
                  <FileText size={32} style={{ color: "#B8932A" }} />
                  <p className="text-sm font-semibold" style={{ color: "#B8932A" }}>{file.name}</p>
                </>
              ) : (
                <>
                  <FolderOpen size={32} className="text-neutral-400" />
                  <p className="text-sm text-neutral-500">Glissez votre fichier ici ou cliquez pour parcourir</p>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {(apiError || localError) && (
        <Alert variant="destructive" className="mt-4">
          <AlertDescription>{apiError || localError}</AlertDescription>
        </Alert>
      )}


      <Button
        type="button"
        disabled={isSubmitting || loadingMotifs}
        className="h-12 text-base mt-4 text-white"
        style={{ background: "#B8932A" }}
        onClick={handleSubmit(onSubmit)}
      >
        {isSubmitting ? "Envoi en cours…" : "Soumettre la demande"}
      </Button>
    </FormPageLayout>
  );
}
