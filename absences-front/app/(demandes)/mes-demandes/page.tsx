export const dynamic = "force-dynamic";

import Link from "next/link";
import Image from "next/image";
import { Button } from "@/components/ui/button";
import { serverApiClient } from "@/lib/api/server-client";

interface Absence {
  id: string;
  type: string;
  typeAbsence?: string;
  dateDebut: string;
  dateFin: string | null;
  nombreJours: number | null;
  statut: string;
  etapeCouranteLibelle?: string;
}

async function getMesDemandes(): Promise<Absence[]> {
  try {
    const api = await serverApiClient();
    const { data } = await api.get("/api/v5/demandes/moi");
    return data;
  } catch {
    return [];
  }
}

const STATUT_CONFIG: Record<string, { label: string; color: string; bg: string; icon: string }> = {
  BROUILLON:              { label: "Brouillon",             color: "#6B7280", bg: "#F3F4F6", icon: "📝" },
  EN_VALIDATION_ETAPE:    { label: "En validation",         color: "#D97706", bg: "#FFFBEB", icon: "⏳" },
  VALIDEE:                { label: "Validée",               color: "#059669", bg: "#ECFDF5", icon: "✅" },
  REJETEE:                { label: "Rejetée",               color: "#DC2626", bg: "#FEF2F2", icon: "❌" },
  REJETEE_PAR_LE_SYSTEME: { label: "Rejetée (système)",     color: "#DC2626", bg: "#FEF2F2", icon: "🚫" },
  CLOTUREE:               { label: "Clôturée",              color: "#4B5563", bg: "#F9FAFB", icon: "🔒" },
  INSTRUCTION:            { label: "En instruction",        color: "#7C3AED", bg: "#F5F3FF", icon: "🔍" },
};

const TYPE_LABELS: Record<string, { label: string; icon: string; color: string }> = {
  CONGE_ANNUEL:    { label: "Congé annuel",         icon: "🌴", color: "#C41E22" },
  CONGE_MALADIE:   { label: "Congé maladie",        icon: "🏥", color: "#1A1A2E" },
  PERMISSION:      { label: "Permission",           icon: "📋", color: "#B8932A" },
  MISSION_LONGUE:  { label: "Mission longue durée", icon: "✈️", color: "#2C2C2C" },
  CONGE_MATERNITE: { label: "Congé maternité",      icon: "👶", color: "#96751A" },
};

const KENTE = "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

