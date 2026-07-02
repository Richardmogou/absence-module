import Image from "next/image";
import Link from "next/link";

const KENTE =
  "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 10px,#B8932A 10px,#B8932A 20px,#2C2C2C 20px,#2C2C2C 30px,#F5F5F5 30px,#F5F5F5 40px)";

const liens = {
  demandes: [
    { label: "Congé annuel",         href: "/nouvelle/conge-annuel" },
    { label: "Congé maladie",        href: "/nouvelle/conge-maladie" },
    { label: "Permission",           href: "/nouvelle/permission" },
    { label: "Mission longue durée", href: "/nouvelle/mission-longue" },
    { label: "Congé maternité",      href: "/nouvelle/conge-maternite" },
  ],
  admin: [
    { label: "Circuits de validation", href: "/circuits" },
    { label: "Nouveau circuit",        href: "/circuits/nouveau" },
  ],
  support: [
    { label: "Documentation RH",      href: "#" },
    { label: "Contacter le support",  href: "#" },
    { label: "Politique de données",  href: "#" },
  ],
};

export default function AppFooter() {
  const annee = new Date().getFullYear();

  return (
    <footer className="relative mt-auto overflow-hidden">
      {/* Bande kente haut */}
      <div className="h-1 w-full" style={{ background: KENTE }} />

      {/* Fond avec image africaine très atténuée */}
      <div className="relative">
        <Image
          src="/Image_africaine6_resize.png"
          alt=""
          aria-hidden="true"
          fill
          sizes="100vw"
          className="object-cover object-center opacity-10"
        />
        {/* Overlay bleu nuit profond */}
        <div
          className="absolute inset-0"
          style={{
            background:
              "linear-gradient(180deg, rgba(26,26,46,0.97) 0%, rgba(10,10,20,0.99) 100%)",
          }}
        />

        {/* Contenu footer */}
        <div className="relative z-10">

          {/* ── Zone principale ── */}
          <div className="mx-auto max-w-container px-6 sm:px-10 py-12">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-10">

              {/* Colonne 1 — Identité AFB */}
              <div className="flex flex-col gap-5 lg:col-span-1">
                <div className="bg-white inline-flex rounded-sm p-1 self-start">
                  <Image
                    src="/header/logo_afb.png"
                    alt="AFB"
                    width={100}
                    height={36}
                    className="h-8 w-auto"
                  />
                </div>
                <div className="flex flex-col gap-1">
                  <p className="font-heading text-base font-bold text-white">
                    Africa Financial Bank
                  </p>
                  <p className="text-xs text-neutral-400 font-ui leading-relaxed">
                    Portail de gestion des absences — usage interne exclusivement réservé aux collaborateurs AFB.
                  </p>
                </div>
                {/* Réseaux / badges */}
                <div className="flex items-center gap-3 mt-1">
                  <div
                    className="h-1 w-16 rounded-sm"
                    style={{ background: KENTE }}
                  />
                  <span className="text-xxs text-neutral-500 font-ui tracking-wider uppercase">
                    Intranet RH
                  </span>
                </div>
              </div>

              {/* Colonne 2 — Demandes */}
              <div className="flex flex-col gap-4">
                <div className="flex items-center gap-2">
                  <div
                    className="h-4 w-0.5 rounded-full"
                    style={{ background: "#C41E22" }}
                  />
                  <h3 className="text-xxs text-neutral-400 uppercase tracking-[0.18em] font-ui font-semibold">
                    Types de demande
                  </h3>
                </div>
                <ul className="flex flex-col gap-2">
                  {liens.demandes.map((l) => (
                    <li key={l.href}>
                      <Link
                        href={l.href}
                        className="text-sm text-neutral-400 hover:text-gold-300 transition-colors font-ui flex items-center gap-1.5 group"
                      >
                        <span className="opacity-0 group-hover:opacity-100 transition-opacity text-gold-500 text-xs">›</span>
                        {l.label}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>

              {/* Colonne 3 — Administration */}
              <div className="flex flex-col gap-4">
                <div className="flex items-center gap-2">
                  <div
                    className="h-4 w-0.5 rounded-full"
                    style={{ background: "#B8932A" }}
                  />
                  <h3 className="text-xxs text-neutral-400 uppercase tracking-[0.18em] font-ui font-semibold">
                    Administration
                  </h3>
                </div>
                <ul className="flex flex-col gap-2">
                  {liens.admin.map((l) => (
                    <li key={l.href}>
                      <Link
                        href={l.href}
                        className="text-sm text-neutral-400 hover:text-gold-300 transition-colors font-ui flex items-center gap-1.5 group"
                      >
                        <span className="opacity-0 group-hover:opacity-100 transition-opacity text-gold-500 text-xs">›</span>
                        {l.label}
                      </Link>
                    </li>
                  ))}
                </ul>

                {/* Séparateur */}
                <div className="h-px bg-neutral-800 mt-2" />

                <div className="flex items-center gap-2">
                  <div
                    className="h-4 w-0.5 rounded-full"
                    style={{ background: "#1A1A2E" }}
                  />
                  <h3 className="text-xxs text-neutral-400 uppercase tracking-[0.18em] font-ui font-semibold">
                    Support
                  </h3>
                </div>
                <ul className="flex flex-col gap-2">
                  {liens.support.map((l) => (
                    <li key={l.label}>
                      <Link
                        href={l.href}
                        className="text-sm text-neutral-400 hover:text-gold-300 transition-colors font-ui flex items-center gap-1.5 group"
                      >
                        <span className="opacity-0 group-hover:opacity-100 transition-opacity text-gold-500 text-xs">›</span>
                        {l.label}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>

              {/* Colonne 4 — Image décorative + infos */}
              <div className="hidden lg:flex flex-col gap-4">
                <div className="relative rounded-lg overflow-hidden shadow-gold" style={{ height: "140px" }}>
                  <Image
                    src="/Image_Afrique3_resize.png"
                    alt="AFB Afrique"
                    fill
                    sizes="300px"
                    className="object-cover object-center"
                  />
                  <div
                    className="absolute inset-0"
                    style={{ background: "linear-gradient(135deg,rgba(26,26,46,0.6),rgba(184,147,42,0.3))" }}
                  />
                  <div className="absolute bottom-3 left-3">
                    <p className="font-heading text-sm font-bold text-white">
                      Votre banque, votre avenir
                    </p>
                    <p className="text-xxs text-gold-300 font-ui tracking-wider uppercase mt-0.5">
                      Africa Financial Bank
                    </p>
                  </div>
                </div>

                {/* Infos contact */}
                <div className="flex flex-col gap-2 mt-1">
                  <p className="text-xxs text-neutral-500 font-ui uppercase tracking-wider">
                    Assistance RH
                  </p>
                  <p className="text-sm text-neutral-300 font-ui">
                    rh@afb.africa
                  </p>
                  <p className="text-xs text-neutral-500 font-ui">
                    Lun–Ven · 08h00 – 17h00
                  </p>
                </div>
              </div>

            </div>
          </div>

          {/* ── Séparateur or ── */}
          <div
            className="mx-auto max-w-container px-6 sm:px-10"
          >
            <div
              className="h-px w-full"
              style={{
                background:
                  "linear-gradient(90deg, transparent 0%, #B8932A 30%, #D4A017 50%, #B8932A 70%, transparent 100%)",
              }}
            />
          </div>

          {/* ── Bas de footer : copyright ── */}
          <div className="mx-auto max-w-container px-6 sm:px-10 py-5">
            <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
              <div className="flex items-center gap-3">
                <Image
                  src="/icon_afb.png"
                  alt=""
                  aria-hidden="true"
                  width={16}
                  height={16}
                  className="opacity-40"
                />
                <p className="text-xs text-neutral-500 font-ui">
                  © {annee} Africa Financial Bank — Tous droits réservés
                </p>
              </div>
              <p className="text-xxs text-neutral-600 font-ui tracking-wider uppercase">
                Application interne · Gestion des absences v1.0
              </p>
            </div>
          </div>

        </div>
      </div>
    </footer>
  );
}
