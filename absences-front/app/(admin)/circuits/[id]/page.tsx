import Link from "next/link";
import { notFound } from "next/navigation";
import { Button } from "@/components/ui/button";
import { BoutonSupprimerCircuit } from "../BoutonSupprimerCircuit";
import { BoutonToggleActif } from "../BoutonToggleActif";
import { serverApiClient } from "@/lib/api/server-client";
import { ArrowLeft, Lock } from "lucide-react";

interface RegleAffectation {
  mecanisme: string;
  roleKeycloakCible: string | null;
  profondeurHierarchique: number | null;
  gradeDeclencheur: string | null;
  priorite: number | null;
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
  gradeDeclencheur: string | null;
  uniteIdentifianteExterne: string | null;
  actif: boolean;
  estModeleNomme: boolean;
  etapes: Etape[];
}

async function getCircuit(id: string): Promise<Circuit | null> {
  try {
    const api = await serverApiClient();
    const { data } = await api.get(`/api/v5/admin/circuits/${id}`);
    return data;
  } catch {
    return null;
  }
}

const TYPE_LABELS: Record<string, string> = {
  CONGE_ANNUEL:    "Congé annuel",
  CONGE_MALADIE:   "Congé maladie",
  PERMISSION:      "Permission",
  MISSION_LONGUE:  "Mission longue durée",
  CONGE_MATERNITE: "Congé maternité",
};

const MECANISME_META: Record<string, { label: string; color: string; desc: string }> = {
  BACKUP: {
    label: "Backup N+0",
    color: "#059669",
    desc:  "Collègue de même grade et même unité, choisi par le demandeur",
  },
  HIERARCHIQUE: {
    label: "Hiérarchique",
    color: "#1A1A2E",
    desc:  "Responsable hiérarchique à N+X niveaux",
  },
  ROLE_FIXE_SCOPE_RESEAU: {
    label: "Rôle fixe (réseau)",
    color: "#B8932A",
    desc:  "Rôle Keycloak dans le périmètre réseau du demandeur",
  },
  ROLE_FIXE_GLOBAL: {
    label: "Rôle fixe (global)",
    color: "#7C3AED",
    desc:  "Rôle Keycloak à portée globale",
  },
  DG_CONDITIONNEL: {
    label: "DG conditionnel",
    color: "#6B7280",
    desc:  "Direction générale, déclenché sous condition",
  },
};

const KENTE =
  "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

/** Décrit la cible concrète d'une règle selon son mécanisme (colonne « Cible » du tableau). */
function cibleRegle(r: RegleAffectation): string {
  switch (r.mecanisme) {
    case "HIERARCHIQUE":
      return r.profondeurHierarchique != null ? `N+${r.profondeurHierarchique}` : "Hiérarchie";
    case "ROLE_FIXE_SCOPE_RESEAU":
    case "ROLE_FIXE_GLOBAL":
      return r.roleKeycloakCible ?? "—";
    case "DG_CONDITIONNEL":
      return r.gradeDeclencheur ? `Grade ${r.gradeDeclencheur}` : "DG";
    case "BACKUP":
      return "Backup désigné";
    default:
      return "—";
  }
}

