import Link from "next/link";
import Image from "next/image";

/* ─────────────────────────────────────────────
 * Données des types d'absence
 * Chaque carte a une image africaine, une couleur
 * d'accent et une description courte
 * ───────────────────────────────────────────── */
const demandes = [
  {
    href: "/nouvelle/conge-annuel",
    label: "Congé annuel",
    description: "Repos annuel réglementaire",
    image: "/Image_africaine6_resize.png",
    accent: "#C41E22",
    accentLight: "rgba(196,30,34,0.85)",
  },
  {
    href: "/nouvelle/conge-maladie",
    label: "Congé maladie",
    description: "Arrêt médical certifié",
    image: "/Image_Afrique3_resize.png",
    accent: "#1A1A2E",
    accentLight: "rgba(26,26,46,0.85)",
  },
  {
    href: "/nouvelle/permission",
    label: "Permission",
    description: "Absence courte durée autorisée",
    image: "/Image_africaine5_resize.png",
    accent: "#B8932A",
    accentLight: "rgba(184,147,42,0.85)",
  },
  {
    href: "/nouvelle/mission-longue",
    label: "Mission longue durée",
    description: "Déplacement professionnel étendu",
    image: "/Image_africaine5_resize.png",
    accent: "#2C2C2C",
    accentLight: "rgba(44,44,44,0.85)",
  },
  {
    href: "/nouvelle/conge-maternite",
    label: "Congé maternité",
    description: "Congé naissance & maternité",
    image: "/Image_africaine6_resize.png",
    accent: "#96751A",
    accentLight: "rgba(150,117,26,0.85)",
  },
];

const KENTE =
  "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 10px,#B8932A 10px,#B8932A 20px,#2C2C2C 20px,#2C2C2C 30px,#F5F5F5 30px,#F5F5F5 40px)";

/* ─────────────────────────────────────────────
 * Page
 * ───────────────────────────────────────────── */
