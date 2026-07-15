"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import apiClient from "@/lib/api/client";
import {
  AlertTriangle, Baby, ClipboardList, FileText, HelpCircle, Landmark,
  Paperclip, PartyPopper, Plane, Stethoscope, TreePalm, XCircle, type LucideIcon,
} from "lucide-react";

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

const TYPE_LABELS: Record<string, { label: string; icon: LucideIcon; color: string }> = {
  CONGE_ANNUEL:    { label: "Congé annuel",         icon: TreePalm,      color: "#C41E22" },
  CONGE_MALADIE:   { label: "Congé maladie",        icon: Stethoscope,   color: "#1A1A2E" },
  PERMISSION:      { label: "Permission",           icon: ClipboardList, color: "#B8932A" },
  MISSION_LONGUE:  { label: "Mission longue durée", icon: Plane,         color: "#2C2C2C" },
  CONGE_MATERNITE: { label: "Congé maternité",      icon: Baby,          color: "#96751A" },
};

const KENTE = "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

export default function DRHPage() {
  const [demandes, setDemandes]   = useState<Absence[]>([]);
  const [loading, setLoading]     = useState(true);
  const [erreur, setErreur]       = useState<string | null>(null);

  useEffect(() => {
    apiClient
      .get("/api/v5/demandes?statut=EN_VALIDATION_DRH")
      .then(r => setDemandes(r.data))
      .finally(() => setLoading(false));
  }, []);

  const total    = demandes.length;
  const avecJust = demandes.filter(d => (d.justificatifs?.length ?? 0) > 0).length;
  const sansJust = total - avecJust;

  return (
    <div className="flex flex-col gap-6 max-w-4xl mx-auto py-4">

      {/* Hero */}
      <div className="relative overflow-hidden rounded-xl" style={{ minHeight: "130px" }}>
        <Image src="/Image_africaine5_resize.png" alt="" fill sizes="100vw" className="object-cover object-center" />
        <div className="absolute inset-0" style={{ background: "linear-gradient(110deg,rgba(26,26,46,0.92) 0%,rgba(184,147,42,0.40) 100%)" }} />
        <div className="relative z-10 px-8 py-7 h-full flex flex-col gap-1 justify-center">
          <span className="text-xxs text-gold-300 tracking-[0.2em] uppercase font-ui">Espace DRH — AFB</span>
          <h1 className="font-heading text-3xl font-bold text-white">Validation DRH</h1>
          <p className="text-sm text-neutral-300 mt-1">Décision finale sur les demandes d&apos;absence</p>
        </div>
        <div className="absolute bottom-0 left-0 right-0 h-1" style={{ background: KENTE }} />
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-4">
        {[
          { label: "En attente",           value: total,    color: "#B8932A", icon: Landmark },
          { label: "Justificatif présent", value: avecJust, color: "#059669", icon: Paperclip },
          { label: "Justificatif manquant",value: sansJust, color: "#DC2626", icon: XCircle },
        ].map(s => (
          <div key={s.label} className="relative overflow-hidden rounded-xl border border-neutral-200 bg-white px-5 py-4">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xxs text-neutral-400 uppercase tracking-wider font-ui">{s.label}</p>
                <p className="font-heading text-3xl font-bold mt-1" style={{ color: s.color }}>
                  {loading ? "…" : s.value}
                </p>
              </div>
              <s.icon size={22} style={{ color: s.color }} />
            </div>
            <div className="absolute bottom-0 left-0 right-0 h-0.5" style={{ background: s.color }} />
          </div>
        ))}
      </div>

      {/* Erreur */}
      {erreur && (
        <div className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3">
          <AlertTriangle size={18} className="text-red-600 flex-shrink-0 mt-0.5" />
          <p className="text-sm text-red-700">{erreur}</p>
        </div>
      )}

      {/* Liste */}
      <Card>
        <CardHeader><CardTitle className="text-base flex items-center gap-2"><Landmark size={18} /> Demandes à valider</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-3">
          {loading && <p className="text-sm text-neutral-400 text-center py-8">Chargement…</p>}
          {!loading && demandes.length === 0 && (
            <div className="flex flex-col items-center gap-3 py-12">
              <PartyPopper size={48} className="text-gold-400" />
              <p className="text-sm font-semibold text-neutral-500">Aucune demande en attente</p>
              <p className="text-xs text-neutral-400">Toutes les demandes ont été traitées.</p>
            </div>
          )}
          {demandes.map(d => {
            const type          = TYPE_LABELS[d.type] ?? { label: d.type, icon: HelpCircle, color: "#6B7280" };
            const aJustificatif = (d.justificatifs?.length ?? 0) > 0;
            return (
              <div key={d.id} className="rounded-xl border border-neutral-200 bg-white px-5 py-4 flex flex-col gap-3">

                {/* En-tête */}
                <div className="flex items-center justify-between flex-wrap gap-3">
                  <div className="flex items-center gap-4">
                    <div className="w-11 h-11 rounded-xl flex items-center justify-center flex-shrink-0"
                      style={{ background: type.color + "15" }}>
                      <type.icon size={20} style={{ color: type.color }} />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-primary-500">{type.label}</p>
                      <p className="text-xs text-neutral-400">{d.demandeurIdentifiantExterne}</p>
                      <p className="text-xs text-neutral-400">
                        {d.dateDebut} → {d.dateFin ?? "—"} · {d.nombreJours ?? "?"} jour(s)
                      </p>
                    </div>
                  </div>
                  <span className={`inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-1 rounded-full border ${
                    aJustificatif
                      ? "bg-green-50 text-green-700 border-green-200"
                      : "bg-amber-50 text-amber-700 border-amber-200"
                  }`}>
                    {aJustificatif
                      ? <><Paperclip size={12} /> Justificatif présent</>
                      : <><AlertTriangle size={12} /> Sans justificatif</>}
                  </span>
                </div>

                {/* Justificatifs */}
                {aJustificatif && (
                  <div className="flex flex-wrap gap-2">
                    {d.justificatifs!.map(j => (
                      <a key={j.id} href={j.urlFichier} target="_blank" rel="noopener noreferrer"
                        className="inline-flex items-center gap-1 text-xs text-gold-700 border border-gold-200 bg-gold-50 rounded px-2.5 py-1 hover:bg-gold-100 transition-colors">
                        <FileText size={12} /> {j.typePiece}
                      </a>
                    ))}
                  </div>
                )}

                {/* Actions */}
                <div className="flex gap-3 flex-wrap pt-1">
                  <Button asChild size="sm" style={{ background: "#B8932A" }}>
                    <Link href={`/demande/${d.id}/validation-drh`}><Landmark size={14} /> Statuer</Link>
                  </Button>
                  <Button asChild size="sm" variant="outline">
                    <Link href={`/demande/${d.id}`}>Voir le détail</Link>
                  </Button>
                </div>
              </div>
            );
          })}
        </CardContent>
      </Card>
    </div>
  );
}
