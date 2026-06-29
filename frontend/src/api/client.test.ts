import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiFetch } from "./client";

function makeFetchResponse(
  status: number,
  body: unknown,
  headers: Record<string, string> = {},
) {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: String(status),
    headers: new Headers(headers),
    json: () => Promise.resolve(body),
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
  sessionStorage.clear();
});

describe("apiFetch — success paths", () => {
  it("returns parsed JSON on 200", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(makeFetchResponse(200, { id: "1" })),
    );
    const result = await apiFetch<{ id: string }>("/api/v1/resources/1");
    expect(result).toEqual({ id: "1" });
  });

  it("returns undefined on 204", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(makeFetchResponse(204, null)),
    );
    const result = await apiFetch<void>("/api/v1/resources/2");
    expect(result).toBeUndefined();
  });

  it("omits Content-Type on bodyless GET", async () => {
    const fetchMock = vi.fn().mockResolvedValue(makeFetchResponse(200, {}));
    vi.stubGlobal("fetch", fetchMock);
    await apiFetch<unknown>("/api/v1/no-body");
    const sentHeaders: Record<string, string> = fetchMock.mock.calls[0][1].headers;
    expect(sentHeaders["Content-Type"]).toBeUndefined();
  });

  it("sends Content-Type on POST with body", async () => {
    const fetchMock = vi.fn().mockResolvedValue(makeFetchResponse(201, {}));
    vi.stubGlobal("fetch", fetchMock);
    await apiFetch<unknown>("/api/v1/with-body", { method: "POST", body: '{"name":"x"}' });
    const sentHeaders: Record<string, string> = fetchMock.mock.calls[0][1].headers;
    expect(sentHeaders["Content-Type"]).toBe("application/json");
  });
});

describe("apiFetch — ETag / If-Match", () => {
  it("stores ETag from response and sends If-Match on subsequent PUT", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        makeFetchResponse(200, { version: 1 }, { ETag: '"v1"' }),
      ),
    );
    await apiFetch<unknown>("/api/v1/projects/etag-test");

    const putMock = vi.fn().mockResolvedValue(makeFetchResponse(200, {}));
    vi.stubGlobal("fetch", putMock);
    await apiFetch<unknown>("/api/v1/projects/etag-test", {
      method: "PUT",
      body: "{}",
    });

    const sentHeaders: Record<string, string> = putMock.mock.calls[0][1].headers;
    expect(sentHeaders["If-Match"]).toBe('"v1"');
  });

  it("does not send If-Match on GET even after ETag stored", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        makeFetchResponse(200, {}, { ETag: '"v2"' }),
      ),
    );
    await apiFetch<unknown>("/api/v1/projects/get-no-ifmatch");

    const getMock = vi.fn().mockResolvedValue(makeFetchResponse(200, {}));
    vi.stubGlobal("fetch", getMock);
    await apiFetch<unknown>("/api/v1/projects/get-no-ifmatch");

    const sentHeaders: Record<string, string> = getMock.mock.calls[0][1].headers;
    expect(sentHeaders["If-Match"]).toBeUndefined();
  });
});

describe("apiFetch — auth injection", () => {
  it("injects Authorization header when jwt is in sessionStorage", async () => {
    sessionStorage.setItem("jwt", "tok123");
    const fetchMock = vi.fn().mockResolvedValue(makeFetchResponse(200, {}));
    vi.stubGlobal("fetch", fetchMock);

    await apiFetch<unknown>("/api/v1/auth-check");

    const sentHeaders: Record<string, string> = fetchMock.mock.calls[0][1].headers;
    expect(sentHeaders["Authorization"]).toBe("Bearer tok123");
  });

  it("omits Authorization header when sessionStorage has no jwt", async () => {
    const fetchMock = vi.fn().mockResolvedValue(makeFetchResponse(200, {}));
    vi.stubGlobal("fetch", fetchMock);

    await apiFetch<unknown>("/api/v1/no-auth-check");

    const sentHeaders: Record<string, string> = fetchMock.mock.calls[0][1].headers;
    expect(sentHeaders["Authorization"]).toBeUndefined();
  });
});

describe("apiFetch — error handling", () => {
  it("throws ApiError for non-JSON error responses", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(makeFetchResponse(404, null)),
    );
    await expect(apiFetch<unknown>("/api/v1/not-found")).rejects.toMatchObject({
      name: "ApiError",
      status: 404,
    });
  });

  it("throws ApiError with problem+json fields on 409", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        makeFetchResponse(
          409,
          { title: "Conflict", detail: "Version mismatch", type: "about:blank" },
          { "Content-Type": "application/problem+json" },
        ),
      ),
    );
    await expect(apiFetch<unknown>("/api/v1/conflict")).rejects.toMatchObject({
      status: 409,
      title: "Conflict",
      detail: "Version mismatch",
      type: "about:blank",
    });
  });

  it("ApiError is an instance of Error", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(makeFetchResponse(500, null)),
    );
    try {
      await apiFetch<unknown>("/api/v1/server-error");
    } catch (err) {
      expect(err).toBeInstanceOf(Error);
      expect(err).toBeInstanceOf(ApiError);
    }
  });
});
