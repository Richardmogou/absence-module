"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useState, useRef } from "react";
import { FormField } from "@/components/FormField";
import { Button } from "@/components/ui/button";
import FormPageLayout from "@/components/FormPageLayout";
import { BackupSelector } from "@/components/BackupSelector";
import { ArrowRight, Paperclip, Stethoscope, FileText, FolderOpen } from "lucide-react";
import { useSoumission } from "@/lib/hooks/useSoumission";
import { Alert, AlertDescription } from "@/components/ui/alert";

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
  const {
    register,
    handleSubmit,
    watch,
    setValue,
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
      setLocalError("Le certificat médical est obligatoire.");
      return;
    }
    await soumettre(
      {
        type: "CONGE_MALADIE",
        dateDebut: data.dateDebut,
        dateFin: data.dateFin,
        backupIdentifiantExterne: data.backupIdentifiantExterne,
      },
      file ? { file, typePiece: "CERTIFICAT_MEDICAL" } : undefined
    );
  }

  return (
    <FormPageLayout
      image="/Image_Afrique3_resize.png"
      accentColor="#1A1A2E"
      badge="Nouvelle demande"
      title="Congé maladie"
      subtitle="Arrêt médical certifié. Veuillez joindre votre certificat médical ci-dessous."
      icon={<Stethoscope size={24} />}
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

      <div className="flex flex-col gap-2 mt-4">
        <label className="text-sm font-medium text-neutral-700">Certificat médical (Obligatoire)</label>
        <div
          className="relative flex flex-col items-center justify-center rounded-xl border-2 border-dashed px-6 py-6 text-center transition-colors cursor-pointer"
          style={{
            borderColor: file ? "#1A1A2E" : "#D1D5DB",
            background:  file ? "#1A1A2E0A" : "#FAFAFA",
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
                <FileText size={32} style={{ color: "#1A1A2E" }} />
                <p className="text-sm font-semibold" style={{ color: "#1A1A2E" }}>{file.name}</p>
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

      {(apiError || localError) && (
        <Alert variant="destructive" className="mt-4">
          <AlertDescription>{apiError || localError}</AlertDescription>
        </Alert>
      )}


      <Button
        type="button"
        disabled={isSubmitting}
        className="h-12 text-base mt-4"
        onClick={handleSubmit(onSubmit)}
      >
        {isSubmitting ? "Envoi en cours…" : "Soumettre la demande"}
      </Button>
    </FormPageLayout>
  );
}
