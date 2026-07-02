import Link from "next/link";
import { notFound } from "next/navigation";
import { Button } from "@/components/ui/button";
import { BoutonSupprimerCircuit } from "../BoutonSupprimerCircuit";
import { BoutonToggleActif } from "../BoutonToggleActif";
import { serverApiClient } from "@/lib/api/server-client";

interface RegleAffectation {
  mecanisme: string;
  roleKeycloakCible: string | null;
  profondeurHierarchique: number | null;
}

interface Etape {
  id: string;
  ordre: number;
  libelle: string;
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

export default async function CircuitDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const circuit = await getCircuit(id);

  if (!circuit) notFound();

  return (
    <div className="flex flex-col gap-6 max-w-3xl mx-auto py-4">

      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-xs font-ui text-neutral-400">
        <Link href="/circuits" className="hover:text-accent-500 transition-colors">
          ← Circuits
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

      {/* Étapes */}
      <div className="flex flex-col gap-3">
        <h2 className="font-heading text-lg font-bold text-primary-500">
          Étapes de validation · {circuit.etapes?.length ?? 0} au total
        </h2>

        {circuit.etapes && circuit.etapes.length > 0 ? (
          <div className="flex flex-col gap-3">
            {circuit.etapes.map((etape, i) => {
              const premiereRegle = etape.regles?.[0];
              const meta = premiereRegle
                ? (MECANISME_META[premiereRegle.mecanisme] ?? {
                    label: premiereRegle.mecanisme,
                    color: "#6B7280",
                    desc:  "",
                  })
                : { label: "—", color: "#6B7280", desc: "" };

              return (
                <div key={etape.id} className="flex items-stretch gap-4">
                  {/* Connecteur vertical */}
                  <div className="flex flex-col items-center gap-0">
                    <div
                      className="w-9 h-9 rounded-full flex items-center justify-center text-sm font-bold text-white flex-shrink-0"
                      style={{ background: meta.color }}
                    >
                      {i + 1}
                    </div>
                    {i < (circuit.etapes?.length ?? 0) - 1 && (
                      <div className="w-px flex-1 my-1" style={{ background: meta.color + "40" }} />
                    )}
                  </div>

                  {/* Carte étape */}
                  <div className={`flex-1 rounded-lg border px-4 py-3 mb-2 ${
                    etape.estVerrouillable
                      ? "border-green-200 bg-green-50"
                      : "border-neutral-200 bg-white"
                  }`}>
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex flex-col gap-1">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-semibold text-sm text-primary-500">
                            {etape.libelle}
                          </span>
                          {etape.estVerrouillable && (
                            <span className="text-xxs px-2 py-0.5 rounded-full bg-green-100 text-green-700 font-semibold">
                              🔒 Verrouillé
                            </span>
                          )}
                        </div>
                        <div className="flex items-center gap-2 flex-wrap">
                          <span
                            className="text-xxs px-2 py-0.5 rounded font-semibold"
                            style={{ background: meta.color + "18", color: meta.color }}
                          >
                            {meta.label}
                          </span>
                          {meta.desc && (
                            <span className="text-xxs text-neutral-400">{meta.desc}</span>
                          )}
                        </div>
                      </div>

                      {/* Détails règle */}
                      {premiereRegle && (
                        <div className="text-xxs text-neutral-400 font-mono text-right flex-shrink-0">
                          {premiereRegle.profondeurHierarchique != null && (
                            <div>N+{premiereRegle.profondeurHierarchique}</div>
                          )}
                          {premiereRegle.roleKeycloakCible && (
                            <div>{premiereRegle.roleKeycloakCible}</div>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <p className="text-sm text-neutral-400 italic">Aucune étape définie.</p>
        )}
      </div>

      {/* Actions */}
      <div className="flex gap-3 pt-2 border-t border-neutral-100">
        <Button asChild variant="outline" className="flex-1">
          <Link href="/circuits">← Retour à la liste</Link>
        </Button>
      </div>
    </div>
  );
}
