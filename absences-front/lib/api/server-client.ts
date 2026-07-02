import { auth } from "@/auth";
import axios from "axios";

/**
 * Client axios côté serveur (Server Components, Route Handlers).
 * Récupère le access_token depuis la session NextAuth et l'injecte en Bearer.
 */
export async function serverApiClient() {
  const session = await auth();

  return axios.create({
    baseURL: process.env.API_INTERNAL_URL ?? process.env.NEXT_PUBLIC_API_URL,
    headers: {
      "Content-Type": "application/json",
      ...(session?.accessToken
        ? { Authorization: `Bearer ${session.accessToken}` }
        : {}),
    },
    timeout: 15_000,
  });
}
