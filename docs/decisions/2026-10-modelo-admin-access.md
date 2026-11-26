---
type: adr
numero: "002"
titulo: Usar tabela única para a nova administração
data: 2026-10-13
status: aceita
---

# ADR-002: Usar `admin_access` para a nova administração

## Contexto

A nova administração começa sem importar identidades, pendências ou aprovações do Supabase. A V1 contém `admins` e `pending_admins` legadas; a agenda pública e seus dados precisam ser preservados.

## Decisão

Criar uma tabela aditiva `admin_access` com UUID interno, Google `sub` textual único, estado `PENDING` ou `APPROVED`, data de solicitação obrigatória e email/nome opcionais. A identidade Google precisará ser validada pelo backend antes de qualquer uso da tabela para autorização. A V1 e as tabelas legadas permanecem intactas.

## Consequências

- Uma única linha por `google_sub` impede pendência e aprovação simultâneas para a mesma identidade.
- Unicidade e estados válidos são garantidos no PostgreSQL; concorrência de operações administrativas ainda exigirá tratamento na camada de serviço.
- A change `admin-access-management` (Passo 9) implementou o primeiro admin, o pedido de acesso e a gestão:
  - o primeiro admin vem de `APP_BOOTSTRAP_ADMIN_SUB`, aplicada só sem nenhum `APPROVED` e serializada por advisory lock; a variável pode ficar definida, porque só volta a valer se a base ficar sem admin;
  - rejeitar e revogar apagam a linha, sem status `REJECTED` nem histórico;
  - aprovação e remoção travam os `APPROVED` com `FOR UPDATE` e reconferem quem executa, e ninguém remove o próprio acesso, então nunca se fica sem admin, mesmo com remoções simultâneas.
- Nenhuma conta ou aprovação legada recebe privilégio automaticamente.

## Alternativas consideradas

- Manter duas tabelas administrativas novas: exigiria coordenar estados entre elas e uma aprovação com transferência de linha.

Veja também: `PLAN.md`.
