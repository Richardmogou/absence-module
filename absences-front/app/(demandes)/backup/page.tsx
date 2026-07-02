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
  backupIdentifiantExterne: string | null;
}

const TYPE_LABELS: Record<string, { label: string; icon: string; color: string }> = {
  CONGE_ANNUEL:    { label: "Congé annuel",         icon: "🌴", color: "#C41E22" },
  CONGE_MALADIE:   { label: "Congé maladie",        icon: "🏥", color: "#1A1A2E" },
  PERMISSION:      { label: "Permission",           icon: "📋", color: "#B8932A" },
  MISSION_LONGUE:  { label: "Mission longue durée", icon: "✈️", color: "#2C2C2C" },
  CONGE_MATERNITE: { label: "Congé maternité",      icon: "👶", color: "#96751A" },
};

const STATUT_CONFIG: Record<string, { label: string; color: string; bg: string; icon: string }> = {
  BROUILLON:                  { label: "Brouillon",          color: "#6B7280", bg: "#F3F4F6", icon: "📝" },
  SOUMISE:                    { label: "Soumise",            color: "#0EA5E9", bg: "#F0F9FF", icon: "📤" },
  EN_VALIDATION_ETAPE:        { label: "En validation",      color: "#D97706", bg: "#FFFBEB", icon: "⏳" },
  EN_INSTRUCTION_ANALYSTE_RH: { label: "En instruction RH", color: "#7C3AED", bg: "#F5F3FF", icon: "🔍" },
  EN_VALIDATION_DRH:          { label: "En validation DRH", color: "#B8932A", bg: "#FDFBF0", icon: "🏛️" },
  VALIDEE:                    { label: "Validée ✅",         color: "#059669", bg: "#ECFDF5", icon: "✅" },
  REJETEE:                    { label: "Rejetée",            color: "#DC2626", bg: "#FEF2F2", icon: "❌" },
  CLOTUREE:                   { label: "Clôturée",           color: "#4B5563", bg: "#F9FAFB", icon: "🔒" },
};

const KENTE = "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

