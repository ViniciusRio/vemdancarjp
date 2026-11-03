---
type: adr
numero: "005"
titulo: Abandonar o Supabase e publicar o backend em plano gratuito
data: 2026-11-02
status: aceita
---

# ADR-005: Abandonar o Supabase e publicar o backend em plano gratuito

## Contexto

A home publicada está em erro desde 16/09, porque chama `/api/agenda` sem backend em produção. Em 02/11, o host do projeto Supabase usado pelo build não resolvia mais no DNS, então login e painel admin também não têm mais para onde apontar. O `PLAN.md` previa reconciliar as duas bases e manter o Supabase como caminho de retorno.

## Decisão

- Abandonar o Supabase e descartar seus dados. Vinícius considera que eles não são importantes.
- Usar `public/agenda.json`, revisado por ele, como fonte da carga inicial do PostgreSQL.
- Publicar o backend Java com PostgreSQL em uma VM do Oracle Cloud Always Free, região São Paulo.
- Permanecer somente em planos gratuitos durante o desenvolvimento. Opções pagas estão descartadas nesta fase, mesmo que falte capacidade na Oracle.
- Ficar sem backup e monitoramento enquanto o painel admin estiver fora do ar, porque a agenda é recriável pela carga versionada.

## Consequências

- Não há reconciliação de dados nem retorno ao Supabase.
- Login e painel admin ficam fora do ar em produção até o corte do frontend. Até lá, correções na agenda entram primeiro na carga versionada e só depois no banco.
- Backup e monitoramento são pré-requisitos da change que voltar a permitir escrita pelo painel admin.
- Uma queda só é notada quando alguém abre o site.
- O SDK e os secrets `VITE_SUPABASE_*` ficam até o corte do frontend, porque a home importa o cliente Supabase.
- A produção depende de capacidade Arm em uma região com um único domínio de disponibilidade, e o site fica fora do ar enquanto ela não aparecer.

## Alternativas consideradas

- Restaurar o Supabase: descartada por decisão de Vinícius.
- Hostinger, Railway ou Hetzner: descartadas por serem pagas.
- Plano gratuito do Render: descartado porque o serviço dorme e o banco expira em 30 dias.

Veja também: ADR-001 e `openspec/changes/backend-production-deploy/`.
