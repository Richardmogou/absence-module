/**
 * ============================================================================
 *  Test de performance — Module Absences
 *  Parcours complet CONGE_ANNUEL : création → soumission → validations d'étapes
 *  → instruction Analyste RH → validation DRH → VALIDEE
 * ============================================================================
 *
 *  Modèle : 4 populations concurrentes (multi-rôles) qui pilotent le workflow
 *  via la file `/a-valider`, comme en usage réel :
 *    1. AGENTS            → créent + soumettent des demandes (charge d'écriture)
 *    2. VALIDATEURS ÉTAPE → dépilent /a-valider et valident les étapes intermédiaires
 *    3. ANALYSTES RH      → instruisent les demandes EN_INSTRUCTION_ANALYSTE_RH
 *    4. DRH               → valident les demandes EN_VALIDATION_DRH
 *
 *  On choisit CONGE_ANNUEL car il ne nécessite PAS de justificatif (pas d'upload
 *  multipart), ce qui isole la performance du cœur workflow.
 *
 *  PRÉREQUIS (voir README) :
 *    - Comptes Keycloak provisionnés pour chaque rôle, avec une hiérarchie et un
 *      réseau cohérents avec le jeu de données seedé.
 *    - Base peuplée à volume réaliste (script SQL de seed séparé).
 *    - k6 >= 0.45 :  https://k6.io/docs/get-started/installation/
 *
 *  LANCEMENT :
 *    k6 run parcours-complet.js
 *    # override d'une variable :
 *    k6 run -e BASE_URL=http://api:8080 -e VUS_AGENTS=100 parcours-complet.js
 * ============================================================================
 */

import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import encoding from 'k6/encoding';

// ── Configuration (surchargeable par -e VAR=valeur) ─────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const KC_TOKEN_URL =
  __ENV.KC_TOKEN_URL ||
  'http://localhost:8180/realms/absences/protocol/openid-connect/token';
// Client dédié perf (grant password activé) — absences-front ne l'autorise pas.
const CLIENT_ID = __ENV.CLIENT_ID || 'absences-perf';
const CLIENT_SECRET = __ENV.CLIENT_SECRET || 'perf-secret-k6-dev';

const VUS_AGENTS = Number(__ENV.VUS_AGENTS || 50);
const VUS_VALIDATEURS = Number(__ENV.VUS_VALIDATEURS || 20);
const VUS_ANALYSTES = Number(__ENV.VUS_ANALYSTES || 5);
const VUS_DRH = Number(__ENV.VUS_DRH || 3);
const DUREE = __ENV.DUREE || '10m';

// ── Comptes de test par rôle ────────────────────────────────────────────────
// ⚠️ À REMPLACER par vos comptes Keycloak réels. Le pool permet de répartir la
//    charge sur plusieurs identités (évite le rate-limiting Keycloak / sessions).
// Chaîne perf-* provisionnée dans Keycloak (realm absences) le 2026-07-17 :
// perf-agent1/2 --manager--> perf-manager1 --manager--> perf-directeur1.
// perf-backup1 est le back-up désigné des deux agents (étape BACKUP du circuit).
const PERF_PASSWORD = 'PerfK6-2026';
const COMPTES = {
  agents: new SharedArray('agents', () => [
    { username: 'perf-agent1', password: PERF_PASSWORD, backup: '1373e31b-907b-4477-9915-f402ffbde9f5' },
    { username: 'perf-agent2', password: PERF_PASSWORD, backup: '1373e31b-907b-4477-9915-f402ffbde9f5' },
  ]),
  validateurs: new SharedArray('validateurs', () => [
    { username: 'perf-backup1', password: PERF_PASSWORD },
    { username: 'perf-manager1', password: PERF_PASSWORD },
    { username: 'perf-directeur1', password: PERF_PASSWORD },
  ]),
  analystes: new SharedArray('analystes', () => [
    { username: 'perf-analyste1', password: PERF_PASSWORD },
  ]),
  drh: new SharedArray('drh', () => [
    { username: 'perf-drh1', password: PERF_PASSWORD },
  ]),
};

// ── Métriques personnalisées (latence par étape métier) ─────────────────────
const dCreer = new Trend('etape_creer', true);
const dSoumettre = new Trend('etape_soumettre', true);
const dValiderEtape = new Trend('etape_valider', true);
const dInstruire = new Trend('etape_instruire', true);
const dValiderDrh = new Trend('etape_valider_drh', true);
const dAValider = new Trend('etape_a_valider', true);