export default async function MesDemandesPage() {
  const demandes = await getMesDemandes();

  const stats = {
    total:       demandes.length,
    enCours:     demandes.filter((d) => d.statut === "EN_VALIDATION_ETAPE").length,
    validees:    demandes.filter((d) => d.statut === "VALIDEE").length,
    rejetees:    demandes.filter((d) => ["REJETEE", "REJETEE_PAR_LE_SYSTEME"].includes(d.statut)).length,
  };

  return (
    <div className="flex flex-col gap-8">

      {/* ── Hero ── */}
      <div className="relative overflow-hidden rounded-xl" style={{ minHeight: "140px" }}>
        <Image src="/Image_africaine6_resize.png" alt="" fill sizes="100vw" className="object-cover object-center" />
        <div className="absolute inset-0"
          style={{ background: "linear-gradient(110deg,rgba(26,26,46,0.92) 0%,rgba(44,44,44,0.70) 60%,rgba(196,30,34,0.30) 100%)" }} />
        <div className="absolute inset-0 opacity-10"
          style={{ backgroundImage: "repeating-linear-gradient(45deg,#B8932A 0px,#B8932A 1px,transparent 1px,transparent 32px)" }} />
        <div className="relative z-10 h-full flex items-center justify-between px-8 py-8 gap-4">
          <div className="flex flex-col gap-1">
            <div className="flex items-center gap-2">
              <div className="h-px w-5 bg-gold-400" />
              <span className="text-xxs text-gold-300 tracking-[0.2em] uppercase font-ui">Portail RH — AFB</span>
            </div>
            <h1 className="font-heading text-3xl font-bold text-white">Mes demandes</h1>
          </div>
          <Button asChild className="flex-shrink-0">
            <Link href="/">+ Nouvelle demande</Link>
          </Button>
        </div>
        <div className="absolute bottom-0 left-0 right-0 h-1" style={{ background: KENTE }} />
      </div>

      {/* ── Stats ── */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        {[
          { label: "Total",      value: stats.total,    color: "#2C2C2C", icon: "📋" },
          { label: "En cours",   value: stats.enCours,  color: "#D97706", icon: "⏳" },
          { label: "Validées",   value: stats.validees, color: "#059669", icon: "✅" },
          { label: "Rejetées",   value: stats.rejetees, color: "#DC2626", icon: "❌" },
        ].map((s) => (
          <div key={s.label}
            className="relative overflow-hidden rounded-xl border border-neutral-200 bg-white/90 px-5 py-4 shadow-card">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xxs text-neutral-400 uppercase tracking-wider font-ui">{s.label}</p>
                <p className="font-heading text-3xl font-bold mt-1" style={{ color: s.color }}>{s.value}</p>
              </div>
              <span className="text-2xl">{s.icon}</span>
            </div>
            <div className="absolute bottom-0 left-0 right-0 h-0.5" style={{ background: s.color }} />
          </div>
        ))}
      </div>

      {/* ── Liste ── */}
      {demandes.length === 0 ? (
        <div className="flex flex-col items-center gap-4 py-20 rounded-xl border-2 border-dashed border-neutral-200">
          <span className="text-5xl">📭</span>
          <div className="text-center">
            <p className="font-heading text-lg font-semibold text-primary-500">Aucune demande</p>
            <p className="text-sm text-neutral-400 mt-1">Vous n&apos;avez pas encore soumis de demande d&apos;absence.</p>
          </div>
          <Button asChild><Link href="/">Créer ma première demande</Link></Button>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {demandes.map((d) => {
            const typeKey = d.type ?? d.typeAbsence ?? "";
            const type   = TYPE_LABELS[typeKey] ?? { label: typeKey, icon: "❓", color: "#6B7280" };
            const statut = STATUT_CONFIG[d.statut] ?? { label: d.statut, color: "#6B7280", bg: "#F3F4F6", icon: "❓" };
            const statutText = d.statut === "EN_VALIDATION_ETAPE" && d.etapeCouranteLibelle ? `${statut.label} (${d.etapeCouranteLibelle})` : statut.label;
            return (
              <Link key={d.id} href={`/${d.id}`}
                className="group relative overflow-hidden rounded-xl border border-neutral-200 bg-white/90 shadow-card hover:shadow-gold transition-all duration-350 px-5 py-4 flex items-center gap-4">
                {/* Barre colorée gauche */}
                <div className="absolute left-0 top-0 bottom-0 w-1 rounded-l-xl" style={{ background: type.color }} />

                {/* Icône */}
                <div className="w-11 h-11 rounded-xl flex items-center justify-center text-xl flex-shrink-0 transition-transform duration-350 group-hover:scale-110"
                  style={{ background: type.color + "15" }}>
                  {type.icon}
                </div>

                {/* Infos */}
                <div className="flex-1 min-w-0">
                  <p className="font-semibold text-primary-500 text-sm">{type.label}</p>
                  <p className="text-xs text-neutral-400 mt-0.5">
                    Du {d.dateDebut} au {d.dateFin ?? "—"} · {d.nombreJours ?? "?"} jour(s)
                  </p>
                </div>

                {/* Statut */}
                <span className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold flex-shrink-0"
                  style={{ background: statut.bg, color: statut.color }}>
                  {statut.icon} {statutText}
                </span>

                {/* Flèche */}
                <span className="text-neutral-300 group-hover:text-gold-500 transition-colors text-lg">›</span>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}
