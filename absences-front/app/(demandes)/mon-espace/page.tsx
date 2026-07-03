export const dynamic = "force-dynamic";

import Link from "next/link";
import Image from "next/image";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { serverApiClient } from "@/lib/api/server-client";
import { auth } from "@/auth";

interface Solde {
  joursAcquis: number;
  joursPris: number;
  joursRestants: number;
  exercice: number;
}

interface Absence {
  id: string;
  type: string;
  dateDebut: string;
  dateFin: string | null;
  nombreJours: number | null;
  statut: string;
}

const SOLDE_VIDE: Solde = { joursAcquis: 0, joursPris: 0, joursRestants: 0, exercice: new Date().getFullYear() };

async function getData(): Promise<{ solde: Solde; demandes: Absence[]; aValider: Absence[] }> {
  try {
    const api = await serverApiClient();
    const [soldeRes, demandesRes, aValiderRes] = await Promise.all([
      api.get("/api/v5/demandes/moi/solde"),
      api.get("/api/v5/demandes/moi"),
      api.get("/api/v5/demandes/a-valider"),
    ]);
    return {
      solde:     soldeRes.data    ?? SOLDE_VIDE,
      demandes:  demandesRes.data ?? [],
      aValider:  aValiderRes.data ?? [],
    };
  } catch {
    return { solde: SOLDE_VIDE, demandes: [], aValider: [] };
  }
}

const STATUT_CONFIG: Record<string, { label: string; color: string; bg: string; icon: string }> = {
  BROUILLON:                  { label: "Brouillon",          color: "#6B7280", bg: "#F3F4F6", icon: "📝" },
  SOUMISE:                    { label: "Soumise",            color: "#0EA5E9", bg: "#F0F9FF", icon: "📤" },
  EN_VALIDATION_ETAPE:        { label: "En validation",      color: "#D97706", bg: "#FFFBEB", icon: "⏳" },
  EN_INSTRUCTION_ANALYSTE_RH: { label: "En instruction RH", color: "#7C3AED", bg: "#F5F3FF", icon: "🔍" },
  EN_VALIDATION_DRH:          { label: "En validation DRH", color: "#B8932A", bg: "#FDFBF0", icon: "🏛️" },
  VALIDEE:                    { label: "Validée",            color: "#059669", bg: "#ECFDF5", icon: "✅" },
  REJETEE:                    { label: "Rejetée",            color: "#DC2626", bg: "#FEF2F2", icon: "❌" },
  REJETEE_PAR_LE_SYSTEME:     { label: "Rejetée système",   color: "#DC2626", bg: "#FEF2F2", icon: "🚫" },
  CLOTUREE:                   { label: "Clôturée",           color: "#4B5563", bg: "#F9FAFB", icon: "🔒" },
};

const TYPE_LABELS: Record<string, { label: string; icon: string }> = {
  CONGE_ANNUEL:    { label: "Congé annuel",         icon: "🌴" },
  CONGE_MALADIE:   { label: "Congé maladie",        icon: "🏥" },
  PERMISSION:      { label: "Permission",           icon: "📋" },
  MISSION_LONGUE:  { label: "Mission longue durée", icon: "✈️" },
  CONGE_MATERNITE: { label: "Congé maternité",      icon: "👶" },
};

const KENTE = "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

