# Tests de performance k6 — Module Absences

Scénario de charge **multi-rôles** du parcours complet d'une demande de congé annuel :
`création → soumission → validations d'étapes → instruction Analyste RH → validation DRH`.

## Prérequis

1. **k6** installé — https://k6.io/docs/get-started/installation/
2. **Stack lancée** (API, PostgreSQL, Keycloak, MinIO) : `docker compose up -d`
3. **Comptes Keycloak** provisionnés pour chaque rôle (voir ci-dessous).
4. **Base peuplée** à volume réaliste (script de seed séparé).
5. Générateur de charge sur une **machine distincte** de l'API pour des mesures fiables.

## Configuration

Éditer le bloc `COMPTES` dans `parcours-complet.js` avec vos identités réelles.
Contrainte clé : la **hiérarchie et le réseau** de ces comptes doivent être cohérents
avec les demandes créées, sinon les étapes `HIERARCHIQUE` / `ROLE_FIXE_SCOPE_RESEAU`
ne pourront pas être validées (les demandes n'apparaîtront pas dans `/a-valider`).

| Rôle Keycloak requis | Usage dans le scénario |
|----------------------|------------------------|
| _(agent)_ | Création + soumission |
| N+1, N+2, `CHEF_PROCESSUS` | Validation des étapes intermédiaires |
| `ANALYSTE_RH` | Instruction |
| `DRH` | Validation finale |

> Le compte `backup` de chaque agent doit être un `sub` Keycloak valide (étape Back-up du circuit Agent).

## Lancement

```bash
# Exécution nominale
k6 run parcours-complet.js

# Surcharge de paramètres
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e KC_TOKEN_URL=http://localhost:8180/realms/absences/protocol/openid-connect/token \
  -e VUS_AGENTS=100 -e VUS_VALIDATEURS=40 -e DUREE=15m \
  parcours-complet.js

# Sortie vers InfluxDB/Grafana pour visualisation temps réel
k6 run --out influxdb=http://localhost:8086/k6 parcours-complet.js
```

## Variables d'environnement

| Variable | Défaut | Description |
|----------|--------|-------------|
| `BASE_URL` | `http://localhost:8080` | URL de l'API |
| `KC_TOKEN_URL` | `…:8180/realms/absences/…/token` | Token endpoint Keycloak |
| `CLIENT_ID` / `CLIENT_SECRET` | `absences-front` / _(dev)_ | Client OIDC pour le grant password |
| `VUS_AGENTS` | `50` | Utilisateurs virtuels créateurs |
| `VUS_VALIDATEURS` | `20` | Validateurs d'étapes |
| `VUS_ANALYSTES` | `5` | Analystes RH |
| `VUS_DRH` | `3` | DRH |
| `DUREE` | `10m` | Durée du palier de charge |

## Interprétation

Métriques métier personnalisées (latence par étape) : `etape_creer`, `etape_soumettre`,
`etape_valider`, `etape_instruire`, `etape_valider_drh`, `etape_a_valider`.
Compteurs : `demandes_creees`, `demandes_validees_drh`. Les **seuils (SLO)** sont
définis dans `options.thresholds` — ajustez-les à vos objectifs.

En parallèle du run, surveillez : `actuator/metrics` (JVM, GC, pool Hikari),
`pg_stat_statements` (slow queries), CPU/RAM des conteneurs.

## Points de vigilance connus (cf. audit)

- `GET /{id}` et le débit DRH font des **appels HTTP sortants vers Keycloak** (résolution
  de noms / hiérarchie) → suspects de latence sous charge.
- Retirer les `System.out.println` de `findById` **avant** tout test sérieux.
- `GET /a-valider` et `findAll` : à éprouver avec **gros volume** de données.