export default function BackupDashboardPage() {
  const [demandes, setDemandes]   = useState<Absence[]>([]);
  const [loading, setLoading]     = useState(true);

  useEffect(() => {
    apiClient
      .get("/api/v5/demandes/moi/backup")
      .then(r => setDemandes(r.data))
      .finally(() => setLoading(false));
  }, []);

  // Séparation : demandes nécessitant ma validation vs absences en cours où je suis Back-up actif
  const aValider   = demandes.filter(d => d.statut === "EN_VALIDATION_ETAPE");
  const enCours    = demandes.filter(d => d.statut === "VALIDEE");
  const historique = demandes.filter(d => !["EN_VALIDATION_ETAPE", "VALIDEE"].includes(d.statut));

  return (
    <div className="flex flex-col gap-6 max-w-4xl mx-auto py-4">

      {/* Hero */}
      <div className="relative overflow-hidden rounded-xl" style={{ minHeight: "140px" }}>
        <Image src="/Image_africaine6_resize.png" alt="" fill sizes="100vw" className="object-cover object-center" />
        <div className="absolute inset-0"
          style={{ background: "linear-gradient(110deg,rgba(26,26,46,0.92) 0%,rgba(196,30,34,0.30) 100%)" }} />
        <div className="relative z-10 px-8 py-8 h-full flex flex-col gap-1 justify-center">
          <span className="text-xxs text-gold-300 tracking-[0.2em] uppercase font-ui">
            Espace Back-up — AFB
          </span>
          <h1 className="font-heading text-3xl font-bold text-white">Mon rôle de Back-up</h1>
          <p className="text-sm text-neutral-300 mt-1">
            Demandes pour lesquelles vous êtes désigné comme Back-up par un collègue
          </p>
        </div>
        <div className="absolute bottom-0 left-0 right-0 h-1" style={{ background: KENTE }} />
      </div>

      {/* Explication du rôle */}
      <div className="flex items-start gap-4 rounded-xl border border-blue-200 bg-blue-50 px-5 py-4">
        <span className="text-2xl mt-0.5">👥</span>
        <div className="flex flex-col gap-1">
          <p className="text-sm font-semibold text-blue-700">Qu&apos;est-ce que le rôle Back-up ?</p>
          <p className="text-xs text-blue-600 leading-relaxed">
            En tant que Back-up, vous êtes un <strong>collègue de même grade</strong> désigné
            pour assurer la continuité du poste pendant l&apos;absence de votre collègue.
            Vous devez <strong>valider sa demande en première étape</strong> du circuit,
            puis vous recevez des habilitations temporaires pendant toute la durée de l&apos;absence.
          </p>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-4">
        {[
          { label: "À valider",       value: aValider.length,   color: "#D97706", icon: "⏳" },
          { label: "Absences actives", value: enCours.length,   color: "#059669", icon: "🟢" },
          { label: "Historique",       value: historique.length, color: "#6B7280", icon: "📂" },
        ].map(s => (
          <div key={s.label} className="relative overflow-hidden rounded-xl border border-neutral-200 bg-white px-5 py-4">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xxs text-neutral-400 uppercase tracking-wider font-ui">{s.label}</p>
                <p className="font-heading text-3xl font-bold mt-1" style={{ color: s.color }}>
                  {loading ? "…" : s.value}
                </p>
              </div>
              <span className="text-2xl">{s.icon}</span>
            </div>
            <div className="absolute bottom-0 left-0 right-0 h-0.5" style={{ background: s.color }} />
          </div>
        ))}
      </div>

      {/* Section : demandes à valider */}
      {!loading && aValider.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">⏳ Demandes en attente de votre validation Back-up</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
              <span className="text-lg">⚠️</span>
              <p className="text-xs text-amber-700 leading-relaxed">
                Ces demandes nécessitent votre validation en tant que Back-up <strong>avant</strong> de
                poursuivre le circuit RH. Délai conseillé : <strong>2 jours ouvrés</strong>.
              </p>
            </div>
            {aValider.map(d => (
              <DemandeCarte key={d.id} d={d} actionLabel="✅ Valider en tant que Back-up" actionHref={`/${d.id}/validation`} highlight />
            ))}
          </CardContent>
        </Card>
      )}

      {/* Section : absences actives — je suis Back-up opérationnel */}
      {!loading && enCours.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">🟢 Absences en cours — vous êtes Back-up actif</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            <div className="flex items-start gap-3 rounded-lg border border-green-200 bg-green-50 px-4 py-3">
              <span className="text-lg">ℹ️</span>
              <p className="text-xs text-green-700 leading-relaxed">
                Ces demandes ont été validées. Vous exercez actuellement les habilitations
                temporaires du collègue absent. Elles seront révoquées automatiquement
                à son retour (anticipé ou à la date de fin prévue).
              </p>
            </div>
            {enCours.map(d => (
              <DemandeCarte key={d.id} d={d} actionLabel="Voir le détail" actionHref={`/${d.id}`} />
            ))}
          </CardContent>
        </Card>
      )}

      {/* Section : historique */}
      {!loading && historique.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">📂 Historique</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            {historique.map(d => (
              <DemandeCarte key={d.id} d={d} actionLabel="Voir" actionHref={`/${d.id}`} compact />
            ))}
          </CardContent>
        </Card>
      )}

      {/* État vide */}
      {!loading && demandes.length === 0 && (
        <div className="flex flex-col items-center gap-4 py-16 rounded-xl border-2 border-dashed border-neutral-200">
          <span className="text-5xl">👥</span>
          <div className="text-center">
            <p className="font-heading text-lg font-semibold text-primary-500">
              Aucune demande Back-up
            </p>
            <p className="text-sm text-neutral-400 mt-1">
              Aucun collègue ne vous a désigné comme Back-up pour l&apos;instant.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

function DemandeCarte({
  d,
  actionLabel,
  actionHref,
  highlight = false,
  compact = false,
}: {
  d: Absence;
  actionLabel: string;
  actionHref: string;
  highlight?: boolean;
  compact?: boolean;
}) {
  const type   = TYPE_LABELS[d.type] ?? { label: d.type, icon: "❓", color: "#6B7280" };
  const statut = STATUT_CONFIG[d.statut] ?? { label: d.statut, color: "#6B7280", bg: "#F3F4F6", icon: "❓" };

  return (
    <div className={`rounded-xl border px-5 py-4 flex items-center justify-between gap-4 ${
      highlight ? "border-amber-300 bg-amber-50/50" : "border-neutral-200 bg-white"
    }`}>
      <div className="flex items-center gap-4 min-w-0">
        <div className="w-11 h-11 rounded-xl flex items-center justify-center text-xl flex-shrink-0"
          style={{ background: type.color + "15" }}>
          {type.icon}
        </div>
        <div className="min-w-0">
          <p className="text-sm font-semibold text-primary-500">{type.label}</p>
          <p className="text-xs text-neutral-400">
            Demandeur : <span className="font-mono">{d.demandeurIdentifiantExterne}</span>
          </p>
          {!compact && (
            <p className="text-xs text-neutral-400">
              {d.dateDebut} → {d.dateFin ?? "—"} · {d.nombreJours ?? "?"} jour(s)
            </p>
          )}
        </div>
      </div>
      <div className="flex flex-col items-end gap-2 flex-shrink-0">
        <span className="flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold"
          style={{ background: statut.bg, color: statut.color }}>
          {statut.icon} {statut.label}
        </span>
        <Button asChild size="sm" variant={highlight ? "default" : "outline"}>
          <Link href={actionHref}>{actionLabel}</Link>
        </Button>
      </div>
    </div>
  );
}
