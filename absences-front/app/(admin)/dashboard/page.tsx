export const dynamic = "force-dynamic";

import Link from "next/link";
import Image from "next/image";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { serverApiClient } from "@/lib/api/server-client";
import {
  BarChart3, Ban, CheckCircle2, ClipboardList, Clock, FilePen, HelpCircle,
  Hourglass, Landmark, Lock, Search, Send, Settings, XCircle, type LucideIcon,
} from "lucide-react";

interface Absence {
  id: string;
  type: string;
  statut: string;
  demandeurIdentifiantExterne: string;
  dateDebut: string;
  dateFin: string | null;
  nombreJours: number | null;
}

interface Circuit {
  id: string;
  nom: string;
  typeAbsenceCible: string | null;
  actif: boolean;
  etapes: any[];
}

async function getData() {
  const api = await serverApiClient();
  const [toutesRes, enValidationRes, instructionRes, drhRes, valideeRes, rejeteeRes, circuitsRes] =
    await Promise.all([
      api.get("/api/v5/demandes"),
      api.get("/api/v5/demandes?statut=EN_VALIDATION_ETAPE"),
      api.get("/api/v5/demandes?statut=EN_INSTRUCTION_ANALYSTE_RH"),
      api.get("/api/v5/demandes?statut=EN_VALIDATION_DRH"),
      api.get("/api/v5/demandes?statut=VALIDEE"),
      api.get("/api/v5/demandes?statut=REJETEE"),
      api.get("/api/v5/admin/circuits").catch(() => ({ data: [] })),
    ]);
  return {
    toutes:       toutesRes.data      as Absence[],
    enValidation: enValidationRes.data as Absence[],
    instruction:  instructionRes.data  as Absence[],
    drh:          drhRes.data          as Absence[],
    validees:     valideeRes.data      as Absence[],
    rejetees:     rejeteeRes.data      as Absence[],
    circuits:     circuitsRes.data     as Circuit[],
  };
}

const TYPE_LABELS: Record<string, string> = {
  CONGE_ANNUEL:    "Congé annuel",
  CONGE_MALADIE:   "Congé maladie",
  PERMISSION:      "Permission",
  MISSION_LONGUE:  "Mission longue durée",
  CONGE_MATERNITE: "Congé maternité",
};

const STATUT_CONFIG: Record<string, { label: string; color: string; bg: string; icon: LucideIcon }> = {
  BROUILLON:                  { label: "Brouillon",          color: "#6B7280", bg: "#F3F4F6", icon: FilePen },
  SOUMISE:                    { label: "Soumise",            color: "#0EA5E9", bg: "#F0F9FF", icon: Send },
  EN_VALIDATION_ETAPE:        { label: "En validation",      color: "#D97706", bg: "#FFFBEB", icon: Hourglass },
  EN_INSTRUCTION_ANALYSTE_RH: { label: "En instruction RH", color: "#7C3AED", bg: "#F5F3FF", icon: Search },
  EN_VALIDATION_DRH:          { label: "En validation DRH", color: "#B8932A", bg: "#FDFBF0", icon: Landmark },
  VALIDEE:                    { label: "Validée",            color: "#059669", bg: "#ECFDF5", icon: CheckCircle2 },
  REJETEE:                    { label: "Rejetée",            color: "#DC2626", bg: "#FEF2F2", icon: XCircle },
  REJETEE_PAR_LE_SYSTEME:     { label: "Rejetée système",   color: "#DC2626", bg: "#FEF2F2", icon: Ban },
  CLOTUREE:                   { label: "Clôturée",           color: "#4B5563", bg: "#F9FAFB", icon: Lock },
};

const KENTE = "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

