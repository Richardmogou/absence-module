"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { useForm, useFieldArray, Controller, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import Image from "next/image";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { FormField } from "@/components/FormField";
import { Label } from "@/components/ui/label";
import apiClient from "@/lib/api/client";
import { ArrowRight, Link2, Lock, X } from "lucide-react";

// ── Schéma ─────────────────────────────────────────────────────────────────

const etapeSchema = z.object({
  mecanismeResolution: z.enum(["BACKUP", "HIERARCHIQUE", "ROLE_FIXE_SCOPE_RESEAU", "ROLE_FIXE_GLOBAL"]),
  roleHabilite: z.string().optional(),
  profondeurHierarchique: z.number().int().min(1).max(10).optional().nullable(),
});

const schema = z.object({
  nom: z.string().min(1, "Le nom du circuit est obligatoire"),
  typeAbsenceCible: z.enum(
    ["CONGE_ANNUEL", "CONGE_MALADIE", "PERMISSION", "MISSION_LONGUE", "CONGE_MATERNITE"]
  ),
  gradeDeclencheur: z.string().optional(),
  uniteIdentifianteExterne: z.string().optional(),
  etapesIntermediaires: z.array(etapeSchema).min(1, "Au moins une étape est requise"),
});

type CircuitData = z.infer<typeof schema>;

// ── Types erreurs métier ────────────────────────────────────────────────────

type ErreurDoublon = {
  type: "DOUBLON_VALIDATEUR_DETECTE";
  circuitId: string;
  etapeRoleFixeRedondanteId: string;
  gradeDeclencheur: string;
};
type ErreurEmploye = { type: "EMPLOYE_TYPE_INTROUVABLE"; gradeDeclencheur: string };
type ErreurMetier  = ErreurDoublon | ErreurEmploye | null;

// ── Constants ───────────────────────────────────────────────────────────────

const KENTE =
  "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)";

const TYPE_LABELS: Record<string, string> = {
  CONGE_ANNUEL:    "Congé annuel",
  CONGE_MALADIE:   "Congé maladie",
  PERMISSION:      "Permission",
  MISSION_LONGUE:  "Mission longue durée",
  CONGE_MATERNITE: "Congé maternité",
};

const MECANISME_OPTIONS = [
  { value: "BACKUP",                label: "N+0 · Backup (même grade & unité)",  color: "#059669" },
  { value: "HIERARCHIQUE",          label: "N+X · Hiérarchique",                 color: "#1A1A2E" },
  { value: "ROLE_FIXE_SCOPE_RESEAU", label: "Rôle fixe (périmètre réseau)",      color: "#B8932A" },
  { value: "ROLE_FIXE_GLOBAL",      label: "Rôle fixe (global)",                  color: "#7C3AED" },
];

const SELECT_CLASS =
  "w-full rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm text-primary-500 " +
  "focus:outline-none focus:ring-2 focus:ring-accent-500 focus:border-accent-500 transition-colors";

// ── Sous-composant : champs d'une étape ────────────────────────────────────

