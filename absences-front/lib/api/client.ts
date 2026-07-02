import axios, { type InternalAxiosRequestConfig } from "axios";
import { getSession } from "next-auth/react";

// Cache local pour éviter un appel getSession() à chaque requête
let _cachedToken: string | null = null;
let _tokenExpiry  = 0;

async function getBearerToken(): Promise<string | null> {
  if (typeof window === "undefined") return null;

  const now = Date.now();
  if (_cachedToken && now < _tokenExpiry - 30_000) return _cachedToken;

  const session = await getSession();
  if (session?.accessToken) {
    _cachedToken = session.accessToken;
    _tokenExpiry = session.expires ? new Date(session.expires).getTime() : now + 3_600_000;
    return _cachedToken;
  }
  return null;
}

const apiClient = axios.create({
  // Pas de baseURL absolue : les requêtes passent par le proxy Next.js (/api/* → Spring Boot)
  timeout: 15_000,
});

apiClient.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const token = await getBearerToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (typeof window !== "undefined" && error?.response?.status === 401) {
      _cachedToken = null;
      window.location.href = "/connexion";
    }
    return Promise.reject(error);
  }
);

export default apiClient;
