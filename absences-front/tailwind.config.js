/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [ "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
 
    // Or if using `src` directory:
    "./src/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        display: ['Cormorant Garamond', 'Georgia', 'serif'],
        sans:    ['Montserrat', 'Inter', 'sans-serif'],
        heading: ['Cormorant Garamond', 'Georgia', 'serif'],
        ui:      ['Montserrat', 'Inter', 'sans-serif'],
      },
      colors: {

        /* ────────────────────────────────────────────
         * Couleurs sémantiques rapides
         * ──────────────────────────────────────────── */
        'dark':  '#2C2C2C',   // Noir profond  — texte principal, fonds sombres
        'light': '#F5F5F5',   // Blanc cassé   — fonds clairs, surfaces
        'silver':'#C8C8C8',   // Argent        — bordures, séparateurs, fonds secondaires

        /* ────────────────────────────────────────────
         * PRIMARY — Noir profond #2C2C2C
         * Usage : fonds principaux, navbar, texte corps,
         *         tout ce qui doit ancrer la page
         * ──────────────────────────────────────────── */
        primary: {
          '50':  '#F6F6F6',
          '100': '#E8E8E8',
          '200': '#D1D1D1',
          '300': '#ABABAB',
          '400': '#808080',
          '500': '#2C2C2C',  // ← Noir profond AFB  (référence principale)
          '600': '#242424',
          '700': '#1C1C1C',
          '800': '#141414',
          '900': '#0A0A0A',
          '950': '#050505',
        },

        /* ────────────────────────────────────────────
         * SECONDARY — Rouge AFB #C41E22
         * Usage : CTA, boutons d'action, accents forts,
         *         logo, liens actifs
         * ──────────────────────────────────────────── */
        secondary: {
          '50':  '#FFF1F1',
          '100': '#FFD6D6',
          '200': '#FFADAD',
          '300': '#FF7A7A',
          '400': '#E03A3E',
          '500': '#C41E22',  // ← Rouge logo AFB  (référence principale)
          '600': '#A8181C',  // Rouge foncé #8B1A1C ≈ '700'
          '700': '#8B1A1C',  // ← Rouge foncé AFB exact
          '800': '#6E0D10',
          '900': '#52090B',
          '950': '#380507',
        },

        /* ────────────────────────────────────────────
         * ACCENT — Bleu nuit #1A1A2E
         * Usage : navbar principale, footer, panneaux
         *         sombres, en-têtes de section premium
         * ──────────────────────────────────────────── */
        accent: {
          '50':  '#EEEEF5',
          '100': '#D2D2E8',
          '200': '#ABABD4',
          '300': '#7A7AB8',
          '400': '#4A4A8A',
          '500': '#1A1A2E',  // ← Bleu nuit AFB  (référence principale)
          '600': '#141426',
          '700': '#0F0F1E',
          '800': '#0A0A16',
          '900': '#06060E',
          '950': '#030307',
        },

        /* ────────────────────────────────────────────
         * GOLD — Or prestige
         * Usage : bordures de prestige, titres serif,
         *         badges, séparateurs décoratifs
         * ──────────────────────────────────────────── */
        gold: {
          '50':  '#FDFBF0',
          '100': '#FAF3D0',
          '200': '#F4E49A',
          '300': '#EDD05D',
          '400': '#D4A017',
          '500': '#B8932A',  // ← Or prestige AFB
          '600': '#96751A',
          '700': '#74590F',
          '800': '#523F09',
          '900': '#332703',
          '950': '#1A1300',
        },

        /* ────────────────────────────────────────────
         * NEUTRAL — Gris argentés AFB
         * Usage : fonds secondaires, cartes, inputs,
         *         texte tertiaire, icônes désactivées
         * Basé sur l'argent #C8C8C8 du fond du logo
         * ──────────────────────────────────────────── */
        neutral: {
          '50':  '#F5F5F5',  // ← Blanc cassé AFB exact
          '100': '#EBEBEB',
          '200': '#D8D8D8',
          '300': '#C8C8C8',  // ← Argent AFB exact
          '400': '#ABABAB',
          '500': '#8A8A8A',
          '600': '#6D6D6D',
          '700': '#4F4F4F',
          '800': '#3A3A3A',
          '900': '#2C2C2C',  // = dark / primary-500
          '950': '#1A1A1A',
        },

      },

      /* ────────────────────────────────────────────
       * Ombres prestige
       * ──────────────────────────────────────────── */
      boxShadow: {
        'gold-sm': '0 1px 3px 0 rgba(184,147,42,0.15)',
        'gold':    '0 4px 16px 0 rgba(184,147,42,0.20)',
        'gold-lg': '0 8px 32px 0 rgba(184,147,42,0.28)',
        'red':     '0 4px 16px 0 rgba(196,30,34,0.25)',
        'card':    '0 2px 12px 0 rgba(44,44,44,0.18)',
        'modal':   '0 16px 64px 0 rgba(44,44,44,0.35)',
      },

      /* ────────────────────────────────────────────
       * Rayon de bordure — sobre et institutionnel
       * ──────────────────────────────────────────── */
      borderRadius: {
        'none': '0px',
        'sm':   '2px',
        DEFAULT:'4px',
        'md':   '6px',
        'lg':   '8px',
        'xl':   '12px',
      },

      /* ────────────────────────────────────────────
       * Espacement — grille 8 pt
       * ──────────────────────────────────────────── */
      spacing: {
        '18': '4.5rem',
        '22': '5.5rem',
        '26': '6.5rem',
        '30': '7.5rem',
        '72': '18rem',
        '84': '21rem',
        '96': '24rem',
      },

      /* ────────────────────────────────────────────
       * Échelle typographique
       * ──────────────────────────────────────────── */
      fontSize: {
        'xxs':  ['0.625rem', { lineHeight: '1rem',   letterSpacing: '0.08em' }],
        'xs':   ['0.75rem',  { lineHeight: '1.25rem', letterSpacing: '0.06em' }],
        'sm':   ['0.875rem', { lineHeight: '1.5rem'  }],
        'base': ['1rem',     { lineHeight: '1.75rem' }],
        'lg':   ['1.125rem', { lineHeight: '1.875rem'}],
        'xl':   ['1.25rem',  { lineHeight: '2rem'    }],
        '2xl':  ['1.5rem',   { lineHeight: '2.25rem' }],
        '3xl':  ['2rem',     { lineHeight: '2.5rem'  }],
        '4xl':  ['2.5rem',   { lineHeight: '3rem'    }],
        '5xl':  ['3.25rem',  { lineHeight: '3.75rem' }],
        '6xl':  ['4rem',     { lineHeight: '4.5rem'  }],
        'hero': ['5rem',     { lineHeight: '5.5rem', letterSpacing: '-0.02em' }],
      },

      /* ────────────────────────────────────────────
       * Largeurs max
       * ──────────────────────────────────────────── */
      maxWidth: {
        'prose-narrow': '45ch',
        'prose':        '65ch',
        'prose-wide':   '80ch',
        'container':    '1280px',
        'wide':         '1440px',
      },

      /* ────────────────────────────────────────────
       * Transitions
       * ──────────────────────────────────────────── */
      transitionDuration: {
        '250': '250ms',
        '350': '350ms',
        '450': '450ms',
      },
      transitionTimingFunction: {
        'smooth': 'cubic-bezier(0.4, 0, 0.2, 1)',
        'out':    'cubic-bezier(0, 0, 0.2, 1)',
      },

      /* ────────────────────────────────────────────
       * Dégradés réutilisables
       * ──────────────────────────────────────────── */
      backgroundImage: {
        'hero-gradient':  'linear-gradient(135deg, #2C2C2C 0%, #1A1A2E 60%, #2C2C2C 100%)',
        'dark-gradient':  'linear-gradient(180deg, #2C2C2C 0%, #1A1A2E 100%)',
        'silver-gradient':'linear-gradient(180deg, #F5F5F5 0%, #C8C8C8 100%)',
        'gold-line':      'linear-gradient(90deg, transparent 0%, #B8932A 50%, transparent 100%)',
        'kente-stripe':   'repeating-linear-gradient(90deg, #C41E22 0px, #C41E22 8px, #B8932A 8px, #B8932A 16px, #2C2C2C 16px, #2C2C2C 24px, #F5F5F5 24px, #F5F5F5 32px)',
        'red-shine':      'linear-gradient(135deg, #C41E22 0%, #8B1A1C 100%)',
        'gold-shine':     'linear-gradient(135deg, #D4A017 0%, #B8932A 50%, #96751A 100%)',
        'card-light':     'linear-gradient(180deg, #FFFFFF 0%, #F5F5F5 100%)',
        'card-dark':      'linear-gradient(180deg, #2C2C2C 0%, #1A1A2E 100%)',
        'overlay-dark':   'linear-gradient(135deg, rgba(44,44,44,0.88) 0%, rgba(26,26,46,0.70) 50%, rgba(44,44,44,0.92) 100%)',
      },
    },
  },
  plugins: [],
}