function EtapeFields({
  index,
  control,
  register,
  errors,
  remove,
  rolesOptions,
}: {
  index: number;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  control: any;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  register: any;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  errors: any;
  remove: (i: number) => void;
  rolesOptions: { value: string, label: string }[];
}) {
  const mecanisme = useWatch({
    control,
    name: `etapesIntermediaires.${index}.mecanismeResolution`,
  });

  const needsRole      = mecanisme === "ROLE_FIXE_SCOPE_RESEAU" || mecanisme === "ROLE_FIXE_GLOBAL";
  const needsProfondeur = mecanisme === "HIERARCHIQUE";

  const optionColor =
    MECANISME_OPTIONS.find((o) => o.value === mecanisme)?.color ?? "#6B7280";

  return (
    <div className="flex gap-3 p-3 rounded-lg border border-neutral-200 bg-neutral-50 group/step items-start">
      {/* Numéro */}
      <div
        className="w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold flex-shrink-0 mt-6"
        style={{ background: optionColor, color: "#fff" }}
      >
        {index + 1}
      </div>

      <div className="flex-1 grid grid-cols-1 sm:grid-cols-2 gap-3">
        {/* Mécanisme */}
        <div className="flex flex-col gap-1.5">
          <Label htmlFor={`etapesIntermediaires.${index}.mecanismeResolution`}>
            Type d&apos;étape
          </Label>
          <Controller
            control={control}
            name={`etapesIntermediaires.${index}.mecanismeResolution`}
            render={({ field }) => (
              <select {...field} className={SELECT_CLASS}>
                {MECANISME_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            )}
          />
        </div>

        {/* Profondeur hiérarchique (HIERARCHIQUE uniquement) */}
        {needsProfondeur && (
          <FormField
            id={`etapesIntermediaires.${index}.profondeurHierarchique`}
            label="Niveaux à remonter (N+X)"
            type="number"
            min={1}
            max={10}
            placeholder="ex: 1 pour N+1"
            error={errors.etapesIntermediaires?.[index]?.profondeurHierarchique}
            {...register(`etapesIntermediaires.${index}.profondeurHierarchique`, { valueAsNumber: true })}
          />
        )}

        {/* Rôle (ROLE_FIXE_* uniquement) */}
        {needsRole && (
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`etapesIntermediaires.${index}.roleHabilite`}>
              Rôle Keycloak habilité
            </Label>
            <select
              id={`etapesIntermediaires.${index}.roleHabilite`}
              {...register(`etapesIntermediaires.${index}.roleHabilite`)}
              className={SELECT_CLASS + (errors.etapesIntermediaires?.[index]?.roleHabilite ? " border-red-500" : "")}
            >
              {rolesOptions.map((r) => (
                <option key={r.value} value={r.value}>{r.label}</option>
              ))}
            </select>
            {errors.etapesIntermediaires?.[index]?.roleHabilite && (
              <span className="text-xs text-red-500">{errors.etapesIntermediaires[index].roleHabilite.message}</span>
            )}
          </div>
        )}

        {/* Info BACKUP */}
        {mecanisme === "BACKUP" && (
          <p className="text-xs text-neutral-500 col-span-full bg-amber-50 border border-amber-200 rounded px-3 py-2">
            Le système proposera automatiquement les collègues de même grade et même unité au demandeur.
          </p>
        )}
      </div>

      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={() => remove(index)}
        className="mt-6 text-neutral-400 hover:text-secondary-500 opacity-0 group-hover/step:opacity-100 transition-opacity flex-shrink-0"
      >
        <X size={16} />
      </Button>
    </div>
  );
}

// ── Page principale ────────────────────────────────────────────────────────

