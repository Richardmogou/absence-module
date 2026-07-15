"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { FormField } from "@/components/FormField";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import FormPageLayout from "@/components/FormPageLayout";
import { BackupSelector } from "@/components/BackupSelector";
import { Plane } from "lucide-react";
import { useSoumission } from "@/lib/hooks/useSoumission";

const schema = z
  .object({
    dateDebut: z.string().min(1, "La date de départ est obligatoire"),
    dateFin:   z.string().min(1, "La date de retour est obligatoire"),
    objetMission: z.string().min(1, "L'objet de la mission est obligatoire").max(500),
    motifMission: z.string().min(1, "La justification est obligatoire").max(1000),
    destination: z.string().min(1, "La destination est obligatoire").max(200),
    categorie: z.string().min(1, "La catégorie est obligatoire"),
    backupIdentifiantExterne: z.string().optional(),
  })
  .refine((d) => new Date(d.dateFin) >= new Date(d.dateDebut), {
    message: "La date de retour doit être après la date de départ",
    path: ["dateFin"],
  });

type FormData = z.infer<typeof schema>;

function calculerJours(dateDebut: string, dateFin: string): number {
  if (!dateDebut || !dateFin) return 0;
  const diff = Math.ceil(
    (new Date(dateFin).getTime() - new Date(dateDebut).getTime()) /
      (1000 * 60 * 60 * 24)
  ) + 1;
  return Math.max(0, diff);
}

export default function MissionPage() {
  const {
    register, handleSubmit, watch, setValue,
    formState: { errors },
  } = useForm<FormData>({ resolver: zodResolver(schema) });

  const [dateDebut, dateFin] = watch(["dateDebut", "dateFin"]);
  const nombreJours = calculerJours(dateDebut, dateFin);

  const {
    soumettre,
    apiError,
    isSubmitting
  } = useSoumission();

  async function onSubmit(data: FormData) {
    const jours = calculerJours(data.dateDebut, data.dateFin);
    await soumettre({
      type:        "MISSION",
      dateDebut:   data.dateDebut,
      dateFin:     data.dateFin,
      nombreJours: jours,
      objetMission: data.objetMission,
      motifMission: data.motifMission,
      destination: data.destination,
      categorie: data.categorie,
      backupIdentifiantExterne: data.backupIdentifiantExterne,
    });
  }

  return (
    <FormPageLayout
      image="/Image_africaine5_resize.png"
      accentColor="#2C2C2C"
      badge="Nouvelle demande"
      title="Mission"
      subtitle="Déplacement professionnel classique. Un ordre de mission sera généré à l'issue des validations."
      icon={<Plane size={24} />}
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

      {nombreJours > 0 && (
        <div
          className="flex items-center justify-between rounded-lg border px-4 py-3 transition-colors border-[#2C2C2C] bg-[#F6F6F6]"
        >
          <div className="flex items-center gap-3">
            <Plane size={20} className="flex-shrink-0 text-[#2C2C2C]" />
            <div>
              <p className="text-xs uppercase tracking-wider font-ui font-semibold text-[#2C2C2C]">
                Durée calculée
              </p>
              <p className="text-lg font-heading font-bold text-[#2C2C2C]">
                {nombreJours} jour{nombreJours > 1 ? "s" : ""}
              </p>
            </div>
          </div>
        </div>
      )}

      <div className="flex flex-col gap-1.5 mt-2">
        <label htmlFor="categorie" className="text-sm font-medium text-neutral-700">Catégorie de la mission</label>
        <select
          id="categorie"
          className="h-10 w-full rounded-md border border-neutral-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          {...register("categorie")}
        >
          <option value="">Sélectionnez une catégorie</option>
          <option value="Terrain/Commerciale">Missions terrain / commerciales</option>
          <option value="Supervision/Audit">Missions supervision / audit</option>
          <option value="Formations/Séminaires">Formations et séminaires</option>
          <option value="Réunions institutionnelles">Réunions institutionnelles</option>
          <option value="Représentation externe">Représentation externe</option>
          <option value="Autre">Autre</option>
        </select>
        {errors.categorie && <p className="text-xs text-secondary-500">{errors.categorie.message}</p>}
      </div>

      <FormField
        id="destination"
        label="Ville / Pays de destination"
        placeholder="ex: Douala"
        error={errors.destination}
        {...register("destination")}
      />

      <FormField
        id="objetMission"
        label="Objet de la mission"
        placeholder="ex: Formation réglementaire"
        error={errors.objetMission}
        {...register("objetMission")}
      />

      <div className="flex flex-col gap-1.5 mt-2">
        <label htmlFor="motifMission" className="text-sm font-medium text-neutral-700">Justification / Motif détaillé</label>
        <textarea
          id="motifMission"
          rows={3}
          className="w-full rounded-md border border-neutral-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
          placeholder="Détaillez les raisons de cette mission..."
          {...register("motifMission")}
        />
        {errors.motifMission && <p className="text-xs text-secondary-500">{errors.motifMission.message}</p>}
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

      {apiError && (
        <Alert variant="destructive" className="mt-4">
          <AlertDescription>{apiError}</AlertDescription>
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
