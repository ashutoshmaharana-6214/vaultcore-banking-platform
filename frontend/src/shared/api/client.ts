/**
 * Browser-safe client. Wire credentials/token handling only after backend
 * authentication, CSRF strategy and authorization rules are finalized.
 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";

export async function getBackendHealth(signal?: AbortSignal): Promise<boolean> {
  const response = await fetch("/actuator/health", { signal });
  if (!response.ok) {
    throw new Error(`Backend health request failed (${response.status})`);
  }
  const health: unknown = await response.json();
  return typeof health === "object" && health !== null && "status" in health && health.status === "UP";
}

export async function apiGet<T>(path: string, signal?: AbortSignal): Promise<T> {
  if (!path.startsWith("/") || path.startsWith("//")) {
    throw new Error("API path must be relative and start with /");
  }
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "GET",
    signal,
    headers: { Accept: "application/json" }
  });
  if (!response.ok) {
    throw new Error(`API request failed (${response.status})`);
  }
  return (await response.json()) as T;
}
