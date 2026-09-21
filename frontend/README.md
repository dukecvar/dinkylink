# frontend

The Dinky Link web UI (React, Vite, TypeScript, Tailwind CSS, shadcn/ui).

## Install and run

```bash
npm install
npm run dev       # dev server on http://localhost:5173, bound to 0.0.0.0
npm run build     # production build (tsc + vite)
```

## Adding shadcn/ui components

```bash
npx shadcn@latest add <component>
```

Use the `cn()` helper from `@/lib/utils` to compose Tailwind classes —
no ad-hoc inline styles (see the repo's `CLAUDE.md`).
