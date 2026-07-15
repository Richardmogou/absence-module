"use client";

import Image from "next/image";

interface FormPageLayoutProps {
  /** Image africaine de fond (colonne gauche) */
  image: string;
  /** Couleur d'accent hex — barre latérale, badge, bouton */
  accentColor: string;
  /** Libellé court affiché en badge */
  badge: string;
  /** Titre principal */
  title: string;
  /** Sous-titre informatif */
  subtitle: string;
  /** Icône SVG ou emoji */
  icon: React.ReactNode;
  children: React.ReactNode;
}

export default function FormPageLayout({
  image,
  accentColor,
  badge,
  title,
  subtitle,
  icon,
  children,
}: FormPageLayoutProps) {
  return (
    <div className="min-h-[calc(100vh-64px)] flex items-stretch">
      {/* ── Colonne gauche : image africaine ── */}
      <div className="hidden lg:flex lg:w-2/5 relative overflow-hidden">
        <Image
          src={image}
          alt=""
          aria-hidden="true"
          fill
          sizes="40vw"
          className="object-cover object-center"
          priority
        />
        {/* Overlay dégradé */}
        <div
          className="absolute inset-0"
          style={{
            background: `linear-gradient(160deg, rgba(26,26,46,0.70) 0%, ${accentColor}CC 100%)`,
          }}
        />
        {/* Motif géométrique */}
        <div
          className="absolute inset-0 opacity-10"
          style={{
            backgroundImage:
              "repeating-linear-gradient(45deg,#B8932A 0px,#B8932A 1px,transparent 1px,transparent 36px)",
          }}
        />
        {/* Contenu overlay */}
        <div className="relative z-10 flex flex-col justify-between p-10 h-full">
          <div className="flex items-center gap-2">
            <div className="h-px w-6" style={{ background: "#D4A017" }} />
            <span className="text-xxs text-gold-300 tracking-[0.2em] uppercase font-ui">
              AFB — Portail RH
            </span>
          </div>
          <div className="flex flex-col gap-4">
            <div
              className="w-14 h-14 rounded-xl flex items-center justify-center text-2xl shadow-gold"
              style={{ background: "rgba(255,255,255,0.12)", backdropFilter: "blur(8px)" }}
            >
              {icon}
            </div>
            <h2 className="font-heading text-4xl font-bold text-white leading-tight">
              {title}
            </h2>
            <p className="text-sm text-neutral-300 font-ui max-w-xs leading-relaxed">
              {subtitle}
            </p>
            {/* Bande kente déco */}
            <div
              className="h-1 w-20 rounded-sm mt-2"
              style={{
                background:
                  "repeating-linear-gradient(90deg,#C41E22 0px,#C41E22 8px,#B8932A 8px,#B8932A 16px,#2C2C2C 16px,#2C2C2C 24px,#F5F5F5 24px,#F5F5F5 32px)",
              }}
            />
          </div>

        </div>
      </div>

      {/* ── Colonne droite : formulaire ── */}
      <div className="flex-1 flex flex-col bg-white/85 backdrop-blur-sm">
        {/* Barre d'accent colorée en haut */}
        <div className="h-1 w-full" style={{ background: accentColor }} />

        <div className="flex-1 flex flex-col justify-center px-6 sm:px-12 py-6 w-full gap-4">
          {/* Badge mobile (masqué sur lg) */}
          <div className="flex items-center gap-3 lg:hidden">
            <div
              className="w-10 h-10 rounded-lg flex items-center justify-center text-xl"
              style={{ background: accentColor + "18" }}
            >
              {icon}
            </div>
            <div>
              <p
                className="text-xxs tracking-[0.18em] uppercase font-ui font-semibold"
                style={{ color: accentColor }}
              >
                {badge}
              </p>
              <h1 className="font-heading text-xl font-bold text-primary-500">{title}</h1>
            </div>
          </div>

          {/* Titre desktop */}
          <div className="hidden lg:flex flex-col gap-1">
            <p
              className="text-xxs tracking-[0.18em] uppercase font-ui font-semibold"
              style={{ color: accentColor }}
            >
              {badge}
            </p>
            <h1 className="font-heading text-2xl font-bold text-primary-500">{title}</h1>
            <p className="text-xs text-neutral-500 mt-1">{subtitle}</p>
          </div>

          {/* Séparateur or */}
          <div
            className="h-px w-full"
            style={{
              background:
                "linear-gradient(90deg, " + accentColor + " 0%, transparent 60%)",
            }}
          />

          {/* Slot formulaire */}
          <div className="flex flex-col gap-4">{children}</div>
        </div>
      </div>
    </div>
  );
}
