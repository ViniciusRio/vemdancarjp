# Estratégia de Modelos (GLM-5.2 vs Kimi K3)

- **Modelo padrão:** GLM-5.2 via OpenRouter (`openrouter/z-ai/glm-5.2`) — trabalho do dia a dia.
- **Modelo pesado:** Kimi K3 via OpenRouter (`openrouter/moonshotai/kimi-k3`) — arquitetura e raciocínio profundo.

A seleção de modelo é garantida pelos agentes em `.opencode/agents/`. Esta doc explica **quando** escalar; o **como** (qual modelo carregar) já está codificado no frontmatter de cada agente.

## Quando usar GLM-5.2 (padrão)

Use GLM-5.2 para a maior parte do trabalho de engenharia — é mais rápido e mais barato, e suficiente para a maioria das tarefas:

- Escrever e refatorar componentes, composables e stores Vue 3.
- Implementar features dentro de `src/features/agenda/` (events, venues, CRUD admin).
- Escrever e corrigir testes unitários com Vitest.
- Triagem de lint/typecheck (`npm run lint`, `npm run type-check`).
- Refactors pequenos e médios, renomeações, movimentação de arquivos e bumps de dependência.
- Ler e explicar código existente.

Exemplo (modelo padrão, sem flag):

```
opencode "refatorar useAgendaAdmin para separar o carregamento de venues em useAdminVenues"
```

## Quando usar Kimi K3 (pesado)

Escale para Kimi K3 quando a tarefa exigir raciocínio profundo, planejamento de longo alcance ou decisões de alto risco:

- Decisões de arquitetura (novas áreas de feature, fronteiras de módulo, formato de estado).
- Migrations e mudanças de schema no Supabase em `events`, `variable_venues`, `other_venues`, `admins`, `pending_admins`.
- Projeto e revisão de políticas de Row Level Security (RLS).
- Análise de trade-offs (estratégia de cache, fluxos de auth, alternativas de modelo de dados).
- Modelagem de ameaças e auth (Google OAuth, onboarding de admins, fluxo `pending_admins`).
- Refactors grandes que tocam muitos arquivos ou cruzam fronteiras de feature.

Exemplo (optar pelo modelo pesado, via agente `architect`):

```
opencode -a architect "projetar RLS para que apenas admins na tabela admins possam escrever em events; anon só pode ler eventos futuros"
```

Exemplo (continuar no GLM-5.2 para um fix de rotina):

```
opencode -a agenda-dev "corrigir o erro de tipo em agendaService.ts linha 42"
```

Regra prática: comece no GLM-5.2. Se a tarefa for "projetar", "decidir", "migrar" ou "proteger", troque para Kimi K3 (agentes `architect` ou `security-reviewer`).

## Fluxos ideais

### Tarefa simples

```
GLM 5.2 (agenda-dev) → implementar → verificar
```

### Tarefa complexa de arquitetura

```
Kimi K3 (architect)  → plano e trade-offs
GLM 5.2 (agenda-dev) → implementação
GLM 5.2 (agenda-dev) → testes
Kimi K3 (architect)  → revisão final, se o risco justificar
```

### Tarefa de segurança

```
Kimi K3 (security-reviewer) → modelo de ameaça e auditoria
GLM 5.2 (agenda-dev)         → correção mecânica, se simples
Kimi K3 (security-reviewer) → revisão da correção
```

Não use Kimi K3 em loops de agente longos. Veja `docs/AI-COST-CONTROL.md`.
