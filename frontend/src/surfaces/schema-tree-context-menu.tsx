import { useEffect, useRef } from "react";

export interface ContextMenuAction {
  label: string;
  icon?: string;
  onClick?: () => void;
  disabled?: boolean;
  divider?: boolean;
  submenu?: ContextMenuAction[];
}

interface Props {
  x: number;
  y: number;
  actions: ContextMenuAction[];
  onClose: () => void;
}

export function SchemaTreeContextMenu({ x, y, actions, onClose }: Props) {
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        onClose();
      }
    }

    function handleEscape(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }

    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [onClose]);

  return (
    <div
      ref={menuRef}
      className="fixed z-50 rounded-md border border-shell-line bg-white py-1 shadow-lg"
      style={{
        left: `${x}px`,
        top: `${y}px`,
      }}
    >
      {actions.map((action, index) => (
        action.divider ? (
          <div key={`divider-${index}`} className="my-1 border-t border-shell-line" />
        ) : (
          <button
            key={action.label}
            className={`block w-full px-3 py-2 text-left text-sm transition ${
              action.disabled
                ? "cursor-not-allowed opacity-50 text-shell-muted"
                : "hover:bg-shell-accent/10 text-shell-ink"
            }`}
            disabled={action.disabled}
            onClick={() => {
              action.onClick();
              onClose();
            }}
          >
            {action.label}
          </button>
        )
      ))}
    </div>
  );
}
