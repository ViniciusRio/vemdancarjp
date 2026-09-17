# Padrões de Prompt

Bons prompts compartilham quatro partes: **contexto** (qual arquivo/área), **objetivo** (qual resultado), **restrições** (regras a seguir) e **formato de saída** (código, diff ou plano).

Para templates prontos para copiar e preencher, veja `docs/ai-prompts.md`. Esta doc descreve apenas a **estrutura** de cada padrão.

## Explicar código / projeto

- **Objetivo:** Entender um arquivo, função ou fluxo de dados específico sem enrolação genérica.
- **Estrutura:** Nomeie o arquivo/símbolo → diga o que quer explicado → peça a resposta mínima.
- **Exemplo:**
  > Leia `src/services/agendaService.ts` e explique como `fetchEvents` é chamado por `agendaStore`. Cite file:line para cada etapa.

## Refatorar

- **Objetivo:** Mudar a estrutura sem mudar o comportamento, verificado por testes e typecheck.
- **Estrutura:** Aponte o alvo → descreva o cheiro → liste as restrições → exija verificação.
- **Exemplo:**
  > Refatore `useAgendaAdmin` em `src/features/agenda/composables/` para extrair o carregamento de venues em `useAdminVenues`. Preserve o retorno público, não toque em `agendaStore`, e rode `npm run type-check && npm run test:unit` antes de parar.

## Criar feature

- **Objetivo:** Implementar uma nova capacidade bem delimitada, de ponta a ponta.
- **Estrutura:** Declare o resultado do usuário → liste os arquivos afetados → defina as restrições (Pinia, `<script setup lang="ts">`, UI em pt-BR) → exija verificação.
- **Exemplo:**
  > Adicione um filtro de "eventos passados" à agenda home view. Toque `agendaStore`, `useAgendaAdmin` e `AgendaHomeView.vue`. Use Pinia para estado, mantenha a UI em pt-BR, e passe `npm run lint && npm run type-check`.

## Revisar segurança / RLS (Supabase)

- **Objetivo:** Encontrar e corrigir lacunas de autorização; sempre emparelhe com Kimi K3 (agente `security-reviewer`).
- **Estrutura:** Aponte a política/migration → declare o modelo de ameaças → exija um veredito por tabela.
- **Exemplo:**
  > Revise RLS em `events`, `variable_venues`, `other_venues`, `admins` e `pending_admins`. Para cada tabela, diga quem pode SELECT/INSERT/UPDATE/DELETE e sinalize qualquer caminho em que anon ou usuários autenticados não-admin possam mutar. Produza uma tabela com colunas `tabela | papel | privilégio | veredito | correção`.

## Planejar arquitetura / migration

- **Objetivo:** Produzir um plano antes de escrever código; sempre use Kimi K3 (agente `architect` ou comando `/plan-feature`).
- **Estrutura:** Declare a mudança → liste as áreas afetadas → peça alternativas + trade-offs → exija um plano passo a passo.
- **Exemplo:**
  > Planeje uma migration para dividir `events` em recorrentes vs únicas sem downtime. Cubra schema, RLS, `agendaService`, `agendaStore` e a UI admin. Forneça 2 alternativas com trade-offs, depois escolha uma e produza um plano passo a passo com rollback.
