"use client";

import { use, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useRouter } from "next/navigation";
import Image from "next/image";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Label } from "@/components/ui/label";
import { FormField } from "@/components/FormField";
import apiClient from "@/lib/api/client";

const schema = z
  .object({
    decision: z.enum(["VALIDER", "REJETER"] as const, { error: "Veuillez choisir une décision" }),
    motif: z.string().optional(),
    nombreJoursAjuste: z.string().optional(),
  })
  .refine((d) => !(d.decision === "REJETER" && !d.motif?.trim()), {
    message: "Le motif est obligatoire pour un rejet",
    path: ["motif"],
  });

type FormData = z.infer<typeof schema>;

interface Demande {
  type?: string;        // champ discriminant JPA (string brut)
  typeAbsence?: string; // champ AbsenceResponse (enum sérialisé)
  dateDebut?: string;
  dateFin?: string;
  nombreJours?: number;
  statut?: string;
  demandeurIdentifiantExterne?: string;
  codeMotif?: string;   // spécifique DemandePermission
}

const TYPE_LABELS: Record<string, string> = {
  CONGE_ANNUEL: "Congé annuel", CONGE_MALADIE: "Congé maladie",
  PERMISSION: "Permission", MISSION_LONGUE: "Mission longue durée",
  CONGE_MATERNITE: "Congé maternité",
};

const KENTE = "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

/* Types nécessitant un justificatif avant validation DRH */
const TYPES_AVEC_JUSTIFICATIF = ["CONGE_MALADIE", "PERMISSION", "MISSION_LONGUE", "CONGE_MATERNITE"];

