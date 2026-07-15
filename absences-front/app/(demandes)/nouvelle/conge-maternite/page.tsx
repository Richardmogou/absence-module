"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useState, useRef } from "react";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import FormPageLayout from "@/components/FormPageLayout";
import { BackupSelector } from "@/components/BackupSelector";
import { ArrowRight, Info, Paperclip, Scale, FileText, FolderOpen } from "lucide-react";
import { useSoumission } from "@/lib/hooks/useSoumission";

const schema = z.object({
  backupIdentifiantExterne: z.string().optional(),
});

type FormData = z.infer<typeof schema>;

const DUREE_SEMAINES = 14;

export default function CongeMaterniteePage() {
  const {
    handleSubmit, watch, setValue,
    formState: { errors },
  } = useForm<FormData>({ resolver: zodResolver(schema) });

  const {
    soumettre,
    apiError,
    isSubmitting
  } = useSoumission();

  const fileRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);

  async function onSubmit(data: FormData) {
    setLocalError(null);
    if (!file) {
      setLocalError("Le certificat de grossesse est obligatoire.");
      return;
    }

    await soumettre(
      {
        type: "CONGE_MATERNITE",
        backupIdentifiantExterne: data.backupIdentifiantExterne,
      },
      file ? { file, typePiece: "CERTIFICAT_GROSSESSE" } : undefined
    );
  }

  return (
    <FormPageLayout
      image="/Image_africaine6_resize.png"
      accentColor="#96751A"
      badge="Nouvelle demande"
      title="Congé maternité"
      subtitle="Congé naissance & maternité. La durée légale est fixée à 14 semaines — la date de fin est calculée automatiquement."
      icon={
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="icon icon-tabler icons-tabler-outline icon-tabler-baby-carriage">
	<path stroke="none" d="M0 0h24v24H0z" fill="none" />
	<path d="M6 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0" />
	<path d="M16 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0" />
	<path d="M2 5h2.5l1.632 4.897a6 6 0 0 0 5.693 4.103h2.675a5.5 5.5 0 0 0 0 -11h-.5v6" />
	<path d="M6 9h14" />
	<path d="M9 17l1 -3" />
	<path d="M16 14l1 3" />
</svg>
      }
    >
      {/* Pas de date de début ici : elle est fixée par l'analyste RH à l'instruction. */}
      <div className="flex items-start gap-3 rounded-xl border border-gold-200 bg-gold-50 px-4 py-4">
        <Info size={18} className="text-gold-600 flex-shrink-0 mt-0.5" />
        <div className="flex flex-col gap-1">
          <p className="text-sm font-semibold text-gold-800">
            Date de début fixée par les Ressources Humaines
          </p>
          <p className="text-xs text-gold-700 leading-relaxed">
            Vous n'avez pas à saisir de date. L'analyste RH déterminera la date de début lors
            de l'instruction de votre dossier ; la date de fin ({DUREE_SEMAINES} semaines /
            98 jours) et la durée seront alors calculées automatiquement.
          </p>
        </div>
      </div>

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

      <div className="flex flex-col gap-2 mt-4">
        <label className="text-sm font-medium text-neutral-700">Certificat de grossesse (Obligatoire)</label>
        <div
          className="relative flex flex-col items-center justify-center rounded-xl border-2 border-dashed px-6 py-6 text-center transition-colors cursor-pointer"
          style={{
            borderColor: file ? "#96751A" : "#D1D5DB",
            background:  file ? "#96751A0A" : "#FAFAFA",
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
                <FileText size={32} style={{ color: "#96751A" }} />
                <p className="text-sm font-semibold" style={{ color: "#96751A" }}>{file.name}</p>
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

      {/* Info légale */}
      <div className="flex items-start gap-3 rounded-lg border border-neutral-200 bg-neutral-50 px-4 py-3 mt-4">
        <Scale size={18} className="text-neutral-400 flex-shrink-0 mt-0.5" />
        <p className="text-xs text-neutral-500 leading-relaxed">
          Conformément au <strong>Code du travail</strong>, la durée du congé maternité
          est fixée à <strong>14 semaines</strong> (98 jours) non modulables.
        </p>
      </div>

      {(apiError || localError) && (
        <Alert variant="destructive" className="mt-4">
          <AlertDescription>{apiError || localError}</AlertDescription>
        </Alert>
      )}


      <Button
        type="button"
        disabled={isSubmitting}
        className="h-12 text-base mt-4 text-white"
        onClick={handleSubmit(onSubmit)}
        style={{ background: "#96751A" }}
      >
        {isSubmitting ? "Envoi en cours…" : "Soumettre la demande"}
      </Button>
    </FormPageLayout>
  );
}
