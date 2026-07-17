import { auth } from "@/auth";
import { notFound } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { serverApiClient } from "@/lib/api/server-client";
import { BoutonProlongation } from "./BoutonProlongation";
import BoutonsSupprimerModifier from "./BoutonsSupprimerModifier";
import BoutonInstruction from "./BoutonInstruction";
import { BoutonsDocumentTitreConge } from "./BoutonsDocumentTitreConge";
import type { TitreCongeData } from "@/lib/pdf/titreCongeDocument";
import {
  AlertTriangle, ArrowLeft, ArrowRight, ArrowRightLeft, Ban, Check,
  CheckCircle2, Circle, FileCheck, FilePen, FileText, HelpCircle, Hourglass,
  Landmark, Lock, Paperclip, PartyPopper, RotateCcw, Search, Send, X, XCircle,
  type LucideIcon,
} from "lucide-react";

/** Un identifiant de demande est toujours un UUID. Les segments statiques frères
 *  (`mon-espace`, `backup`, …) ne doivent jamais atteindre l'API (→ 400 UUID invalide). */
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

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
  documentsMiseEnConge?: {
    numero: string;
    urlDocument: string;
    genereLe: string;
  }[];
  documentMiseEnCongeUrl?: string;
  objetMission?: string;
  motifMission?: string;
  destination?: string;
  categorie?: string;
  employeePosition?: string;
  employeeDepartment?: string;
  directManagerName?: string;
  hrSignatoryName?: string;
  analysteRhName?: string;
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
  isModal = false,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ success?: string }>;
  isModal?: boolean;
}) {
  const { id } = await params;
  if (!UUID_RE.test(id)) notFound();
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

  /* Champs d'information — partagés entre la grille (page pleine) et le tableau (modal) */
  const infoCells: { label: string; value: string }[] = [
    { label: "Employé",       value: absence.nomCompletDemandeur ?? absence.demandeurIdentifiantExterne },
    { label: "Type",          value: TYPE_LABELS[absence.type] ?? absence.type },
    { label: "Date de début", value: absence.dateDebut },
    { label: "Date de fin",   value: absence.dateFin ?? "—" },
    { label: "Durée",         value: absence.nombreJours != null ? `${absence.nombreJours} jour(s)` : "—" },
    ...(absence.destination ? [{ label: "Destination", value: absence.destination }] : []),
    ...(absence.categorie   ? [{ label: "Catégorie",   value: absence.categorie }]   : []),
  ];

  /* ── Données du Titre de congé — version frontend pleine fidélité ──
     Le navigateur rend nativement dégradés / opacity / en-tête fixe, contrairement
     au PDF backend (openhtmltopdf). */
  const fmtFr = (iso?: string | null) =>
    iso ? new Date(iso).toLocaleDateString("fr-FR") : "—";
  const dateReprise = (() => {
    if (!absence.dateFin) return "—";
    const d = new Date(absence.dateFin);
    d.setDate(d.getDate() + 1);
    return d.toLocaleDateString("fr-FR");
  })();
  const nowFr = new Date();
  const titreCongeData: TitreCongeData = {
    issuing_department: "Direction des Ressources Humaines",
    absence_type: TYPE_LABELS[absence.type] ?? absence.type,
    employee_full_name: absence.nomCompletDemandeur ?? absence.demandeurIdentifiantExterne,
    employee_matricule: absence.demandeurIdentifiantExterne,
    employee_position: absence.employeePosition,
    employee_department: absence.employeeDepartment,
    direct_manager_name: absence.directManagerName,
    date_debut: fmtFr(absence.dateDebut),
    date_fin: fmtFr(absence.dateFin),
    nombre_jours: absence.nombreJours ?? "—",
    date_reprise: dateReprise,
    document_location: "Abidjan",
    document_date: nowFr.toLocaleDateString("fr-FR"),
    hr_signatory_title: "Le Directeur des Ressources Humaines",
    hr_signatory_name: absence.hrSignatoryName ?? "La Direction des Ressources Humaines",
    analyste_rh_name: absence.analysteRhName ?? "Service RH",
    generation_timestamp: nowFr.toLocaleString("fr-FR"),
  };

  /* Fond des cards : blanc franc + ombre en modal (pour se détacher du filigrane africain),
     effet verre dépoli sur la page pleine (posée sur l'image de fond du layout). */
  const cardClass = isModal
    ? "rounded-lg shadow-md border border-neutral-200/70 bg-white"
    : "rounded-3xl shadow-sm border-neutral-100/60 bg-white/60 backdrop-blur-xl";

  return (
    <div className={`mx-auto flex flex-col gap-6 py-2 px-2 md:px-4 ${isModal ? 'max-w-full' : 'max-w-5xl'}`}>

      {/* ── Bannière succès ── */}
      {sp.success === "1" && (
        <div className="flex items-center gap-3 rounded-2xl border border-green-200 bg-green-50/80 backdrop-blur-md px-6 py-4 shadow-sm animate-in slide-in-from-top-4">
          <PartyPopper size={24} className="text-green-600 flex-shrink-0" />
          <div>
            <p className="text-sm font-bold text-green-800">Demande soumise avec succès !</p>
            <p className="text-xs text-green-700 mt-0.5">Votre demande est maintenant dans le circuit de validation.</p>
          </div>
        </div>
      )}

      {/* ── Header carte Premium (Masqué en mode Modal) ── */}
      {!isModal && (
        <div className="relative overflow-hidden rounded-3xl shadow-lg border border-neutral-100">
          <Image src="/Image_africaine6_resize.png" alt="" aria-hidden fill sizes="100vw"
            className="object-cover object-center opacity-30 mix-blend-overlay" />
          <div className="absolute inset-0" style={{ background: "linear-gradient(135deg, rgba(26,26,46,0.95), rgba(44,44,44,0.85))" }} />
          <div className="relative z-10 px-8 py-8 flex flex-col sm:flex-row sm:items-center justify-between gap-6">
            <div className="flex flex-col gap-2">
              <p className="text-xs text-gold-400 tracking-[0.2em] uppercase font-ui font-semibold">
                Demande #{absence.id.slice(0, 8)}
              </p>
              <h1 className="font-heading text-3xl md:text-4xl font-extrabold text-white tracking-tight">
                {TYPE_LABELS[absence.type] ?? absence.type}
              </h1>
            </div>
            {/* Badge statut */}
            <span
              className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-bold shadow-sm backdrop-blur-md border border-white/10"
              style={{ background: statut.bg, color: statut.color }}
            >
              <statut.icon size={16} /> {statut.label}
            </span>
          </div>
          <div className="absolute bottom-0 left-0 right-0 h-1" style={{ background: KENTE }} />
        </div>
      )}

      {/* ── En-tête compact pour le mode Modal ── */}
      {isModal && (
        <div className="flex items-start justify-between pb-2 border-b border-neutral-100/50 mb-2 mt-2">
          <div className="flex flex-col gap-1">
            <h1 className="font-heading text-2xl font-bold text-primary-900 tracking-tight">
              {TYPE_LABELS[absence.type] ?? absence.type}
            </h1>
            <p className="text-xs text-neutral-400 font-medium">Demande #{absence.id.slice(0, 8)}</p>
          </div>
          <span
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold shadow-sm"
            style={{ background: statut.bg, color: statut.color }}
          >
            <statut.icon size={14} /> {statut.label}
          </span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Colonne Principale (Infos + Fichiers) */}
        <div className="lg:col-span-2 flex flex-col gap-6">
          {/* ── Informations ── */}
          <Card className={cardClass}>
            <CardHeader className="pb-4">
              <CardTitle className="text-lg text-primary-900 font-heading">Informations de la demande</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-1">
              {isModal ? (
                /* ── Modal : tableau horizontal sur une ligne (scroll si trop de champs) ── */
                <div className="overflow-x-auto styled-scrollbar -mx-2">
                  <table className="w-full border-collapse text-left">
                    <thead>
                      <tr className="border-b border-neutral-100">
                        {infoCells.map((c) => (
                          <th key={c.label} className="px-3 py-2 text-[10px] font-semibold text-neutral-400 uppercase tracking-wider whitespace-nowrap">
                            {c.label}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        {infoCells.map((c) => (
                          <td key={c.label} className="px-3 py-2 text-sm text-primary-800 font-bold whitespace-nowrap">
                            {c.value}
                          </td>
                        ))}
                      </tr>
                    </tbody>
                  </table>
                </div>
              ) : (
                /* ── Page pleine : grille verticale 2 colonnes ── */
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-1">
                  {infoCells.map((c) => (
                    <Row key={c.label} label={c.label} value={c.value} />
                  ))}
                </div>
              )}

              {absence.objetMission && (
                <div className="mt-4 pt-4 border-t border-neutral-100">
                  <span className="text-xs font-semibold text-neutral-400 uppercase tracking-wider block mb-1">Objet de la mission</span>
                  <span className="text-sm text-primary-700 font-medium">{absence.objetMission}</span>
                </div>
              )}
              {absence.motifMission && (
                <div className="mt-4 pt-4 border-t border-neutral-100">
                  <span className="text-xs font-semibold text-neutral-400 uppercase tracking-wider block mb-1">Justification / Motif</span>
                  <span className="text-sm text-primary-700 font-medium leading-relaxed">{absence.motifMission}</span>
                </div>
              )}
            </CardContent>
          </Card>

          {/* ── Progression du circuit (Horizontale Premium) ── */}
          {absence.progression && absence.progression.length > 0 && (
            <Card className={`${cardClass} overflow-hidden`}>
              <CardHeader className="bg-neutral-50/50 border-b border-neutral-100/50 pb-4">
                <CardTitle className="text-lg text-primary-900 font-heading">Circuit de validation</CardTitle>
              </CardHeader>
              <CardContent className="pt-8 pb-10 overflow-x-auto styled-scrollbar">
                <CircuitProgression etapes={absence.progression} />
              </CardContent>
            </Card>
          )}
        </div>

        {/* Colonne Latérale (Actions + Documents) */}
        <div className="flex flex-col gap-6">
          {/* ── Actions ── */}
          <Card className={cardClass}>
            <CardHeader className="pb-4">
              <CardTitle className="text-lg text-primary-900 font-heading">Actions disponibles</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              {peutValiderDRH && (
                <Button asChild size="lg" className="w-full justify-start gap-3 bg-secondary-600 hover:bg-secondary-700 text-white rounded-xl shadow-md">
                  {lienAction(isModal, `/demande/${absence.id}/validation-drh`, <><CheckCircle2 size={18} /> Valider (DRH)</>)}
                </Button>
              )}
              {peutValiderEtape && (
                <Button asChild size="lg" className="w-full justify-start gap-3 bg-primary-600 hover:bg-primary-700 text-white rounded-xl shadow-md">
                  {lienAction(isModal, `/demande/${absence.id}/validation`, <><Search size={18} /> Analyser et Valider</>)}
                </Button>
              )}
              {peutInstruire && (
                <BoutonInstruction id={absence.id} type={absence.type} />
              )}
              {peutRetourAnticipe && (
                <Button asChild variant="outline" size="lg" className="w-full justify-start gap-3 rounded-xl border-neutral-200 hover:bg-neutral-50">
                  {lienAction(isModal, `/demande/${absence.id}/retour-anticipe`, <><RotateCcw size={18} /> Déclarer un retour anticipé</>)}
                </Button>
              )}
              {(peutModifier || peutSupprimer) && (
                <div className="pt-2">
                  <BoutonsSupprimerModifier id={absence.id} peutModifier={peutModifier} peutSupprimer={peutSupprimer} />
                </div>
              )}
              {absence.statut === "BROUILLON" && (
                <Button asChild size="lg" className="w-full justify-start gap-3 bg-red-600 hover:bg-red-700 text-white rounded-xl shadow-md mt-2">
                  {lienAction(isModal, `/demande/${absence.id}/preview`, <><Send size={18} /> Soumettre la demande</>)}
                </Button>
              )}
            </CardContent>
          </Card>

          {/* ── Motif rejet système ── */}
          {absence.motifRejetSysteme && (
            <div className="flex items-start gap-3 rounded-3xl border border-red-200 bg-red-50/80 p-5 shadow-sm">
              <AlertTriangle size={20} className="text-red-600 flex-shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-bold text-red-800">Motif du rejet</p>
                <p className="text-sm text-red-700 mt-1 leading-relaxed">{absence.motifRejetSysteme}</p>
              </div>
            </div>
          )}

          {/* ── Justificatifs déposés ── */}
          {absence.justificatifs && absence.justificatifs.length > 0 && (
            <Card className={cardClass}>
              <CardHeader className="pb-4">
                <CardTitle className="text-lg text-primary-900 font-heading flex items-center gap-2">
                  <Paperclip size={18} className="text-gold-500" /> Pièces jointes
                </CardTitle>
              </CardHeader>
              <CardContent className="flex flex-col gap-3">
                {absence.justificatifs.map((j) => (
                  <a
                    key={j.id}
                    href={formatMinioUrl(j.urlFichier)}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="group flex flex-col gap-2 rounded-2xl border border-neutral-200/60 bg-white p-4 hover:border-gold-300 hover:shadow-md transition-all duration-300"
                  >
                    <div className="flex items-start gap-3">
                      <div className="w-10 h-10 rounded-xl bg-gold-50 flex items-center justify-center text-gold-600 group-hover:scale-110 transition-transform">
                        <FileText size={20} />
                      </div>
                      <div className="flex-1">
                        <p className="text-sm font-bold text-primary-800 line-clamp-1">{j.typePiece}</p>
                        <p className="text-xs text-neutral-400 mt-0.5">
                          Déposé le {new Date(j.deposeLe).toLocaleDateString("fr-FR")}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center justify-end w-full pt-2 border-t border-neutral-50 mt-1">
                      <span className="text-xs font-semibold text-gold-600 group-hover:text-gold-700 flex items-center gap-1">Ouvrir <ArrowRight size={12} /></span>
                    </div>
                  </a>
                ))}
              </CardContent>
            </Card>
          )}

          {/* ── Document de Mise en Congé (Titre de congé) ── */}
          {absence.documentsMiseEnConge && absence.documentsMiseEnConge.length > 0 ? (
            <div className="flex flex-col gap-4">
              {absence.documentsMiseEnConge.map((doc, idx) => (
                <Card key={doc.numero} className="rounded-3xl shadow-md border-gold-200 bg-gradient-to-br from-gold-50 to-white">
                  <CardContent className="p-6 flex flex-col gap-4">
                    <div className="flex items-start gap-4">
                      <div className="w-12 h-12 rounded-2xl bg-gold-100 flex items-center justify-center text-gold-600 shadow-inner">
                        <FileCheck size={24} />
                      </div>
                      <div>
                        <p className="text-base font-bold text-gold-900">
                          {idx === 0 ? "Titre de congé (Pré-validé)" : "Titre de congé (Validé DRH)"}
                        </p>
                        <p className="text-xs text-gold-700 mt-1">Généré le {new Date(doc.genereLe).toLocaleDateString('fr-FR')} à {new Date(doc.genereLe).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}</p>
                      </div>
                    </div>
                    <BoutonsDocumentTitreConge
                      minioUrl={formatMinioUrl(doc.urlDocument)}
                      data={titreCongeData}
                    />
                    <p className="text-[11px] text-gold-700/80 leading-snug">
                      <strong>Serveur</strong> : PDF officiel archivé lors de l'étape.
                    </p>
                  </CardContent>
                </Card>
              ))}
            </div>
          ) : absence.documentMiseEnCongeUrl && (
            <Card className="rounded-3xl shadow-md border-gold-200 bg-gradient-to-br from-gold-50 to-white">
              <CardContent className="p-6 flex flex-col gap-4">
                <div className="flex items-start gap-4">
                  <div className="w-12 h-12 rounded-2xl bg-gold-100 flex items-center justify-center text-gold-600 shadow-inner">
                    <FileCheck size={24} />
                  </div>
                  <div>
                    <p className="text-base font-bold text-gold-900">Titre de congé</p>
                    <p className="text-xs text-gold-700 mt-1">Généré et approuvé par la DRH</p>
                  </div>
                </div>
                <BoutonsDocumentTitreConge
                  minioUrl={formatMinioUrl(absence.documentMiseEnCongeUrl)}
                  data={titreCongeData}
                />
              </CardContent>
            </Card>
          )}

          {/* ── Prolongation maternité ── */}
          {absence.type === "CONGE_MATERNITE" && absence.statut === "VALIDEE" && (
            <Card className="rounded-3xl shadow-sm border-pink-200 bg-pink-50/50">
              <CardContent className="p-6">
                <BoutonProlongation demandeId={absence.id} />
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}

const ETAPE_STATUT_CONFIG: Record<string, { color: string; bg: string; icon: LucideIcon; ring: string }> = {
  EN_ATTENTE: { color: "#9CA3AF", bg: "#F9FAFB", icon: Circle,    ring: "#E5E7EB" },
  EN_COURS:   { color: "#D97706", bg: "#FFFBEB", icon: Hourglass, ring: "#F59E0B" },
  APPROUVEE:  { color: "#059669", bg: "#ECFDF5", icon: Check,     ring: "#10B981" },
  REJETEE:    { color: "#DC2626", bg: "#FEF2F2", icon: X,         ring: "#EF4444" },
};

function CircuitProgression({ etapes }: { etapes: EtapeProgression[] }) {
  const total = etapes.length;
  if (total === 0) return null;
  const approuvees = etapes.filter(e => e.statut === "APPROUVEE").length;
  const rejetee    = etapes.find(e => e.statut === "REJETEE");
  const pct = Math.max(0, Math.round((approuvees / total) * 100));

  return (
    <div className="flex flex-col gap-4">
      {/* Barre globale */}
      <div className="flex items-center gap-3">
        <div className="flex-1 h-1.5 rounded-full bg-neutral-100 overflow-hidden">
          <div
            className="h-full rounded-full transition-all duration-500"
            style={{
              width: `${pct}%`,
              background: rejetee ? "#DC2626" : "#059669",
            }}
          />
        </div>
        <span className="text-[10px] font-semibold text-neutral-500 w-8 text-right">
          {approuvees}/{total}
        </span>
      </div>

      {/* Étapes détaillées Horizontales Simplifiées */}
      <div className="relative flex w-full justify-between pt-1 pb-2">
        {etapes.map((etape, idx) => {
          const cfg = ETAPE_STATUT_CONFIG[etape.statut] ?? ETAPE_STATUT_CONFIG.EN_ATTENTE;
          const isLast = idx === total - 1;
          
          return (
            <div key={etape.position} className="relative flex flex-col items-center flex-1 text-center">
              {/* Ligne connectrice */}
              {!isLast && (
                <div
                  className="absolute top-3 left-[50%] w-full h-[1px] -z-10"
                  style={{ background: etape.statut === "APPROUVEE" ? "#059669" : "#E5E7EB" }}
                />
              )}
              
              {/* Petit Cercle */}
              <div
                className="flex h-6 w-6 items-center justify-center rounded-full text-sm font-bold border-2 flex-shrink-0 z-10 bg-white"
                style={{
                  color: cfg.color,
                  borderColor: cfg.ring,
                  boxShadow: etape.statut === "EN_COURS" ? `0 0 0 2px ${cfg.bg}` : "none",
                }}
              >
                <cfg.icon size={11} strokeWidth={3} />
              </div>
              
              {/* Libellé et Statut */}
              <div className="mt-2 px-1">
                <p className="text-[11px] font-semibold" style={{ color: cfg.color, lineHeight: "1.2" }}>
                  {etape.libelle}
                </p>
                <p className="text-[9px] text-neutral-400 mt-0.5 uppercase tracking-wide font-ui">
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

/**
 * Bouton d'action de la colonne latérale.
 * En mode modal, on force une navigation "dure" (<a>) : elle résout le slot @modal
 * vers son default (null) → le modal se ferme et la page-formulaire s'affiche normalement.
 * Une navigation douce (<Link>) laisserait l'overlay bloquant au-dessus de la page.
 */
function lienAction(modal: boolean, href: string, children: React.ReactNode) {
  return modal ? <a href={href}>{children}</a> : <Link href={href}>{children}</Link>;
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col py-3 border-b border-neutral-100/50 last:border-0">
      <span className="text-xs font-semibold text-neutral-400 uppercase tracking-wider">{label}</span>
      <span className="text-sm text-primary-800 font-bold mt-1">{value}</span>
    </div>
  );
}
