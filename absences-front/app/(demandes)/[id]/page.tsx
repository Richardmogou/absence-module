import { auth } from "@/auth";
import Link from "next/link";
import Image from "next/image";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { serverApiClient } from "@/lib/api/server-client";
import { BoutonProlongation } from "./BoutonProlongation";
import BoutonsSupprimerModifier from "./BoutonsSupprimerModifier";
import BoutonInstruction from "./BoutonInstruction";
import {
  AlertTriangle, ArrowLeft, ArrowRight, ArrowRightLeft, Ban, Check,
  CheckCircle2, Circle, FileCheck, FilePen, FileText, HelpCircle, Hourglass,
  Landmark, Lock, Paperclip, PartyPopper, RotateCcw, Search, Send, X, XCircle,
  type LucideIcon,
} from "lucide-react";

function formatMinioUrl(url?: string | null) {
  if (!url) return "";
  return url.replace("http://minio:9000", "http://localhost:9000");
}

/* ── Types ── */
interface EtapeProgression {
  position: number;
  libelle: string;
  statut: "EN_ATTENTE" | "EN_COURS" | "APPROUVEE" | "REJETEE";
}

interface Absence {
  id: string;
  demandeurIdentifiantExterne: string;
  nomCompletDemandeur?: string;
  type: string;
  dateDebut: string;
  dateFin: string | null;
  nombreJours: number | null;
  statut: string;
  etapeCouranteLibelle?: string;
  motifRejetSysteme: string | null;
  estMonTourDeValider?: boolean;
  progression?: EtapeProgression[];
  justificatifs?: {
    id: string;
    typePiece: string;
    urlFichier: string;
    deposeLe: string;
  }[];
  documentMiseEnCongeUrl?: string;
}

async function getAbsence(id: string): Promise<Absence> {
  const api = await serverApiClient();
  const { data } = await api.get(`/api/v5/demandes/${id}`);
  return data;
}

/* ── Config statuts — tous les StatutDemande couverts ── */
const STATUT_CONFIG: Record<string, { label: string; color: string; bg: string; icon: LucideIcon }> = {
  BROUILLON:                  { label: "Brouillon",              color: "#6B7280", bg: "#F3F4F6", icon: FilePen },
  SOUMISE:                    { label: "Soumise",                color: "#0EA5E9", bg: "#F0F9FF", icon: Send },
  EN_VALIDATION_ETAPE:        { label: "En cours de validation", color: "#D97706", bg: "#FFFBEB", icon: Hourglass },
  EN_INSTRUCTION_ANALYSTE_RH: { label: "En instruction RH",     color: "#7C3AED", bg: "#F5F3FF", icon: Search },
  EN_VALIDATION_DRH:          { label: "En validation DRH",     color: "#B8932A", bg: "#FDFBF0", icon: Landmark },
  VALIDEE:                    { label: "Validée",                color: "#059669", bg: "#ECFDF5", icon: CheckCircle2 },
  REJETEE:                    { label: "Rejetée",                color: "#DC2626", bg: "#FEF2F2", icon: XCircle },
  REJETEE_PAR_LE_SYSTEME:     { label: "Rejetée système",        color: "#DC2626", bg: "#FEF2F2", icon: Ban },
  DELEGUEE:                   { label: "Déléguée",               color: "#6366F1", bg: "#EEF2FF", icon: ArrowRightLeft },
  CLOTUREE:                   { label: "Clôturée",               color: "#4B5563", bg: "#F9FAFB", icon: Lock },
  ANNULEE:                    { label: "Annulée",                color: "#6B7280", bg: "#F3F4F6", icon: Ban },
};

const TYPE_LABELS: Record<string, string> = {
  CONGE_ANNUEL:    "Congé annuel",
  CONGE_MALADIE:   "Congé maladie",
  PERMISSION:      "Permission",
  MISSION_LONGUE:  "Mission longue durée",
  CONGE_MATERNITE: "Congé maternité",
};

const KENTE = "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

