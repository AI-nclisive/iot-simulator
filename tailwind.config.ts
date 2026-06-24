import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./frontend/src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      boxShadow: {
        panel: "0 10px 30px rgba(26, 35, 31, 0.08)",
      },
      colors: {
        shell: {
          base: "#eef1e7",
          line: "#cfd6ca",
          ink: "#183028",
          muted: "#5e6f67",
          panel: "#fbfcf8",
          accent: "#157a6e",
          warning: "#b36a22",
          danger: "#a94b42",
        },
      },
    },
  },
  plugins: [],
} satisfies Config;
