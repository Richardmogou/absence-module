import { auth } from "@/auth";
import { NextResponse } from "next/server";

// Routes publiques (sans connexion)
const PUBLIC_PATHS = ["/", "/connexion", "/api/auth", "/non-autorise"];

// Routes protégées par rôle : seuls les utilisateurs ayant AU MOINS UN des rôles listés peuvent y accéder
const PROTECTED_ROUTES: { prefix: string; roles: string[] }[] = [
  { prefix: "/admin",          roles: ["ADMIN_RH"]                    },
  { prefix: "/analyste-rh",    roles: ["ANALYSTE_RH"]                 },
  { prefix: "/drh",            roles: ["DRH"]                         },
  { prefix: "/validation-file",roles: ["ANALYSTE_RH", "DRH"]          },
];

export default auth((req) => {
  const { nextUrl, auth: session } = req;
  const pathname = nextUrl.pathname;

  const isPublic = PUBLIC_PATHS.some((p) => pathname.startsWith(p));

  // 1. Non connecté → page de connexion
  if (!session && !isPublic) {
    const loginUrl = new URL("/connexion", nextUrl.origin);
    loginUrl.searchParams.set("callbackUrl", pathname);
    return NextResponse.redirect(loginUrl);
  }

  if (!session) return NextResponse.next();

  // 2. Vérification des rôles pour les routes protégées
  const roles: string[] = session.roles ?? [];

  for (const { prefix, roles: required } of PROTECTED_ROUTES) {
    if (pathname.startsWith(prefix)) {
      const hasRole = required.some((r) => roles.includes(r));
      if (!hasRole) {
        return NextResponse.redirect(new URL("/non-autorise", nextUrl.origin));
      }
    }
  }

  return NextResponse.next();
});

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|api/v5|.*\\.png$|.*\\.jpg$|.*\\.svg$).*)"],
};