export default function ValidationDRHPage({ params }: { params: Promise<{ id: string }> }) {
  const { id }   = use(params);
  const router   = useRouter();
  const [demande, setDemande]           = useState<Demande | null>(null);

  const [apiError, setApiError]         = useState<string | null>(null);
  const [success, setSuccess]           = useState(false);

  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } =
    useForm<FormData>({ resolver: zodResolver(schema), defaultValues: { motif: "" }, mode: "onChange" });

  const [decision, motif] = watch(["decision", "motif"]);

  useEffect(() => {
    apiClient.get(`/api/v5/demandes/${id}`).then(({ data }) => {
      setDemande(data);

    }).catch(() => setDemande(null));
  }, [id]);

  const isDisabled = isSubmitting || success ||
    (decision === "REJETER" && !motif?.trim());

  async function onSubmit(data: FormData) {
    setApiError(null);
    try {
      await apiClient.post(`/api/v5/demandes/${id}/validation-drh`, {
        decision: data.decision,
        motif: data.motif?.trim() || null,
        nombreJoursAjuste: data.nombreJoursAjuste ? Number(data.nombreJoursAjuste) : null,
      });
      setSuccess(true);
      setTimeout(() => router.push(`/${id}?success=1`), 1500);
    } catch (err: unknown) {
      const resp = (err as { response?: { status?: number; data?: { code?: string } } })?.response;
      const code = resp?.data?.code;
      if (code === "JUSTIFICATIF_REQUIS")
        setApiError("Le justificatif (certificat médical) est obligatoire avant de valider.");
      else if (code === "MOTIF_REQUIS")
        setApiError("Le motif est obligatoire pour un rejet.");
      else if (resp?.status === 403)
        setApiError("Vous n'êtes pas habilité à effectuer la validation DRH.");
      else
        setApiError("Une erreur est survenue. Veuillez réessayer.");
    }
  }

  return (
    <div className="min-h-[calc(100vh-176px)] flex items-stretch rounded-xl overflow-hidden border border-neutral-200 shadow-card">

      {/* ── Colonne gauche ── */}
      <div className="hidden lg:flex lg:w-2/5 relative overflow-hidden">
        <Image src="/Image_africaine5_resize.png" alt="" fill sizes="40vw" className="object-cover" priority />
        <div className="absolute inset-0"
          style={{ background: "linear-gradient(160deg,rgba(26,26,46,0.92) 0%,rgba(184,147,42,0.30) 100%)" }} />
        <div className="absolute inset-0 opacity-10"
          style={{ backgroundImage: "repeating-linear-gradient(45deg,#B8932A 0px,#B8932A 1px,transparent 1px,transparent 36px)" }} />

        <div className="relative z-10 flex flex-col justify-between p-10 h-full">
          <div className="flex items-center gap-2">
            <div className="h-px w-6 bg-gold-400" />
            <span className="text-xxs text-gold-300 tracking-[0.2em] uppercase font-ui">Direction RH — AFB</span>
          </div>

          <div className="flex flex-col gap-4">
            <div className="w-14 h-14 rounded-xl flex items-center justify-center text-2xl"
              style={{ background: "rgba(255,255,255,0.10)", backdropFilter: "blur(8px)" }}>🏛️</div>
            <h2 className="font-heading text-4xl font-bold text-white leading-tight">
              Validation DRH
            </h2>
            <p className="text-sm text-neutral-300 font-ui max-w-xs leading-relaxed">
              Décision finale du Directeur des Ressources Humaines. Cette action est irréversible.
            </p>

            {demande && (
              <div className="flex flex-col gap-2 rounded-lg p-4"
                style={{ background: "rgba(255,255,255,0.08)", backdropFilter: "blur(6px)" }}>
                <p className="text-xs text-gold-300 uppercase tracking-wider font-ui">Demande</p>
                <p className="text-white font-semibold">{TYPE_LABELS[demande.type ?? demande.typeAbsence ?? ""] ?? (demande.type ?? demande.typeAbsence)}</p>
                <p className="text-neutral-300 text-sm">Du {demande.dateDebut} au {demande.dateFin ?? "—"}</p>
                <p className="text-neutral-300 text-sm">{demande.nombreJours} jour(s)</p>
              </div>
            )}



            <div className="h-1 w-20 rounded-sm" style={{ background: KENTE }} />
          </div>

          <span className="font-heading text-8xl font-bold opacity-10 select-none" style={{ color: "#D4A017" }}>DRH</span>
        </div>
      </div>

      {/* ── Colonne droite ── */}
      <div className="flex-1 flex flex-col bg-white/90 backdrop-blur-sm">
        <div className="h-1 w-full" style={{ background: "#B8932A" }} />

        <div className="flex-1 flex flex-col px-8 sm:px-12 py-10 gap-6">
          <div className="flex flex-col gap-1">
            <p className="text-xxs text-gold-600 tracking-[0.18em] uppercase font-ui font-semibold">Validation finale</p>
            <h1 className="font-heading text-3xl font-bold text-primary-500">Décision DRH</h1>
            <p className="text-sm text-neutral-500 mt-1">
              Dernière étape du circuit de validation. Le document de mise en congé sera généré automatiquement.
            </p>
          </div>

          <div className="h-px" style={{ background: "linear-gradient(90deg,#B8932A,transparent 60%)" }} />



          <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-6">

            {/* Décision */}
            <div className="flex flex-col gap-3">
              <Label>Décision finale</Label>
              <div className="grid grid-cols-2 gap-3">
                {(["VALIDER", "REJETER"] as const).map((val) => (
                  <label key={val} className="relative flex items-center gap-3 rounded-lg border-2 px-4 py-4 cursor-pointer transition-all"
                    style={{ borderColor: val === "VALIDER" ? "#B8932A" : "#DC2626", background: val === "VALIDER" ? "#FDFBF020" : "#FEF2F220" }}>
                    <input type="radio" value={val} {...register("decision")} className="sr-only" />
                    <div className="w-5 h-5 rounded-full border-2 flex items-center justify-center flex-shrink-0"
                      style={{ borderColor: val === "VALIDER" ? "#B8932A" : "#DC2626" }}>
                      {decision === val && (
                        <div className="w-2.5 h-2.5 rounded-full"
                          style={{ background: val === "VALIDER" ? "#B8932A" : "#DC2626" }} />
                      )}
                    </div>
                    <div>
                      <p className="text-sm font-semibold" style={{ color: val === "VALIDER" ? "#B8932A" : "#DC2626" }}>
                        {val === "VALIDER" ? "✅ Approuver" : "❌ Rejeter"}
                      </p>
                      <p className="text-xs text-neutral-400">
                        {val === "VALIDER" ? "Générer le document" : "Retour au demandeur"}
                      </p>
                    </div>
                  </label>
                ))}
              </div>
              {errors.decision && <p className="text-xs text-secondary-500">{errors.decision.message}</p>}
            </div>

            {/* Ajustement jours pour AUTRE_MOTIF / PERMISSION */}
            {decision === "VALIDER" &&
              (demande?.type === "PERMISSION" || demande?.typeAbsence === "PERMISSION") &&
              demande?.codeMotif === "AUTRE_MOTIF" && (
              <FormField
                id="nombreJoursAjuste"
                label="Nombre de jours ajusté (optionnel)"
                type="number"
                min="1"
                placeholder="ex: 3"
                {...register("nombreJoursAjuste")}
              />
            )}

            {/* Motif rejet */}
            {decision === "REJETER" && (
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="motif">Motif du rejet <span className="text-secondary-500">*</span></Label>
                <textarea id="motif" rows={4} placeholder="Saisir le motif du rejet…"
                  className="w-full rounded border border-neutral-300 bg-white px-3 py-2 text-sm text-primary-500 focus:outline-none focus:ring-2 focus:ring-secondary-500 resize-none"
                  {...register("motif")} />
                {errors.motif && <p className="text-xs text-secondary-500">{errors.motif.message}</p>}
              </div>
            )}

            {apiError && <Alert variant="destructive"><AlertDescription>{apiError}</AlertDescription></Alert>}
            {success && <Alert><AlertDescription>✅ Décision DRH enregistrée. Redirection…</AlertDescription></Alert>}

            <div className="flex gap-3 pt-2">
              <Button type="button" variant="outline" onClick={() => router.back()} className="flex-1 h-12">Annuler</Button>
              <Button type="submit" disabled={isDisabled} className="flex-1 h-12 text-base"
                style={{ background: isDisabled ? undefined : "#B8932A" }}>
                {isSubmitting ? "Envoi…" : "Confirmer la décision DRH →"}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
