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
import { AlertTriangle, CheckCircle2, Pencil } from "lucide-react";

const schema = z
  .object({
    dateDebut:   z.string().min(1, "La date de début est obligatoire"),
    dateFin:     z.string().optional(),
    nombreJours: z.string().optional(),
  })
  .refine(
    (d) => !d.dateFin || new Date(d.dateFin) >= new Date(d.dateDebut),
    { message: "La date de fin doit être après la date de début", path: ["dateFin"] }
  );

type FormData = z.infer<typeof schema>;

interface Demande {
  type?: string;
  dateDebut?: string;
  dateFin?: string | null;
  nombreJours?: number | null;
  statut?: string;
}

const TYPE_LABELS: Record<string, string> = {
  CONGE_ANNUEL: "Congé annuel", CONGE_MALADIE: "Congé maladie",
  PERMISSION: "Permission", MISSION_LONGUE: "Mission longue durée",
  CONGE_MATERNITE: "Congé maternité",
};

const KENTE = "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

export default function ModifierPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const router  = useRouter();

  const [demande, setDemande]   = useState<Demande | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);
  const [success, setSuccess]   = useState(false);

  const {
    register, handleSubmit, reset, watch,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({ resolver: zodResolver(schema) });

  useEffect(() => {
    apiClient.get(`/api/v5/demandes/${id}`).then(({ data }) => {
      setDemande(data);
      reset({
        dateDebut:   data.dateDebut ?? "",
        dateFin:     data.dateFin ?? "",
        nombreJours: data.nombreJours != null ? String(data.nombreJours) : "",
      });
    }).catch(() => setApiError("Impossible de charger la demande."));
  }, [id, reset]);

  const dateDebutWatched = watch("dateDebut");
  const dateFinWatched   = watch("dateFin");
  const isMaternite  = demande?.type === "CONGE_MATERNITE";
  const isMission    = demande?.type === "MISSION_LONGUE";

  // Recalcul dateFin automatique pour CONGE_MATERNITE si dateDebut change
  const dateFinCalculee = isMaternite && dateDebutWatched
    ? (() => {
        const d = new Date(dateDebutWatched);
        d.setDate(d.getDate() + 98);
        return d.toISOString().split("T")[0];
      })()
    : null;

  // Calcul durée pour MISSION_LONGUE
  const joursCalcules = isMission && dateDebutWatched && dateFinWatched
    ? Math.ceil((new Date(dateFinWatched).getTime() - new Date(dateDebutWatched).getTime()) / (1000 * 60 * 60 * 24)) + 1
    : null;
  const dureeInvalide = isMission && joursCalcules !== null && joursCalcules < 15;

  async function onSubmit(data: FormData) {
    setApiError(null);
    try {
      await apiClient.put(`/api/v5/demandes/${id}`, {
        dateDebut:   data.dateDebut,
        // Pour CONGE_MATERNITE : dateFin recalculée automatiquement (14 semaines)
        dateFin:     isMaternite ? dateFinCalculee : (data.dateFin || null),
        nombreJours: isMaternite ? 98 : isMission ? (joursCalcules ?? null) : (data.nombreJours ? Number(data.nombreJours) : null),
      });
      setSuccess(true);
      setTimeout(() => router.push(`/${id}`), 1500);
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { code?: string } } })
        ?.response?.data?.code;
      if (code === "MODIFICATION_IMPOSSIBLE")
        setApiError("Cette demande ne peut plus être modifiée dans son état actuel.");
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
          style={{ background: "linear-gradient(160deg,rgba(44,44,44,0.90) 0%,rgba(26,26,46,0.75) 100%)" }} />
        <div className="absolute inset-0 opacity-10"
          style={{ backgroundImage: "repeating-linear-gradient(45deg,#B8932A 0px,#B8932A 1px,transparent 1px,transparent 36px)" }} />

        <div className="relative z-10 flex flex-col justify-between p-10 h-full">
          <div className="flex items-center gap-2">
            <div className="h-px w-6 bg-gold-400" />
            <span className="text-xxs text-gold-300 tracking-[0.2em] uppercase font-ui">
              Modification — AFB
            </span>
          </div>

          <div className="flex flex-col gap-4">
            <div className="w-14 h-14 rounded-xl flex items-center justify-center"
              style={{ background: "rgba(255,255,255,0.10)", backdropFilter: "blur(8px)" }}>
              <Pencil size={26} className="text-gold-300" />
            </div>
            <h2 className="font-heading text-4xl font-bold text-white leading-tight">
              Modifier la demande
            </h2>
            {demande && (
              <div className="flex flex-col gap-1 rounded-lg p-4"
                style={{ background: "rgba(255,255,255,0.08)", backdropFilter: "blur(6px)" }}>
                <p className="text-xs text-gold-300 uppercase tracking-wider font-ui">Type</p>
                <p className="text-white font-semibold text-lg">
                  {TYPE_LABELS[demande.type ?? ""] ?? demande.type}
                </p>
                <p className="text-neutral-400 text-xs mt-1 font-mono">#{id.slice(0, 8)}…</p>
              </div>
            )}
            <p className="text-sm text-neutral-300 font-ui max-w-xs leading-relaxed">
              Seules les demandes en statut <strong className="text-white">Brouillon</strong> ou{" "}
              <strong className="text-white">Rejetée</strong> peuvent être modifiées.
            </p>
            <div className="h-1 w-20 rounded-sm" style={{ background: KENTE }} />
          </div>

          <span className="font-heading text-8xl font-bold opacity-10 select-none" style={{ color: "#D4A017" }}>
            EDIT
          </span>
        </div>
      </div>

      {/* ── Colonne droite ── */}
      <div className="flex-1 flex flex-col bg-white/90 backdrop-blur-sm">
        <div className="h-1 w-full bg-primary-500" />

        <div className="flex-1 flex flex-col px-8 sm:px-12 py-10 gap-6 justify-center">
          <div className="flex flex-col gap-1">
            <p className="text-xxs text-primary-400 tracking-[0.18em] uppercase font-ui font-semibold">
              Modification
            </p>
            <h1 className="font-heading text-3xl font-bold text-primary-500">
              Mettre à jour les dates
            </h1>
            <p className="text-sm text-neutral-500 mt-1">
              Modifiez les informations de votre demande puis sauvegardez.
            </p>
          </div>

          <div className="h-px" style={{ background: "linear-gradient(90deg,#2C2C2C,transparent 60%)" }} />

          {apiError && (
            <Alert variant="destructive">
              <AlertDescription>{apiError}</AlertDescription>
            </Alert>
          )}
          {success && (
            <Alert>
              <AlertDescription className="flex items-center gap-2"><CheckCircle2 size={16} className="text-green-600 flex-shrink-0" /> Demande mise à jour. Redirection…</AlertDescription>
            </Alert>
          )}
          {dureeInvalide && (
            <Alert variant="destructive">
              <AlertDescription>
                <AlertTriangle size={14} className="inline mr-1 -mt-0.5" /> La mission longue durée doit durer au minimum <strong>15 jours</strong>.
                Durée actuelle : {joursCalcules} jour(s).
              </AlertDescription>
            </Alert>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
            <div className="grid grid-cols-2 gap-4">
              <FormField
                id="dateDebut"
                label="Date de début"
                type="date"
                error={errors.dateDebut}
                {...register("dateDebut")}
              />
              {/* dateFin non éditable pour CONGE_MATERNITE — recalculée auto */}
              {!isMaternite ? (
                <FormField
                  id="dateFin"
                  label="Date de fin (optionnel)"
                  type="date"
                  error={errors.dateFin}
                  {...register("dateFin")}
                />
              ) : (
                <div className="flex flex-col gap-1.5">
                  <label className="text-sm font-medium text-neutral-700">
                    Date de fin (calculée)
                  </label>
                  <div className="h-10 flex items-center rounded border border-neutral-200 bg-neutral-100 px-3 text-sm text-neutral-500">
                    {dateFinCalculee ?? "—"}
                  </div>
                  <p className="text-xxs text-gold-600">14 semaines fixées par la loi</p>
                </div>
              )}
            </div>

            {!isMaternite && (
              <FormField
                id="nombreJours"
                label="Nombre de jours (optionnel)"
                type="number"
                min="1"
                placeholder="Calculé automatiquement si vide"
                error={errors.nombreJours}
                {...register("nombreJours")}
              />
            )}

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
                disabled={isSubmitting || success || dureeInvalide}
                className="flex-1 h-12 text-base"
              >
                {isSubmitting ? "Sauvegarde…" : "Sauvegarder les modifications →"}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
