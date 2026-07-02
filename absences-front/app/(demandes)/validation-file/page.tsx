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
  etapeCouranteLibelle?: string;
  demandeurIdentifiantExterne: string;
}

const TYPE_LABELS: Record<string, { label: string; icon: string; color: string }> = {
  CONGE_ANNUEL:    { label: "Congé annuel",         icon: "🌴", color: "#C41E22" },
  CONGE_MALADIE:   { label: "Congé maladie",        icon: "🏥", color: "#1A1A2E" },
  PERMISSION:      { label: "Permission",           icon: "📋", color: "#B8932A" },
  MISSION_LONGUE:  { label: "Mission longue durée", icon: "✈️", color: "#2C2C2C" },
  CONGE_MATERNITE: { label: "Congé maternité",      icon: "👶", color: "#96751A" },
};

const KENTE = "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

export default function ValidationFilePage() {
  const [demandes, setDemandes] = useState<Absence[]>([]);
  const [loading, setLoading]   = useState(true);

  useEffect(() => {
    apiClient
      .get("/api/v5/demandes/a-valider")
      .then(r => setDemandes(r.data))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="flex flex-col gap-6 max-w-4xl mx-auto py-4">

      {/* Hero */}
      <div className="relative overflow-hidden rounded-xl" style={{ minHeight: "130px" }}>
        <Image src="/Image_Afrique3_resize.png" alt="" fill sizes="100vw" className="object-cover object-center" />
        <div className="absolute inset-0" style={{ background: "linear-gradient(110deg,rgba(26,26,46,0.92) 0%,rgba(184,147,42,0.35) 100%)" }} />
        <div className="relative z-10 px-8 py-7 h-full flex flex-col gap-1 justify-center">
          <span className="text-xxs text-gold-300 tracking-[0.2em] uppercase font-ui">File de validation — Manager</span>
          <h1 className="font-heading text-3xl font-bold text-white">Demandes à valider</h1>
          <p className="text-sm text-neutral-300 mt-1">Demandes en attente de votre décision hiérarchique</p>
        </div>
        <div className="absolute bottom-0 left-0 right-0 h-1" style={{ background: KENTE }} />
      </div>

      {/* Compteur */}
      <div className="flex items-center gap-4 rounded-xl border border-amber-200 bg-amber-50 px-5 py-4">
        <span className="text-3xl">⏳</span>
        <div>
          <p className="text-sm font-semibold text-amber-700">
            {loading ? "Chargement…" : `${demandes.length} demande(s) en attente de validation`}
          </p>
          <p className="text-xs text-amber-600 mt-0.5">Toutes les demandes au statut EN_VALIDATION_ETAPE</p>
        </div>
      </div>

      {/* Liste */}
      <Card>
        <CardHeader><CardTitle className="text-base">📋 File d&apos;attente</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-3">
          {loading && <p className="text-sm text-neutral-400 text-center py-8">Chargement…</p>}
          {!loading && demandes.length === 0 && (
            <div className="flex flex-col items-center gap-3 py-12">
              <span className="text-5xl">🎉</span>
              <p className="text-sm font-semibold text-neutral-500">Aucune demande en attente</p>
              <p className="text-xs text-neutral-400">Toutes les demandes ont été traitées.</p>
            </div>
          )}
          {demandes.map(d => {
            const type = TYPE_LABELS[d.type] ?? { label: d.type, icon: "❓", color: "#6B7280" };
            return (
              <div key={d.id}
                className="flex items-center justify-between rounded-xl border border-neutral-200 bg-white px-5 py-4">
                <div className="flex items-center gap-4">
                  <div className="w-11 h-11 rounded-xl flex items-center justify-center text-xl flex-shrink-0"
                    style={{ background: type.color + "15" }}>
                    {type.icon}
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-primary-500">{type.label}</p>
                    <p className="text-xs text-neutral-400 mt-1">
                      Demandeur : <span className="font-medium text-neutral-600">{d.demandeurIdentifiantExterne}</span>
                    </p>
                    <p className="text-xs text-neutral-400">
                      Du {d.dateDebut} au {d.dateFin ?? "—"} · {d.nombreJours ?? "?"} jour(s)
                    </p>
                    {d.etapeCouranteLibelle && (
                      <span className="mt-2 inline-block px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-100 text-amber-700">
                        {d.etapeCouranteLibelle}
                      </span>
                    )}
                  </div>
                </div>
                <div className="flex flex-col gap-2 items-end">
                  <Button asChild size="sm">
                    <Link href={`/${d.id}/validation`}>✅ Décider</Link>
                  </Button>
                  <Link href={`/${d.id}`} className="text-xs text-neutral-400 hover:underline">
                    Voir le détail →
                  </Link>
                </div>
              </div>
            );
          })}
        </CardContent>
      </Card>
    </div>
  );
}
