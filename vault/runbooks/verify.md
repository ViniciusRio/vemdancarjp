---
type: runbook
nome: verify
atualizado: 2026-09-17
---

# Verificação — lint + type-check + testes

Sequência completa:

```bash
npm run lint && npm run type-check && npm run test:unit
```

## Comandos isolados

- **Typecheck:** `npm run type-check` (`vue-tsc --build`)
- **Lint:** `npm run lint` (`oxlint . --fix` + `eslint . --fix --cache`)
- **Format:** `npm run format` (`prettier --write --experimental-cli src/`)
- **Build:** `npm run build` (`vue-tsc --build && vite build`)
- **Testes unitários:** `npm run test:unit` (`vitest --passWithNoTests`)
- **Teste isolado:** `npx vitest run path/to/file.spec.ts`
