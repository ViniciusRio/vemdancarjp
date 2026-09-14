# LEARNING.md — Caderno de aprendizado do projeto

Este arquivo é o caderno de anotações do projeto. É atualizado automaticamente ao final de cada passo do plano em `PLAN.md` (ver seção "Fluxo de Aprendizado" lá).

Cada entrada documenta o que foi aprendido ao concluir um dos passos da "Ordem de Implementação" do `PLAN.md`. As entradas mais recentes ficam no topo.

## Estrutura de cada entrada

```
### Passo N — <título do passo>

**Conceito:** <1-3 frases sobre o que este passo ensinou>

**Comandos:**
- `<comando>` — <o que faz>
- `<comando>` — <o que faz>

**Erros:**
- <erro encontrado> → <resolução aplicada>

**Referências:**
- PLAN.md:<linha> — <por que citou>
- <arquivo>:<linha> — <por que citou>
- <link externo, se houver> — <por que citou>
```

---

<!-- Novas entradas vão sendo inseridas imediatamente abaixo desta linha, mantendo a mais recente no topo. -->

### Passo 1 — Docker + PostgreSQL

**Conceito:** Subir um PostgreSQL 17 dentro de um container Docker é o que isola o banco do sistema host, garante reprodutibilidade (qualquer máquina roda `docker compose up` e tem o mesmo banco) e permite reset fácil com `docker compose down -v`. O volume nomeado `postgres_data` persiste os dados entre restarts do container; sem ele, todo `down` apagaria o banco. A porta `5433:5432` mapeia a porta 5432 interna do container para a 5433 do host, evitando conflito com um Postgres nativo que porventura já ocupe a 5432 no host.

**Comandos:**
- `docker compose up -d` — sobe os serviços definidos no `docker-compose.yml` em modo detached (background). Na primeira execução, baixa a imagem.
- `docker compose down` — para e remove os containers e a rede (mas NÃO os volumes nomeados, a menos que use `-v`).
- `docker compose down -v` — idem, mas também apaga volumes nomeados (joga fora o banco). Usar só quando quiser resetar tudo.
- `docker compose logs postgres --tail 20` — últimos 20 logs do serviço `postgres`. Útil para diagnosticar startup.
- `docker compose exec -T postgres psql -U postgres -d vemdancarjp` — abre `psql` dentro do container, conectando como usuário `postgres` ao database `vemdancarjp`. `-T` desativa TTY (necessário em pipe/não-interativo).
- `\l` (dentro do psql) — lista todos os databases.
- `\dt` (dentro do psql) — lista tabelas do database atual.

**Erros:**
- Nenhum. O volume `postgres_data` já continha dados de uma execução anterior (log: "Database directory appears to contain a database; Skipping initialization"), então o Postgres reaproveitou o estado existente.

**Referências:**
- `PLAN.md:431` — definição do Passo 1.
- `docker-compose.yml:5-15` — serviço `postgres` (imagem `postgres:17-alpine`, porta `5433:5432`, volume `postgres_data`, variáveis via `${POSTGRES_*}`).
- `PLAN.md:384` — comando `docker compose exec postgres psql -U postgres -d vemdancarjp` usado para acessar o banco.
- `.gitignore` — confirma que `.env` e `.env.docker` não entram no git (segredos protegidos).
