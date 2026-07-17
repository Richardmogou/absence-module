"""Provisionne le realm 'absences' pour les tests k6 : client absences-perf + chaîne perf-*.

Idempotent : réutilise client/utilisateurs existants. N'altère RIEN des comptes réels.
"""
import json
import urllib.request
import urllib.parse

KC = "http://localhost:8180"
REALM = "absences"
PERF_CLIENT_ID = "absences-perf"
PERF_CLIENT_SECRET = "perf-secret-k6-dev"
PERF_PASSWORD = "PerfK6-2026"


def call(method, path, token=None, data=None, form=False):
    url = KC + path
    body = None
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    if data is not None:
        if form:
            body = urllib.parse.urlencode(data).encode()
            headers["Content-Type"] = "application/x-www-form-urlencoded"
        else:
            body = json.dumps(data).encode()
            headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return r.status, json.loads(raw) if raw.strip() else None
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:200]


def admin_token():
    _, r = call("POST", "/realms/master/protocol/openid-connect/token", form=True,
                data={"grant_type": "password", "client_id": "admin-cli",
                      "username": "admin", "password": "admin"})
    return r["access_token"]


T = admin_token()
A = f"/admin/realms/{REALM}"

# ── Client absences-perf ─────────────────────────────────────────────────────
st, clients = call("GET", f"{A}/clients?clientId={PERF_CLIENT_ID}", T)
if clients:
    cid = clients[0]["id"]
    print(f"client {PERF_CLIENT_ID} existe déjà ({cid})")
else:
    st, _ = call("POST", f"{A}/clients", T, {
        "clientId": PERF_CLIENT_ID,
        "protocol": "openid-connect",
        "publicClient": False,
        "secret": PERF_CLIENT_SECRET,
        "directAccessGrantsEnabled": True,
        "standardFlowEnabled": False,
        "serviceAccountsEnabled": False,
        "fullScopeAllowed": True,
    })
    assert st == 201, f"création client: {st}"
    _, clients = call("GET", f"{A}/clients?clientId={PERF_CLIENT_ID}", T)
    cid = clients[0]["id"]
    print(f"client {PERF_CLIENT_ID} créé ({cid})")

# Mappers identiques à absences-front (grade / manager / reseau / unite / roles)
_, existing = call("GET", f"{A}/clients/{cid}/protocol-mappers/models", T)
have = {m["name"] for m in (existing or [])}
mappers = [
    ("realm-roles-mapper", "oidc-usermodel-realm-role-mapper",
     {"multivalued": "true", "claim.name": "realm_access.roles",
      "jsonType.label": "String", "access.token.claim": "true"}),
    ("mapper-manager", "oidc-usermodel-attribute-mapper",
     {"user.attribute": "manager", "claim.name": "manager",
      "jsonType.label": "String", "access.token.claim": "true"}),
    ("mapper-grade", "oidc-usermodel-attribute-mapper",
     {"user.attribute": "grade", "claim.name": "grade",
      "jsonType.label": "String", "access.token.claim": "true"}),
    ("mapper-reseau", "oidc-usermodel-attribute-mapper",
     {"user.attribute": "reseau", "claim.name": "reseau",
      "jsonType.label": "String", "access.token.claim": "true"}),
    ("mapper-unite", "oidc-usermodel-attribute-mapper",
     {"user.attribute": "unite", "claim.name": "unite",
      "jsonType.label": "String", "access.token.claim": "true"}),
]
for name, mapper_type, cfg in mappers:
    if name in have:
        continue
    st, _ = call("POST", f"{A}/clients/{cid}/protocol-mappers/models", T, {
        "name": name, "protocol": "openid-connect",
        "protocolMapper": mapper_type, "config": cfg})
    print(f"mapper {name}: {st}")

# ── Utilisateurs perf-* ──────────────────────────────────────────────────────
def ensure_user(username, grade, attrs=None, roles=None):
    st, found = call("GET", f"{A}/users?username={username}&exact=true", T)
    if found:
        uid = found[0]["id"]
    else:
        payload = {
            "username": username, "enabled": True,
            "email": f"{username}@perf.local", "emailVerified": True,
            "firstName": username.split("-")[1].capitalize(), "lastName": "Perf",
            "attributes": {"grade": [grade], "unite": ["DSI"],
                           "reseau": ["CENTRE_EST"], **(attrs or {})},
            "credentials": [{"type": "password", "value": PERF_PASSWORD,
                             "temporary": False}],
        }
        st, err = call("POST", f"{A}/users", T, payload)
        assert st == 201, f"{username}: {st} {err}"
        _, found = call("GET", f"{A}/users?username={username}&exact=true", T)
        uid = found[0]["id"]
    for role in roles or []:
        _, r = call("GET", f"{A}/roles/{role}", T)
        call("POST", f"{A}/users/{uid}/role-mappings/realm", T, [r])
    return uid


def set_manager(uid, manager_uid):
    _, u = call("GET", f"{A}/users/{uid}", T)
    attrs = u.get("attributes", {})
    attrs["manager"] = [manager_uid]
    call("PUT", f"{A}/users/{uid}", T, {"attributes": attrs})


directeur = ensure_user("perf-directeur1", "CHEF_PROCESSUS")
manager = ensure_user("perf-manager1", "MANAGER")
set_manager(manager, directeur)
backup = ensure_user("perf-backup1", "AGENT")
agent1 = ensure_user("perf-agent1", "AGENT")
agent2 = ensure_user("perf-agent2", "AGENT")
for a in (agent1, agent2, backup):
    set_manager(a, manager)
analyste = ensure_user("perf-analyste1", "AGENT", roles=["ANALYSTE_RH"])
drh = ensure_user("perf-drh1", "CHEF_PROCESSUS", roles=["DRH"])

print(json.dumps({
    "agent1": agent1, "agent2": agent2, "backup": backup,
    "manager": manager, "directeur": directeur,
    "analyste": analyste, "drh": drh,
}, indent=2))
