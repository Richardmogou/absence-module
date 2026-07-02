"use server";

import { auth, signOut } from "@/auth";
import { headers } from "next/headers";

export async function federatedLogout() {
  const session = await auth();
  
  if (!session) {
    return "/connexion";
  }
  
  const idToken = session.idToken;
  const clientId = process.env.AUTH_KEYCLOAK_ID;
  const issuer = process.env.AUTH_KEYCLOAK_ISSUER;
  
  const headersList = await headers();
  const host = headersList.get("host") || "localhost:3000";
  const protocol = host.includes("localhost") ? "http" : "https";
  const postLogoutRedirectUri = `${protocol}://${host}/`;
  
  let logoutUrl = `${issuer}/protocol/openid-connect/logout?client_id=${clientId}&post_logout_redirect_uri=${encodeURIComponent(postLogoutRedirectUri)}`;
  
  if (idToken) {
    logoutUrl += `&id_token_hint=${idToken}`;
  }
  
  // Perform local sign out without redirecting yet
  await signOut({ redirect: false });
  
  return logoutUrl;
}