export default function HomePage() {
  return (
    <div className="flex flex-col flex-1">

      {/* ══════════════════════════════════════
          HERO — plein écran avec parallax visuel
          ══════════════════════════════════════ */}
      <section className="relative overflow-hidden" style={{ minHeight: "380px" }}>
        {/* Image africaine principale */}
        <Image
          src="/Image_africaine6_resize.png"
          alt=""
          aria-hidden="true"
          fill
          priority
          sizes="100vw"
          className="object-cover object-center scale-105"
        />
        {/* Overlay multicouche : sombre à gauche, rouge AFB à droite */}
        <div
          className="absolute inset-0"
          style={{
            background:
              "linear-gradient(110deg, rgba(26,26,46,0.93) 0%, rgba(44,44,44,0.75) 45%, rgba(196,30,34,0.45) 100%)",
          }}
        />
        {/* Motif géométrique subtil */}
        <div
          className="absolute inset-0 opacity-10"
          style={{
            backgroundImage:
              "repeating-linear-gradient(45deg, #B8932A 0px, #B8932A 1px, transparent 1px, transparent 40px)",
          }}
        />

        {/* Contenu hero */}
        <div className="relative z-10 h-full flex flex-col items-start justify-center px-8 sm:px-16 py-16 gap-5"
          style={{ minHeight: "380px" }}>
          {/* Badge */}
          <div className="flex items-center gap-2">
            <div className="h-px w-8 bg-gold-400" />
            <span className="text-xxs text-gold-300 tracking-[0.25em] uppercase font-ui">
              Portail RH — Africa Financial Bank
            </span>
          </div>

          <h1 className="font-heading text-5xl sm:text-6xl font-bold text-white leading-tight max-w-xl">
            Gérez vos{" "}
            <span
              className="relative inline-block"
              style={{ color: "#EDD05D" }}
            >
              absences
              {/* Soulignement or */}
              <span
                className="absolute bottom-0 left-0 right-0 h-0.5"
                style={{ background: "linear-gradient(90deg, #D4A017, #B8932A)" }}
              />
            </span>
            <br />en toute sérénité
          </h1>

          <p className="text-neutral-300 text-base max-w-md font-ui">
            Soumettez, suivez et gérez toutes vos demandes depuis un espace
            unifié, sécurisé et validé par votre hiérarchie.
          </p>

          {/* Statistiques fictives — crédibilité institutionnelle */}
          <div className="flex gap-8 mt-2">
            {[
              { value: "5", label: "Types de congé" },
              { value: "24h", label: "Délai de traitement" },
              { value: "100%", label: "Dématérialisé" },
            ].map((s) => (
              <div key={s.label} className="flex flex-col">
                <span className="font-heading text-2xl font-bold text-gold-300">{s.value}</span>
                <span className="text-xxs text-neutral-400 tracking-wider uppercase">{s.label}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Bande kente en bas du hero */}
        <div className="absolute bottom-0 left-0 right-0 h-1.5" style={{ background: KENTE }} />
      </section>

      {/* ══════════════════════════════════════
          SECTION TITRE
          ══════════════════════════════════════ */}
      <section className="bg-white/70 backdrop-blur-sm border-b border-neutral-200 px-8 sm:px-16 py-8">
        <div className="mx-auto max-w-container flex items-end justify-between gap-4">
          <div>
            <p className="text-xxs text-secondary-500 tracking-[0.2em] uppercase font-ui mb-1">
              Nouvelle demande
            </p>
            <h2 className="font-heading text-3xl font-bold text-primary-500">
              Choisissez votre type d&apos;absence
            </h2>
          </div>
          {/* Séparateur or */}
          <div className="hidden sm:block h-12 w-1 rounded-full"
            style={{ background: "linear-gradient(180deg, #D4A017, transparent)" }} />
        </div>
      </section>

      {/* ══════════════════════════════════════
          GRILLE DES CARTES D'ABSENCE
          ══════════════════════════════════════ */}
      <section className="flex-1 px-8 sm:px-16 py-10">
        <div className="mx-auto max-w-container">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">

            {/* 4 premières cartes en grille */}
            {demandes.slice(0, 4).map((d) => (
              <AbsenceCard key={d.href} {...d} />
            ))}

            {/* 5ème carte + bloc décoratif côte à côte sur large */}
            <AbsenceCard {...demandes[4]} />

            {/* Bloc admin — discret */}
            <div className="flex flex-col justify-between rounded-lg border border-neutral-200 bg-white/80 p-6 shadow-card">
              <div className="flex flex-col gap-2">
                <div
                  className="h-1 w-12 rounded-full"
                  style={{ background: "linear-gradient(90deg, #1A1A2E, #4A4A8A)" }}
                />
                <h3 className="font-heading text-lg font-semibold text-accent-500">
                  Administration
                </h3>
                <p className="text-sm text-neutral-500">
                  Configurez les circuits de validation et les règles métier RH.
                </p>
              </div>
              <Link
                href="/circuits"
                className="mt-6 inline-flex items-center gap-2 text-sm font-medium text-accent-500 hover:text-gold-500 transition-colors group"
              >
                Gérer les circuits
                <span className="transition-transform group-hover:translate-x-1">→</span>
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* ══════════════════════════════════════
          FOOTER DÉCORATIF
          ══════════════════════════════════════ */}
      <footer className="px-8 sm:px-16 py-6 bg-white/50">
        <div className="mx-auto max-w-container flex items-center gap-4">
          <div className="flex-1 h-px"
            style={{ background: "linear-gradient(90deg, transparent, #B8932A)" }} />
          <Image
            src="/icon_afb.png"
            alt=""
            aria-hidden="true"
            width={20}
            height={20}
            className="opacity-40"
          />
          <div className="flex-1 h-px"
            style={{ background: "linear-gradient(90deg, #B8932A, transparent)" }} />
        </div>
      </footer>

    </div>
  );
}

/* ─────────────────────────────────────────────
 * Composant carte d'absence
 * ───────────────────────────────────────────── */
function AbsenceCard({
  href,
  label,
  description,
  image,
  accentLight,
}: {
  href: string;
  label: string;
  description: string;
  image: string;
  accent: string;
  accentLight: string;
}) {
  return (
    <Link
      href={href}
      className="group relative overflow-hidden rounded-xl shadow-card hover:shadow-gold transition-all duration-350"
      style={{ minHeight: "200px" }}
    >
      {/* Image africaine de fond */}
      <Image
        src={image}
        alt=""
        aria-hidden="true"
        fill
        sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 33vw"
        className="object-cover object-center transition-transform duration-450 group-hover:scale-105"
      />

      {/* Overlay de base — sombre en bas */}
      <div
        className="absolute inset-0 transition-opacity duration-350"
        style={{
          background:
            "linear-gradient(to top, rgba(10,10,10,0.90) 0%, rgba(10,10,10,0.30) 55%, transparent 100%)",
        }}
      />

      {/* Overlay coloré au hover */}
      <div
        className="absolute inset-0 opacity-0 group-hover:opacity-60 transition-opacity duration-350"
        style={{ background: accentLight }}
      />

      {/* Contenu de la carte */}
      <div className="absolute inset-0 flex flex-col justify-end p-5 z-10">
        {/* Ligne décorative */}
        <div
          className="w-8 h-0.5 mb-3 transition-all duration-350 group-hover:w-14"
          style={{ background: "linear-gradient(90deg, #D4A017, #B8932A)" }}
        />
        <h3 className="font-heading text-xl font-bold text-white leading-tight">
          {label}
        </h3>
        <p className="text-xs text-neutral-300 mt-1 font-ui">{description}</p>

        {/* CTA apparaît au hover */}
        <div className="mt-3 flex items-center gap-1 text-gold-300 text-xs font-ui
          opacity-0 translate-y-2 group-hover:opacity-100 group-hover:translate-y-0
          transition-all duration-350">
          <span>Faire une demande</span>
          <span>→</span>
        </div>
      </div>
    </Link>
  );
}
