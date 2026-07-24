import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";

export type TemplateInfo = {
  name: string;
  group: string;
  description: string;
  variableCount: number;
};

export type TemplatePreview = {
  nodeNames: string[];
};

type TemplatePickerModalProps = {
  open: boolean;
  templates: TemplateInfo[];
  onSelectTemplate: (templateName: string) => void;
  onClose: () => void;
  isLoading?: boolean;
};

export function TemplatePickerModal({
  open,
  templates,
  onSelectTemplate,
  onClose,
  isLoading = false,
}: TemplatePickerModalProps) {
  const titleId = useId();
  const descriptionId = useId();
  const closeButtonRef = useRef<HTMLButtonElement | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedTemplateName, setSelectedTemplateName] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }

    closeButtonRef.current?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !isLoading) {
        onClose();
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [isLoading, onClose, open]);

  if (!open || typeof document === "undefined") {
    return null;
  }

  const filteredTemplates = templates.filter((template) => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return true;
    return (
      template.name.toLowerCase().includes(query) ||
      template.description.toLowerCase().includes(query) ||
      template.group.toLowerCase().includes(query)
    );
  });

  const selectedTemplate = templates.find((t) => t.name === selectedTemplateName);

  const handleAddTemplate = () => {
    if (selectedTemplateName) {
      onSelectTemplate(selectedTemplateName);
    }
  };

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-shell-ink/45 px-4 py-8">
      {!isLoading ? (
        <button
          aria-label="Close template picker dialog"
          className="absolute inset-0"
          type="button"
          onClick={onClose}
        />
      ) : null}

      <div
        aria-describedby={descriptionId}
        aria-labelledby={titleId}
        aria-modal="true"
        className="relative z-10 w-full max-w-2xl rounded-lg border border-shell-line bg-white shadow-panel max-h-[80vh] flex flex-col"
        role="dialog"
      >
        <div className="border-b border-shell-line px-5 py-4">
          <h2 id={titleId} className="text-lg font-semibold text-shell-ink">
            Add from template
          </h2>
          <p id={descriptionId} className="mt-2 text-sm leading-6 text-shell-muted">
            Select a template to add a pre-configured set of nodes to your schema.
          </p>
        </div>

        <div className="flex-1 overflow-hidden flex flex-col">
          <div className="px-5 py-3 border-b border-shell-line">
            <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
              Search templates
              <input
                aria-label="Search templates by name or description"
                className="shell-field w-full"
                placeholder="For example: pump, device, simulation"
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  setSelectedTemplateName(null);
                }}
              />
            </label>
          </div>

          <div className="flex-1 overflow-hidden flex gap-4">
            {/* Templates list */}
            <div className="flex-1 overflow-y-auto border-r border-shell-line">
              {filteredTemplates.length === 0 ? (
                <div className="px-5 py-8 text-center text-sm text-shell-muted">
                  No templates match your search.
                </div>
              ) : (
                <div className="p-3 space-y-2">
                  {filteredTemplates.map((template) => (
                    <button
                      key={template.name}
                      className={`w-full rounded-md border px-3 py-3 text-left text-sm transition ${
                        selectedTemplateName === template.name
                          ? "border-shell-accent bg-shell-accent/5"
                          : "border-shell-line hover:border-shell-accent/50"
                      }`}
                      type="button"
                      onClick={() => setSelectedTemplateName(template.name)}
                      disabled={isLoading}
                    >
                      <p className="font-medium text-shell-ink">{template.name}</p>
                      <p className="text-xs text-shell-muted mt-1">{template.description}</p>
                      <p className="text-xs text-shell-muted mt-2">
                        {template.variableCount} variables • {template.group}
                      </p>
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* Preview panel */}
            <div className="w-56 border-l border-shell-line bg-shell-base/30 p-4 overflow-y-auto">
              {selectedTemplate ? (
                <div className="space-y-3">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                      Template preview
                    </p>
                    <p className="text-sm font-medium text-shell-ink mt-2">{selectedTemplate.name}</p>
                    <p className="text-xs text-shell-muted mt-1">{selectedTemplate.description}</p>
                  </div>
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                      Will add
                    </p>
                    <p className="text-sm text-shell-ink mt-2">
                      1 folder with {selectedTemplate.variableCount} variables
                    </p>
                  </div>
                </div>
              ) : (
                <div className="text-center text-sm text-shell-muted">
                  <p>Select a template to see preview</p>
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="flex flex-col-reverse gap-2 border-t border-shell-line px-5 py-4 sm:flex-row sm:items-center sm:justify-end">
          <button
            ref={closeButtonRef}
            className="shell-action"
            disabled={isLoading}
            type="button"
            onClick={onClose}
          >
            Cancel
          </button>
          <button
            className="shell-action"
            disabled={isLoading || !selectedTemplateName}
            type="button"
            onClick={handleAddTemplate}
          >
            {isLoading ? "Adding…" : "Add template"}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
