export const dynamic = "force-dynamic";

import Link from "next/link";
import { Button } from "@/components/ui/button";
import { BoutonSupprimerCircuit } from "./BoutonSupprimerCircuit";
import { BoutonToggleActif } from "./BoutonToggleActif";
import { serverApiClient } from "@/lib/api/server-client";
import { CheckCircle2, Link2, PauseCircle, Settings } from "lucide-react";

interface RegleAffectation {
  mecanisme: string;
  roleKeycloakCible: string | null;
  profondeurHierarchique: number | null;
  gradeDeclencheur: string | null;
}

interface Etape {
  id: string;
  ordre: number;
  libelle: string;
  delaiJours: number | null;
  estVerrouillable: boolean;
  regles: RegleAffectation[];
}

interface Circuit {
  id: string;
  nom: string;
  typeAbsenceCible: string | null;
  actif: boolean;
  estModeleNomme: boolean;
  etapes: Etape[];
}

async function getCircuits(): Promise<Circuit[]> {
  try {
    const api = await serverApiClient();
    const { data } = await api.get("/api/v5/admin/circuits");
    return data;
  } catch (err: any) {
    console.error("Erreur getCircuits:", err?.response?.data || err?.message || err);
    return [];
  }
}

const TYPE_LABELS: Record<string, string> = {
  CONGE_ANNUEL:    "Congé annuel",
  CONGE_MALADIE:   "Congé maladie",
  PERMISSION:      "Permission",
  MISSION_LONGUE:  "Mission longue durée",
  CONGE_MATERNITE: "Congé maternité",
};

const MECANISME_LABELS: Record<string, { label: string; color: string }> = {
  BACKUP:                 { label: "Backup N+0",          color: "#059669" },
  HIERARCHIQUE:           { label: "Hiérarchique",        color: "#1A1A2E" },
  ROLE_FIXE_SCOPE_RESEAU: { label: "Rôle fixe (réseau)",  color: "#B8932A" },
  ROLE_FIXE_GLOBAL:       { label: "Rôle fixe (global)",  color: "#7C3AED" },
  DG_CONDITIONNEL:        { label: "DG conditionnel",     color: "#6B7280" },
};

const CIRCUIT_COLORS = ["#C41E22", "#1A1A2E", "#B8932A", "#059669", "#7C3AED", "#2C2C2C"];

const KENTE = "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

export default async function CircuitsPage() {
  const circuits = await getCircuits();
  const actifs   = circuits.filter(c => c.actif);
  const inactifs = circuits.filter(c => !c.actif);
  const totalEtapes = circuits.reduce((acc, c) => acc + (c.etapes?.length ?? 0), 0);

  return (
    <div className="flex flex-col gap-6 max-w-5xl mx-auto py-4">

      {/* Header */}
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="font-heading text-2xl font-bold text-primary-500">
            Circuits de validation
          </h1>
          <p className="text-sm text-neutral-500 mt-0.5">
            {circuits.length} circuit{circuits.length > 1 ? "s" : ""} configuré{circuits.length > 1 ? "s" : ""}
          </p>
        </div>
        <Button asChild>
          <Link href="/circuits/nouveau">+ Nouveau circuit</Link>
        </Button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-4">
        {[
          { label: "Circuits actifs",   value: actifs.length,   color: "#059669", icon: CheckCircle2 },
          { label: "Circuits inactifs", value: inactifs.length, color: "#6B7280", icon: PauseCircle },
          { label: "Étapes totales",    value: totalEtapes,     color: "#C41E22", icon: Link2 },
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

      {/* Liste circuits */}
      {circuits.length === 0 ? (
        <div className="flex flex-col items-center gap-4 py-16 rounded-xl border-2 border-dashed border-neutral-200">
          <Link2 size={48} className="text-neutral-300" />
          <p className="font-heading text-lg font-semibold text-primary-500">Aucun circuit configuré</p>
          <Button asChild><Link href="/circuits/nouveau">+ Créer un circuit</Link></Button>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {circuits.map((circuit, idx) => {
            const color = CIRCUIT_COLORS[idx % CIRCUIT_COLORS.length];
            return (
              <div key={circuit.id}
                className="group relative overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm hover:shadow-md transition-all">
                <div className="absolute left-0 top-0 bottom-0 w-1 rounded-l-xl" style={{ background: color }} />

                <div className="pl-6 pr-5 py-5 flex flex-col gap-4">

                  {/* En-tête */}
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex flex-col gap-1">
                      <div className="flex items-center gap-3 flex-wrap">
                        <h3 className="font-heading text-lg font-bold text-primary-500">{circuit.nom}</h3>
                        <span className={`px-2 py-0.5 rounded-full text-xxs font-semibold uppercase tracking-wider ${
                          circuit.actif
                            ? "bg-green-100 text-green-700"
                            : "bg-neutral-100 text-neutral-500"
                        }`}>
                          {circuit.actif ? "Actif" : "Inactif"}
                        </span>
                        {circuit.typeAbsenceCible && (
                          <span className="px-2 py-0.5 rounded text-xxs border"
                            style={{ borderColor: color + "50", color, background: color + "10" }}>
                            {TYPE_LABELS[circuit.typeAbsenceCible] ?? circuit.typeAbsenceCible}
                          </span>
                        )}
                      </div>
                      <p className="text-sm text-neutral-500">
                        {circuit.etapes?.length ?? 0} étape(s) · {circuit.estModeleNomme ? "Modèle nommé" : "Circuit personnalisé"}
                      </p>
                    </div>
                    <div className="flex gap-2 flex-shrink-0 items-center">
                      <BoutonToggleActif circuitId={circuit.id} actif={circuit.actif} />
                      <Button asChild size="sm" variant="outline">
                        <Link href={`/circuits/${circuit.id}`}><Settings size={14} /> Détail</Link>
                      </Button>
                      <BoutonSupprimerCircuit circuitId={circuit.id} />
                    </div>
                  </div>

                  {/* Workflow visuel */}
                  {circuit.etapes && circuit.etapes.length > 0 && (
                    <div className="flex items-center gap-2 overflow-x-auto pb-1">
                      {circuit.etapes.map((etape, i) => {
                        const premiereRegle = etape.regles?.[0];
                        const mecanisme = premiereRegle
                          ? (MECANISME_LABELS[premiereRegle.mecanisme] ?? { label: premiereRegle.mecanisme, color: "#6B7280" })
                          : { label: "—", color: "#6B7280" };
                        return (
                          <div key={etape.id} className="flex items-center gap-2 flex-shrink-0">
                            <div className="flex flex-col items-center gap-1">
                              <div className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold text-white flex-shrink-0"
                                style={{ background: mecanisme.color }}>
                                {i + 1}
                              </div>
                              <div className="text-center" style={{ maxWidth: "80px" }}>
                                <p className="text-xxs font-semibold text-primary-500 leading-tight truncate">
                                  {etape.libelle}
                                </p>
                                <p className="text-xxs text-neutral-400 leading-tight">
                                  {mecanisme.label}
                                </p>
                                {etape.delaiJours && (
                                  <p className="text-xxs text-neutral-300">{etape.delaiJours}j</p>
                                )}
                              </div>
                            </div>
                            {i < circuit.etapes.length - 1 && (
                              <div className="h-px w-6 bg-neutral-200 flex-shrink-0 mb-5" />
                            )}
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>

                <div className="absolute bottom-0 left-0 right-0 h-0.5 opacity-0 group-hover:opacity-100 transition-opacity"
                  style={{ background: KENTE }} />
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
