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
import apiClient from "@/lib/api/client";

const schema = z
  .object({
    decision: z.enum(["VALIDER", "REJETER"] as const, { error: "Veuillez choisir une décision" }),
    motif: z.string().optional(),
  })
  .refine((d) => !(d.decision === "REJETER" && !d.motif?.trim()), {
    message: "Le motif est obligatoire pour un rejet",
    path: ["motif"],
  });

type FormData = z.infer<typeof schema>;

interface Demande {
  type?: string;
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

const KENTE = "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

export default function ValidationPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const router  = useRouter();
  const [demande, setDemande]   = useState<Demande | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);
  const [success, setSuccess]   = useState(false);

  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } =
    useForm<FormData>({ resolver: zodResolver(schema), defaultValues: { motif: "" }, mode: "onChange" });

  const [decision, motif] = watch(["decision", "motif"]);

  useEffect(() => {
    apiClient.get(`/api/v5/demandes/${id}`)
      .then(({ data }) => setDemande(data))
      .catch(() => setDemande(null));
  }, [id]);

  async function onSubmit(data: FormData) {
    setApiError(null);
    try {
      await apiClient.post(`/api/v5/demandes/${id}/validation`, {
        decision: data.decision,
        motif: data.motif?.trim() || null,
      });
      setSuccess(true);
      setTimeout(() => router.push(`/${id}?success=1`), 1500);
    } catch (err: unknown) {
      const resp = (err as { response?: { status?: number; data?: { code?: string } } })?.response;
      const code = resp?.data?.code;
      if (resp?.status === 403 && code === "VALIDATEUR_NON_AUTORISE")
        setApiError("Vous n'êtes pas habilité à valider cette étape.");
      else if (resp?.status === 403 && code === "ETAPE_VERROUILLEE")
        setApiError("Cette étape est verrouillée et ne peut pas être traitée.");
      else if (resp?.status === 409 && code === "TRANSITION_ILLEGALE")
        setApiError("La demande ne peut pas être validée dans son état actuel.");
      else
        setApiError("Une erreur est survenue. Veuillez réessayer.");
    }
  }

  const isDisabled = isSubmitting || success || (decision === "REJETER" && !motif?.trim());

  return (
    <div className="min-h-[calc(100vh-176px)] flex items-stretch rounded-xl overflow-hidden border border-neutral-200 shadow-card">

      {/* ── Colonne gauche ── */}
      <div className="hidden lg:flex lg:w-2/5 relative overflow-hidden">
        <Image src="/Image_Afrique3_resize.png" alt="" fill sizes="40vw" className="object-cover" priority />
        <div className="absolute inset-0" style={{ background: "linear-gradient(160deg,rgba(26,26,46,0.92) 0%,rgba(5,150,105,0.35) 100%)" }} />
        <div className="absolute inset-0 opacity-10"
          style={{ backgroundImage: "repeating-linear-gradient(45deg,#B8932A 0px,#B8932A 1px,transparent 1px,transparent 36px)" }} />

        <div className="relative z-10 flex flex-col justify-between p-10 h-full">
          <div className="flex items-center gap-2">
            <div className="h-px w-6 bg-gold-400" />
            <span className="text-xxs text-gold-300 tracking-[0.2em] uppercase font-ui">Validation — AFB</span>
          </div>

          <div className="flex flex-col gap-4">
            <div className="w-14 h-14 rounded-xl flex items-center justify-center text-2xl"
              style={{ background: "rgba(255,255,255,0.10)", backdropFilter: "blur(8px)" }}>
              ✅
            </div>
            <h2 className="font-heading text-4xl font-bold text-white leading-tight">
              Décision de validation
            </h2>

            {/* Récap demande */}
            {demande && (
              <div className="flex flex-col gap-2 rounded-lg p-4"
                style={{ background: "rgba(255,255,255,0.08)", backdropFilter: "blur(6px)" }}>
                <p className="text-xs text-gold-300 uppercase tracking-wider font-ui">Demande concernée</p>
                <p className="text-white font-semibold">{TYPE_LABELS[demande.type ?? ""] ?? demande.type}</p>
                <p className="text-neutral-300 text-sm">
                  Du {demande.dateDebut} au {demande.dateFin ?? "—"}
                </p>
                <p className="text-neutral-300 text-sm">{demande.nombreJours} jour(s)</p>
                <p className="text-neutral-400 text-xs font-mono mt-1">{demande.demandeurIdentifiantExterne}</p>
              </div>
            )}
            <div className="h-1 w-20 rounded-sm" style={{ background: KENTE }} />
          </div>

          <span className="font-heading text-8xl font-bold opacity-10 select-none" style={{ color: "#059669" }}>V+1</span>
        </div>
      </div>

      {/* ── Colonne droite : formulaire ── */}
      <div className="flex-1 flex flex-col bg-white/90 backdrop-blur-sm">
        <div className="h-1 w-full" style={{ background: "#059669" }} />

        <div className="flex-1 flex flex-col px-8 sm:px-12 py-10 gap-6">
          <div className="flex flex-col gap-1">
            <p className="text-xxs text-green-700 tracking-[0.18em] uppercase font-ui font-semibold">Étape de validation</p>
            <h1 className="font-heading text-3xl font-bold text-primary-500">Ma décision</h1>
            <p className="text-sm text-neutral-500 mt-1">
              Validez ou rejetez cette demande d&apos;absence. Tout rejet requiert un motif obligatoire.
            </p>
          </div>

          <div className="h-px" style={{ background: "linear-gradient(90deg,#059669,transparent 60%)" }} />

          <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-6">

            {/* Choix décision */}
            <div className="flex flex-col gap-3">
              <Label>Votre décision</Label>
              <div className="grid grid-cols-2 gap-3">
                {(["VALIDER", "REJETER"] as const).map((val) => (
                  <label
                    key={val}
                    className="relative flex items-center gap-3 rounded-lg border-2 px-4 py-4 cursor-pointer transition-all"
                    style={{
                      borderColor: val === "VALIDER" ? "#059669" : "#DC2626",
                      background: val === "VALIDER" ? "#ECFDF520" : "#FEF2F220",
                    }}
                  >
                    <input type="radio" value={val} {...register("decision")} className="sr-only" />
                    <div className="w-5 h-5 rounded-full border-2 flex items-center justify-center flex-shrink-0"
                      style={{ borderColor: val === "VALIDER" ? "#059669" : "#DC2626" }}>
                      {decision === val && (
                        <div className="w-2.5 h-2.5 rounded-full"
                          style={{ background: val === "VALIDER" ? "#059669" : "#DC2626" }} />
                      )}
                    </div>
                    <div>
                      <p className="text-sm font-semibold" style={{ color: val === "VALIDER" ? "#059669" : "#DC2626" }}>
                        {val === "VALIDER" ? "✅ Valider" : "❌ Rejeter"}
                      </p>
                      <p className="text-xs text-neutral-400">
                        {val === "VALIDER" ? "Approuver la demande" : "Refuser la demande"}
                      </p>
                    </div>
                  </label>
                ))}
              </div>
              {errors.decision && <p className="text-xs text-secondary-500" role="alert">{errors.decision.message}</p>}
            </div>

            {/* Motif rejet */}
            {decision === "REJETER" && (
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="motif">
                  Motif du rejet <span className="text-secondary-500">*</span>
                </Label>
                <textarea
                  id="motif"
                  rows={4}
                  placeholder="Saisir le motif du rejet…"
                  className="w-full rounded border border-neutral-300 bg-white px-3 py-2 text-sm text-primary-500 focus:outline-none focus:ring-2 focus:ring-secondary-500 resize-none"
                  {...register("motif")}
                />
                {errors.motif && <p className="text-xs text-secondary-500" role="alert">{errors.motif.message}</p>}
              </div>
            )}

            {apiError && <Alert variant="destructive"><AlertDescription>{apiError}</AlertDescription></Alert>}
            {success && (
              <Alert>
                <AlertDescription>✅ Décision enregistrée. Redirection en cours…</AlertDescription>
              </Alert>
            )}

            <div className="flex gap-3 pt-2">
              <Button type="button" variant="outline" onClick={() => router.back()} className="flex-1 h-12">
                Annuler
              </Button>
              <Button type="submit" disabled={isDisabled} className="flex-1 h-12 text-base">
                {isSubmitting ? "Envoi…" : "Confirmer ma décision →"}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
