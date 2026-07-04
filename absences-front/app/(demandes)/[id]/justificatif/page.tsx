"use client";

import { use, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Image from "next/image";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import apiClient from "@/lib/api/client";
import {
  ArrowRight, Baby, CheckCircle2, ClipboardList, FileText, FolderOpen,
  Plane, Stethoscope, type LucideIcon,
} from "lucide-react";

const KENTE =
  "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

/* Config par type d'absence */
const JUSTIF_CONFIG: Record<string, {
  typePiece: string;
  label: string;
  description: string;
  icon: LucideIcon;
  accentColor: string;
}> = {
  CONGE_MALADIE:   {
    typePiece:    "CERTIFICAT_MEDICAL",
    label:        "Certificat médical",
    description:  "Certificat établi par un médecin agréé, mentionnant la durée de l'arrêt.",
    icon:         Stethoscope,
    accentColor:  "#1A1A2E",
  },
  PERMISSION:      {
    typePiece:    "JUSTIFICATIF_PERMISSION",
    label:        "Justificatif officiel",
    description:  "Document officiel correspondant au motif sélectionné.",
    icon:         ClipboardList,
    accentColor:  "#B8932A",
  },
  MISSION_LONGUE:  {
    typePiece:    "ORDRE_DE_MISSION",
    label:        "Ordre de mission",
    description:  "Document signé par la hiérarchie autorisée, précisant la destination et l'objet.",
    icon:         Plane,
    accentColor:  "#2C2C2C",
  },
  CONGE_MATERNITE: {
    typePiece:    "CERTIFICAT_GROSSESSE",
    label:        "Certificat de grossesse",
    description:  "Certificat médical attestant la grossesse et la date présumée d'accouchement.",
    icon:         Baby,
    accentColor:  "#96751A",
  },
};

const DEFAULT_CFG = JUSTIF_CONFIG["CONGE_MALADIE"];

export default function JustificatifPage({ params }: { params: Promise<{ id: string }> }) {
  const { id }  = use(params);
  const router  = useRouter();
  const fileRef = useRef<HTMLInputElement>(null);

  const [envoi,       setEnvoi]       = useState(false);
  const [ok,          setOk]          = useState(false);
  const [erreur,      setErreur]      = useState<string | null>(null);
  const [fileName,    setFileName]    = useState<string | null>(null);
  const [typeAbsence, setTypeAbsence] = useState<string | null>(null);

  useEffect(() => {
    apiClient
      .get(`/api/v5/demandes/${id}`)
      .then(({ data }) => setTypeAbsence(data.type ?? data.typeAbsence ?? null))
      .catch(() => null);
  }, [id]);

  const cfg = typeAbsence
    ? (JUSTIF_CONFIG[typeAbsence] ?? DEFAULT_CFG)
    : DEFAULT_CFG;

  async function deposer() {
    if (!fileRef.current?.files?.length) {
      setErreur("Veuillez sélectionner un fichier avant de déposer.");
      return;
    }
    setEnvoi(true);
    setErreur(null);
    try {
      const formData = new FormData();
      formData.append("typePiece", cfg.typePiece);
      formData.append("fichier", fileRef.current.files[0]);

      await apiClient.post(`/api/v5/demandes/${id}/justificatif`, formData);
      setOk(true);
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { code?: string } } })
        ?.response?.data?.code;
      setErreur(
        code === "TRANSITION_ILLEGALE"
          ? "La demande n'est plus dans un état permettant le dépôt de justificatif."
          : "Erreur lors du dépôt. Veuillez réessayer."
      );
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <div className="min-h-[calc(100vh-176px)] flex items-stretch rounded-xl overflow-hidden border border-neutral-200 shadow-card">

      {/* ── Colonne gauche ── */}
      <div className="hidden lg:flex lg:w-2/5 relative overflow-hidden">
        <Image src="/Image_africaine5_resize.png" alt="" fill sizes="40vw" className="object-cover" priority />
        <div
          className="absolute inset-0"
          style={{
            background: `linear-gradient(160deg,rgba(26,26,46,0.92) 0%,${cfg.accentColor}55 100%)`,
          }}
        />
        <div
          className="absolute inset-0 opacity-10"
          style={{ backgroundImage: "repeating-linear-gradient(45deg,#B8932A 0px,#B8932A 1px,transparent 1px,transparent 36px)" }}
        />

        <div className="relative z-10 flex flex-col justify-between p-10 h-full">
          <div className="flex items-center gap-2">
            <div className="h-px w-6 bg-gold-400" />
            <span className="text-xxs text-gold-300 tracking-[0.2em] uppercase font-ui">
              Justificatif — AFB
            </span>
          </div>

          <div className="flex flex-col gap-4">
            <div
              className="w-14 h-14 rounded-xl flex items-center justify-center"
              style={{ background: "rgba(255,255,255,0.10)", backdropFilter: "blur(8px)" }}
            >
              <cfg.icon size={26} className="text-gold-300" />
            </div>
            <h2 className="font-heading text-4xl font-bold text-white leading-tight">
              {cfg.label}
            </h2>
            <p className="text-sm text-neutral-300 font-ui max-w-xs leading-relaxed">
              {cfg.description}
            </p>

            <div className="flex flex-col gap-2 mt-2">
              {["PDF, JPG, PNG acceptés", "Taille max : 5 Mo", "Document original requis"].map((t) => (
                <div key={t} className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-gold-400 flex-shrink-0" />
                  <span className="text-xs text-neutral-300 font-ui">{t}</span>
                </div>
              ))}
            </div>

            <div className="h-1 w-20 rounded-sm" style={{ background: KENTE }} />
          </div>

          <span
            className="font-heading text-8xl font-bold opacity-10 select-none"
            style={{ color: "#D4A017" }}
          >
            DOC
          </span>
        </div>
      </div>

      {/* ── Colonne droite ── */}
      <div className="flex-1 flex flex-col bg-white/90 backdrop-blur-sm">
        <div className="h-1 w-full" style={{ background: cfg.accentColor }} />

        <div className="flex-1 flex flex-col px-8 sm:px-12 py-10 gap-6 justify-center">
          <div className="flex flex-col gap-1">
            <p
              className="text-xxs tracking-[0.18em] uppercase font-ui font-semibold"
              style={{ color: cfg.accentColor }}
            >
              Étape 2 sur 3
            </p>
            <h1 className="font-heading text-3xl font-bold text-primary-500">
              Déposer le {cfg.label.toLowerCase()}
            </h1>
            <p className="text-sm text-neutral-500 mt-1">
              {cfg.description}
            </p>
          </div>

          <div
            className="h-px"
            style={{ background: `linear-gradient(90deg,${cfg.accentColor},transparent 60%)` }}
          />

          {!ok ? (
            <div className="flex flex-col gap-5">
              <div className="flex flex-col gap-2">
                <label className="text-sm font-medium text-neutral-700">
                  Fichier — {cfg.label}
                </label>
                <div
                  className="relative flex flex-col items-center justify-center rounded-xl border-2 border-dashed px-6 py-10 text-center transition-colors cursor-pointer"
                  style={{
                    borderColor: fileName ? cfg.accentColor : "#D1D5DB",
                    background:  fileName ? cfg.accentColor + "0A" : "#FAFAFA",
                  }}
                >
                  <input
                    ref={fileRef}
                    type="file"
                    accept=".pdf,.jpg,.jpeg,.png"
                    className="absolute inset-0 opacity-0 cursor-pointer"
                    onChange={(e) => setFileName(e.target.files?.[0]?.name ?? null)}
                  />
                  <div className="flex flex-col items-center gap-3 pointer-events-none">
                    {fileName
                      ? <FileText size={40} style={{ color: cfg.accentColor }} />
                      : <FolderOpen size={40} className="text-neutral-400" />}
                    {fileName ? (
                      <div className="flex flex-col items-center gap-1">
                        <p className="text-sm font-semibold" style={{ color: cfg.accentColor }}>
                          {fileName}
                        </p>
                        <p className="text-xs text-neutral-400">Cliquez pour changer</p>
                      </div>
                    ) : (
                      <div className="flex flex-col items-center gap-1">
                        <p className="text-sm text-neutral-500">
                          Glissez votre fichier ici ou{" "}
                          <span className="font-medium" style={{ color: cfg.accentColor }}>
                            parcourez
                          </span>
                        </p>
                        <p className="text-xs text-neutral-400">PDF, JPG, PNG — max 5 Mo</p>
                      </div>
                    )}
                  </div>
                </div>
              </div>

              {erreur && (
                <Alert variant="destructive">
                  <AlertDescription>{erreur}</AlertDescription>
                </Alert>
              )}

              <div className="flex gap-3">
                <Button
                  type="button"
                  disabled={envoi || !fileName}
                  onClick={deposer}
                  className="flex-1 h-12 text-base"
                  style={{ background: fileName ? cfg.accentColor : undefined }}
                >
                  {envoi ? "Dépôt en cours…" : `Déposer le ${cfg.label.toLowerCase()}`}
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => router.push(`/${id}/preview`)}
                  className="h-12 text-sm text-neutral-400"
                >
                  Passer
                </Button>
              </div>
            </div>
          ) : (
            <div className="flex flex-col gap-5">
              <div className="flex items-center gap-3 rounded-xl border border-green-200 bg-green-50 px-5 py-4">
                <CheckCircle2 size={24} className="text-green-600 flex-shrink-0" />
                <div>
                  <p className="text-sm font-semibold text-green-700">
                    {cfg.label} déposé avec succès
                  </p>
                  <p className="text-xs text-green-600 mt-0.5 font-mono">{fileName}</p>
                </div>
              </div>
              <Button
                type="button"
                className="h-12 text-base"
                style={{ background: cfg.accentColor }}
                onClick={() => router.push(`/${id}/preview`)}
              >
                Continuer vers l&apos;aperçu <ArrowRight size={16} />
              </Button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
