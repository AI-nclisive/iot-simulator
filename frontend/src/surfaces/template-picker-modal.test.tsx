import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TemplatePickerModal, type TemplateInfo } from "./template-picker-modal";

describe("TemplatePickerModal", () => {
  const mockTemplates: TemplateInfo[] = [
    {
      name: "Tank / vessel",
      group: "Process equipment",
      description: "A vessel with level, process measurements, limits, and status.",
      variableCount: 6,
    },
    {
      name: "Pump",
      group: "Process equipment",
      description: "A pump with commands, operating state, speed, pressure, and flow.",
      variableCount: 8,
    },
    {
      name: "Simulation signals",
      group: "Simulation",
      description: "A folder with common generated signals.",
      variableCount: 5,
    },
  ];

  it("renders when open is true", () => {
    const mockOnClose = vi.fn();
    const mockOnSelect = vi.fn();

    render(
      <TemplatePickerModal
        open={true}
        templates={mockTemplates}
        onSelectTemplate={mockOnSelect}
        onClose={mockOnClose}
      />,
    );

    expect(screen.getByText("Add from template")).toBeTruthy();
    expect(screen.getByText("Select a template to add a pre-configured set of nodes to your schema.")).toBeTruthy();
  });

  it("does not render when open is false", () => {
    const mockOnClose = vi.fn();
    const mockOnSelect = vi.fn();

    const { container } = render(
      <TemplatePickerModal
        open={false}
        templates={mockTemplates}
        onSelectTemplate={mockOnSelect}
        onClose={mockOnClose}
      />,
    );

    expect(container.firstChild).toBeNull();
  });

  it("displays all templates in the list", () => {
    const mockOnClose = vi.fn();
    const mockOnSelect = vi.fn();

    render(
      <TemplatePickerModal
        open={true}
        templates={mockTemplates}
        onSelectTemplate={mockOnSelect}
        onClose={mockOnClose}
      />,
    );

    expect(screen.getByText("Tank / vessel")).toBeTruthy();
    expect(screen.getByText("Pump")).toBeTruthy();
    expect(screen.getByText("Simulation signals")).toBeTruthy();
  });

  it("filters templates by search query", async () => {
    const mockOnClose = vi.fn();
    const mockOnSelect = vi.fn();
    const user = userEvent.setup();

    render(
      <TemplatePickerModal
        open={true}
        templates={mockTemplates}
        onSelectTemplate={mockOnSelect}
        onClose={mockOnClose}
      />,
    );

    const searchInput = screen.getByPlaceholderText("For example: pump, device, simulation") as HTMLInputElement;
    await user.type(searchInput, "pump");

    expect(screen.getByText("Pump")).toBeTruthy();
    expect(screen.queryByText("Tank / vessel")).toBeNull();
    expect(screen.queryByText("Simulation signals")).toBeNull();
  });

  it("filters templates by group", async () => {
    const mockOnClose = vi.fn();
    const mockOnSelect = vi.fn();
    const user = userEvent.setup();

    render(
      <TemplatePickerModal
        open={true}
        templates={mockTemplates}
        onSelectTemplate={mockOnSelect}
        onClose={mockOnClose}
      />,
    );

    const searchInput = screen.getByPlaceholderText("For example: pump, device, simulation") as HTMLInputElement;
    await user.type(searchInput, "simulation");

    expect(screen.getByText("Simulation signals")).toBeTruthy();
    expect(screen.queryByText("Tank / vessel")).toBeNull();
    expect(screen.queryByText("Pump")).toBeNull();
  });

  it("allows template selection", () => {
    const mockOnClose = vi.fn();
    const mockOnSelect = vi.fn();

    render(
      <TemplatePickerModal
        open={true}
        templates={mockTemplates}
        onSelectTemplate={mockOnSelect}
        onClose={mockOnClose}
      />,
    );

    const pumpButton = screen.getByText("Pump").closest("button");
    fireEvent.click(pumpButton!);

    expect(screen.getByText("Template preview")).toBeTruthy();
    expect(screen.getByText("Will add")).toBeTruthy();
    expect(screen.getByText("1 folder with 8 variables")).toBeTruthy();
  });

  it("calls onSelectTemplate when Add template button is clicked", () => {
    const mockOnClose = vi.fn();
    const mockOnSelect = vi.fn();

    render(
      <TemplatePickerModal
        open={true}
        templates={mockTemplates}
        onSelectTemplate={mockOnSelect}
        onClose={mockOnClose}
      />,
    );

    // Select a template
    const pumpButton = screen.getByText("Pump").closest("button");
    fireEvent.click(pumpButton!);

    // Click Add button
    const addButton = Array.from(screen.getAllByRole("button")).find((btn) => btn.textContent?.includes("Add template"));
    fireEvent.click(addButton!);

    expect(mockOnSelect).toHaveBeenCalledWith("Pump");
  });

  it("calls onClose when Cancel button is clicked", () => {
    const mockOnClose = vi.fn();
    const mockOnSelect = vi.fn();

    render(
      <TemplatePickerModal
        open={true}
        templates={mockTemplates}
        onSelectTemplate={mockOnSelect}
        onClose={mockOnClose}
      />,
    );

    const cancelButton = Array.from(screen.getAllByRole("button")).find((btn) => btn.textContent?.includes("Cancel"));
    fireEvent.click(cancelButton!);

    expect(mockOnClose).toHaveBeenCalled();
  });

  it("disables Add button when no template is selected", () => {
    const mockOnClose = vi.fn();
    const mockOnSelect = vi.fn();

    render(
      <TemplatePickerModal
        open={true}
        templates={mockTemplates}
        onSelectTemplate={mockOnSelect}
        onClose={mockOnClose}
      />,
    );

    const addButton = Array.from(screen.getAllByRole("button")).find((btn) => btn.textContent?.includes("Add template")) as HTMLButtonElement;
    expect(addButton.disabled).toBe(true);
  });

  it("disables buttons when isLoading is true", () => {
    const mockOnClose = vi.fn();
    const mockOnSelect = vi.fn();

    render(
      <TemplatePickerModal
        open={true}
        templates={mockTemplates}
        onSelectTemplate={mockOnSelect}
        onClose={mockOnClose}
        isLoading={true}
      />,
    );

    const addButton = Array.from(screen.getAllByRole("button")).find((btn) => btn.textContent?.includes("Adding")) as HTMLButtonElement;
    const cancelButton = Array.from(screen.getAllByRole("button")).find((btn) => btn.textContent?.includes("Cancel")) as HTMLButtonElement;

    expect(addButton.disabled).toBe(true);
    expect(cancelButton.disabled).toBe(true);
  });

  it("shows message when no templates match search", async () => {
    const mockOnClose = vi.fn();
    const mockOnSelect = vi.fn();
    const user = userEvent.setup();

    render(
      <TemplatePickerModal
        open={true}
        templates={mockTemplates}
        onSelectTemplate={mockOnSelect}
        onClose={mockOnClose}
      />,
    );

    const searchInput = screen.getByPlaceholderText("For example: pump, device, simulation") as HTMLInputElement;
    await user.type(searchInput, "nonexistent");

    expect(screen.getByText("No templates match your search.")).toBeTruthy();
  });

  it("clears selection when search query changes", async () => {
    const mockOnClose = vi.fn();
    const mockOnSelect = vi.fn();
    const user = userEvent.setup();

    render(
      <TemplatePickerModal
        open={true}
        templates={mockTemplates}
        onSelectTemplate={mockOnSelect}
        onClose={mockOnClose}
      />,
    );

    // Select a template
    const pumpButton = screen.getByText("Pump").closest("button");
    fireEvent.click(pumpButton!);

    expect(screen.getByText("Template preview")).toBeTruthy();

    // Change search query
    const searchInput = screen.getByPlaceholderText("For example: pump, device, simulation") as HTMLInputElement;
    await user.type(searchInput, "tank");

    // Preview should disappear
    expect(screen.queryByText("Template preview")).toBeNull();
    expect(screen.getByText("Select a template to see preview")).toBeTruthy();
  });
});
