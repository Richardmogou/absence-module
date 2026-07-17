# Grille de SLO — Tests de performance Module Absences

> Objectifs de niveau de service **par endpoint**, servant de référence aux seuils
> des tests k6 (`perf/k6/parcours-complet.js`) et à l'acceptation en recette.
> Les chiffres sont des **hypothèses de départ** — à ajuster à votre volumétrie réelle.

## 1. Hypothèses de dimensionnement

| Paramètre | Valeur retenue | À adapter selon |
|-----------|----------------|-----------------|
| Effectif total | 5 000 agents | RH |
| Pic d'activité | Campagne de congés (fin d'exercice) | Calendrier métier |
| Utilisateurs actifs simultanés au pic | ~500 (10 % de l'effectif) | Analytics / logs |
| Débit agrégé cible au pic | ~150 req/s | Mesure réelle |
| Répartition lecture / écriture | 80 % / 20 % | Profil d'usage |
| Fenêtre de mesure des SLO | palier de charge de 10-15 min | — |
| Disponibilité cible | 99,9 % (budget d'erreur 0,1 %) | Politique SI |

> **Principe :** un SLO se mesure sur un **percentile** (p95/p99), jamais sur la moyenne
> (qui masque les cas lents). La latence est mesurée **côté serveur** (hors réseau client).

## 2. Grille de SLO par endpoint

Catégories : **L** = lecture · **É** = écriture · **Ext** = fait un appel HTTP sortant (Keycloak/MinIO) → naturellement plus lent.

| Endpoint | Méth. | Cat. | % trafic | Débit cible (req/s) | p50 | p95 | p99 | Budget erreur |
|----------|:-----:|:----:|:--------:|:-------------------:|:---:|:---:|:---:|:-------------:|
| `/api/v5/demandes/moi` | GET | L | 25 % | 38 | 80 ms | 300 ms | 600 ms | 0,1 % |
| `/api/v5/demandes/moi/solde` | GET | L | 10 % | 15 | 50 ms | 200 ms | 400 ms | 0,1 % |
| `/api/v5/demandes/a-valider` | GET | L | 15 % | 23 | 120 ms | 500 ms | 900 ms | 0,1 % |
| `/api/v5/demandes/{id}` | GET | L·Ext | 12 % | 18 | 150 ms | 500 ms | 1 000 ms | 0,2 % |
| `/api/v5/demandes` (liste) | GET | L | 8 % | 12 | 150 ms | 600 ms | 1 200 ms | 0,2 % |
| `/api/v5/demandes/{id}/preview` | GET | L·Ext | 5 % | 8 | 150 ms | 600 ms | 1 200 ms | 0,2 % |
| `/api/v5/demandes` (création) | POST | É | 6 % | 9 | 120 ms | 500 ms | 900 ms | 0,5 % |
| `/api/v5/demandes/{id}/soumettre` | POST | É·Ext | 5 % | 8 | 300 ms | 1 200 ms | 2 000 ms | 0,5 % |
| `/api/v5/demandes/{id}/validation` | POST | É | 5 % | 8 | 180 ms | 600 ms | 1 000 ms | 0,5 % |
| `/api/v5/demandes/{id}/instruction` | POST | É | 2 % | 3 | 120 ms | 500 ms | 900 ms | 0,5 % |
| `/api/v5/demandes/{id}/validation-drh` | POST | É·Ext | 2 % | 3 | 400 ms | 1 500 ms | 2 500 ms | 0,5 % |
| `/api/v5/demandes/{id}/justificatif` | POST | É·Ext | 3 % | 5 | 300 ms | 1 500 ms | 3 000 ms | 0,5 % |
| **Global (toutes routes)** | — | — | 100 % | **~150** | 120 ms | **< 800 ms** | 1 500 ms | **< 1 %** |

## 3. Justification des endpoints « lourds »

- **`GET /{id}`** (L·Ext) — `toResponse` appelle `hierarchicalChainResolver.resolveNomComplet` →
  **requête HTTP vers l'API admin Keycloak** à chaque lecture unitaire. La cible p95 (500 ms)
  intègre cette latence réseau. Si mesuré au-delà : mettre en cache la résolution des noms.
- **`POST /{id}/soumettre`** (É·Ext) — résolution hiérarchique (appels Keycloak) + détermination
  de circuit + création des snapshots en base. Chemin d'écriture le plus coûteux hors DRH.
- **`POST /{id}/validation-drh`** (É·Ext) — **débit du solde + génération du document PDF de mise
  en congé + upload MinIO**. La cible p99 (2,5 s) reflète la génération documentaire.
- **`POST /{id}/justificatif`** (É·Ext) — **upload multipart vers MinIO** ; la latence dépend de
  la taille du fichier (budget p99 large à 3 s pour des pièces jointes réalistes).
- **`GET /a-valider`** et **`GET /demandes`** — requêtes avec jointures + agrégations en mémoire
  (`toResponseBatch`) ; sensibles au **volume** → à valider sur la base seedée à pleine échelle.

## 4. Correspondance avec les seuils k6

Les métriques métier du script k6 (`etape_*`) correspondent à la colonne **p95** ci-dessus.
À reporter dans `options.thresholds` de `parcours-complet.js` :

```js
thresholds: {
  http_req_failed:      ['rate<0.01'],   // budget global < 1 %
  'etape_creer':        ['p(95)<500'],
  'etape_soumettre':    ['p(95)<1200'],
  'etape_valider':      ['p(95)<600'],
  'etape_instruire':    ['p(95)<500'],
  'etape_valider_drh':  ['p(95)<1500'],
  'etape_a_valider':    ['p(95)<800'],   // GET /a-valider, marge vs cible 500 sous charge
  'erreurs_metier':     ['rate<0.02'],
}
```

## 5. Critères d'acceptation (recette de performance)

Le module est **conforme** si, sur le palier de charge nominal (500 VUs, 10 min) :
1. Chaque endpoint respecte sa cible **p95** ci-dessus ;
2. Le taux d'erreur global reste **< 1 %** ;
3. Aucune **dégradation** de la latence sur un soak test de 1-2 h (pas de fuite mémoire) ;
4. Le point de rupture (stress test) est **> 2×** la charge nominale.

Ressources serveur à surveiller en parallèle (sinon le SLO est « vert » mais la marge nulle) :
CPU < 70 %, pool Hikari non saturé, GC pause p99 < 200 ms, pas de slow query PostgreSQL
(`pg_stat_statements`) au-dessus de 100 ms récurrent.

## 6. Révision

Ces valeurs sont un **point de départ**. Après le premier run de référence (baseline),
réajuster chaque cible à `baseline_p95 × 1,3` (marge de 30 %) pour détecter les régressions
sans faux positifs, et re-caler les hypothèses de dimensionnement (§1) sur les mesures réelles.
