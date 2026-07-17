"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { Button } from "@/components/ui/button";
import apiClient from "@/lib/api/client";
import {
  AlertTriangle, Baby, ClipboardList, HelpCircle,
  Plane, Search, Stethoscope, TreePalm, XCircle, FilePen, CheckCircle2, Ban, Lock, Hourglass, type LucideIcon, Inbox
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
  etapeCouranteLibelle?: string;
}

const TYPES_AVEC_JUSTIFICATIF = new Set([
  "CONGE_MALADIE", "PERMISSION", "MISSION_LONGUE", "CONGE_MATERNITE",
]);

const TYPE_LABELS: Record<string, { label: string; icon: LucideIcon; color: string }> = {
  CONGE_ANNUEL:    { label: "Congé annuel",         icon: TreePalm,      color: "#C41E22" },
  CONGE_MALADIE:   { label: "Congé maladie",        icon: Stethoscope,   color: "#1A1A2E" },
  PERMISSION:      { label: "Permission",           icon: ClipboardList, color: "#B8932A" },
  MISSION_LONGUE:  { label: "Mission longue durée", icon: Plane,         color: "#2C2C2C" },
  CONGE_MATERNITE: { label: "Congé maternité",      icon: Baby,          color: "#96751A" },
};

const STATUT_CONFIG: Record<string, { label: string; color: string; bg: string; icon: LucideIcon }> = {
  BROUILLON:                      { label: "Brouillon",             color: "#6B7280", bg: "#F3F4F6", icon: FilePen },
  EN_VALIDATION_ETAPE:            { label: "En validation",         color: "#D97706", bg: "#FFFBEB", icon: Hourglass },
  VALIDEE:                        { label: "Validée",               color: "#059669", bg: "#ECFDF5", icon: CheckCircle2 },
  REJETEE:                        { label: "Rejetée",               color: "#DC2626", bg: "#FEF2F2", icon: XCircle },
  REJETEE_PAR_LE_SYSTEME:         { label: "Rejetée (système)",     color: "#DC2626", bg: "#FEF2F2", icon: Ban },
  CLOTUREE:                       { label: "Clôturée",              color: "#4B5563", bg: "#F9FAFB", icon: Lock },
  INSTRUCTION:                    { label: "En instruction",        color: "#7C3AED", bg: "#F5F3FF", icon: Search },
  EN_INSTRUCTION_ANALYSTE_RH:     { label: "Attente RH",            color: "#7C3AED", bg: "#F5F3FF", icon: Search },
};

const KENTE = "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

