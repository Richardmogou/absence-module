"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import apiClient from "@/lib/api/client";

interface Absence {
  id: string;
  type: string;
  dateDebut: string;
  dateFin: string | null;
  nombreJours: number | null;
  statut: string;
  demandeurIdentifiantExterne: string;
  justificatifs?: { id: string; typePiece: string; urlFichier: string }[];
}

const TYPES_AVEC_JUSTIFICATIF = new Set([
  "CONGE_MALADIE", "PERMISSION", "MISSION_LONGUE", "CONGE_MATERNITE",
]);

const TYPE_LABELS: Record<string, { label: string; icon: string; color: string }> = {
  CONGE_ANNUEL:    { label: "Congé annuel",         icon: "🌴", color: "#C41E22" },
  CONGE_MALADIE:   { label: "Congé maladie",        icon: "🏥", color: "#1A1A2E" },
  PERMISSION:      { label: "Permission",           icon: "📋", color: "#B8932A" },
  MISSION_LONGUE:  { label: "Mission longue durée", icon: "✈️", color: "#2C2C2C" },
  CONGE_MATERNITE: { label: "Congé maternité",      icon: "👶", color: "#96751A" },
};

const KENTE = "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

export default function AnalysteRHPage() {
  const [demandes, setDemandes]   = useState<Absence[]>([]);
  const [loading, setLoading]     = useState(true);
  const [loadingId, setLoadingId] = useState<string | null>(null);
  const [erreur, setErreur]       = useState<string | null>(null);

  useEffect(() => {
    apiClient
      .get("/api/v5/demandes?statut=EN_INSTRUCTION_ANALYSTE_RH")
      .then(r => setDemandes(r.data))
      .finally(() => setLoading(false));
  }, []);

  async function instruire(id: string) {
    setLoadingId(id);
    setErreur(null);
    try {
      await apiClient.post(`/api/v5/demandes/${id}/instruction`);
      setDemandes(prev => prev.filter(d => d.id !== id));
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { code?: string } } })?.response?.data?.code;
      if (code === "JUSTIFICATIF_REQUIS")
        setErreur(`Demande ${id.slice(0, 8)}… : justificatif manquant avant transmission.`);
      else
        setErreur("Une erreur est survenue lors de la transmission.");
    } finally {
      setLoadingId(null);
    }
  }

  return (
    <div className="flex flex-col gap-6 max-w-4xl mx-auto py-4">

      {/* Hero */}
      <div className="relative overflow-hidden rounded-xl" style={{ minHeight: "130px" }}>
        <Image src="/Image_Afrique3_resize.png" alt="" fill sizes="100vw" className="object-cover object-center" />
        <div className="absolute inset-0" style={{ background: "linear-gradient(110deg,rgba(26,26,46,0.92) 0%,rgba(124,58,237,0.35) 100%)" }} />
        <div className="relative z-10 px-8 py-7 h-full flex flex-col gap-1 justify-center">
          <span className="text-xxs text-gold-300 tracking-[0.2em] uppercase font-ui">Espace Analyste RH — AFB</span>
          <h1 className="font-heading text-3xl font-bold text-white">File d&apos;instruction</h1>
          <p className="text-sm text-neutral-300 mt-1">Vérifiez les justificatifs puis transmettez au DRH</p>
        </div>
        <div className="absolute bottom-0 left-0 right-0 h-1" style={{ background: KENTE }} />
      </div>

      {/* Compteur */}
      <div className="flex items-center gap-4 rounded-xl border border-purple-200 bg-purple-50 px-5 py-4">
        <span className="text-3xl">🔍</span>
        <div>
          <p className="text-sm font-semibold text-purple-700">
            {loading ? "Chargement…" : `${demandes.length} demande(s) en attente d'instruction`}
          </p>
          <p className="text-xs text-purple-600 mt-0.5">Statut : EN_INSTRUCTION_ANALYSTE_RH</p>
        </div>
      </div>

      {/* Erreur globale */}
      {erreur && (
        <div className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3">
          <span className="text-lg">⚠️</span>
          <p className="text-sm text-red-700">{erreur}</p>
        </div>
      )}

      {/* Liste */}
      <Card>
        <CardHeader><CardTitle className="text-base">📋 Demandes à instruire</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-3">
          {loading && <p className="text-sm text-neutral-400 text-center py-8">Chargement…</p>}
          {!loading && demandes.length === 0 && (
            <div className="flex flex-col items-center gap-3 py-12">
              <span className="text-5xl">🎉</span>
              <p className="text-sm font-semibold text-neutral-500">Aucune demande en attente</p>
              <p className="text-xs text-neutral-400">Toutes les demandes ont été instruites.</p>
            </div>
          )}
          {demandes.map(d => {
            const type          = TYPE_LABELS[d.type] ?? { label: d.type, icon: "❓", color: "#6B7280" };
            const justificatifRequis = TYPES_AVEC_JUSTIFICATIF.has(d.type);
            const aJustificatif = (d.justificatifs?.length ?? 0) > 0;
            return (
              <div key={d.id} className="rounded-xl border border-neutral-200 bg-white px-5 py-4 flex flex-col gap-3">

                {/* En-tête de la carte */}
                <div className="flex items-center justify-between flex-wrap gap-3">
                  <div className="flex items-center gap-4">
                    <div className="w-11 h-11 rounded-xl flex items-center justify-center text-xl flex-shrink-0"
                      style={{ background: type.color + "15" }}>
                      {type.icon}
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-primary-500">{type.label}</p>
                      <p className="text-xs text-neutral-400">
                        {d.demandeurIdentifiantExterne}
                      </p>
                      <p className="text-xs text-neutral-400">
                        {d.dateDebut} → {d.dateFin ?? "—"} · {d.nombreJours ?? "?"} jour(s)
                      </p>
                    </div>
                  </div>
                  <span className={`text-xs font-semibold px-2.5 py-1 rounded-full border ${
                    aJustificatif
                      ? "bg-green-50 text-green-700 border-green-200"
                      : justificatifRequis
                      ? "bg-red-50 text-red-700 border-red-200"
                      : "bg-neutral-50 text-neutral-500 border-neutral-200"
                  }`}>
                    {aJustificatif ? "📎 Justificatif présent" : justificatifRequis ? "❌ Justificatif manquant" : "Sans justificatif"}
                  </span>
                </div>

                {/* Liste justificatifs */}
                {aJustificatif && (
                  <div className="flex flex-wrap gap-2">
                    {d.justificatifs!.map(j => (
                      <a key={j.id} href={j.urlFichier} target="_blank" rel="noopener noreferrer"
                        className="text-xs text-purple-700 border border-purple-200 bg-purple-50 rounded px-2.5 py-1 hover:bg-purple-100 transition-colors">
                        📄 {j.typePiece}
                      </a>
                    ))}
                  </div>
                )}

                {/* Actions */}
                <div className="flex gap-3 flex-wrap pt-1">
                  <Button
                    size="sm"
                    disabled={loadingId === d.id || (justificatifRequis && !aJustificatif)}
                    onClick={() => instruire(d.id)}
                    style={{ background: (!justificatifRequis || aJustificatif) ? "#7C3AED" : undefined }}
                  >
                    {loadingId === d.id ? "Transmission…" : "🔍 Transmettre au DRH"}
                  </Button>
                  <Button asChild size="sm" variant="outline">
                    <Link href={`/${d.id}`}>Voir le détail</Link>
                  </Button>
                  {justificatifRequis && !aJustificatif && (
                    <Button asChild size="sm" variant="outline">
                      <Link href={`/${d.id}/justificatif`}>📎 Déposer justificatif</Link>
                    </Button>
                  )}
                </div>
              </div>
            );
          })}
        </CardContent>
      </Card>
    </div>
  );
}
