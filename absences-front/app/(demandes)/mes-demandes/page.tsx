export const dynamic = "force-dynamic";

import Link from "next/link";
import Image from "next/image";
import { Button } from "@/components/ui/button";
import { serverApiClient } from "@/lib/api/server-client";
import {
  Baby, Ban, CheckCircle2, ClipboardList, FilePen, HelpCircle, Hourglass,
  Inbox, Lock, Plane, Search, Stethoscope, TreePalm, XCircle, type LucideIcon,
} from "lucide-react";

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

const STATUT_CONFIG: Record<string, { label: string; color: string; bg: string; icon: LucideIcon }> = {
  BROUILLON:              { label: "Brouillon",             color: "#6B7280", bg: "#F3F4F6", icon: FilePen },
  EN_VALIDATION_ETAPE:    { label: "En validation",         color: "#D97706", bg: "#FFFBEB", icon: Hourglass },
  VALIDEE:                { label: "Validée",               color: "#059669", bg: "#ECFDF5", icon: CheckCircle2 },
  REJETEE:                { label: "Rejetée",               color: "#DC2626", bg: "#FEF2F2", icon: XCircle },
  REJETEE_PAR_LE_SYSTEME: { label: "Rejetée (système)",     color: "#DC2626", bg: "#FEF2F2", icon: Ban },
  CLOTUREE:               { label: "Clôturée",              color: "#4B5563", bg: "#F9FAFB", icon: Lock },
  INSTRUCTION:            { label: "En instruction",        color: "#7C3AED", bg: "#F5F3FF", icon: Search },
};

const TYPE_LABELS: Record<string, { label: string; icon: LucideIcon; color: string }> = {
  CONGE_ANNUEL:    { label: "Congé annuel",         icon: TreePalm,      color: "#C41E22" },
  CONGE_MALADIE:   { label: "Congé maladie",        icon: Stethoscope,   color: "#1A1A2E" },
  PERMISSION:      { label: "Permission",           icon: ClipboardList, color: "#B8932A" },
  MISSION_LONGUE:  { label: "Mission longue durée", icon: Plane,         color: "#2C2C2C" },
  CONGE_MATERNITE: { label: "Congé maternité",      icon: Baby,          color: "#96751A" },
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
    <div className="flex flex-col gap-8 w-full py-4">

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
          { label: "Total",      value: stats.total,    color: "#2C2C2C", icon: ClipboardList },
          { label: "En cours",   value: stats.enCours,  color: "#D97706", icon: Hourglass },
          { label: "Validées",   value: stats.validees, color: "#059669", icon: CheckCircle2 },
          { label: "Rejetées",   value: stats.rejetees, color: "#DC2626", icon: XCircle },
        ].map((s) => (
          <div key={s.label}
            className="relative overflow-hidden rounded-xl border border-neutral-200 bg-white/90 px-5 py-4 shadow-card">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xxs text-neutral-400 uppercase tracking-wider font-ui">{s.label}</p>
                <p className="font-heading text-3xl font-bold mt-1" style={{ color: s.color }}>{s.value}</p>
              </div>
              <s.icon size={22} style={{ color: s.color }} />
            </div>
            <div className="absolute bottom-0 left-0 right-0 h-0.5" style={{ background: s.color }} />
          </div>
        ))}
      </div>

      {/* ── Liste (Tableau) ── */}
      {demandes.length === 0 ? (
        <div className="flex flex-col items-center gap-4 py-20 rounded-xl border-2 border-dashed border-neutral-200 bg-white/50">
          <Inbox size={48} className="text-neutral-300" />
          <div className="text-center">
            <p className="font-heading text-lg font-semibold text-primary-500">Aucune demande</p>
            <p className="text-sm text-neutral-400 mt-1">Vous n&apos;avez pas encore soumis de demande d&apos;absence.</p>
          </div>
          <Button asChild><Link href="/">Créer ma première demande</Link></Button>
        </div>
      ) : (
        <div className="bg-white/90 backdrop-blur-sm rounded-xl border border-neutral-200 shadow-card overflow-hidden">
          <div className="overflow-x-auto w-full">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-neutral-50/80 border-b border-neutral-200 text-xs font-semibold text-neutral-500 uppercase tracking-wider font-ui">
                  <th className="py-4 px-6 font-medium">Type d'absence</th>
                  <th className="py-4 px-6 font-medium">Période</th>
                  <th className="py-4 px-6 font-medium">Durée</th>
                  <th className="py-4 px-6 font-medium">Statut</th>
                  <th className="py-4 px-6 text-right font-medium">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-neutral-100">
                {demandes.map((d) => {
                  const typeKey = d.type ?? d.typeAbsence ?? "";
                  const type   = TYPE_LABELS[typeKey] ?? { label: typeKey, icon: HelpCircle, color: "#6B7280" };
                  const statut = STATUT_CONFIG[d.statut] ?? { label: d.statut, color: "#6B7280", bg: "#F3F4F6", icon: HelpCircle };
                  const statutText = d.statut === "EN_VALIDATION_ETAPE" && d.etapeCouranteLibelle ? `${statut.label} (${d.etapeCouranteLibelle})` : statut.label;
                  
                  return (
                    <tr key={d.id} className="group hover:bg-neutral-50/80 transition-colors">
                      <td className="py-3 px-6 whitespace-nowrap">
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0" style={{ background: type.color + "15", color: type.color }}>
                            <type.icon size={16} />
                          </div>
                          <span className="font-semibold text-primary-500 text-sm">{type.label}</span>
                        </div>
                      </td>
                      <td className="py-3 px-6 text-sm text-neutral-600 whitespace-nowrap">
                        {d.dateDebut} <span className="text-neutral-400 mx-1">→</span> {d.dateFin ?? "—"}
                      </td>
                      <td className="py-3 px-6 text-sm text-neutral-600 whitespace-nowrap">
                        {d.nombreJours ?? "?"} jour(s)
                      </td>
                      <td className="py-3 px-6 whitespace-nowrap">
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold" style={{ background: statut.bg, color: statut.color }}>
                          <statut.icon size={12} /> {statutText}
                        </span>
                      </td>
                      <td className="py-3 px-6 text-right whitespace-nowrap">
                        <Link href={`/demande/${d.id}`} className="inline-flex items-center gap-2 px-3 py-1.5 rounded-md text-neutral-600 hover:text-gold-700 hover:bg-gold-50 transition-colors border border-neutral-200 hover:border-gold-400 font-medium text-sm">
                          <FilePen size={16} /> Détails
                        </Link>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
