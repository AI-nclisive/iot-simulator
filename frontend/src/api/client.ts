export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly title: string,
    public readonly detail: string | undefined,
    public readonly type: string | undefined,
  ) {
    super(title);
    this.name = "ApiError";
  }
}

const etagStore = new Map<string, string>();

function baseUrl(): string {
  return import.meta.env.VITE_API_BASE_URL ?? "";
}

function authHeaders(): Record<string, string> {
  const token = sessionStorage.getItem("jwt");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const url = `${baseUrl()}${path}`;
  const method = (init.method ?? "GET").toUpperCase();
  const etag = etagStore.get(path);

  const headers: Record<string, string> = {
    ...(init.body !== undefined ? { "Content-Type": "application/json" } : {}),
    Accept: "application/json",
    ...authHeaders(),
    ...(init.headers as Record<string, string>),
  };

  if (etag && (method === "PUT" || method === "DELETE")) {
    headers["If-Match"] = etag;
  }

  const response = await fetch(url, { ...init, headers });

  const responseEtag = response.headers.get("ETag");
  if (responseEtag) {
    etagStore.set(path, responseEtag);
  }

  if (!response.ok) {
    const ct = response.headers.get("Content-Type") ?? "";
    if (ct.includes("problem+json")) {
      const problem = (await response.json()) as {
        title?: string;
        detail?: string;
        type?: string;
      };
      throw new ApiError(
        response.status,
        problem.title ?? response.statusText,
        problem.detail,
        problem.type,
      );
    }
    throw new ApiError(response.status, response.statusText, undefined, undefined);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}