/* ── Page ── */
export default async function AbsenceDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ success?: string }>;
}) {
  const { id } = await params;
  const sp     = await searchParams;
  const absence = await getAbsence(id);
  const session = await auth();
  const currentUserId = session?.user?.id;
  const roles: string[] = (session as { roles?: string[] })?.roles ?? [];
  const isDemandeur  = currentUserId === absence.demandeurIdentifiantExterne;
  const estDRH       = roles.includes("DRH");
  const estAnalyste  = roles.includes("ANALYSTE_RH");

  const statut  = STATUT_CONFIG[absence.statut] ?? { label: absence.statut, color: "#6B7280", bg: "#F3F4F6", icon: HelpCircle };
  const peutModifier     = ["BROUILLON", "REJETEE"].includes(absence.statut);
  const peutSupprimer    = ["BROUILLON", "REJETEE", "REJETEE_PAR_LE_SYSTEME"].includes(absence.statut);
  const peutValiderEtape = !!absence.estMonTourDeValider;
  const peutValiderDRH   = absence.statut === "EN_VALIDATION_DRH" && estDRH && !isDemandeur;
  const peutInstruire    = absence.statut === "EN_INSTRUCTION_ANALYSTE_RH" && estAnalyste && !isDemandeur;
  const peutRetourAnticipe = absence.statut === "VALIDEE";

  return (
    <div className="max-w-2xl mx-auto flex flex-col gap-5 py-2">

      {/* ── Bannière succès ── */}
      {sp.success === "1" && (
        <div className="flex items-center gap-3 rounded-xl border border-green-200 bg-green-50 px-5 py-4">
          <PartyPopper size={24} className="text-green-600 flex-shrink-0" />
          <div>
            <p className="text-sm font-semibold text-green-700">Demande soumise avec succès !</p>
            <p className="text-xs text-green-600 mt-0.5">Votre demande est maintenant en cours de validation.</p>
          </div>
        </div>
      )}

      {/* ── Header carte ── */}
      <div className="relative overflow-hidden rounded-xl shadow-card">
        <Image src="/Image_africaine6_resize.png" alt="" aria-hidden fill sizes="100vw"
          className="object-cover object-center opacity-20" />
        <div className="absolute inset-0" style={{ background: "linear-gradient(135deg,rgba(26,26,46,0.85),rgba(44,44,44,0.70))" }} />
        <div className="relative z-10 px-6 py-5 flex items-start justify-between gap-4">
          <div className="flex flex-col gap-1">
            <p className="text-xxs text-gold-300 tracking-[0.18em] uppercase font-ui">
              Demande #{absence.id.slice(0, 8)}…
            </p>
            <h1 className="font-heading text-2xl font-bold text-white">
              {TYPE_LABELS[absence.type] ?? absence.type}
            </h1>
          </div>
          {/* Badge statut */}
          <span
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold flex-shrink-0"
            style={{ background: statut.bg, color: statut.color }}
          >
            <statut.icon size={14} /> {statut.label}
          </span>
        </div>
        <div className="absolute bottom-0 left-0 right-0 h-0.5" style={{ background: KENTE }} />
      </div>

      {/* ── Informations ── */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Informations de la demande</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-0">
          <Row label="Employé"        value={absence.nomCompletDemandeur ?? absence.demandeurIdentifiantExterne} />
          <Row label="Type"           value={TYPE_LABELS[absence.type] ?? absence.type} />
          <Row label="Date de début"  value={absence.dateDebut} />
          <Row label="Date de fin"    value={absence.dateFin ?? "—"} />
          <Row label="Nombre de jours" value={absence.nombreJours != null ? `${absence.nombreJours} jour(s)` : "—"} />
          <Row label="Statut"         value={absence.statut === "EN_VALIDATION_ETAPE" && absence.etapeCouranteLibelle ? `${statut.label} (${absence.etapeCouranteLibelle})` : statut.label} last />
        </CardContent>
      </Card>

      {/* ── Progression du circuit ── */}
      {absence.progression && absence.progression.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Circuit de validation</CardTitle>
          </CardHeader>
          <CardContent>
            <CircuitProgression etapes={absence.progression} />
          </CardContent>
        </Card>
      )}

      {/* ── Motif rejet système ── */}
      {absence.motifRejetSysteme && (
        <div className="flex items-start gap-3 rounded-lg border border-secondary-200 bg-secondary-50 px-4 py-3">
          <AlertTriangle size={18} className="text-secondary-600 flex-shrink-0 mt-0.5" />
          <div>
            <p className="text-sm font-semibold text-secondary-700">Motif du rejet</p>
            <p className="text-sm text-secondary-600 mt-0.5">{absence.motifRejetSysteme}</p>
          </div>
        </div>
      )}

      {/* ── Justificatifs déposés ── */}
      {absence.justificatifs && absence.justificatifs.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2"><Paperclip size={18} /> Justificatifs déposés</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            {absence.justificatifs.map((j) => (
              <a
                key={j.id}
                href={formatMinioUrl(j.urlFichier)}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center justify-between rounded-lg border border-neutral-200 bg-neutral-50 px-4 py-3 hover:bg-neutral-100 transition-colors"
              >
                <div className="flex items-center gap-3">
                  <FileText size={20} className="text-neutral-500 flex-shrink-0" />
                  <div>
                    <p className="text-sm font-medium text-primary-500">{j.typePiece}</p>
                    <p className="text-xs text-neutral-400">
                      Déposé le {new Date(j.deposeLe).toLocaleDateString("fr-FR")}
                    </p>
                  </div>
                </div>
                <span className="inline-flex items-center gap-1 text-xs text-gold-600 font-medium">Télécharger <ArrowRight size={12} /></span>
              </a>
            ))}
          </CardContent>
        </Card>
      )}

      {/* ── Prolongation maternité ── */}
      {absence.type === "CONGE_MATERNITE" && absence.statut === "VALIDEE" && (
        <Card>
          <CardContent className="pt-5">
            <BoutonProlongation demandeId={absence.id} />
          </CardContent>
        </Card>
      )}

      {/* ── Document de Mise en Congé (Titre de congé) ── */}
      {absence.documentMiseEnCongeUrl && (
        <Card>
          <CardContent className="pt-5 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <FileCheck size={24} className="text-gold-600 flex-shrink-0" />
              <div>
                <p className="text-sm font-bold text-primary-600">Titre de congé / Lettre de mise en congé</p>
                <p className="text-xs text-neutral-500">Généré suite à la validation DRH</p>
              </div>
            </div>
            <Button asChild style={{ background: "#B8932A", color: "white" }}>
              <a href={formatMinioUrl(absence.documentMiseEnCongeUrl)} target="_blank" rel="noopener noreferrer">
                Télécharger le PDF
              </a>
            </Button>
          </CardContent>
        </Card>
      )}

      {/* ── Actions ── */}
      <div className="flex flex-wrap gap-3">
        {peutValiderDRH && (
          <Button asChild className="gap-2">
            <Link href={`/${absence.id}/validation-drh`}><CheckCircle2 size={14} /> Valider (DRH)</Link>
          </Button>
        )}
        {peutValiderEtape && (
          <Button asChild variant="outline" className="gap-2">
            <Link href={`/${absence.id}/validation`}><Search size={14} /> Étape de validation</Link>
          </Button>
        )}
        {peutInstruire && (
          <BoutonInstruction id={absence.id} />
        )}
        {peutRetourAnticipe && (
          <Button asChild variant="outline" className="gap-2">
            <Link href={`/${absence.id}/retour-anticipe`}><RotateCcw size={14} /> Retour anticipé</Link>
          </Button>
        )}
        {(peutModifier || peutSupprimer) && (
          <BoutonsSupprimerModifier
            id={absence.id}
            peutModifier={peutModifier}
            peutSupprimer={peutSupprimer}
          />
        )}
        {absence.statut === "BROUILLON" && (
          <Button asChild className="gap-2" style={{ background: "#C41E22" }}>
            <Link href={`/${absence.id}/preview`}><Send size={14} /> Soumettre</Link>
          </Button>
        )}
        <Button asChild variant="ghost" className="text-neutral-500 text-sm">
          <Link href="/"><ArrowLeft size={14} /> Retour à l&apos;accueil</Link>
        </Button>
      </div>

    </div>
  );
}

