# Runbook — Recette de performance sur banc séparé

> Objectif : statuer sur les critères d'acceptation de `SLO_absences.md` §5 avec des
> chiffres **opposables** — ce qui impose un générateur de charge sur une machine
> distincte de la stack. Les campagnes du 2026-07-17 (générateur co-localisé) ont
> montré des latences écrasées par la contention CPU locale : elles ne comptent pas.

## Topologie

| Rôle | Machine | Prérequis |
|---|---|---|
| **Stack** (API, PostgreSQL, Keycloak, MinIO) | ce poste — `192.168.88.113` (Wi-Fi) | Docker, stack `docker compose up -d` |
| **Générateur** | toute machine du même LAN | k6 (`winget install GrafanaLabs.k6` / https://k6.io) + le dossier `perf/k6/` |

## 1. Préparation du poste stack (une fois)

```powershell
# Terminal ADMINISTRATEUR — ouvre l'API et Keycloak au LAN (profil privé).
New-NetFirewallRule -DisplayName "Absences perf - API 8080 (recette LAN)" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
New-NetFirewallRule -DisplayName "Absences perf - Keycloak 8180 (recette LAN)" -Direction Inbound -Protocol TCP -LocalPort 8180 -Action Allow -Profile Private
# Rollback après la recette :
# Remove-NetFirewallRule -DisplayName "Absences perf*"
```

Vérification depuis le générateur : `curl http://192.168.88.113:8180/realms/absences/.well-known/openid-configuration` doit répondre 200.

> L'API valide les jetons par JWKS (URL interne au réseau docker) : les jetons émis
> via `192.168.88.113:8180` sont acceptés quel que soit l'hôte appelant.

## 2. Avant CHAQUE run — sur le poste stack

```bash
./perf/preparer_run.sh          # purge k6 + seed 50k + soldes
```

Obligatoire entre deux runs : les fenêtres de dates du scénario sont déterministes
par (VU, itération) — un run sur données résiduelles fabrique des 409 artificiels.

## 3. Les trois runs de la recette — sur le générateur

```bash
cd perf/k6
BASE="-e BASE_URL=http://192.168.88.113:8080 -e KC_TOKEN_URL=http://192.168.88.113:8180/realms/absences/protocol/openid-connect/token"

# 3a. Nominal (référence SLO) — préparer_run avant
k6 run $BASE --summary-export nominal.json parcours-complet.js

# 3b. Soak 1 h 30 (fuites mémoire / dégradation) — préparer_run avant
k6 run $BASE -e DUREE=90m --summary-export soak.json parcours-complet.js

# 3c. Stress 2× nominal (point de rupture) — préparer_run avant
k6 run $BASE -e VUS_AGENTS=100 -e VUS_VALIDATEURS=40 -e VUS_ANALYSTES=10 -e VUS_DRH=6 \
  --summary-export stress.json parcours-complet.js
```

## 4. Pendant les runs — surveiller le poste stack

```bash
docker stats                                   # CPU < 70 % attendu au nominal
docker logs -f absences-api | grep -E "ERROR|ALERTE_"   # ALERTE_DOCUMENT_NON_GENERE = incident
```

## 5. Verdict

Conforme si (cf. `SLO_absences.md` §5) :
1. chaque `etape_*` sous sa cible p95 au run nominal ;
2. erreurs HTTP < 1 % et `erreurs_metier` < 2 % (déjà tenus au banc local : 0,6 % / 1,1 %) ;
3. pas de dérive de latence sur le soak ;
4. le stress 2× passe sans effondrement (le point de rupture est au-delà).

Après le premier run conforme : recaler chaque seuil à `p95_mesuré × 1,3` (§6) pour
détecter les régressions futures sans faux positifs.