const cDemandesCreees = new Counter('demandes_creees');
const cDemandesValidees = new Counter('demandes_validees_drh');
const rErreursMetier = new Rate('erreurs_metier'); // 4xx/5xx inattendus

// ── Options k6 : 4 scénarios concurrents + seuils (SLO) ─────────────────────
export const options = {
  scenarios: {
    agents: {
      executor: 'ramping-vus',
      exec: 'flowAgent',
      startVUs: 0,
      stages: [
        { duration: '1m', target: VUS_AGENTS },
        { duration: DUREE, target: VUS_AGENTS },
        { duration: '30s', target: 0 },
      ],
    },
    validateurs: {
      executor: 'constant-vus',
      exec: 'flowValidateurEtape',
      vus: VUS_VALIDATEURS,
      duration: DUREE,
      startTime: '20s',
    },
    analystes: {
      executor: 'constant-vus',
      exec: 'flowAnalysteRh',
      vus: VUS_ANALYSTES,
      duration: DUREE,
      startTime: '40s',
    },
    drh: {
      executor: 'constant-vus',
      exec: 'flowDrh',
      vus: VUS_DRH,
      duration: DUREE,
      startTime: '60s',
    },
  },
  thresholds: {
    // SLO globaux — à ajuster selon vos objectifs (étape 1 de la démarche)
    http_req_failed: ['rate<0.01'],
    'etape_creer': ['p(95)<500'],
    'etape_soumettre': ['p(95)<1200'], // + lourd : résolution hiérarchique Keycloak
    'etape_valider': ['p(95)<600'],
    'etape_instruire': ['p(95)<500'],
    'etape_valider_drh': ['p(95)<1500'], // + débit solde + génération document
    'etape_a_valider': ['p(95)<800'],
    'erreurs_metier': ['rate<0.02'],
  },
};

// ── Gestion des tokens (cache par VU, refresh avant expiration) ─────────────
// État module-level = persistant au sein d'un même VU entre itérations.
let tokenCache = null; // { access_token, exp }

function decodeExp(jwt) {
  try {
    const payload = JSON.parse(encoding.b64decode(jwt.split('.')[1], 'rawurl', 's'));
    return payload.exp; // epoch seconds
  } catch (_) {
    return Math.floor(Date.now() / 1000) + 60;
  }
}

function getToken(compte) {
  const now = Math.floor(Date.now() / 1000);
  if (tokenCache && tokenCache.user === compte.username && tokenCache.exp - 15 > now) {
    return tokenCache.access_token;
  }
  const res = http.post(
    KC_TOKEN_URL,
    {
      grant_type: 'password',
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      username: compte.username,
      password: compte.password,
    },
    { tags: { name: 'kc_token' } }
  );
  if (res.status !== 200) {
    fail(`Auth Keycloak échouée pour ${compte.username} : ${res.status} ${res.body}`);
  }
  const token = res.json('access_token');
  tokenCache = { user: compte.username, access_token: token, exp: decodeExp(token) };
  return token;
}

function authHeaders(token, name) {
  return {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    tags: { name },
  };
}

// Compte ÉPINGLÉ par VU : le cache de token (une entrée par VU) devient efficace.
// La rotation par itération (__VU + __ITER) forçait un grant password Keycloak à
// quasi chaque requête — le hachage saturait le CPU et polluait les latences API.
function pick(pool) {
  return pool[__VU % pool.length];
}

// ── Génère une période de congé annuel valide (>= 12 jours calendaires) ─────
// Fenêtre UNIQUE par (VU, itération) : un même agent réutilisait sa période à
// chaque itération et se chevauchait lui-même dès la 2e soumission
// (CHEVAUCHEMENT_DATES). Stride 10 000 jours par VU, 25 jours par itération :
// aucune paire (VU, ITER) ne produit deux fenêtres de 21 jours qui se touchent.
function periodeCongeAnnuel() {
  const debut = new Date();
  debut.setDate(debut.getDate() + 30 + (__VU % 1000) * 10000 + __ITER * 25);
  const fin = new Date(debut);
  fin.setDate(fin.getDate() + 20);
  const iso = (d) => d.toISOString().slice(0, 10);
  return { dateDebut: iso(debut), dateFin: iso(fin) };
}