export default function AnalysteRHPage() {
  const [demandes, setDemandes]   = useState<Absence[]>([]);
  const [loading, setLoading]     = useState(true);
  const [loadingId, setLoadingId] = useState<string | null>(null);
  const [erreur, setErreur]       = useState<string | null>(null);

  useEffect(() => {
    // Récupérer TOUTES les demandes au lieu de juste celles en instruction
    apiClient
      .get("/api/v5/demandes")
      .then(r => setDemandes(r.data))
      .finally(() => setLoading(false));
  }, []);

  async function instruire(id: string) {
    setLoadingId(id);
    setErreur(null);
    try {
      await apiClient.post(`/api/v5/demandes/${id}/instruction`);
      // Mettre à jour le statut localement après instruction
      setDemandes(prev => prev.map(d => d.id === id ? { ...d, statut: "EN_VALIDATION_ETAPE" } : d));
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { code?: string } } })?.response?.data?.code;
      if (code === "JUSTIFICATIF_REQUIS")
        setErreur(`Demande ${id.slice(0, 8)}… : justificatif manquant avant transmission.`);
      else
        setErreur("Une erreur est survenue lors de la transmission.");
    } finally {
      setLoadingId(null);
    }
  }

  const enAttenteCount = demandes.filter(d => d.statut === "EN_INSTRUCTION_ANALYSTE_RH").length;

  return (
    <div className="flex flex-col gap-6 max-w-6xl mx-auto py-4 w-full">

      {/* Hero */}
      <div className="relative overflow-hidden rounded-xl" style={{ minHeight: "130px" }}>
        <Image src="/Image_Afrique3_resize.png" alt="" fill sizes="100vw" className="object-cover object-center" />
        <div className="absolute inset-0" style={{ background: "linear-gradient(110deg,rgba(26,26,46,0.92) 0%,rgba(124,58,237,0.35) 100%)" }} />
        <div className="relative z-10 px-8 py-7 h-full flex flex-col gap-1 justify-center">
          <span className="text-xxs text-gold-300 tracking-[0.2em] uppercase font-ui">Espace Analyste RH — AFB</span>
          <h1 className="font-heading text-3xl font-bold text-white">Suivi des demandes</h1>
          <p className="text-sm text-neutral-300 mt-1">Supervision de l&apos;ensemble des demandes d&apos;absences du personnel</p>
        </div>
        <div className="absolute bottom-0 left-0 right-0 h-1" style={{ background: KENTE }} />
      </div>

      {/* Compteur (Aperçu RH) */}
      <div className="flex items-center gap-4 rounded-xl border border-purple-200 bg-purple-50 px-5 py-4">
        <Search size={28} className="text-purple-600 flex-shrink-0" />
        <div>
          <p className="text-sm font-semibold text-purple-700">
            {loading ? "Chargement…" : `${demandes.length} demande(s) au total, dont ${enAttenteCount} en attente d'instruction.`}
          </p>
          <p className="text-xs text-purple-600 mt-0.5">Vous pouvez instruire directement les demandes nécessitant votre action.</p>
        </div>
      </div>

      {/* Erreur globale */}
      {erreur && (
        <div className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3">
          <AlertTriangle size={18} className="text-red-600 flex-shrink-0 mt-0.5" />
          <p className="text-sm text-red-700">{erreur}</p>
        </div>
      )}

      {/* Liste (Tableau) */}
      {loading ? (
        <div className="flex justify-center py-12">
          <p className="text-sm text-neutral-400">Chargement des données...</p>
        </div>
      ) : demandes.length === 0 ? (
        <div className="flex flex-col items-center gap-4 py-20 rounded-xl border-2 border-dashed border-neutral-200 bg-white/50">
          <Inbox size={48} className="text-neutral-300" />
          <div className="text-center">
            <p className="font-heading text-lg font-semibold text-primary-500">Aucune demande</p>
            <p className="text-sm text-neutral-400 mt-1">Aucune demande n&apos;est actuellement enregistrée dans le système.</p>
          </div>
        </div>
      ) : (
        <div className="bg-white/90 backdrop-blur-sm rounded-xl border border-neutral-200 shadow-card overflow-hidden">
          <div className="overflow-x-auto w-full">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-neutral-50/80 border-b border-neutral-200 text-xs font-semibold text-neutral-500 uppercase tracking-wider font-ui">
                  <th className="py-4 px-6 font-medium">Demandeur</th>
                  <th className="py-4 px-6 font-medium">Type d'absence</th>
                  <th className="py-4 px-6 font-medium">Période</th>
                  <th className="py-4 px-6 font-medium">Justificatif</th>
                  <th className="py-4 px-6 font-medium">Statut</th>
                  <th className="py-4 px-6 text-right font-medium">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-neutral-100">
                {demandes.map((d) => {
                  const type = TYPE_LABELS[d.type] ?? { label: d.type, icon: HelpCircle, color: "#6B7280" };
                  const statutObj = STATUT_CONFIG[d.statut] ?? { label: d.statut, color: "#6B7280", bg: "#F3F4F6", icon: HelpCircle };
                  const statutText = d.statut === "EN_VALIDATION_ETAPE" && d.etapeCouranteLibelle ? `${statutObj.label} (${d.etapeCouranteLibelle})` : statutObj.label;
                  
                  const justificatifRequis = TYPES_AVEC_JUSTIFICATIF.has(d.type);
                  const aJustificatif = (d.justificatifs?.length ?? 0) > 0;
                  const enInstruction = d.statut === "EN_INSTRUCTION_ANALYSTE_RH";

                  return (
                    <tr key={d.id} className="group hover:bg-neutral-50/80 transition-colors">
                      {/* Demandeur */}
                      <td className="py-3 px-6 whitespace-nowrap">
                        <span className="text-sm font-semibold text-neutral-700">{d.demandeurIdentifiantExterne}</span>
                      </td>

                      {/* Type */}
                      <td className="py-3 px-6 whitespace-nowrap">
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0" style={{ background: type.color + "15", color: type.color }}>
                            <type.icon size={16} />
                          </div>
                          <span className="font-semibold text-primary-500 text-sm">{type.label}</span>
                        </div>
                      </td>

                      {/* Période */}
                      <td className="py-3 px-6 text-sm text-neutral-600 whitespace-nowrap">
                        {d.dateDebut} <span className="text-neutral-400 mx-1">→</span> {d.dateFin ?? "—"}
                        <div className="text-xs text-neutral-400 mt-0.5">{d.nombreJours ?? "?"} jour(s)</div>
                      </td>

                      {/* Justificatif */}
                      <td className="py-3 px-6 whitespace-nowrap">
                        <span className={`inline-flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded-full border ${
                          aJustificatif
                            ? "bg-green-50 text-green-700 border-green-200"
                            : justificatifRequis
                            ? "bg-red-50 text-red-700 border-red-200"
                            : "bg-neutral-50 text-neutral-500 border-neutral-200"
                        }`}>
                          {aJustificatif ? "Fourni" : justificatifRequis ? "Manquant" : "Non requis"}
                        </span>
                      </td>

                      {/* Statut */}
                      <td className="py-3 px-6 whitespace-nowrap">
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold" style={{ background: statutObj.bg, color: statutObj.color }}>
                          <statutObj.icon size={12} /> {statutText}
                        </span>
                      </td>

                      {/* Action */}
                      <td className="py-3 px-6 text-right whitespace-nowrap">
                        <div className="flex items-center justify-end gap-2">
                          {enInstruction && (
                            <Button 
                              size="sm" 
                              onClick={() => instruire(d.id)}
                              disabled={loadingId === d.id || (justificatifRequis && !aJustificatif)}
                              className="h-8 text-xs bg-purple-600 hover:bg-purple-700 text-white"
                            >
                              {loadingId === d.id ? "..." : "Transmettre"}
                            </Button>
                          )}
                          <Link href={`/demande/${d.id}`} className="inline-flex items-center justify-center w-8 h-8 rounded-md text-neutral-500 hover:text-gold-700 hover:bg-gold-50 transition-colors border border-neutral-200 hover:border-gold-400">
                            <FilePen size={14} />
                          </Link>
                        </div>
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