const ETAPE_STATUT_CONFIG: Record<string, { color: string; bg: string; icon: LucideIcon; ring: string }> = {
  EN_ATTENTE: { color: "#9CA3AF", bg: "#F9FAFB", icon: Circle,    ring: "#D1D5DB" },
  EN_COURS:   { color: "#D97706", bg: "#FFFBEB", icon: Hourglass, ring: "#D97706" },
  APPROUVEE:  { color: "#059669", bg: "#ECFDF5", icon: Check,     ring: "#059669" },
  REJETEE:    { color: "#DC2626", bg: "#FEF2F2", icon: X,         ring: "#DC2626" },
};

function CircuitProgression({ etapes }: { etapes: EtapeProgression[] }) {
  const total = etapes.length;
  const approuvees = etapes.filter(e => e.statut === "APPROUVEE").length;
  const rejetee    = etapes.find(e => e.statut === "REJETEE");
  const pct = total > 0 ? Math.round((approuvees / total) * 100) : 0;

  return (
    <div className="flex flex-col gap-4">
      {/* Barre globale */}
      <div className="flex items-center gap-3">
        <div className="flex-1 h-2 rounded-full bg-neutral-200 overflow-hidden">
          <div
            className="h-full rounded-full transition-all duration-500"
            style={{
              width: `${pct}%`,
              background: rejetee ? "#DC2626" : "#059669",
            }}
          />
        </div>
        <span className="text-xs font-semibold text-neutral-500 w-12 text-right">
          {approuvees}/{total}
        </span>
      </div>

      {/* Étapes détaillées */}
      <div className="relative flex flex-col gap-0">
        {etapes.map((etape, idx) => {
          const cfg = ETAPE_STATUT_CONFIG[etape.statut] ?? ETAPE_STATUT_CONFIG.EN_ATTENTE;
          const isLast = idx === total - 1;
          return (
            <div key={etape.position} className="flex items-start gap-3">
              {/* Indicateur vertical */}
              <div className="flex flex-col items-center">
                <div
                  className="flex h-7 w-7 items-center justify-center rounded-full text-sm font-bold border-2 flex-shrink-0 z-10"
                  style={{
                    background: cfg.bg,
                    color: cfg.color,
                    borderColor: cfg.ring,
                  }}
                >
                  <cfg.icon size={13} strokeWidth={3} />
                </div>
                {!isLast && (
                  <div
                    className="w-0.5 flex-1 min-h-[20px]"
                    style={{ background: etape.statut === "APPROUVEE" ? "#059669" : "#E5E7EB" }}
                  />
                )}
              </div>
              {/* Contenu */}
              <div className={`pb-4 ${isLast ? "pb-0" : ""}`}>
                <p className="text-sm font-medium" style={{ color: cfg.color }}>
                  {etape.libelle}
                </p>
                <p className="text-xs text-neutral-400">
                  {etape.statut === "EN_ATTENTE"  ? "En attente"  :
                   etape.statut === "EN_COURS"    ? "En cours"    :
                   etape.statut === "APPROUVEE"   ? "Approuvée"   :
                   etape.statut === "REJETEE"     ? "Rejetée"     : etape.statut}
                </p>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function Row({ label, value, last }: { label: string; value: string; last?: boolean }) {
  return (
    <div className={`flex justify-between py-3 ${!last ? "border-b border-neutral-100" : ""}`}>
      <span className="text-sm font-medium text-neutral-500">{label}</span>
      <span className="text-sm text-primary-500 font-medium">{value}</span>
    </div>
  );
}
