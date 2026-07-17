"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { congeAnnuelSchema, type CongeAnnuelData } from "@/lib/schemas/absence";
import { FormField } from "@/components/FormField";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import FormPageLayout from "@/components/FormPageLayout";
import { BackupSelector } from "@/components/BackupSelector";
import { useSoumission } from "@/lib/hooks/useSoumission";

export default function CongeAnnuelPage() {
  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<CongeAnnuelData>({ resolver: zodResolver(congeAnnuelSchema) });

  const {
    soumettre,
    apiError,
    isSubmitting
  } = useSoumission();

  async function onSubmit(data: CongeAnnuelData) {
    await soumettre({
      type: "CONGE_ANNUEL",
      dateDebut: data.dateDebut,
      dateFin: data.dateFin,
      backupIdentifiantExterne: data.backupIdentifiantExterne,
    });
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
      <div className="space-y-6">
        {/* SECTION 1: BACK-UP */}
        <div className="rounded-md border border-neutral-200 bg-white shadow-sm">
          <div className="bg-neutral-50 px-4 py-2 border-b border-neutral-200 flex items-center justify-between rounded-t-md">
            <h3 className="text-sm font-semibold text-neutral-800 uppercase tracking-wider">Délégation (Back-up)</h3>
            <span className="text-[10px] font-bold text-neutral-500 bg-neutral-200/80 px-2 py-0.5 rounded tracking-wide">OBLIGATOIRE</span>
          </div>
          <div className="p-4 space-y-4">
            <div className="flex flex-col gap-2 relative">
              <label htmlFor="backupIdentifiantExterne" className="text-sm font-medium text-neutral-700">
                Identifiant du Back-up (collègue de même grade)
              </label>
              <div className="w-full">
                <BackupSelector 
                  value={watch("backupIdentifiantExterne")} 
                  onChange={(val) => setValue("backupIdentifiantExterne", val, { shouldValidate: true })}
                  placeholder="Rechercher par nom ou matricule..."
                />
              </div>
              {errors.backupIdentifiantExterne && (
                <p className="text-xs text-red-600 font-medium mt-1">{errors.backupIdentifiantExterne.message}</p>
              )}
            </div>

            <div className="flex items-start gap-3 rounded bg-neutral-50 border-l-4 border-neutral-400 px-4 py-3">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="text-neutral-500 mt-0.5 shrink-0">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
              </svg>
              <p className="text-sm text-neutral-700 leading-relaxed">
                Le Back-up désigné assurera la continuité de service. Cette personne interviendra comme premier niveau d'approbation conformément aux procédures internes en vigueur.
              </p>
            </div>
          </div>
        </div>

        {/* SECTION 2: PERIODE */}
        <div className="rounded-md border border-neutral-200 bg-white shadow-sm">
          <div className="bg-neutral-50 px-4 py-2 border-b border-neutral-200 rounded-t-md">
            <h3 className="text-sm font-semibold text-neutral-800 uppercase tracking-wider">Période d'absence</h3>
          </div>
          <div className="p-4 grid grid-cols-1 md:grid-cols-2 gap-6">
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
        </div>
      </div>

      {apiError && (
        <Alert variant="destructive" className="mt-6 rounded-md border-red-300 bg-red-50">
          <AlertDescription className="text-[#C41E22] font-medium">{apiError}</AlertDescription>
        </Alert>
      )}


      {/* NAV BUTTONS */}
      <div className="pt-6 flex justify-end items-center border-t border-neutral-200 mt-8">
        <Button
          type="button"
          disabled={isSubmitting}
          className="h-10 px-6 bg-[#C41E22] hover:bg-[#A0181C] text-white font-medium rounded-md shadow-sm transition-colors flex items-center gap-2"
          onClick={handleSubmit(onSubmit)}
        >
          {isSubmitting ? (
            <>
              <svg className="animate-spin h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              Traitement en cours...
            </>
          ) : (
            <>
              Soumettre la demande
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M5 12h14" />
                <path d="m12 5 7 7-7 7" />
              </svg>
            </>
          )}
        </Button>
      </div>
    </FormPageLayout>
  );
}
