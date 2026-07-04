"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import apiClient from "@/lib/api/client";
import { Baby, CheckCircle2, FileText, FolderOpen } from "lucide-react";

interface Props {
  demandeId: string;
}

export function BoutonProlongation({ demandeId }: Props) {
  const router   = useRouter();
  const fileRef  = useRef<HTMLInputElement>(null);
  const [loading,   setLoading]   = useState(false);
  const [fileName,  setFileName]  = useState<string | null>(null);
  const [erreur,    setErreur]    = useState<string | null>(null);
  const [done,      setDone]      = useState(false);

  async function creerProlongation() {
    setLoading(true);
    setErreur(null);
    try {
      const res = await apiClient.post(
        `/api/v5/demandes/${demandeId}/prolongation-maternite`,
        {
          // urlJustificatif obligatoire dans CreationProlongationRequest
          urlJustificatif: fileRef.current?.files?.[0]?.name ?? null,
        }
      );
      setDone(true);
      // Rediriger vers la page de la nouvelle demande prolongation
      setTimeout(() => router.push(`/${res.data.id}`), 1500);
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { code?: string; message?: string } } })
        ?.response?.data?.code;
      if (code === "PROLONGATION_NON_AUTORISEE")
        setErreur("La prolongation n'est autorisée que pour un congé maternité validé.");
      else if (code === "TRANSITION_ILLEGALE")
        setErreur("La demande n'est pas dans l'état attendu pour cette action.");
      else
        setErreur("Erreur lors de la création de la prolongation. Veuillez réessayer.");
    } finally {
      setLoading(false);
    }
  }

  if (done) {
    return (
      <div className="flex items-center gap-3 rounded-lg border border-green-200 bg-green-50 px-4 py-3">
        <CheckCircle2 size={20} className="text-green-600 flex-shrink-0" />
        <div>
          <p className="text-sm font-semibold text-green-700">Prolongation créée avec succès</p>
          <p className="text-xs text-green-600 mt-0.5">Redirection vers la nouvelle demande…</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {/* Titre section */}
      <div className="flex items-center gap-2">
        <div className="h-4 w-0.5 rounded-full" style={{ background: "#96751A" }} />
        <p className="text-sm font-semibold text-primary-500">Prolongation de maternité</p>
      </div>
      <p className="text-xs text-neutral-500 leading-relaxed">
        La prolongation est de <strong>6 semaines supplémentaires</strong> (42 jours).
        Elle débutera automatiquement le lendemain de la fin du congé initial.
      </p>

      {/* Upload justificatif prolongation */}
      <div className="flex flex-col gap-2">
        <label className="text-sm font-medium text-neutral-700">
          Justificatif de prolongation (optionnel)
        </label>
        <div
          className="relative flex items-center justify-center rounded-lg border-2 border-dashed px-4 py-5 text-center cursor-pointer transition-colors hover:border-gold-400"
          style={{ borderColor: fileName ? "#96751A" : "#D1D5DB", background: fileName ? "#FDFBF0" : "#FAFAFA" }}
        >
          <input
            ref={fileRef}
            type="file"
            accept=".pdf,.jpg,.jpeg,.png"
            className="absolute inset-0 opacity-0 cursor-pointer"
            onChange={(e) => setFileName(e.target.files?.[0]?.name ?? null)}
          />
          <div className="flex items-center gap-3 pointer-events-none">
            {fileName
              ? <FileText size={24} style={{ color: "#96751A" }} />
              : <FolderOpen size={24} className="text-neutral-400" />}
            <p className="text-sm text-neutral-500">
              {fileName
                ? <span className="font-medium" style={{ color: "#96751A" }}>{fileName}</span>
                : <>Joindre un justificatif <span className="text-neutral-400">(optionnel)</span></>
              }
            </p>
          </div>
        </div>
      </div>

      {erreur && (
        <Alert variant="destructive">
          <AlertDescription>{erreur}</AlertDescription>
        </Alert>
      )}

      <Button
        type="button"
        disabled={loading}
        onClick={creerProlongation}
        className="h-11 gap-2"
        style={{ background: "#96751A" }}
      >
        {loading ? "Création en cours…" : <><Baby size={16} /> Créer la prolongation (6 semaines)</>}
      </Button>
    </div>
  );
}
