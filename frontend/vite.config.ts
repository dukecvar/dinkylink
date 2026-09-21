import path from "node:path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vite"

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "./src"),
    },
  },
  server: {
    // Bind 0.0.0.0, not just 127.0.0.1 — the nginx container (Task 3)
    // reaches this via host.docker.internal from inside the Docker VM,
    // which can't see a loopback-only bind.
    host: true,
  },
})
