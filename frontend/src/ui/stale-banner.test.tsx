/**
 * Tests for StaleBanner component (UI-092)
 *
 * Covers:
 * - Renders the provided message
 * - Has amber styling class
 */

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { StaleBanner } from "./stale-banner";

afterEach(cleanup);

describe("StaleBanner", () => {
  it("renders the provided message", () => {
    render(<StaleBanner message="Data may be outdated. Refresh the page." />);
    expect(screen.getByText("Data may be outdated. Refresh the page.")).toBeTruthy();
  });

  it("applies amber styling", () => {
    const { container } = render(<StaleBanner message="Stale" />);
    const banner = container.firstChild as HTMLElement;
    expect(banner.className).toContain("amber");
  });
});