// ── SCÉNARIO 1 : Agent — création + soumission ──────────────────────────────
export function flowAgent() {
  const compte = pick(COMPTES.agents);
  const token = getToken(compte);
  const { dateDebut, dateFin } = periodeCongeAnnuel();

  // 1) Création
  const payload = JSON.stringify({
    type: 'CONGE_ANNUEL',
    dateDebut,
    dateFin,
    backupIdentifiantExterne: compte.backup,
  });
  let res = http.post(`${BASE_URL}/api/v5/demandes`, payload, authHeaders(token, 'POST /demandes'));
  dCreer.add(res.timings.duration);
  const okCreer = check(res, { 'création 201': (r) => r.status === 201 });
  rErreursMetier.add(!okCreer);
  if (!okCreer) return;

  const id = res.json('id');
  cDemandesCreees.add(1);

  // 2) Soumission — uniquement si la création a laissé un BROUILLON. L'API actuelle
  // enclenche le circuit dès la création (EN_VALIDATION_ETAPE) : re-soumettre entre
  // alors en course avec les validateurs qui dépilent déjà (conflits d'état factices).
  if (res.json('statut') === 'BROUILLON') {
    res = http.post(
      `${BASE_URL}/api/v5/demandes/${id}/soumettre?confirmDoublon=true`,
      null,
      authHeaders(token, 'POST /{id}/soumettre')
    );
    dSoumettre.add(res.timings.duration);
    const okSoum = check(res, { 'soumission 202': (r) => r.status === 202 });
    rErreursMetier.add(!okSoum);
  }

  // Lecture "mes demandes" (charge de lecture réaliste)
  http.get(`${BASE_URL}/api/v5/demandes/moi`, authHeaders(token, 'GET /moi'));

  sleep(1 + Math.random());
}

// ── Worker générique : dépile /a-valider et applique une action ─────────────
function traiterFile(compte, statutCible, action) {
  const token = getToken(compte);

  const res = http.get(`${BASE_URL}/api/v5/demandes/a-valider`, authHeaders(token, 'GET /a-valider'));
  dAValider.add(res.timings.duration);
  if (!check(res, { 'a-valider 200': (r) => r.status === 200 })) {
    rErreursMetier.add(true);
    sleep(1);
    return;
  }

  const demandes = res.json();
  // Cible ALÉATOIRE dans la file, pas la tête : tous les workers prenaient la même
  // demande (thundering herd) — un seul gagnait, les autres récoltaient un conflit
  // d'état. Mesuré au run nominal : 78 % d'échecs artificiels sur validation étape.
  const cibles = (demandes || []).filter((d) => d.statut === statutCible);
  if (cibles.length === 0) {
    sleep(0.5 + Math.random()); // file vide → petite attente
    return;
  }
  const cible = cibles[Math.floor(Math.random() * cibles.length)];
  action(token, cible.id);
}

// ── SCÉNARIO 2 : Validateur d'étape intermédiaire ───────────────────────────
export function flowValidateurEtape() {
  const compte = pick(COMPTES.validateurs);
  traiterFile(compte, 'EN_VALIDATION_ETAPE', (token, id) => {
    const body = JSON.stringify({ decision: 'VALIDER', motif: 'Validation perf-test' });
    const res = http.post(
      `${BASE_URL}/api/v5/demandes/${id}/validation`,
      body,
      authHeaders(token, 'POST /{id}/validation')
    );
    dValiderEtape.add(res.timings.duration);
    rErreursMetier.add(!check(res, { 'validation étape 200': (r) => r.status === 200 }));
  });
  sleep(0.5);
}

// ── SCÉNARIO 3 : Analyste RH — instruction ──────────────────────────────────
export function flowAnalysteRh() {
  const compte = pick(COMPTES.analystes);
  traiterFile(compte, 'EN_INSTRUCTION_ANALYSTE_RH', (token, id) => {
    const res = http.post(
      `${BASE_URL}/api/v5/demandes/${id}/instruction`,
      null,
      authHeaders(token, 'POST /{id}/instruction')
    );
    dInstruire.add(res.timings.duration);
    rErreursMetier.add(!check(res, { 'instruction 200': (r) => r.status === 200 }));
  });
  sleep(0.5);
}

// ── SCÉNARIO 4 : DRH — validation finale ────────────────────────────────────
export function flowDrh() {
  const compte = pick(COMPTES.drh);
  traiterFile(compte, 'EN_VALIDATION_DRH', (token, id) => {
    const body = JSON.stringify({ decision: 'VALIDER', motif: 'Validation DRH perf-test' });
    const res = http.post(
      `${BASE_URL}/api/v5/demandes/${id}/validation-drh`,
      body,
      authHeaders(token, 'POST /{id}/validation-drh')
    );
    dValiderDrh.add(res.timings.duration);
    const ok = check(res, { 'validation DRH 200': (r) => r.status === 200 });
    rErreursMetier.add(!ok);
    if (ok) cDemandesValidees.add(1);
  });
  sleep(0.5);
}
