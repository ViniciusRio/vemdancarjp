# AGENTS.md

## Comandos e Verificação

- **Servidor de dev:** `npm run dev`
- **Typecheck:** `npm run type-check` (`vue-tsc --build`)
- **Linting:** `npm run lint` (executa `oxlint . --fix` seguido de `eslint . --fix --cache`)
- **Formatação:** `npm run format` (`prettier --write --experimental-cli src/`)
- **Build:** `npm run build` (`vue-tsc --build && vite build`)
- **Testes unitários:** `npm run test:unit` (`vitest --passWithNoTests`)
- **Arquivo de teste isolado:** `npx vitest run path/to/file.spec.ts`
- **Sequência de verificação:** `npm run lint && npm run type-check && npm run test:unit`

## Idioma

- Código, nomes de variáveis, tipos e mensagens de commit em inglês.
- Documentação, explicações, prompts e texto de UI em português (pt-BR).
- Os agentes devem responder em pt-BR, salvo quando o usuário pedir expressamente em inglês.
- Termos técnicos consagrados permanecem em inglês (ex.: Vue, Pinia, Supabase, RLS, Composition API, composable, store, service, router, Vite, Tailwind, etc.).

## Arquitetura e Estrutura

- **Stack:** Vue 3 (Composition API) + TypeScript + Vite + Tailwind CSS v4 + Pinia + Supabase.
- **Path alias:** `@/` mapeia para `src/`.
- **Fronteiras de diretório:**
  - `src/features/agenda/`: Componentes, composables (`useAgendaAdmin`, `useAdminVenues`) e tipos de domínio da feature.
  - `src/views/`: `AgendaHomeView.vue`, `admin/AdminLoginView.vue`, `admin/AdminView.vue`.
  - `src/stores/`: `agendaStore.ts` (estado público da agenda), `authStore.ts` (autenticação Supabase e privilégios de admin).
  - `src/services/`: `supabase.ts` (singleton do cliente Supabase), `agendaService.ts` (CRUD no banco).

## Ambiente e Supabase

- **Variáveis de ambiente:** configuradas em `.env` (template em `.env.example`):
  - `VITE_SUPABASE_URL`
  - `VITE_SUPABASE_ANON_KEY`
- **Tabelas do banco:** `events`, `variable_venues`, `other_venues`, `admins`, `pending_admins`.
- **Auth:** apenas Google OAuth (`supabase.auth.signInWithOAuth({ provider: 'google' })`). Segurança garantida por Row Level Security (RLS).

## Convenções

- **Idioma do código:** código, comentários, nomes de variáveis e mensagens de commit em inglês; texto de UI em português (`pt-BR`).
- **Componentes Vue:** usar `<script setup lang="ts">` com tipos TypeScript explícitos para props/emits. Options API é proibido.
- **Nomenclatura:** `PascalCase.vue` para componentes; `camelCase.ts` para composables, services, stores e helpers.
- **Gerenciamento de estado:** usar stores Pinia (`authStore`, `agendaStore`). Não ler/gravar `localStorage` diretamente para estado de auth ou da app.

## Migração para Backend Java (WIP)

- O projeto está migrando de Supabase para um backend Java + Spring Boot próprio. O plano completo está em `PLAN.md` (stack, entidades, endpoints, Docker, ordem de implementação).
- **Fluxo de Aprendizado obrigatório:** ao executar qualquer passo da "Ordem de Implementação" do `PLAN.md`, seguir a seção "Fluxo de Aprendizado" lá definida: explicar antes de escrever → aguardar confirmação → implementar → resumir o diff → verificar → **registrar aprendizado em `LEARNING.md`** → só então avançar.
- **`LEARNING.md`** é o caderno de anotações do projeto. Deve ser atualizado ao final de cada passo concluído, com a entrada mais recente no topo. Nunca pular essa etapa — mesmo em modo direto ("só faz").
- **Modo direto:** se o usuário pedir "só faz" / "sem enrola", pular explicação prévia e resumo do diff, mas **nunca** pular a atualização do `LEARNING.md`.

## Estratégia de Modelo (GLM-5.2 vs Kimi K3)

- **Modelo padrão:** GLM-5.2 via OpenRouter (`openrouter/z-ai/glm-5.2`) — usado para o trabalho do dia a dia.
- **Modelo pesado:** Kimi K3 via OpenRouter (`openrouter/moonshotai/kimi-k3`) — usado para arquitetura e raciocínio profundo.

### Quando usar GLM-5.2 (padrão)

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

### Quando usar Kimi K3 (pesado)

Escale para Kimi K3 quando a tarefa exigir raciocínio profundo, planejamento de longo alcance ou decisões de alto risco:

- Decisões de arquitetura (novas áreas de feature, fronteiras de módulo, formato de estado).
- Migrations e mudanças de schema no Supabase em `events`, `variable_venues`, `other_venues`, `admins`, `pending_admins`.
- Projeto e revisão de políticas de Row Level Security (RLS).
- Análise de trade-offs (estratégia de cache, fluxos de auth, alternativas de modelo de dados).
- Modelagem de ameaças e auth (Google OAuth, onboarding de admins, fluxo `pending_admins`).
- Refactors grandes que tocam muitos arquivos ou cruzam fronteiras de feature.

Exemplo (optar pelo modelo pesado):

```
opencode -m openrouter/moonshotai/kimi-k3 "projetar RLS para que apenas admins na tabela admins possam escrever em events; anon só pode ler eventos futuros"
```

Exemplo (continuar no GLM-5.2 para um fix de rotina):

```
opencode -m openrouter/z-ai/glm-5.2 "corrigir o erro de tipo em agendaService.ts linha 42"
```

Regra prática: comece no GLM-5.2. Se a tarefa for "projetar", "decidir", "migrar" ou "proteger", troque para Kimi K3.

## Padrões de Prompt

Bons prompts compartilham quatro partes: **contexto** (qual arquivo/área), **objetivo** (qual resultado), **restrições** (regras a seguir) e **formato de saída** (código, diff ou plano). Use os padrões abaixo.

### Padrão: Explicar código / projeto

- **Objetivo:** Entender um arquivo, função ou fluxo de dados específico sem enrolação genérica.
- **Estrutura:** Nomeie o arquivo/símbolo → diga o que quer explicado → peça a resposta mínima.
- **Exemplo:**
  > Leia `src/services/agendaService.ts` e explique como `fetchEvents` é chamado por `agendaStore`. Cite file:line para cada etapa.

### Padrão: Refatorar

- **Objetivo:** Mudar a estrutura sem mudar o comportamento, verificado por testes e typecheck.
- **Estrutura:** Aponte o alvo → descreva o cheiro → liste as restrições → exija verificação.
- **Exemplo:**
  > Refatore `useAgendaAdmin` em `src/features/agenda/composables/` para extrair o carregamento de venues em `useAdminVenues`. Preserve o retorno público, não toque em `agendaStore`, e rode `npm run type-check && npm run test:unit` antes de parar.

### Padrão: Criar feature

- **Objetivo:** Implementar uma nova capacidade bem delimitada, de ponta a ponta.
- **Estrutura:** Declare o resultado do usuário → liste os arquivos afetados → defina as restrições (Pinia, `<script setup lang="ts">`, UI em pt-BR) → exija verificação.
- **Exemplo:**
  > Adicione um filtro de "eventos passados" à agenda home view. Toque `agendaStore`, `useAgendaAdmin` e `AgendaHomeView.vue`. Use Pinia para estado, mantenha a UI em pt-BR, e passe `npm run lint && npm run type-check`.

### Padrão: Revisar segurança / RLS (Supabase)

- **Objetivo:** Encontrar e corrigir lacunas de autorização; sempre emparelhe com Kimi K3.
- **Estrutura:** Aponte a política/migration → declare o modelo de ameaças → exija um veredito por tabela.
- **Exemplo:**
  > Revise RLS em `events`, `variable_venues`, `other_venues`, `admins` e `pending_admins`. Para cada tabela, diga quem pode SELECT/INSERT/UPDATE/DELETE e sinalize qualquer caminho em que anon ou usuários autenticados não-admin possam mutar. Produza uma tabela com colunas `tabela | papel | privilégio | veredito | correção`.

### Padrão: Planejar arquitetura / migration

- **Objetivo:** Produzir um plano antes de escrever código; sempre use Kimi K3.
- **Estrutura:** Declare a mudança → liste as áreas afetadas → peça alternativas + trade-offs → exija um plano passo a passo.
- **Exemplo:**
  > Planeje uma migration para dividir `events` em recorrentes vs únicas sem downtime. Cubra schema, RLS, `agendaService`, `agendaStore` e a UI admin. Forneça 2 alternativas com trade-offs, depois escolha uma e produza um plano passo a passo com rollback.

## Performance e Economia de Tokens

Diagnóstico real: gasta-se com **contexto**, não resposta (134k entrada vs 84 saída nas chamadas recentes; cache hit 84,9%). Cada passo do agente recarrega a conversa inteira. Regras abaixo são obrigatórias.