export default async function CircuitDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const circuit = await getCircuit(id);

  if (!circuit) notFound();

  return (
    <div className="flex flex-col gap-6 max-w-3xl mx-auto py-4">

      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-xs font-ui text-neutral-400">
        <Link href="/circuits" className="flex items-center gap-1 hover:text-accent-500 transition-colors">
          <ArrowLeft size={12} /> Circuits
        </Link>
        <span className="text-neutral-300">›</span>
        <span className="text-primary-500 font-semibold">{circuit.nom}</span>
      </nav>

      {/* En-tête */}
      <div className="relative overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm p-6">
        <div className="absolute top-0 left-0 right-0 h-1" style={{ background: KENTE }} />

        <div className="flex items-start justify-between gap-4 mt-2">
          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-3 flex-wrap">
              <h1 className="font-heading text-2xl font-bold text-primary-500">{circuit.nom}</h1>
              <span className={`px-2 py-0.5 rounded-full text-xxs font-semibold uppercase tracking-wider ${
                circuit.actif
                  ? "bg-green-100 text-green-700"
                  : "bg-neutral-100 text-neutral-500"
              }`}>
                {circuit.actif ? "Actif" : "Inactif"}
              </span>
            </div>

            <div className="flex flex-wrap gap-3 text-sm text-neutral-500 font-ui">
              {circuit.typeAbsenceCible && (
                <span className="flex items-center gap-1.5">
                  <span className="text-neutral-300">Type :</span>
                  <strong className="text-primary-500">
                    {TYPE_LABELS[circuit.typeAbsenceCible] ?? circuit.typeAbsenceCible}
                  </strong>
                </span>
              )}
              {circuit.gradeDeclencheur && (
                <span className="flex items-center gap-1.5">
                  <span className="text-neutral-300">Grade :</span>
                  <strong className="text-primary-500 font-mono">{circuit.gradeDeclencheur}</strong>
                </span>
              )}
              {circuit.uniteIdentifianteExterne ? (
                <span className="flex items-center gap-1.5">
                  <span className="text-neutral-300">Unité :</span>
                  <strong className="text-primary-500 font-mono">{circuit.uniteIdentifianteExterne}</strong>
                </span>
              ) : (
                <span className="text-neutral-400 italic">Toutes unités</span>
              )}
            </div>
          </div>

          <div className="flex items-center gap-2 flex-shrink-0">
            <BoutonToggleActif circuitId={circuit.id} actif={circuit.actif} />
            <BoutonSupprimerCircuit circuitId={circuit.id} />
          </div>
        </div>
      </div>

      {/* Étapes — tableau matriciel étape × règles */}
      <div className="flex flex-col gap-3">
        <h2 className="font-heading text-lg font-bold text-primary-500">
          Étapes de validation · {circuit.etapes?.length ?? 0} au total
        </h2>

        {circuit.etapes && circuit.etapes.length > 0 ? (
          <div className="overflow-x-auto rounded-xl border border-neutral-200 bg-white shadow-sm">
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr className="bg-neutral-50 border-b border-neutral-200 text-xxs uppercase tracking-wider text-neutral-400 font-ui">
                  <th className="text-center font-semibold px-3 py-2.5 w-12">#</th>
                  <th className="text-left font-semibold px-3 py-2.5">Étape</th>
                  <th className="text-left font-semibold px-3 py-2.5">Mécanisme</th>
                  <th className="text-left font-semibold px-3 py-2.5">Cible</th>
                  <th className="text-center font-semibold px-3 py-2.5 w-16">Priorité</th>
                  <th className="text-center font-semibold px-3 py-2.5 w-16">Délai</th>
                </tr>
              </thead>
              <tbody>
                {circuit.etapes.map((etape, i) => {
                  const regles = etape.regles?.length ? etape.regles : [null];
                  return regles.map((regle, j) => {
                    const meta = regle
                      ? (MECANISME_META[regle.mecanisme] ?? { label: regle.mecanisme, color: "#6B7280", desc: "" })
                      : { label: "—", color: "#6B7280", desc: "" };
                    const premiereLigne = j === 0;
                    return (
                      <tr
                        key={etape.id + "-" + j}
                        className={`border-b border-neutral-100 last:border-0 ${
                          etape.estVerrouillable ? "bg-green-50/40" : ""
                        }`}
                      >
                        {/* Colonnes niveau ÉTAPE — fusionnées sur toutes les règles */}
                        {premiereLigne && (
                          <>
                            <td rowSpan={regles.length} className="text-center align-top px-3 py-3">
                              <span
                                className="inline-flex items-center justify-center w-7 h-7 rounded-full text-xs font-bold text-white"
                                style={{ background: meta.color }}
                              >
                                {i + 1}
                              </span>
                            </td>
                            <td rowSpan={regles.length} className="align-top px-3 py-3">
                              <div className="flex items-center gap-2 flex-wrap">
                                <span className="font-semibold text-primary-500">{etape.libelle}</span>
                                {etape.estVerrouillable && (
                                  <span className="inline-flex items-center gap-1 text-xxs px-2 py-0.5 rounded-full bg-green-100 text-green-700 font-semibold">
                                    <Lock size={10} /> Verrouillé
                                  </span>
                                )}
                              </div>
                              {regles.length > 1 && (
                                <span className="text-xxs text-neutral-400">{regles.length} règles</span>
                              )}
                            </td>
                          </>
                        )}

                        {/* Colonnes niveau RÈGLE */}
                        <td className="px-3 py-3">
                          <span
                            className="text-xxs px-2 py-0.5 rounded font-semibold whitespace-nowrap"
                            style={{ background: meta.color + "18", color: meta.color }}
                          >
                            {meta.label}
                          </span>
                        </td>
                        <td className="px-3 py-3 font-mono text-xs text-neutral-600">
                          {regle ? cibleRegle(regle) : "—"}
                        </td>
                        <td className="text-center px-3 py-3 text-neutral-500">
                          {regle?.priorite ?? "—"}
                        </td>
                        {premiereLigne && (
                          <td rowSpan={regles.length} className="text-center align-top px-3 py-3 text-neutral-500">
                            {etape.delaiJours != null ? `${etape.delaiJours}j` : "—"}
                          </td>
                        )}
                      </tr>
                    );
                  });
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-neutral-400 italic">Aucune étape définie.</p>
        )}
      </div>

      {/* Actions */}
      <div className="flex gap-3 pt-2 border-t border-neutral-100">
        <Button asChild variant="outline" className="flex-1">
          <Link href="/circuits"><ArrowLeft size={16} /> Retour à la liste</Link>
        </Button>
      </div>
    </div>
  );
}
