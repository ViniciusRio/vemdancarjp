# Templates de Prompts de IA

Prompts prontos para uso neste projeto Vue 3 + Supabase. Copie, preencha os placeholders e rode com `opencode` (GLM-5.2 por padrão) ou `opencode -m openrouter/moonshotai/kimi-k3` para o modelo pesado.

Cada template segue a estrutura **contexto → objetivo → restrições → formato de saída** do `AGENTS.md`. Ajuste os paths de arquivo para o alvo real.

---

## Explicar código / projeto

Use para entender um arquivo, função ou fluxo de dados específico sem enrolação genérica.

```
Leia `src/services/agendaService.ts` e explique como `fetchEvents` é chamado por `src/stores/agendaStore.ts`. Rastreie o caminho completo da action do store através da query Supabase até o tipo retornado. Cite file:line para cada etapa. Não explique o básico de Vue ou Supabase.
```

Quando usar: onboarding em uma área nova, debug de fluxo de dados ou antes de refatorar. Modelo: **GLM-5.2**.

```
Leia `src/stores/authStore.ts` e descreva o fluxo de sign-in do Google OAuth: quais chamadas de `supabase.auth` são feitas, como a sessão é armazenada e como o membership em `admins` é verificado. Cite file:line. Sinalize qualquer coisa que leia `localStorage` diretamente.
```

Quando usar: auditar o handling de estado de auth. Modelo: **GLM-5.2** para entendimento, **Kimi K3** se suspeitar de bug.

---

## Refatorar

Use para mudar a estrutura sem mudar o comportamento. Sempre exija verificação.

```
Refatore `useAgendaAdmin` em `src/features/agenda/composables/useAgendaAdmin.ts` para extrair o carregamento de venues em um novo composable `useAdminVenues` na mesma pasta. Restrições: preserve o shape de retorno público de `useAgendaAdmin`, não toque em `src/stores/agendaStore.ts`, não mude nenhuma query Supabase. Saída: o novo `useAdminVenues.ts` e o diff de `useAgendaAdmin.ts`. Rode `npm run type-check && npm run test:unit` antes de parar.
```

Quando usar: dividir um composable que cresceu demais. Modelo: **GLM-5.2**.

```
Renomeie `variable_venues` para `recurring_venues` em `src/services/agendaService.ts`, `src/stores/agendaStore.ts`, `src/features/agenda/` e os tipos Supabase relacionados. Não mude comportamento nem RLS. Rode `npm run lint && npm run type-check` após.
```

Quando usar: uma renomeação que toca muitos arquivos mas nenhuma lógica. Modelo: **GLM-5.2**.

---

## Criar feature

Use para implementar uma nova capacidade bem delimitada, de ponta a ponta.

```
Adicione um filtro de "eventos passados" à agenda home view. Arquivos afetados: `src/stores/agendaStore.ts` (adicionar um ref booleano `past` + filtro), `src/features/agenda/composables/useAgendaAdmin.ts` (expor eventos passados) e `src/views/AgendaHomeView.vue` (um botão toggle em pt-BR: "Eventos passados"). Restrições: use Pinia para estado, `<script setup lang="ts">`, texto de UI em pt-BR, não adicione uma nova tabela Supabase — filtre client-side dos `events` existentes. Saída: os arquivos editados. Verifique com `npm run lint && npm run type-check && npm run test:unit`.
```

Quando usar: adicionar uma feature de UI autossuficiente. Modelo: **GLM-5.2**.

```
Adicione capacidade de admin para criar uma nova row em `other_venues`. Toque `src/features/agenda/composables/useAdminVenues.ts`, `src/services/agendaService.ts` (novo `createOtherVenue`) e o componente de UI admin sob `src/features/agenda/components/`. Restrições: apenas admins na tabela `admins` podem chamar isso (enforce via RLS — mas não escreva a política, apenas a chamada client), UI em pt-BR, campos do formulário: nome, endereço, link do mapa. Verifique com `npm run type-check`.
```