- **R1 — Kimi K3 nunca dentro de loop de agente.** K3 custa ~$3/M entrada vs ~$0,20/M do GLM (18×). A 134k, ~$0,40/passo. Padrão: GLM-5.2 coleta achados em arquivo; K3 lê **só esse arquivo** (~8k) numa chamada curta e dá veredito. K3 só entra onde julgamento vale o preço e o contexto é enxuto (prompt 04, trade-offs, revisão final).
- **R2 — uma sessão por tarefa.** Sessão longa é o multiplicador: no passo 30 paga-se o histórico dos 29 anteriores. `/compact` ao passar de ~50% da janela; sessão nova a cada fase do `PLAN*.md` (análise, implementação, auditoria não compartilham sessão). Olhar o % de preenchimento no sidebar antes de continuar.
- **R3 — o que é sempre-presente tem que ser pequeno.** `AGENTS.md` é auto-injetado em toda chamada: 1.000 tokens aqui viram 30.000 numa sessão de 30 passos. Regras, não explicação; meta <2k tokens. Cite o caminho de `PLAN*.md`/`DESIGN.md` em vez de colar conteúdo. Proibido `Read` de arquivo inteiro por hábito — usar `grep -n`/`rg -n` + ler só o intervalo de linhas.
- **R4 — proteja o cache.** 84,9% de acerto segura a conta (leitura de cache custa 0,1×–0,5× de token fresco). Cache vale por prefixo estável: não edite, no meio da sessão, arquivo que está no topo do contexto; não troque de modelo no meio (cache é por modelo+provedor); adicione contexto no fim, nunca insira no começo.
- **R5 — trabalho determinístico não paga token.** Tarefa com resposta única e verificável é script, não pergunta: `tokens/core.json → tokens.css` (conversão), contraste WCAG (fórmula), caça a cor hardcoded (`rg -n "green-|gray-|#[0-9a-fA-F]{3,6}" src/`), renomear/mover/formatar (`sed`, `prettier`). Modelo é para julgamento.
- **R6 — guardrails na conta, não na disciplina.** Limite de gasto por chave OpenRouter com `limit_reset` recorrente; alerta de gasto na chave; chave separada por trilha (UI/UX vs backend); roteamento por preço é o padrão (`:nitro`/`throughput` só quando velocidade importar de verdade). Raciocínio em nível baixo/desligado em tarefa mecânica (tokens de raciocínio são cobrados como saída).

### Checklist pré-sessão

1. A tarefa é uma só e cabe nesta sessão?
2. Modelo certo: mecânico → GLM-5.2 · julgamento curto e enxuto → K3 (uma chamada, arquivo de achados)?
3. Arquivos sempre-presentes (`AGENTS.md`) estão enxutos?
4. O que é determinístico já virou script?
5. Vou ler por `rg -n` + intervalo, não arquivo inteiro?
6. `/compact` ou sessão nova ao passar da metade da janela?

### Ferramentas e hábitos

- **Seja específico.** Nomeie arquivos, funções e símbolos (`src/stores/agendaStore.ts:fetchEvents`), não "a app".
- **Plane antes de construir.** Tarefa grande → plano primeiro, depois implementa. Evita retrabalho.
- **Sem explicações genéricas.** Não pergunte "como o Vue funciona" — pergunte sobre arquivos reais deste repo.
- **Prefira `read`/`glob`/`grep`** a `cat`/`find`/`grep` no Bash. Não rode `ls` quando `glob` resolve.
- **Escope o diff.** Menor mudança que satisfaça o objetivo; rejeite refactors não relacionados embutidos.
- **Verifique uma vez.** `npm run lint && npm run type-check && npm run test:unit` no final, não após cada edição.

## Skills (perfis de agente)

Arquivos de skill vivem em `skills/` na raiz do repo. Carregue o perfil correspondente quando a tarefa se encaixar na descrição.

- **`skills/agenda-dev.md` — Desenvolvedor da Agenda**
  - **Papel:** Engenheiro front-end da feature `agenda` (Vue 3 + CRUD Supabase).
  - **Use quando:** Construir/refatorar componentes, composables, stores e services sob `src/features/agenda/`, `src/stores/`, `src/services/`.
  - **Estilo de resposta:** Maioria código; mínima prosa. Entrega a mudança e roda a sequência de verificação.

- **`skills/security-reviewer.md` — Revisor de Segurança**
  - **Papel:** Revê políticas RLS, fluxos de auth e autorização de admin no Supabase.
  - **Use quando:** Escrever ou alterar RLS, fluxos `admins`/`pending_admins`, Google OAuth ou qualquer caminho de mutação.
  - **Estilo de resposta:** Texto primeiro com uma tabela de veredito por tabela e correções concretas em SQL/policy. Prefira Kimi K3.

- **`skills/architect.md` — Arquiteto**
  - **Papel:** Arquiteto de software de longo horizonte para schema, estado e fronteiras de módulo.
  - **Use quando:** Migrations, refactors cross-feature, análise de trade-offs ou qualquer decisão difícil de reverter.
  - **Estilo de resposta:** Plano primeiro — alternativas + trade-offs, depois um plano passo a passo com rollback. Sempre Kimi K3.
