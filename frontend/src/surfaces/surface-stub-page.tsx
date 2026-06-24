import { resolveAccess } from "../shell/access-policy";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";

type SurfaceStubPageProps = {
  adminOnly?: boolean;
  title: string;
  summary: string;
  note: string;
};

export function SurfaceStubPage({
  adminOnly = false,
  title,
  summary: _summary,
  note,
}: SurfaceStubPageProps) {
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const access = resolveAccess(accessMode, sharedRole);

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <SharedStatePanel
          message={
            adminOnly && access.isSharedUser
              ? "This surface is reserved for shared administrators. Switch to Admin or return to a project area that supports User access."
              : note
          }
          state={adminOnly && access.isSharedUser ? "locked" : "empty"}
          title={
            adminOnly && access.isSharedUser
              ? "Admin access is required."
              : title
          }
        />
      </section>
    </div>
  );
}
