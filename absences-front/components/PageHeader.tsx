"use client";

import { usePathname } from "next/navigation";

export function PageHeader() {
  const pathname = usePathname();

  // Si on est sur "mon-espace", on ne l'affiche pas car la page a déjà son propre Hero Header
  if (pathname === "/mon-espace") return null;

  let surTitre = "Intra-EHR";
  let titre = "TITRE";

  if (pathname?.startsWith("/nouvelle")) {
    surTitre = "Saisie";
    titre = "Nouvelle demande";
  } else if (pathname?.startsWith("/mes-demandes")) {
    surTitre = "Historique";
    titre = "Mes demandes";
  } else if (pathname?.startsWith("/validation-file")) {
    surTitre = "Manager";
    titre = "À valider";
  } else if (pathname?.startsWith("/analyste-rh")) {
    surTitre = "Espace RH";
    titre = "File d'instruction RH";
  } else if (pathname?.startsWith("/drh")) {
    surTitre = "Espace RH";
    titre = "Validation DRH";
  } else if (pathname?.startsWith("/backup")) {
    surTitre = "Délégation";
    titre = "Mon rôle Back-up";
  } else if (pathname?.match(/^\/[0-9a-fA-F-]+$/)) {
    // Expression régulière basique pour détecter un UUID (ex: /123e4567-e89b-12d3-a456-426614174000)
    surTitre = "Détails";
    titre = "Consultation de la demande";
  }

  return (
    <section className="bg-white/70 backdrop-blur-sm border-b border-neutral-200 px-8 sm:px-16 py-8">
      <div className="mx-auto max-w-container flex items-end justify-between gap-4">
        <div>
          <p className="text-xxs text-secondary-500 tracking-[0.2em] uppercase font-ui mb-1">
            {surTitre}
          </p>
          <h2 className="font-heading text-3xl font-bold text-primary-500">
            {titre}
          </h2>
        </div>
        {/* Séparateur or */}
        <div className="hidden sm:block h-12 w-1 rounded-full"
          style={{ background: "linear-gradient(180deg, #D4A017, transparent)" }} />
      </div>
    </section>
  );
}