export default async function MonEspacePage() {
  const [{ solde, demandes, aValider }, session] = await Promise.all([getData(), auth()]);
  const roles: string[] = (session as { roles?: string[] })?.roles ?? [];
  const estAnalysteRH = roles.includes("ANALYSTE_RH");
  const estDRH        = roles.includes("DRH");

  const enCours    = demandes.filter(d => ["EN_VALIDATION_ETAPE","EN_INSTRUCTION_ANALYSTE_RH","EN_VALIDATION_DRH"].includes(d.statut));
  const validees   = demandes.filter(d => d.statut === "VALIDEE");
  const rejetees   = demandes.filter(d => ["REJETEE","REJETEE_PAR_LE_SYSTEME"].includes(d.statut));
  const brouillons = demandes.filter(d => d.statut === "BROUILLON");

  return (
    <div className="flex flex-col gap-6 max-w-4xl mx-auto py-4">

      {/* Hero */}
      <div className="relative overflow-hidden rounded-xl" style={{ minHeight: "130px" }}>
        <Image src="/Image_africaine6_resize.png" alt="" fill sizes="100vw" className="object-cover object-center" />
        <div className="absolute inset-0" style={{ background: "linear-gradient(110deg,rgba(26,26,46,0.92) 0%,rgba(196,30,34,0.30) 100%)" }} />
        <div className="relative z-10 flex items-center justify-between px-8 py-7 h-full">
          <div className="flex flex-col gap-1">
            <span className="text-xxs text-gold-300 tracking-[0.2em] uppercase font-ui">Mon espace — AFB</span>
            <h1 className="font-heading text-3xl font-bold text-white">Tableau de bord</h1>
          </div>
          <Button asChild className="flex-shrink-0">
            <Link href="/">+ Nouvelle demande</Link>
          </Button>
        </div>
        <div className="absolute bottom-0 left-0 right-0 h-1" style={{ background: KENTE }} />
      </div>

      {/* Solde congés */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">🌴 Solde congé annuel — Exercice {solde.exercice}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-3 gap-4">
            {[
              { label: "Jours acquis",   value: solde.joursAcquis,   color: "#2C2C2C" },
              { label: "Jours pris",     value: solde.joursPris,     color: "#C41E22" },
              { label: "Jours restants", value: solde.joursRestants, color: "#059669" },
            ].map(s => (
              <div key={s.label} className="flex flex-col items-center rounded-xl border border-neutral-200 py-5 gap-1">
                <span className="font-heading text-4xl font-bold" style={{ color: s.color }}>{s.value}</span>
                <span className="text-xs text-neutral-500 font-ui uppercase tracking-wider">{s.label}</span>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        {[
          { label: "En cours",   value: enCours.length,    color: "#D97706", icon: "⏳" },
          { label: "Validées",   value: validees.length,   color: "#059669", icon: "✅" },
          { label: "Rejetées",   value: rejetees.length,   color: "#DC2626", icon: "❌" },
          { label: "Brouillons", value: brouillons.length, color: "#6B7280", icon: "📝" },
        ].map(s => (
          <div key={s.label} className="relative overflow-hidden rounded-xl border border-neutral-200 bg-white px-5 py-4">
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

      {/* Demandes à valider (section manager/validateur) */}
      {aValider.length > 0 && (
        <div className="relative overflow-hidden rounded-xl border-2 border-secondary-400 bg-secondary-50 px-5 py-4 flex flex-col gap-3">
          <div className="flex items-center gap-3">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-secondary-500 text-white text-base font-bold flex-shrink-0">
              {aValider.length}
            </span>
            <div>
              <p className="font-heading font-semibold text-secondary-700 text-sm">
                {aValider.length === 1
                  ? "1 demande en attente de votre validation"
                  : `${aValider.length} demandes en attente de votre validation`}
              </p>
              <p className="text-xs text-secondary-500">Votre action est requise pour traiter ces demandes.</p>
            </div>
          </div>
          <div className="flex flex-col gap-2">
            {aValider.map(d => {
              const type = TYPE_LABELS[d.type] ?? { label: d.type, icon: "❓" };
              return (
                <Link key={d.id} href={`/${d.id}`}
                  className="flex items-center justify-between rounded-lg border border-secondary-200 bg-white px-4 py-3 hover:bg-secondary-50 transition-colors">
                  <div className="flex items-center gap-3">
                    <span className="text-xl">{type.icon}</span>
                    <div>
                      <p className="text-sm font-medium text-primary-500">{type.label}</p>
                      <p className="text-xs text-neutral-400">{d.dateDebut} → {d.dateFin ?? "—"} · {d.nombreJours ?? "?"} j</p>
                    </div>
                  </div>
                  <span className="text-xs font-semibold text-secondary-600 flex items-center gap-1">
                    Valider →
                  </span>
                </Link>
              );
            })}
          </div>
        </div>
      )}

      {/* Demandes en cours */}
      {enCours.length > 0 && (
        <Card>
          <CardHeader><CardTitle className="text-base">⏳ Demandes en cours de traitement</CardTitle></CardHeader>
          <CardContent className="flex flex-col gap-2">
            {enCours.map(d => <DemandeLigne key={d.id} d={d} />)}
          </CardContent>
        </Card>
      )}

      {/* Toutes les demandes */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-base">📋 Toutes mes demandes</CardTitle>
            <Link href="/mes-demandes" className="text-xs text-gold-600 hover:underline">Voir tout →</Link>
          </div>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {demandes.length === 0 ? (
            <p className="text-sm text-neutral-400 text-center py-6">Aucune demande pour l&apos;instant.</p>
          ) : (
            demandes.slice(0, 5).map(d => <DemandeLigne key={d.id} d={d} />)
          )}
        </CardContent>
      </Card>

      {/* Espaces privilégiés (RH) */}
      {(estAnalysteRH || estDRH) && (
        <div className="flex flex-col gap-3">
          <p className="text-xs font-semibold text-neutral-400 uppercase tracking-wider">Espace RH</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {estAnalysteRH && (
              <Link href="/analyste-rh"
                className="flex items-center gap-3 rounded-xl border-2 border-purple-200 bg-purple-50 px-4 py-4 hover:border-purple-400 transition-colors">
                <span className="text-2xl">🔍</span>
                <div>
                  <p className="text-sm font-semibold text-purple-700">File d&apos;instruction RH</p>
                  <p className="text-xs text-purple-500">Demandes à transmettre au DRH</p>
                </div>
              </Link>
            )}
            {estDRH && (
              <Link href="/drh"
                className="flex items-center gap-3 rounded-xl border-2 border-amber-200 bg-amber-50 px-4 py-4 hover:border-amber-400 transition-colors">
                <span className="text-2xl">🏛️</span>
                <div>
                  <p className="text-sm font-semibold text-amber-700">Validation DRH</p>
                  <p className="text-xs text-amber-500">Demandes en attente de décision finale</p>
                </div>
              </Link>
            )}
          </div>
        </div>
      )}

      {/* Actions rapides */}
      <div className="grid grid-cols-2 gap-3">
        <Link href="/" className="flex items-center gap-3 rounded-xl border border-neutral-200 bg-white px-4 py-4 hover:border-gold-400 transition-colors">
          <span className="text-2xl">➕</span>
          <div>
            <p className="text-sm font-semibold text-primary-500">Nouvelle demande</p>
            <p className="text-xs text-neutral-400">Créer une absence</p>
          </div>
        </Link>
        <Link href="/mes-demandes" className="flex items-center gap-3 rounded-xl border border-neutral-200 bg-white px-4 py-4 hover:border-gold-400 transition-colors">
          <span className="text-2xl">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" className="icon icon-tabler icons-tabler-outline icon-tabler-file-dots">
              <path stroke="none" d="M0 0h24v24H0z" fill="none" />
              <path d="M14 3v4a1 1 0 0 0 1 1h4" />
              <path d="M17 21h-10a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2h7l5 5v11a2 2 0 0 1 -2 2" />
              <path d="M9 14v.01" />
              <path d="M12 14v.01" />
              <path d="M15 14v.01" />
            </svg>
          </span>
          <div>
            <p className="text-sm font-semibold text-primary-500">Mes demandes</p>
            <p className="text-xs text-neutral-400">Voir l&apos;historique complet</p>
          </div>
        </Link>
        <Link href="/backup" className="flex items-center gap-3 rounded-xl border border-neutral-200 bg-white px-4 py-4 hover:border-gold-400 transition-colors col-span-2">
          <span className="text-2xl">👥</span>
          <div>
            <p className="text-sm font-semibold text-primary-500">Mon rôle Back-up</p>
            <p className="text-xs text-neutral-400">
              Demandes où je suis désigné comme Back-up d&apos;un collègue
            </p>
          </div>
        </Link>
      </div>
    </div>
  );
}

function DemandeLigne({ d }: { d: Absence }) {
  const type   = TYPE_LABELS[d.type] ?? { label: d.type, icon: "❓" };
  const statut = STATUT_CONFIG[d.statut] ?? { label: d.statut, color: "#6B7280", bg: "#F3F4F6", icon: "❓" };
  return (
    <Link href={`/${d.id}`}
      className="flex items-center justify-between rounded-lg border border-neutral-200 bg-neutral-50 px-4 py-3 hover:bg-neutral-100 transition-colors">
      <div className="flex items-center gap-3">
        <span className="text-xl">{type.icon}</span>
        <div>
          <p className="text-sm font-medium text-primary-500">{type.label}</p>
          <p className="text-xs text-neutral-400">{d.dateDebut} → {d.dateFin ?? "—"} · {d.nombreJours ?? "?"} j</p>
        </div>
      </div>
      <span className="flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold"
        style={{ background: statut.bg, color: statut.color }}>
        {statut.icon} {statut.label}
      </span>
    </Link>
  );
}
