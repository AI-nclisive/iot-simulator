type StatusTone = "neutral" | "accent" | "warning" | "danger";

type StatusBadgeProps = {
  label: string;
  tone?: StatusTone;
};

function toneClasses(tone: StatusTone) {
  if (tone === "warning") {
    return "border-shell-warning/25 bg-shell-warning/10 text-shell-warning";
  }

  if (tone === "danger") {
    return "border-shell-danger/25 bg-shell-danger/10 text-shell-danger";
  }

  if (tone === "accent") {
    return "border-shell-accent/20 bg-shell-accent/10 text-shell-accent";
  }

  return "border-shell-line bg-white text-shell-muted";
}

export function StatusBadge({ label, tone = "neutral" }: StatusBadgeProps) {
  return <span className={`shell-chip ${toneClasses(tone)}`}>{label}</span>;
}

export type { StatusTone };
