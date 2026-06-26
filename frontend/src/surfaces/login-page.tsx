import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useShellStore } from "../shell/shell-store";

type LoginScenario =
  | "idle"
  | "submitting"
  | "invalid-credentials"
  | "server-unavailable"
  | "session-expired";

type LoginPageProps = {
  initialScenario?: LoginScenario;
};

function errorMessage(scenario: LoginScenario): string | null {
  if (scenario === "invalid-credentials") {
    return "The username or password is incorrect. Check your credentials and try again.";
  }
  if (scenario === "server-unavailable") {
    return "The authentication server is not reachable. Try again in a moment or contact your system administrator.";
  }
  return null;
}

export function LoginPage({ initialScenario = "idle" }: LoginPageProps) {
  const navigate = useNavigate();
  const accessMode = useShellStore((state) => state.accessMode);
  const setAccessMode = useShellStore((state) => state.setAccessMode);
  const setSharedRole = useShellStore((state) => state.setSharedRole);

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [scenario, setScenario] = useState<LoginScenario>(initialScenario);
  const submitTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (submitTimerRef.current !== null) clearTimeout(submitTimerRef.current);
    };
  }, []);

  const isSubmitting = scenario === "submitting";
  const authError = errorMessage(scenario);

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();

    if (!username.trim() || !password.trim()) {
      return;
    }

    setScenario("submitting");

    // STUB: accepts only "admin"/"admin". Any other credentials return invalid-credentials.
    // Replace with a real API call before deployment.
    submitTimerRef.current = setTimeout(() => {
      if (username === "admin" && password === "admin") {
        setAccessMode("shared");
        setSharedRole("admin");
        navigate("/projects");
      } else {
        setScenario("invalid-credentials");
      }
    }, 800);
  }

  if (accessMode === "local") {
    return (
      <div className="min-h-screen px-3 py-3 text-shell-ink sm:px-4 lg:px-5">
        <div className="mx-auto flex min-h-[calc(100vh-1.5rem)] max-w-sm flex-col items-center justify-center gap-6">
          <div className="shell-panel w-full px-6 py-8 text-center">
            <h1 className="text-lg font-semibold text-shell-ink">IoT Simulator</h1>
            <p className="mt-4 text-sm text-shell-muted">
              Running in trusted local mode. Login is not required.
            </p>
            <button
              className="shell-action mt-6"
              type="button"
              onClick={() => navigate("/projects")}
            >
              Open projects
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen px-3 py-3 text-shell-ink sm:px-4 lg:px-5">
      <div className="mx-auto flex min-h-[calc(100vh-1.5rem)] max-w-sm flex-col items-center justify-center gap-6">
        {scenario === "session-expired" ? (
          <div className="w-full rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            Your session has expired. Sign in again to continue.
          </div>
        ) : null}

        <div className="shell-panel w-full px-6 py-8">
          <div className="mb-6">
            <h1 className="text-lg font-semibold text-shell-ink">IoT Simulator</h1>
            <p className="mt-1 text-sm text-shell-muted">Shared environment · Sign in to continue</p>
          </div>

          <form className="space-y-4" onSubmit={handleSubmit}>
            <label className="flex flex-col gap-2 text-sm text-shell-muted">
              Username
              <input
                autoComplete="username"
                className="shell-field"
                disabled={isSubmitting}
                required
                type="text"
                value={username}
                onChange={(event) => {
                  setUsername(event.target.value);
                  if (scenario !== "idle" && scenario !== "submitting") {
                    setScenario("idle");
                  }
                }}
              />
            </label>

            <label className="flex flex-col gap-2 text-sm text-shell-muted">
              Password
              <input
                autoComplete="current-password"
                className="shell-field"
                disabled={isSubmitting}
                required
                type="password"
                value={password}
                onChange={(event) => {
                  setPassword(event.target.value);
                  if (scenario !== "idle" && scenario !== "submitting") {
                    setScenario("idle");
                  }
                }}
              />
            </label>

            {authError ? (
              <p className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
                {authError}
              </p>
            ) : null}

            <button
              className="shell-action w-full justify-center"
              disabled={isSubmitting || !username.trim() || !password.trim()}
              type="submit"
            >
              {isSubmitting ? "Signing in…" : "Sign in"}
            </button>
          </form>
        </div>

      </div>
    </div>
  );
}