export default async function AdminDashboardPage() {
  const { toutes, enValidation, instruction, drh, validees, rejetees, circuits } = await getData();

  // Répartition par type
  const parType = toutes.reduce((acc, d) => {
    acc[d.type] = (acc[d.type] ?? 0) + 1;
    return acc;
  }, {} as Record<string, number>);

  return (
    <div className="flex flex-col gap-6 max-w-5xl mx-auto py-4">

      {/* Hero */}
      <div className="relative overflow-hidden rounded-xl" style={{ minHeight: "140px" }}>
        <Image src="/Image_africaine6_resize.png" alt="" fill sizes="100vw" className="object-cover object-center" />
        <div className="absolute inset-0"
          style={{ background: "linear-gradient(110deg,rgba(26,26,46,0.95) 0%,rgba(196,30,34,0.30) 100%)" }} />
        <div className="relative z-10 flex items-center justify-between px-8 py-8 h-full">
          <div className="flex flex-col gap-1">
            <span className="text-xxs text-gold-300 tracking-[0.2em] uppercase font-ui">Admin RH — AFB</span>
            <h1 className="font-heading text-3xl font-bold text-white">Tableau de bord administration</h1>
            <p className="text-sm text-neutral-300 mt-1">Vue globale de toutes les demandes en cours</p>
          </div>
          <Button asChild variant="outline" className="flex-shrink-0 border-white text-white hover:bg-white/10">
            <Link href="/circuits"><Settings size={16} /> Gérer les circuits</Link>
          </Button>
        </div>
        <div className="absolute bottom-0 left-0 right-0 h-1" style={{ background: KENTE }} />
      </div>

      {/* KPIs pipeline */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        {[
          { label: "Total",         value: toutes.length,       color: "#2C2C2C", icon: ClipboardList },
          { label: "En validation", value: enValidation.length, color: "#D97706", icon: Hourglass },
          { label: "Instruction",   value: instruction.length,  color: "#7C3AED", icon: Search },
          { label: "Attente DRH",   value: drh.length,          color: "#B8932A", icon: Landmark },
          { label: "Validées",      value: validees.length,     color: "#059669", icon: CheckCircle2 },
          { label: "Rejetées",      value: rejetees.length,     color: "#DC2626", icon: XCircle },
        ].map(s => (
          <div key={s.label} className="relative overflow-hidden rounded-xl border border-neutral-200 bg-white px-4 py-4">
            <div className="flex flex-col gap-1">
              <s.icon size={20} style={{ color: s.color }} />
              <p className="font-heading text-2xl font-bold" style={{ color: s.color }}>{s.value}</p>
              <p className="text-xxs text-neutral-400 uppercase tracking-wider font-ui">{s.label}</p>
            </div>
            <div className="absolute bottom-0 left-0 right-0 h-0.5" style={{ background: s.color }} />
          </div>
        ))}
      </div>

      {/* Répartition par type */}
      <Card>
        <CardHeader><CardTitle className="text-base flex items-center gap-2"><BarChart3 size={18} /> Répartition par type d&apos;absence</CardTitle></CardHeader>
        <CardContent>
          <div className="flex flex-col gap-3">
            {Object.entries(parType).map(([type, count]) => {
              const pct = Math.round((count / toutes.length) * 100);
              return (
                <div key={type} className="flex flex-col gap-1">
                  <div className="flex items-center justify-between text-sm">
                    <span className="font-medium text-primary-500">{TYPE_LABELS[type] ?? type}</span>
                    <span className="text-neutral-400 font-mono">{count} ({pct}%)</span>
                  </div>
                  <div className="h-2 rounded-full bg-neutral-100 overflow-hidden">
                    <div className="h-full rounded-full bg-primary-500 transition-all"
                      style={{ width: `${pct}%`, background: "#C41E22" }} />
                  </div>
                </div>
              );
            })}
          </div>
        </CardContent>
      </Card>

      {/* Accès rapides aux files */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {[
          { href: "/validation-file", icon: Hourglass, label: "File de validation",  desc: `${enValidation.length} demande(s)`, color: "#D97706", bg: "#FFFBEB" },
          { href: "/analyste-rh",     icon: Search,    label: "File Analyste RH",    desc: `${instruction.length} demande(s)`,  color: "#7C3AED", bg: "#F5F3FF" },
          { href: "/drh",             icon: Landmark,  label: "File DRH",            desc: `${drh.length} demande(s)`,          color: "#B8932A", bg: "#FDFBF0" },
        ].map(s => (
          <Link key={s.href} href={s.href}
            className="flex items-center gap-4 rounded-xl border px-5 py-4 hover:shadow-md transition-all"
            style={{ borderColor: s.color, background: s.bg }}>
            <s.icon size={28} style={{ color: s.color }} />
            <div>
              <p className="text-sm font-semibold" style={{ color: s.color }}>{s.label}</p>
              <p className="text-xs text-neutral-500">{s.desc}</p>
            </div>
          </Link>
        ))}
      </div>

      {/* Circuits de validation disponibles */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-base flex items-center gap-2"><Settings size={18} /> Circuits de validation configurés</CardTitle>
            <Button asChild variant="ghost" size="sm">
              <Link href="/circuits">Gérer</Link>
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col gap-3">
            {circuits && circuits.length > 0 ? (
              circuits.map((c) => (
                <div
                  key={c.id}
                  className="flex items-center justify-between rounded-lg border border-neutral-100 bg-neutral-50 px-4 py-3"
                >
                  <div>
                    <p className="text-sm font-medium text-primary-500 flex items-center gap-2">
                      {c.nom}
                      {c.actif ? (
                        <span className="bg-green-100 text-green-700 text-xs px-2 py-0.5 rounded-full font-semibold">Actif</span>
                      ) : (
                        <span className="bg-gray-200 text-gray-700 text-xs px-2 py-0.5 rounded-full font-semibold">Inactif</span>
                      )}
                    </p>
                    <p className="text-xs text-neutral-500 mt-1">
                      Cible : {c.typeAbsenceCible ? (TYPE_LABELS[c.typeAbsenceCible] ?? c.typeAbsenceCible) : "Toutes absences"}
                      {" · "}
                      {c.etapes?.length || 0} étape(s)
                    </p>
                  </div>
                </div>
              ))
            ) : (
              <p className="text-sm text-neutral-500">Aucun circuit de validation configuré.</p>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Dernières demandes toutes catégories */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-base flex items-center gap-2"><Clock size={18} /> Dernières demandes</CardTitle>
            <span className="text-xs text-neutral-400">{toutes.length} au total</span>
          </div>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {toutes.slice(0, 10).map(d => {
            const statut = STATUT_CONFIG[d.statut] ?? { label: d.statut, color: "#6B7280", bg: "#F3F4F6", icon: HelpCircle };
            return (
              <Link key={d.id} href={`/${d.id}`}
                className="flex items-center justify-between rounded-lg border border-neutral-100 bg-neutral-50 px-4 py-3 hover:bg-neutral-100 transition-colors">
                <div>
                  <p className="text-sm font-medium text-primary-500">
                    {TYPE_LABELS[d.type] ?? d.type}
                  </p>
                  <p className="text-xs text-neutral-400">
                    {d.demandeurIdentifiantExterne} · {d.dateDebut} → {d.dateFin ?? "—"}
                  </p>
                </div>
                <span className="flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold flex-shrink-0"
                  style={{ background: statut.bg, color: statut.color }}>
                  <statut.icon size={12} /> {statut.label}
                </span>
              </Link>
            );
          })}
        </CardContent>
      </Card>
    </div>
  );
}
