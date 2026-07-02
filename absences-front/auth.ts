import NextAuth from "next-auth";
import Keycloak from "next-auth/providers/keycloak";

function extractRoles(accessToken: string): string[] {
  try {
    const payload = JSON.parse(
      Buffer.from(accessToken.split(".")[1], "base64url").toString()
    );
    return (payload?.realm_access?.roles as string[]) ?? [];
  } catch {
    return [];
  }
}

async function refreshKeycloakToken(refreshToken: string): Promise<{
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  idToken?: string;
  roles: string[];
} | null> {
  const issuer = process.env.AUTH_KEYCLOAK_ISSUER!;
  const tokenUrl = `${issuer}/protocol/openid-connect/token`;
  try {
    const res = await fetch(tokenUrl, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type:    "refresh_token",
        client_id:     process.env.AUTH_KEYCLOAK_ID!,
        client_secret: process.env.AUTH_KEYCLOAK_SECRET!,
        refresh_token: refreshToken,
      }),
    });
    if (!res.ok) return null;
    const data = await res.json();
    return {
      accessToken:  data.access_token,
      refreshToken: data.refresh_token ?? refreshToken,
      expiresAt:    Math.floor(Date.now() / 1000) + (data.expires_in as number),
      idToken:      data.id_token,
      roles:        data.access_token ? extractRoles(data.access_token) : [],
    };
  } catch {
    return null;
  }
}

export const { handlers, signIn, signOut, auth } = NextAuth({
  trustHost: true,
  providers: [
    Keycloak({
      clientId:     process.env.AUTH_KEYCLOAK_ID!,
      clientSecret: process.env.AUTH_KEYCLOAK_SECRET!,
      issuer:       process.env.AUTH_KEYCLOAK_ISSUER!,
      authorization: { params: { scope: "openid email profile" } },
    }),
  ],

  callbacks: {
    async jwt({ token, account }) {
      if (account) {
        token.accessToken  = account.access_token;
        token.refreshToken = account.refresh_token;
        token.expiresAt    = account.expires_at;
        token.idToken      = account.id_token;
        token.roles        = account.access_token
          ? extractRoles(account.access_token)
          : [];
        return token;
      }

      // Rafraîchir le token si expiré (avec 30s de marge)
      if (token.expiresAt && Date.now() / 1000 < token.expiresAt - 30) {
        return token;
      }
      if (!token.refreshToken) return token;

      const refreshed = await refreshKeycloakToken(token.refreshToken as string);
      if (!refreshed) return { ...token, error: "RefreshAccessTokenError" };

      return {
        ...token,
        accessToken:  refreshed.accessToken,
        refreshToken: refreshed.refreshToken,
        expiresAt:    refreshed.expiresAt,
        idToken:      refreshed.idToken ?? token.idToken,
        roles:        refreshed.roles,
      };
    },
    session({ session, token }) {
      session.accessToken = token.accessToken as string | undefined;
      session.idToken     = token.idToken as string | undefined;
      session.roles       = (token.roles as string[]) ?? [];
      if (session.user) {
        session.user.id = token.sub as string;
      }
      return session;
    },
  },

  pages: {
    signIn: "/connexion",
  },
});
