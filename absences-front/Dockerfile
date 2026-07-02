# ─────────────────────────────────────────────────────────────────────────
# Dockerfile — absences-front (Next.js 15)
# Build multi-stage : install deps, build, run standalone
# ─────────────────────────────────────────────────────────────────────────

# ── Stage 1 : installation des dépendances ───────────────────────────────
FROM node:20-alpine AS deps

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --frozen-lockfile

# ── Stage 2 : build Next.js ──────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Variables d'environnement nécessaires au build
ARG NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_SSO_LOGIN_URL
ARG NEXT_PUBLIC_MINIO_URL
ARG API_INTERNAL_URL

ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_SSO_LOGIN_URL=$NEXT_PUBLIC_SSO_LOGIN_URL
ENV NEXT_PUBLIC_MINIO_URL=$NEXT_PUBLIC_MINIO_URL
ENV API_INTERNAL_URL=$API_INTERNAL_URL

RUN npm run build

# ── Stage 3 : image runtime standalone ───────────────────────────────────
FROM node:20-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production

# Créer un utilisateur non-root
RUN addgroup -S nextjs && adduser -S nextjs -G nextjs
USER nextjs

# Copier uniquement les fichiers nécessaires au runtime
COPY --from=builder --chown=nextjs:nextjs /app/public ./public
COPY --from=builder --chown=nextjs:nextjs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nextjs /app/.next/static ./.next/static

EXPOSE 3000

ENV PORT=3000
ENV HOSTNAME=0.0.0.0

CMD ["node", "server.js"]
