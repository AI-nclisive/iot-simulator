import { describe, expect, it } from "vitest";
import { resolveAccess } from "./access-policy";

describe("resolveAccess — canManageAdmin", () => {
  it("is false in local mode (Admin nav hidden locally)", () => {
    expect(resolveAccess("local", "admin").canManageAdmin).toBe(false);
  });

  it("is true for shared admin", () => {
    expect(resolveAccess("shared", "admin").canManageAdmin).toBe(true);
  });

  it("is false for shared user", () => {
    expect(resolveAccess("shared", "user").canManageAdmin).toBe(false);
  });
});