Quando usar: uma feature de CRUD que precisa de uma política RLS correspondente. Modelo: **GLM-5.2** para o client; faça follow-up com a skill security-reviewer (Kimi K3) para a política.

---

## Revisar segurança / RLS (Supabase)

Use para auditar lacunas de autorização. Sempre emparelhe com Kimi K3.

```
Revise RLS em `events`, `variable_venues`, `other_venues`, `admins` e `pending_admins`. Para cada tabela, diga quem pode SELECT/INSERT/UPDATE/DELETE sob os papéis: `anon`, `authenticated` (não-admin) e `admin` (row existe em `admins`). Sinalize qualquer caminho em que `anon` ou um usuário autenticado não-admin possa mutar dados da agenda, e qualquer caminho em que `pending_admins` possam ser promovidos sem aprovação de um admin existente. Produza uma tabela com colunas: `tabela | papel | privilégio | veredito | correção`. Depois forneça o SQL exato para cada correção. Não escreva código de aplicação.
```

Quando usar: antes de expor novas tabelas/colunas ao client, após adicionar uma mutação ao `agendaService.ts` ou como auditoria periódica. Modelo: **Kimi K3**.

```
Audite o fluxo de Google OAuth + onboarding de admin em `src/stores/authStore.ts` e a transição `pending_admins` → `admins`. Declare o modelo de ameaças, liste cada etapa que um usuário autenticado via Google passa e sinalize qualquer etapa em que um não-admin possa se autopromover ou em que um admin revogado mantenha acesso. Produza uma lista numerada de achados com severidade e a correção concreta de política/código para cada um.
```

Quando usar: após mudar o fluxo de onboarding ou adicionar um novo papel de admin. Modelo: **Kimi K3**.

---

## Arquitetura / planejamento de migration

Use para produzir um plano antes de escrever código. Sempre use Kimi K3.

```
Planeje uma migration para dividir `events` em `recurring_events` (com regra de recorrência) vs `one_off_events` (data única) sem downtime. Áreas afetadas: schema Supabase + RLS, `src/services/agendaService.ts`, `src/stores/agendaStore.ts`, `src/features/agenda/composables/` e a UI admin em `src/views/admin/AdminView.vue`. Forneça 2 alternativas com trade-offs (ex.: tabelas separadas vs uma coluna discriminadora `kind`), depois escolha uma e produza um plano passo a passo. Cada passo deve nomear o(s) arquivo(s) ou SQL que toca. Termine com um procedimento de rollback para cada passo. Não escreva código ainda — apenas plano.
```

Quando usar: mudanças de schema, forks de modelo de dados ou qualquer decisão cara de reverter. Modelo: **Kimi K3**.

```
Projetar a estratégia de cache para `fetchEvents` em `src/stores/agendaStore.ts`: compare cache em memória apenas no Pinia vs subscription Supabase Realtime vs SWR (stale-while-revalidate). Arquivos afetados: `agendaStore.ts`, `agendaService.ts`. Forneça 2 alternativas com trade-offs (latência, frescor, complexidade), escolha uma e produza um plano de implementação passo a passo com rollback. Apenas plano — sem código.
```

Quando usar: decisões de estratégia de performance/estado. Modelo: **Kimi K3**.

---

## Notas

- Substitua os paths de arquivo entre crases pelo alvo real antes de rodar.
- Para qualquer prompt que diga "apenas plano" ou "não escreva código ainda", mantenha a resposta em formato de plano e passe para implementação apenas em um turno seguinte (geralmente no GLM-5.2).
- Prompts de segurança e arquitetura devem sempre rodar no Kimi K3 (`-m openrouter/moonshotai/kimi-k3`); prompts de explicar/refatorar/feature do dia a dia usam GLM-5.2 por padrão.
- Após qualquer prompt que produza código, rode a sequência de verificação: `npm run lint && npm run type-check && npm run test:unit`.
