export const dynamic = "force-dynamic";

import Link from "next/link";
import Image from "next/image";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { serverApiClient } from "@/lib/api/server-client";
import { auth } from "@/auth";
import {
  Baby, Ban, CheckCircle2, ClipboardList, FilePen, HelpCircle, Hourglass,
  Landmark, Lock, Plane, Plus, Search, Send, Stethoscope, TreePalm, Users,
  XCircle, type LucideIcon,
} from "lucide-react";

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
  demandeurNom?: string; // Peut provenir de l'API (ex: demandeur.nomComplet ou demandeurNom)
  demandeurMatricule?: string;
  demandeur?: { nomComplet?: string, matricule?: string };
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

const TYPE_LABELS: Record<string, { label: string; icon: LucideIcon }> = {
  CONGE_ANNUEL:    { label: "Congé annuel",         icon: TreePalm },
  CONGE_MALADIE:   { label: "Congé maladie",        icon: Stethoscope },
  PERMISSION:      { label: "Permission",           icon: ClipboardList },
  MISSION:         { label: "Mission classique",    icon: Plane },
  MISSION_LONGUE:  { label: "Mission longue durée", icon: Plane },
  CONGE_MATERNITE: { label: "Congé maternité",      icon: Baby },
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
    <div className="flex flex-col gap-6 w-full py-4">

      {/* Hero */}
      <div className="relative overflow-hidden rounded-xl" style={{ minHeight: "130px" }}>
        <Image src="/Image_africaine6_resize.png" alt="" fill sizes="100vw" className="object-cover object-center" />
        <div className="absolute inset-0" style={{ background: "linear-gradient(110deg,rgba(26,26,46,0.92) 0%,rgba(196,30,34,0.30) 100%)" }} />
        <div className="relative z-10 flex items-center justify-between px-8 py-7 h-full">
          <div className="flex flex-col gap-1">
            <span className="text-xxs text-gold-300 tracking-[0.2em] uppercase font-ui">Mon espace — AFB</span>
            <h1 className="font-heading text-3xl font-bold text-white">Tableau de bord</h1>
          </div>
          <Button asChild className="flex-shrink-0 bg-white/20 backdrop-blur-md text-white border border-white/30 hover:bg-white/30 shadow-none">
            <Link href="/">+ Nouvelle demande</Link>
          </Button>
        </div>
        <div className="absolute bottom-0 left-0 right-0 h-1" style={{ background: KENTE }} />
      </div>

      {/* Solde congés */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2"><TreePalm size={18} /> Solde congé annuel — Exercice {solde.exercice}</CardTitle>
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
          {solde.joursAcquis > 0 && (
            <div className="mt-6 flex flex-col gap-2">
              <div className="flex justify-between text-xs font-semibold text-neutral-500">
                <span>Consommation : {Math.round((solde.joursPris / solde.joursAcquis) * 100)}%</span>
                <span>{solde.joursRestants} jours restants</span>
              </div>
              <div className="h-2 w-full rounded-full bg-neutral-100 overflow-hidden flex">
                <div 
                  className="h-full bg-[#C41E22] transition-all duration-500" 
                  style={{ width: `${(solde.joursPris / solde.joursAcquis) * 100}%` }}
                />
                <div 
                  className="h-full bg-[#059669] transition-all duration-500" 
                  style={{ width: `${(solde.joursRestants / solde.joursAcquis) * 100}%` }}
                />
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        {[
          { label: "En cours",   value: enCours.length,    color: "#D97706", icon: Hourglass },
          { label: "Validées",   value: validees.length,   color: "#059669", icon: CheckCircle2 },
          { label: "Rejetées",   value: rejetees.length,   color: "#DC2626", icon: XCircle },
          { label: "Brouillons", value: brouillons.length, color: "#6B7280", icon: FilePen },
        ].map(s => (
          <div key={s.label} className="relative overflow-hidden rounded-xl border border-neutral-200 bg-white px-5 py-4">
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

      {/* Actions rapides */}
      <div className="grid grid-cols-2 gap-3">
        <Link href="/" className="flex items-center gap-3 rounded-xl border border-neutral-200 bg-white px-4 py-4 hover:border-gold-400 transition-colors">
          <Plus size={24} className="text-gold-600 flex-shrink-0" />
          <div>
            <p className="text-sm font-semibold text-primary-500">Nouvelle demande</p>
            <p className="text-xs text-neutral-400">Créer une absence</p>
          </div>
        </Link>
        <Link href="/mes-demandes" className="flex items-center gap-3 rounded-xl border border-neutral-200 bg-white px-4 py-4 hover:border-gold-400 transition-colors">
          <span className="text-2xl text-gold-600 flex-shrink-0">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path stroke="none" d="M0 0h24v24H0z" fill="none" /><path d="M14 3v4a1 1 0 0 0 1 1h4" /><path d="M17 21h-10a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2h7l5 5v11a2 2 0 0 1 -2 2" /><path d="M9 14v.01" /><path d="M12 14v.01" /><path d="M15 14v.01" /></svg>
          </span>
          <div>
            <p className="text-sm font-semibold text-primary-500">Mes demandes</p>
            <p className="text-xs text-neutral-400">Voir l&apos;historique complet</p>
          </div>
        </Link>
        <Link href="/backup" className="flex items-center gap-3 rounded-xl border border-neutral-200 bg-white px-4 py-4 hover:border-gold-400 transition-colors col-span-2">
          <Users size={24} className="text-gold-600 flex-shrink-0" />
          <div>
            <p className="text-sm font-semibold text-primary-500">Mon rôle Back-up</p>
            <p className="text-xs text-neutral-400">Demandes où je suis désigné comme Back-up d&apos;un collègue</p>
          </div>
        </Link>
      </div>

      {/* Demandes à valider (section manager/validateur) */}
      {aValider.length > 0 && (
        <div className="relative overflow-hidden rounded-xl border-2 border-secondary-400 bg-secondary-50 px-5 py-4 flex flex-col gap-4">
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
          <DemandeTable demandes={aValider} actionType="validate" showDemandeur={true} />
        </div>
      )}

      {/* Demandes en cours */}
      {enCours.length > 0 && (
        <Card className="overflow-hidden">
          <CardHeader className="bg-white/50 border-b border-neutral-100 pb-4">
            <CardTitle className="text-base flex items-center gap-2"><Hourglass size={18} className="text-gold-500" /> Demandes en cours de traitement</CardTitle>
          </CardHeader>
          <DemandeTable demandes={enCours} />
        </Card>
      )}

      {/* Toutes les demandes */}
      <Card className="overflow-hidden">
        <CardHeader className="bg-white/50 border-b border-neutral-100 pb-4">
          <div className="flex items-center justify-between">
            <CardTitle className="text-base flex items-center gap-2"><ClipboardList size={18} className="text-gold-500" /> Toutes mes demandes</CardTitle>
            <Link href="/mes-demandes" className="text-xs font-semibold text-gold-600 hover:text-gold-700 hover:underline">Voir tout →</Link>
          </div>
        </CardHeader>
        {demandes.length === 0 ? (
          <p className="text-sm text-neutral-400 text-center py-8">Aucune demande pour l&apos;instant.</p>
        ) : (
          <DemandeTable demandes={demandes.slice(0, 5)} />
        )}
      </Card>

      {/* Espaces privilégiés (RH) */}
      {(estAnalysteRH || estDRH) && (
        <div className="flex flex-col gap-3">
          <p className="text-xs font-semibold text-neutral-400 uppercase tracking-wider">Espace RH</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {estAnalysteRH && (
              <Link href="/analyste-rh"
                className="flex items-center gap-3 rounded-xl border-2 border-purple-200 bg-purple-50 px-4 py-4 hover:border-purple-400 transition-colors">
                <Search size={24} className="text-purple-600 flex-shrink-0" />
                <div>
                  <p className="text-sm font-semibold text-purple-700">File d&apos;instruction RH</p>
                  <p className="text-xs text-purple-500">Demandes à transmettre au DRH</p>
                </div>
              </Link>
            )}
            {estDRH && (
              <Link href="/drh"
                className="flex items-center gap-3 rounded-xl border-2 border-amber-200 bg-amber-50 px-4 py-4 hover:border-amber-400 transition-colors">
                <Landmark size={24} className="text-amber-600 flex-shrink-0" />
                <div>
                  <p className="text-sm font-semibold text-amber-700">Validation DRH</p>
                  <p className="text-xs text-amber-500">Demandes en attente de décision finale</p>
                </div>
              </Link>
            )}
          </div>
        </div>
      )}

    </div>
  );
}

function DemandeTable({ demandes, actionType = "view", showDemandeur = false }: { demandes: Absence[], actionType?: "view" | "validate", showDemandeur?: boolean }) {
  return (
    <div className="overflow-x-auto w-full">
      <table className="w-full text-left border-collapse">
        <thead>
          <tr className="bg-neutral-50/80 border-b border-neutral-100 text-xs font-semibold text-neutral-500 uppercase tracking-wider font-ui">
            {showDemandeur && <th className="py-3 px-5 font-medium">Demandeur</th>}
            <th className="py-3 px-5 font-medium w-1/3">Type d'absence</th>
            <th className="py-3 px-5 font-medium">Période</th>
            <th className="py-3 px-5 font-medium">Durée</th>
            <th className="py-3 px-5 font-medium">Statut</th>
            <th className="py-3 px-5 text-right font-medium">Action</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-neutral-100">
          {demandes.map((d) => {
            const typeKey = d.type ?? (d as any).typeAbsence ?? "";
            const type   = TYPE_LABELS[typeKey] ?? { label: typeKey, icon: HelpCircle };
            const statut = STATUT_CONFIG[d.statut] ?? { label: d.statut, color: "#6B7280", bg: "#F3F4F6", icon: HelpCircle };
            const nomDemandeur = d.demandeur?.nomComplet || d.demandeurNom || "Non renseigné";
            const matriculeDemandeur = d.demandeur?.matricule || d.demandeurMatricule || "";

            /* La liste « à valider » mélange 3 statuts, chacun avec sa propre action/endpoint :
               - EN_VALIDATION_ETAPE      → page /validation (décision d'étape)
               - EN_VALIDATION_DRH        → page /validation-drh (décision DRH)
               - EN_INSTRUCTION_ANALYSTE_RH (et autres) → page détail (bouton « Transmettre à la DRH »)
               Router tout vers /validation provoque un 409 TRANSITION_ILLEGALE. */
            const actionHref =
              actionType !== "validate"                 ? `/demande/${d.id}`
              : d.statut === "EN_VALIDATION_DRH"        ? `/demande/${d.id}/validation-drh`
              : d.statut === "EN_VALIDATION_ETAPE"      ? `/demande/${d.id}/validation`
              :                                           `/demande/${d.id}`;

            return (
              <tr key={d.id} className="group hover:bg-neutral-50 transition-colors">
                {showDemandeur && (
                  <td className="py-2.5 px-5 whitespace-nowrap">
                    <p className="font-semibold text-primary-500 text-sm">{nomDemandeur}</p>
                    {matriculeDemandeur && <p className="text-xs text-neutral-400">{matriculeDemandeur}</p>}
                  </td>
                )}
                <td className="py-2.5 px-5 whitespace-nowrap">
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-lg bg-neutral-100 flex items-center justify-center flex-shrink-0 text-neutral-500">
                      <type.icon size={16} />
                    </div>
                    <span className="font-semibold text-primary-500 text-sm">{type.label}</span>
                  </div>
                </td>
                <td className="py-2.5 px-5 text-sm text-neutral-600 whitespace-nowrap">
                  {d.dateDebut} <span className="text-neutral-400 mx-1">→</span> {d.dateFin ?? "—"}
                </td>
                <td className="py-2.5 px-5 text-sm text-neutral-600 whitespace-nowrap">
                  {d.nombreJours ?? "?"} jour(s)
                </td>
                <td className="py-2.5 px-5 whitespace-nowrap">
                  <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold" style={{ background: statut.bg, color: statut.color }}>
                    <statut.icon size={12} /> {statut.label}
                  </span>
                </td>
                <td className="py-2.5 px-5 text-right whitespace-nowrap">
                  <Link href={actionHref}
                    className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-md font-medium text-sm transition-colors border ${
                      actionType === "validate"
                        ? "text-secondary-600 hover:text-white hover:bg-secondary-600 border-secondary-200 hover:border-secondary-600"
                        : "text-neutral-600 hover:text-gold-700 hover:bg-gold-50 border-neutral-200 hover:border-gold-400"
                    }`}>
                    {actionType === "validate" ? (
                      <><CheckCircle2 size={16} /> Valider</>
                    ) : (
                      <><FilePen size={16} /> Détails</>
                    )}
                  </Link>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