export default function NouveauCircuitPage() {
  const router = useRouter();
  const [erreurMetier, setErreurMetier]        = useState<ErreurMetier>(null);
  const [choixDoublonEnCours, setChoixEnCours] = useState(false);
  const [rolesOptions, setRolesOptions] = useState<{ value: string, label: string }[]>([
    { value: "", label: "-- Sélectionnez un rôle --" }
  ]);

  useEffect(() => {
    async function fetchRoles() {
      try {
        const res = await apiClient.get<string[]>("/api/v5/referentiel/roles-keycloak");
        const options = res.data.map((role) => ({
          value: role,
          label: role,
        }));
        setRolesOptions([
          { value: "", label: "-- Sélectionnez un rôle --" },
          ...options,
        ]);
      } catch (err) {
        console.error("Erreur lors de la récupération des rôles", err);
      }
    }
    fetchRoles();
  }, []);

  const {
    register, control, handleSubmit, getValues,
    formState: { errors, isSubmitting },
  } = useForm<CircuitData>({
    resolver: zodResolver(schema),
    defaultValues: {
      etapesIntermediaires: [{ mecanismeResolution: "HIERARCHIQUE", roleHabilite: "", profondeurHierarchique: 1 }],
    },
  });

  const { fields, append, remove } = useFieldArray({
    control,
    name: "etapesIntermediaires",
  });

  async function onSubmit(data: CircuitData) {
    setErreurMetier(null);
    try {
      await apiClient.post("/api/v5/admin/circuits", data);
      router.push("/circuits");
    } catch (err: unknown) {
      const res = (err as {
        response?: {
          status?: number;
          data?: {
            code?: string;
            circuitId?: string;
            etapeRoleFixeRedondanteId?: string;
            gradeDeclencheur?: string;
          };
        };
      })?.response;

      if (res?.status === 409 && res.data?.code === "DOUBLON_VALIDATEUR_DETECTE") {
        setErreurMetier({
          type:                      "DOUBLON_VALIDATEUR_DETECTE",
          circuitId:                 res.data?.circuitId ?? "",
          etapeRoleFixeRedondanteId: res.data?.etapeRoleFixeRedondanteId ?? "",
          gradeDeclencheur:          getValues("gradeDeclencheur") ?? "",
        });
      } else if (res?.status === 422 && res.data?.code === "EMPLOYE_TYPE_INTROUVABLE") {
        setErreurMetier({
          type:             "EMPLOYE_TYPE_INTROUVABLE",
          gradeDeclencheur: res.data?.gradeDeclencheur ?? "inconnu",
        });
      } else {
        throw err;
      }
    }
  }

  async function resoudreDoublon(choix: "SUPPRIMER" | "CONSERVER") {
    if (!erreurMetier || erreurMetier.type !== "DOUBLON_VALIDATEUR_DETECTE") return;
    setChoixEnCours(true);
    try {
      await apiClient.post(
        `/api/v5/admin/circuits/${erreurMetier.circuitId}/resolution-doublon`,
        {
          choix,
          etapeId:          erreurMetier.etapeRoleFixeRedondanteId,
          gradeDeclencheur: erreurMetier.gradeDeclencheur,
        }
      );
      setErreurMetier(null);
      router.push("/circuits");
    } finally {
      setChoixEnCours(false);
    }
  }

  return (
    <div className="min-h-[calc(100vh-176px)] flex items-stretch rounded-xl overflow-hidden border border-neutral-200 shadow-card">

      {/* ── Colonne gauche ── */}
      <div className="hidden lg:flex lg:w-2/5 relative overflow-hidden">
        <Image
          src="/Image_africaine5_resize.png"
          alt=""
          aria-hidden="true"
          fill
          sizes="40vw"
          className="object-cover object-center"
          priority
        />
        <div
          className="absolute inset-0"
          style={{ background: "linear-gradient(160deg,rgba(26,26,46,0.92) 0%,rgba(26,26,46,0.70) 100%)" }}
        />
        <div
          className="absolute inset-0 opacity-10"
          style={{ backgroundImage: "repeating-linear-gradient(45deg,#B8932A 0px,#B8932A 1px,transparent 1px,transparent 36px)" }}
        />
        <div className="relative z-10 flex flex-col justify-between p-10 h-full">
          <div className="flex items-center gap-2">
            <div className="h-px w-6 bg-gold-400" />
            <span className="text-xxs text-gold-300 tracking-[0.2em] uppercase font-ui">
              Administration — AFB
            </span>
          </div>

          <div className="flex flex-col gap-5">
            <div
              className="w-14 h-14 rounded-xl flex items-center justify-center"
              style={{ background: "rgba(255,255,255,0.10)", backdropFilter: "blur(8px)" }}
            >
              <Link2 size={26} className="text-gold-300" />
            </div>
            <h2 className="font-heading text-4xl font-bold text-white leading-tight">
              Nouveau circuit de validation
            </h2>
            <p className="text-sm text-neutral-300 font-ui max-w-xs leading-relaxed">
              Définissez le nom, le type d&apos;absence, le grade ciblé et chaque étape.
              ANALYSTE_RH et DRH sont toujours ajoutés en fin de circuit.
            </p>

            <div className="flex flex-col gap-3 mt-2">
              {[
                { n: "01", t: "Nommer & cibler (type, grade, unité)" },
                { n: "02", t: "Ajouter les étapes intermédiaires" },
                { n: "03", t: "Vérification automatique anti-doublon" },
              ].map((s) => (
                <div key={s.n} className="flex items-center gap-3">
                  <span
                    className="w-7 h-7 rounded-full flex items-center justify-center text-xxs font-bold flex-shrink-0"
                    style={{ background: "rgba(184,147,42,0.25)", color: "#EDD05D" }}
                  >
                    {s.n}
                  </span>
                  <span className="text-sm text-neutral-300 font-ui">{s.t}</span>
                </div>
              ))}
            </div>

            <div className="h-1 w-20 rounded-sm" style={{ background: KENTE }} />

            {/* Note ANALYSTE_RH/DRH */}
            <div
              className="rounded-lg px-4 py-3 text-xs text-neutral-300 font-ui leading-relaxed"
              style={{ background: "rgba(255,255,255,0.07)" }}
            >
              <Lock size={12} className="inline mr-1 -mt-0.5" /> Tout circuit se termine obligatoirement par <strong className="text-gold-300">ANALYSTE_RH</strong> puis <strong className="text-gold-300">DRH</strong>. Ces étapes sont verrouillées.
            </div>
          </div>

          <span className="font-heading text-8xl font-bold opacity-10 select-none" style={{ color: "#D4A017" }}>
            RH
          </span>
        </div>
      </div>

      {/* ── Colonne droite : formulaire ── */}
      <div className="flex-1 flex flex-col bg-white/90 backdrop-blur-sm">
        <div className="h-1 w-full bg-accent-500" />

        <div className="flex-1 flex flex-col px-8 sm:px-12 py-10 gap-6 overflow-y-auto">
          <div className="flex flex-col gap-1">
            <p className="text-xxs text-accent-500 tracking-[0.18em] uppercase font-ui font-semibold">
              Nouveau circuit
            </p>
            <h1 className="font-heading text-3xl font-bold text-primary-500">
              Configuration du circuit
            </h1>
            <p className="text-sm text-neutral-500 mt-1">
              Renseignez les paramètres de ciblage puis définissez chaque étape dans l&apos;ordre souhaité.
            </p>
          </div>

          <div className="h-px" style={{ background: "linear-gradient(90deg,#1A1A2E,transparent 60%)" }} />

          {/* ── Bannière 409 ── */}
          {erreurMetier?.type === "DOUBLON_VALIDATEUR_DETECTE" && (
            <Alert variant="destructive">
              <div className="flex flex-col gap-3">
                <div>
                  <p className="font-semibold text-sm mb-1">Doublon de validateur détecté</p>
                  <AlertDescription>
                    L&apos;étape hiérarchique et l&apos;étape de rôle fixe résolvent vers le même validateur.
                    Choisissez comment résoudre ce conflit :
                  </AlertDescription>
                </div>
                <div className="flex gap-2 flex-wrap">
                  <Button
                    size="sm"
                    variant="destructive"
                    disabled={choixDoublonEnCours}
                    onClick={() => resoudreDoublon("SUPPRIMER")}
                  >
                    Supprimer l&apos;étape redondante
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={choixDoublonEnCours}
                    onClick={() => resoudreDoublon("CONSERVER")}
                    className="border-secondary-500 text-secondary-700 hover:bg-secondary-50"
                  >
                    Conserver les deux (décision volontaire)
                  </Button>
                </div>
              </div>
            </Alert>
          )}

          {/* ── Bannière 422 ── */}
          {erreurMetier?.type === "EMPLOYE_TYPE_INTROUVABLE" && (
            <Alert variant="warning">
              <div className="flex flex-col gap-1">
                <p className="font-semibold text-sm">Aucun employé de ce grade trouvé</p>
                <AlertDescription>
                  Aucun utilisateur du grade{" "}
                  <span className="font-mono font-semibold">{erreurMetier.gradeDeclencheur}</span>{" "}
                  n&apos;a été trouvé dans Keycloak. La vérification anti-doublon n&apos;a pas pu s&apos;exécuter.
                </AlertDescription>
              </div>
            </Alert>
          )}

          {/* ── Formulaire ── */}
          <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-6">

            {/* Nom */}
            <FormField
              id="nom"
              label="Nom du circuit"
              placeholder="ex: Circuit standard agents"
              error={errors.nom}
              {...register("nom")}
            />

            {/* Type d'absence */}
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="typeAbsenceCible">Type d&apos;absence ciblé</Label>
              <Controller
                control={control}
                name="typeAbsenceCible"
                render={({ field }) => (
                  <select {...field} id="typeAbsenceCible" className={SELECT_CLASS}>
                    <option value="">— Sélectionnez un type —</option>
                    {Object.entries(TYPE_LABELS).map(([val, lbl]) => (
                      <option key={val} value={val}>{lbl}</option>
                    ))}
                  </select>
                )}
              />
              {errors.typeAbsenceCible && (
                <p className="text-xs text-secondary-500">{errors.typeAbsenceCible.message}</p>
              )}
            </div>

            {/* Grade déclencheur */}
            <FormField
              id="gradeDeclencheur"
              label="Grade déclencheur (optionnel)"
              placeholder="ex: AGENT, CADRE, DIRECTEUR"
              error={errors.gradeDeclencheur}
              {...register("gradeDeclencheur")}
            />

            {/* Unité */}
            <FormField
              id="uniteIdentifianteExterne"
              label="Unité ciblée (laisser vide = toutes unités)"
              placeholder="ex: AGENCE_DAKAR_01"
              error={errors.uniteIdentifianteExterne}
              {...register("uniteIdentifianteExterne")}
            />

            <div className="h-px bg-neutral-100" />

            {/* Étapes intermédiaires */}
            <div className="flex flex-col gap-3">
              <div className="flex items-center justify-between">
                <div>
                  <span className="text-sm font-semibold text-primary-500">Étapes intermédiaires</span>
                  <p className="text-xxs text-neutral-400 mt-0.5">
                    {fields.length} étape{fields.length > 1 ? "s" : ""} · ANALYSTE_RH et DRH seront ajoutés automatiquement
                  </p>
                </div>
              </div>

              <div className="flex flex-col gap-2">
                {fields.map((field, index) => (
                  <EtapeFields
                    key={field.id}
                    index={index}
                    control={control}
                    register={register}
                    errors={errors}
                    remove={remove}
                    rolesOptions={rolesOptions}
                  />
                ))}
              </div>

              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => append({ mecanismeResolution: "HIERARCHIQUE", roleHabilite: "", profondeurHierarchique: 1 })}
                className="self-start gap-2"
              >
                + Ajouter une étape
              </Button>

              {errors.etapesIntermediaires?.root && (
                <p className="text-xs text-secondary-500">{errors.etapesIntermediaires.root.message}</p>
              )}
            </div>

            {/* Aperçu ANALYSTE_RH / DRH */}
            <div className="flex items-center gap-2 p-3 rounded-lg bg-neutral-50 border border-dashed border-neutral-200">
              <span className="text-xs text-neutral-400">… puis automatiquement :</span>
              {["ANALYSTE_RH", "DRH"].map((r) => (
                <span key={r} className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xxs font-semibold bg-green-100 text-green-700 border border-green-200">
                  <Lock size={10} /> {r}
                </span>
              ))}
            </div>

            <div className="flex gap-3 pt-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => router.push("/circuits")}
                className="flex-1 h-12"
              >
                Annuler
              </Button>
              <Button type="submit" disabled={isSubmitting} className="flex-1 h-12 text-base">
                {isSubmitting ? "Création en cours…" : <>Créer le circuit <ArrowRight size={16} /></>}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
