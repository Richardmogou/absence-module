"use client";

import { use, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import Image from "next/image";
import { FormField } from "@/components/FormField";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import apiClient from "@/lib/api/client";

const schema = z.object({
  dateRetourEffective: z.string().min(1, "La date de retour est obligatoire"),
});

type FormData = z.infer<typeof schema>;

interface Demande {
  type?: string;
  typeAbsence?: string;
  dateDebut?: string;
  dateFin?: string;
  nombreJours?: number;
  statut?: string;
  demandeurIdentifiantExterne?: string;
}

const TYPE_LABELS: Record<string, string> = {
  CONGE_ANNUEL: "Congé annuel", CONGE_MALADIE: "Congé maladie",
  PERMISSION: "Permission", MISSION_LONGUE: "Mission longue durée",
  CONGE_MATERNITE: "Congé maternité",
};

/* Types avec recrédit solde automatique */
const TYPES_AVEC_SOLDE = ["CONGE_ANNUEL", "PERMISSION"];

const KENTE = "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

export default function RetourAnticipePage({ params }: { params: Promise<{ id: string }> }) {
  const { id }  = use(params);
  const router  = useRouter();

  const [demande, setDemande]   = useState<Demande | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);
  const [success, setSuccess]   = useState(false);

  const {
    register, handleSubmit, watch,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({ resolver: zodResolver(schema) });

  const dateRetour = watch("dateRetourEffective");

  useEffect(() => {
    apiClient.get(`/api/v5/demandes/${id}`)
      .then(({ data }) => setDemande(data))
      .catch(() => setApiError("Impossible de charger la demande."));
  }, [id]);

  const typeAbsence = demande?.type ?? demande?.typeAbsence ?? "";
  const avecSolde   = TYPES_AVEC_SOLDE.includes(typeAbsence);

  /* Calcul jours non consommés */
  function calculerJoursNonConsommes(): number {
    if (!dateRetour || !demande?.dateFin) return 0;
    const retour  = new Date(dateRetour);
    const fin     = new Date(demande.dateFin);
    const diff    = Math.ceil((fin.getTime() - retour.getTime()) / (1000 * 60 * 60 * 24));
    return Math.max(0, diff);
  }

  const joursNonConsommes = calculerJoursNonConsommes();

  async function onSubmit(data: FormData) {
    setApiError(null);
    try {
      await apiClient.post(`/api/v5/demandes/${id}/retour-anticipe`, {
        dateRetourEffective: data.dateRetourEffective,
      });
      setSuccess(true);
      setTimeout(() => router.push(`/${id}?success=1`), 1500);
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { code?: string } } })
        ?.response?.data?.code;
      if (code === "TRANSITION_ILLEGALE")
        setApiError("La demande n'est pas dans un état permettant le retour anticipé.");
      else
        setApiError("Une erreur est survenue. Veuillez réessayer.");
    }
  }

  return (
    <div className="min-h-[calc(100vh-176px)] flex items-stretch rounded-xl overflow-hidden border border-neutral-200 shadow-card">

      {/* ── Colonne gauche ── */}
      <div className="hidden lg:flex lg:w-2/5 relative overflow-hidden">
        <Image src="/Image_africaine6_resize.png" alt="" fill sizes="40vw" className="object-cover" priority />
        <div className="absolute inset-0"
          style={{ background: "linear-gradient(160deg,rgba(26,26,46,0.92) 0%,rgba(5,150,105,0.30) 100%)" }} />
        <div className="absolute inset-0 opacity-10"
          style={{ backgroundImage: "repeating-linear-gradient(45deg,#B8932A 0px,#B8932A 1px,transparent 1px,transparent 36px)" }} />

        <div className="relative z-10 flex flex-col justify-between p-10 h-full">
          <div className="flex items-center gap-2">
            <div className="h-px w-6 bg-gold-400" />
            <span className="text-xxs text-gold-300 tracking-[0.2em] uppercase font-ui">
              Retour anticipé — AFB
            </span>
          </div>

          <div className="flex flex-col gap-4">
            <div className="w-14 h-14 rounded-xl flex items-center justify-center text-2xl"
              style={{ background: "rgba(255,255,255,0.10)", backdropFilter: "blur(8px)" }}>
              🔄
            </div>
            <h2 className="font-heading text-4xl font-bold text-white leading-tight">
              Retour anticipé
            </h2>

            {demande && (
              <div className="flex flex-col gap-2 rounded-lg p-4"
                style={{ background: "rgba(255,255,255,0.08)", backdropFilter: "blur(6px)" }}>
                <p className="text-xs text-gold-300 uppercase tracking-wider font-ui">Demande</p>
                <p className="text-white font-semibold">
                  {TYPE_LABELS[typeAbsence] ?? typeAbsence}
                </p>
                <p className="text-neutral-300 text-sm">
                  Fin prévue : {demande.dateFin ?? "—"}
                </p>
                <p className="text-neutral-300 text-sm">
                  {demande.nombreJours} jour(s) initial(aux)
                </p>
              </div>
            )}

            {avecSolde && joursNonConsommes > 0 && (
              <div className="flex items-start gap-2 rounded-lg border border-green-400 bg-green-500/20 px-3 py-3">
                <span className="text-lg">💰</span>
                <p className="text-xs text-green-200 leading-relaxed">
                  <strong>{joursNonConsommes} jour(s)</strong> non consommé(s)
                  seront <strong>recrédités</strong> automatiquement sur votre solde.
                </p>
              </div>
            )}

            <div className="h-1 w-20 rounded-sm" style={{ background: KENTE }} />
          </div>

          <span className="font-heading text-8xl font-bold opacity-10 select-none" style={{ color: "#059669" }}>
            ↩
          </span>
        </div>
      </div>

      {/* ── Colonne droite ── */}
      <div className="flex-1 flex flex-col bg-white/90 backdrop-blur-sm">
        <div className="h-1 w-full" style={{ background: "#059669" }} />

        <div className="flex-1 flex flex-col px-8 sm:px-12 py-10 gap-6 justify-center">
          <div className="flex flex-col gap-1">
            <p className="text-xxs text-green-700 tracking-[0.18em] uppercase font-ui font-semibold">
              Clôture anticipée
            </p>
            <h1 className="font-heading text-3xl font-bold text-primary-500">
              Déclarer un retour anticipé
            </h1>
            <p className="text-sm text-neutral-500 mt-1">
              Indiquez votre date de retour effective. La demande sera clôturée immédiatement.
            </p>
          </div>

          <div className="h-px" style={{ background: "linear-gradient(90deg,#059669,transparent 60%)" }} />

          {/* Info recrédit solde */}
          {avecSolde && (
            <div className="flex items-start gap-3 rounded-lg border border-green-200 bg-green-50 px-4 py-3">
              <span className="text-lg mt-0.5">💰</span>
              <p className="text-xs text-green-700 leading-relaxed">
                Pour ce type de congé, les jours non consommés seront
                <strong> automatiquement recrédités</strong> sur votre solde de congés.
              </p>
            </div>
          )}

          {/* Affichage jours récupérés en temps réel */}
          {avecSolde && joursNonConsommes > 0 && (
            <div className="flex items-center gap-4 rounded-xl border border-green-200 bg-green-50 px-5 py-4">
              <div className="flex flex-col items-center">
                <span className="font-heading text-3xl font-bold text-green-600">{joursNonConsommes}</span>
                <span className="text-xxs text-green-500 uppercase tracking-wider font-ui">jour(s)</span>
              </div>
              <div className="h-10 w-px bg-green-200" />
              <p className="text-sm text-green-700">
                seront recrédités sur votre solde après validation
              </p>
            </div>
          )}

          {apiError && (
            <Alert variant="destructive"><AlertDescription>{apiError}</AlertDescription></Alert>
          )}
          {success && (
            <Alert><AlertDescription>✅ Retour anticipé enregistré. Redirection…</AlertDescription></Alert>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
            <FormField
              id="dateRetourEffective"
              label="Date de retour effective"
              type="date"
              error={errors.dateRetourEffective}
              {...register("dateRetourEffective")}
            />

            <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
              <span className="text-lg mt-0.5">⚠️</span>
              <p className="text-xs text-amber-700 leading-relaxed">
                Cette action est <strong>irréversible</strong>. La demande passera au
                statut <strong>Clôturée</strong> et ne pourra plus être modifiée.
              </p>
            </div>

            <div className="flex gap-3 pt-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => router.push(`/${id}`)}
                className="flex-1 h-12"
              >
                Annuler
              </Button>
              <Button
                type="submit"
                disabled={isSubmitting || success}
                className="flex-1 h-12 text-base"
                style={{ background: isSubmitting || success ? undefined : "#059669" }}
              >
                {isSubmitting ? "Enregistrement…" : "Confirmer le retour anticipé →"}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
