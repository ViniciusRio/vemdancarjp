# Plano: Backend Java com Spring Boot

## Contexto

Hoje a app Vue 3 usa Supabase para auth (Google OAuth) e escritas administrativas, com RLS para autorização dessas escritas. A consulta pública `GET /api/agenda` já foi integrada localmente ao backend Java e seu PostgreSQL, com testes de integração; o backend ainda não tem deploy de produção. Este plano prevê uma nova base de identidades administrativas no Java, sem importar contas ou permissões do Supabase, e a migração dos dados públicos da agenda. O Supabase permanece operacional até validar os substitutos e o corte dos dados.

**Frontend permanece Vue 3 + Pinia + Vite + Tailwind v4.** A integração futura troca as chamadas Supabase ainda usadas por `authStore`, composables administrativos e serviços por chamadas ao backend Java; `agendaService.ts` já consulta `/api/agenda`.

## Stack

- **Backend:** Java 21 + Spring Boot 3 + Gradle
- **Banco:** PostgreSQL 17 (via Docker, porta `5433:5432`)
- **Auth (direção provisória):** Spring Security OAuth2 Login/OIDC com sessão HTTP, cookie seguro e proteção CSRF; a autorização administrativa consulta o banco no backend
- **Migrations:** Flyway
- **Container:** Docker + Docker Compose

## Estrutura do Projeto

A árvore abaixo é uma referência de responsabilidades para as próximas etapas, não um inventário de classes já implementadas. O contrato de sessão e os nomes das classes futuras serão fechados antes da implementação.

```
/
├── docker-compose.yml          # atual: apenas PostgreSQL
├── docker-compose.override.yml # planejado: dev (hot reload, volumes)
├── .env.docker                 # configuração local, quando necessária
├── backend/
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── build.gradle
│   ├── settings.gradle
│   ├── gradlew
│   ├── src/main/java/com/vemdancarjp/
│   │   ├── VemdancarJpApplication.java
│   │   ├── config/
│   │   │   ├── SecurityConfig.java
│   │   │   └── CorsConfig.java          # proposta, se a API usar outra origem
│   │   ├── controller/
│   │   │   ├── AgendaController.java       # GET /api/agenda (público)
│   │   │   ├── AuthController.java          # proposta: estado da sessão e logout
│   │   │   ├── EventController.java         # CRUD admin
│   │   │   ├── VariableVenueController.java
│   │   │   ├── OtherVenueController.java
│   │   │   └── AdminController.java         # proposta: solicitações, aprovação, rejeição e revogação
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   │   ├── EventRequest.java
│   │   │   │   ├── VenueRequest.java
│   │   │   │   └── PendingAdminRequest.java
│   │   │   └── response/
│   │   │       ├── AgendaResponse.java       # mesmo shape do Agenda do frontend
│   │   │       ├── EventResponse.java
│   │   │       ├── AuthResponse.java         # proposta: estado da sessão, sem tokens próprios
│   │   │       └── PendingAdminResponse.java
│   │   ├── entity/
│   │   │   ├── Event.java
│   │   │   ├── VariableVenue.java
│   │   │   ├── OtherVenue.java
│   │   │   ├── Admin.java
│   │   │   └── PendingAdmin.java
│   │   ├── repository/
│   │   │   ├── EventRepository.java
│   │   │   ├── VariableVenueRepository.java
│   │   │   ├── OtherVenueRepository.java
│   │   │   ├── AdminRepository.java
│   │   │   └── PendingAdminRepository.java
│   │   ├── service/
│   │   │   ├── AgendaService.java            # monta o AgendaResponse agrupado por day_id
│   │   │   ├── EventService.java
│   │   │   ├── VariableVenueService.java
│   │   │   ├── OtherVenueService.java
│   │   │   ├── AdminService.java             # aprovação transacional e revogação
│   │   │   └── AuthService.java              # proposta: identidade Google nova e sessão
│   │   ├── security/                          # proposta: consulta de aprovação por operação
│   │   └── exception/
│   │       ├── ResourceNotFoundException.java
│   │       ├── UnauthorizedException.java
│   │       └── GlobalExceptionHandler.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/
│   │       └── V1__create_tables.sql
│   └── src/test/java/com/vemdancarjp/
│       └── ...
```

## Entidades JPA (Mapeamento das tabelas)

| Tabela Postgres | JPA Entity | Campos principais | Tipo do `id` |
|---|---|---|---|
| `events` | `Event` | `id` (BIGINT), `day_id` (VARCHAR), `name`, `venue`, `neighborhood`, `instagram`, `frequency`, `sort_order` | `Long` |
| `variable_venues` | `VariableVenue` | `id` (BIGINT), `name`, `neighborhood`, `instagram`, `days`, `sort_order` | `Long` |
| `other_venues` | `OtherVenue` | `id` (BIGINT), `name`, `neighborhood`, `instagram`, `sort_order` | `Long` |
| `admins` | `Admin` | `id` (UUID), `email` | `UUID` |
| `pending_admins` | `PendingAdmin` | `id` (UUID), `email`, `name`, `requested_at` | `UUID` |

Esta tabela descreve **somente o mapeamento atual da V1**, não o modelo final de autenticação. No fluxo atual, o frontend usa `session.user.id` do Supabase como `id` de `admins` e `pending_admins`. Esses UUIDs e os registros administrativos legados **não serão importados**. A nova autenticação identificará administradores e pendências pelo Google `sub` textual validado pelo backend, sem mapear UUID Supabase para `sub` nem conceder acesso por correspondência de email.

**Modelo futuro ainda não implementado:** adaptar `admins` e `pending_admins` para usar um identificador Google textual único, sem tabela de vínculo legado. Definir a forma exata da migration aditiva após conferir o estado do PostgreSQL e os contratos do backend; não alterar a V1 já aplicada nem presumir que seus UUIDs representam `sub`. O primeiro administrador precisa de um procedimento interno de bootstrap no backend, executado de forma controlada após validação da identidade Google, sem endpoint público de autoaprovação. Aprovação, rejeição e revogação posteriores pertencem exclusivamente ao backend.

> **Convenção de nomenclatura:** o banco usa `snake_case` (`day_id`, `sort_order`); as entidades JPA usam `camelCase` (`dayId`, `sortOrder`) com `@Column(name = "day_id")` explícito em cada campo composto.

## Migration Flyway: `V1__create_tables.sql`

```sql
CREATE TABLE events (
    id          BIGSERIAL PRIMARY KEY,
    day_id      VARCHAR(10)  NOT NULL,
    name        TEXT          NOT NULL,
    venue       TEXT          NOT NULL,
    neighborhood TEXT         NOT NULL,
    instagram   TEXT,
    frequency   TEXT,
    sort_order  INTEGER       NOT NULL DEFAULT 0
);

CREATE TABLE variable_venues (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    neighborhood TEXT NOT NULL,
    instagram   TEXT,
    days        TEXT NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE other_venues (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    neighborhood TEXT NOT NULL,
    instagram   TEXT,
    sort_order  INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE admins (
    id    UUID PRIMARY KEY,
    email TEXT NOT NULL UNIQUE
);

CREATE TABLE pending_admins (
    id           UUID PRIMARY KEY,
    email        TEXT NOT NULL UNIQUE,
    name         TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_day_id     ON events(day_id);
CREATE INDEX idx_events_sort_order ON events(sort_order);
```

> **Dados:** a V1 contém apenas DDL e mostra o esquema inicial, não a futura base administrativa. Preservar e reconciliar `events`, `variable_venues` e `other_venues` na migração da agenda. Não importar `admins`, `pending_admins`, contas ou identificadores do Supabase; a nova aprovação administrativa começa no backend e não pode ser inferida de emails ou linhas legadas.

## Endpoints da API

Somente `GET /api/agenda` está implementado. Todos os demais endpoints abaixo são **propostas**, sujeitas à definição do contrato de sessão e à implementação conjunta de segurança e testes.

### Públicos (sem auth)

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/agenda` | Busca agenda completa (eventos agrupados por `day_id` + venues) |

### Autenticação

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/oauth2/authorization/google` | Inicia o OAuth2 Login no backend; caminho padrão do Spring Security, a confirmar |
| `GET` | `/api/auth/session` | Retorna o estado da sessão atual para o frontend |
| `POST` | `/api/auth/logout` | Invalida a sessão e encerra o login local, com proteção CSRF |

### Admin (propostos; exigem sessão válida e aprovação verificada no backend)

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/admin/events` | Lista todos os eventos |
| `POST` | `/api/admin/events` | Cria evento |
| `PUT` | `/api/admin/events/{id}` | Atualiza evento |
| `DELETE` | `/api/admin/events/{id}` | Deleta evento |
| `GET` | `/api/admin/variable-venues` | Lista lugares variáveis |
| `POST` | `/api/admin/variable-venues` | Cria lugar variável |
| `PUT` | `/api/admin/variable-venues/{id}` | Atualiza lugar variável |
| `DELETE` | `/api/admin/variable-venues/{id}` | Deleta lugar variável |
| `GET` | `/api/admin/other-venues` | Lista outros lugares |
| `POST` | `/api/admin/other-venues` | Cria outro lugar |
| `PUT` | `/api/admin/other-venues/{id}` | Atualiza outro lugar |
| `DELETE` | `/api/admin/other-venues/{id}` | Deleta outro lugar |
| `GET` | `/api/admin/pending-admins` | Lista admins pendentes |
| `POST` | `/api/admin/approve-admin/{id}` | Aprova em transação (move `pending_admins` → `admins`); formato do identificador proposto ainda a definir |
| `DELETE` | `/api/admin/reject-admin/{id}` | Rejeita admin (deleta de `pending_admins`) |
| `DELETE` | `/api/admin/admins/{id}` | Revoga a aprovação de um administrador |

> **Autorização:** cada operação em `/api/admin/**` exige sessão autenticada e consulta server-side ao estado aprovado no banco novo, inclusive aprovação, rejeição e revogação. O backend usa o `sub` obtido da identidade Google validada e não aceita role, email ou identidade enviados pelo frontend como prova de privilégio. Usuário pendente não pode aprovar a si mesmo. O bootstrap inicial é interno e controlado, sem autoaprovação pública. Ausência de sessão retorna 401; falta de aprovação retorna 403. `GET /api/agenda` permanece público, sem login.

## Fluxo de Autenticação (direção provisória: Google OIDC → sessão HTTP)

1. O Vue direciona o navegador ao início do OAuth2 Login do backend. O Spring Security conduz o fluxo com Google e valida a resposta OIDC, incluindo assinatura, emissor, público e expiração do token de identidade.
2. O backend usa o `sub` textual validado para consultar a nova base administrativa. Nenhuma conta ou aprovação legada do Supabase recebe privilégio automaticamente; o primeiro administrador entra por bootstrap interno controlado e os demais solicitam aprovação no backend.
3. O Spring Boot cria uma sessão HTTP e envia cookie `Secure`, `HttpOnly` e `SameSite` apropriado à origem final. O frontend não guarda JWT próprio nem refresh token.
4. O Vue consulta o estado da sessão e envia o cookie nas chamadas à API. Operações que alteram estado exigem token CSRF; logout invalida a sessão. Sessão expirada retorna 401; usuário autenticado sem aprovação recebe 403 ao acessar a administração.
5. Cada operação administrativa revalida a aprovação no backend para que a revogação tenha efeito sem depender de uma role gravada na sessão. Aprovação de pendência lê os dados persistidos e promove o usuário em uma transação.

O frontend no GitHub Pages com domínio próprio e a API em possível subdomínio têm origens diferentes. Antes de implementar cookies e CORS, confirmar o domínio efetivo, HTTPS, origens permitidas, política `SameSite` e comportamento em desenvolvimento. Esta direção substitui o desenho anterior de JWT próprio e refresh tokens, mas ainda não está implementada.

## Shape do `AgendaResponse` (alinhado com o frontend)

O endpoint `GET /api/agenda` deve retornar **exatamente o mesmo shape** que `agendaService.ts:fetchAgenda()` retorna hoje, para minimizar mudanças no frontend:

```json
{
  "city": "João Pessoa, PB",
  "title": "Vem Dançar JP",
  "subtitle": "Agenda fixa semanal de Forró Pé de Serra",
  "lastUpdated": "2024-01-15",
  "days": [
    {
      "id": "monday",
      "label": "Segunda-feira",
      "events": [
        { "name": "...", "venue": "...", "neighborhood": "...", "instagram": "...", "frequency": "..." }
      ]
    }
  ],
  "variableVenues": [
    { "name": "...", "neighborhood": "...", "days": "...", "instagram": "..." }
  ],
  "otherVenues": [
    { "name": "...", "neighborhood": "...", "instagram": "..." }
  ]
}
```

O backend (`AgendaService`) já faz o agrupamento por `day_id` e a tradução dos labels (`monday` → `Segunda-feira`); `src/services/agendaService.ts` já consulta `GET /api/agenda` no fluxo local. Preservar esse contrato e o acesso sem login durante as próximas etapas.

## Docker

O frontend permanece no GitHub Pages com domínio próprio. Para a API, consideram-se provisoriamente hospedagem gerenciada no Railway, possível endereço `api.vemdancarjp.com.br` e PostgreSQL persistente. Nada disso está contratado ou configurado. A escolha final de infraestrutura deve preceder a configuração de domínio, HTTPS, cookies, CORS, persistência de dados e sessões.

### `docker-compose.yml` (exemplo planejado; backend ainda não configurado)

```yaml
services:
  postgres:
    image: postgres:17-alpine
    container_name: postgres_db
    restart: always
    shm_size: 128mb
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "5433:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  backend:
    build: ./backend
    container_name: backend_app
    restart: always
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET}
    depends_on:
      - postgres

volumes:
  postgres_data:
```

### `docker-compose.override.yml` (dev — hot reload)

> O Docker Compose mescla `docker-compose.yml` + `docker-compose.override.yml` automaticamente quando se roda `docker compose up` sem `--no-deps`. Em dev, este override habilita hot reload.

```yaml
services:
  backend:
    volumes:
      - ./backend:/app
      - ~/.gradle:/root/.gradle
    environment:
      SPRING_PROFILES_ACTIVE: dev
    command: ["./gradlew", "bootRun"]
```

> Este exemplo de Docker não define a hospedagem de produção. O `docker-compose.yml` atual contém apenas PostgreSQL; serviço Java, Dockerfile e override abaixo continuam planejados.

### `backend/Dockerfile` (multi-stage)

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradle/ ./gradle/
COPY gradlew build.gradle settings.gradle ./
RUN ./gradlew dependencies || true
COPY src/ ./src/
RUN ./gradlew build -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### `backend/.dockerignore`

```
.gradle
build
.idea
*.iml
.git
.env
```

### `.env.docker`

```env
# PostgreSQL
POSTGRES_DB=vemdancarjp
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# Spring
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/vemdancarjp
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres

# Google OAuth
GOOGLE_CLIENT_ID=<seu_client_id>
GOOGLE_CLIENT_SECRET=<segredo_servidor>
```

### Comandos Docker

Os comandos que mencionam `backend` só se aplicam após implementar o serviço planejado no Compose.

```sh
# Rodar tudo (banco + backend) em dev
docker compose up

# Rodar em background
docker compose up -d

# Parar tudo
docker compose down

# Rebuild após mudanças no Dockerfile
docker compose up --build

# Ver logs do backend
docker compose logs -f backend

# Acessar banco
docker compose exec postgres psql -U postgres -d vemdancarjp
```

## SOLID na Prática

| Princípio | Onde aplicar |
|---|---|
| **SRP** | Cada Service cuida de uma entidade (`EventService` só de events) |
| **OCP** | `AgendaService` pode ser estendido sem mudar `AgendaController` |
| **LSP** | Subclasses de `RuntimeException` (`ResourceNotFoundException`) funcionam onde `Exception` é esperada |
| **ISP** | Interfaces de repositório segregadas por entidade |
| **DIP** | `Controller` depende de `Service` (interface), não de implementação |

## Clean Code

- Nomes descritivos: `findAllByDayIdOrderBySortOrder()` não `getStuff()`
- Métodos pequenos: cada função faz uma coisa
- Validação com annotations: `@NotBlank`, `@Size`, `@Valid`
- Exception handling global: `@RestControllerAdvice` em `GlobalExceptionHandler`
- DTOs: nunca expor entidades JPA diretamente na API
- `final` em campos imutáveis; construtor por `@RequiredArgsConstructor` (Lombok)

## Fluxo de Aprendizado (obrigatório em cada passo)

Cada um dos passos da "Ordem de Implementação" abaixo **deve** seguir esta sequência antes de avançar para o próximo. O objetivo é que o dono do projeto entenda o que está sendo feito e por quê — não só receber código pronto.

1. **Explicar antes de escrever** — Antes de criar/editar qualquer arquivo, explicar em pt-BR:
   - O que este passo resolve e por que é necessário (conceito, não só código).
   - Quais arquivos serão tocados e por quê.
   - Quais decisões de design estão embutidas (ex.: por que essa dependência, esse padrão, essa porta).
   - Referências `file:line` a PLAN.md ou ao código existente quando relevante.
2. **Aguardar confirmação** — Parar e esperar o usuário dizer "pode ir" (ou ajustar escopo) antes de implementar. Não avançar sozinho.
3. **Implementar** — Fazer a menor mudança que satisfaça o passo. Sem refactors não relacionados.
4. **Resumir o diff** — Explicar brevemente, em pt-BR, o que cada bloco novo faz, apontando `file:line` das partes mais relevantes. Conectar código ↔ conceito, não reescrever o código em prosa.
5. **Verificar** — Rodar a verificação aplicável: `./gradlew build` (backend) e/ou `npm run lint && npm run type-check && npm run test:unit` (frontend). Reportar o resultado.
6. **Registrar aprendizado em `LEARNING.md`** — Adicionar uma nova entrada no topo do arquivo seguindo o template do `LEARNING.md`:
   - `Passo N — <título>` (igual ao título da lista abaixo).
   - **Conceito:** 1-3 frases sobre o que este passo ensinou.
   - **Comandos:** comandos novos/surpreendentes com o que fazem.
   - **Erros:** erros encontrados e a resolução aplicada.
   - **Referências:** links/`file:line` citados.
7. **Só então avançar para o próximo passo.**

> **Modo direto:** se o usuário disser "só faz", "sem enrola" ou similar, pular as etapas 1, 2 e 4 — mas **nunca** pular a etapa 6 (LEARNING.md).

## Ordem de Implementação

Cada item abaixo é um **Passo** numerado. Ao concluir um Passo, registre uma entrada em `LEARNING.md` usando o **mesmo número e título** (ex.: o item `1. Passo 1 — Docker + PostgreSQL` vira a entrada `### Passo 1 — Docker + PostgreSQL` no `LEARNING.md`). A numeração é a âncora entre o plano e o caderno de aprendizado.

1. **Passo 1 — Docker + PostgreSQL** — usar o `docker-compose.yml` existente (Postgres 17, porta 5433). Confirmar que o banco sobe.
2. **Passo 2 — Setup Gradle + Spring Boot** — `backend/build.gradle` com starters `web`, `data-jpa`, `validation`, `security`, `flyway`, `postgresql`. Conectar ao Docker Postgres.
3. **Passo 3 — Flyway `V1__create_tables.sql`** — rodar a migration e validar o schema.
4. **Passo 4 — Entidades + Repositórios** — mapear JPA entities com `@Column(name = "...")` explícito.
5. **Passo 5 — `AgendaService` + `AgendaController`** — endpoint público `GET /api/agenda` retornando o shape completo. Testar com `curl`.
6. **Passo 6 — Planejar a nova base administrativa** — definir como `admins` e `pending_admins` passarão a identificar novos usuários pelo Google `sub` textual, sem importar UUIDs, contas, solicitações ou aprovações legadas. Planejar a migration aditiva sem editar a V1, o bootstrap interno e controlado do primeiro administrador e testes de unicidade e ausência de autoaprovação. Não associar por email. Registrar a decisão e seus testes.
7. **Passo 7 — OAuth2 Login, sessão e proteção inicial** — configurar Spring Security com Google OIDC, sessão HTTP, cookie seguro, CSRF e CORS conforme a origem confirmada; implementar o modelo administrativo decidido no Passo 6 e o bootstrap controlado, sem endpoint público de autoaprovação. Restringir por padrão as futuras rotas administrativas e manter `GET /api/agenda` público. Criar testes de autenticação, 401/403, CSRF, logout, expiração, bootstrap e leitura pública neste passo.
8. **Passo 8 — CRUD admin protegido** — implementar escrita de events e venues somente com sessão e aprovação verificadas no backend. Testar autorização e persistência junto de cada endpoint; não publicar CRUD administrativo sem esses testes.
9. **Passo 9 — Gestão administrativa protegida** — implementar solicitação de acesso, listagem, aprovação transacional, rejeição e revogação exclusivamente no backend, usando a nova base administrativa. Testar usuário pendente, autoaprovação, falha parcial, revogação de sessão ativa e concorrência; não importar pendências ou aprovações do Supabase.
10. **Passo 10 — Corte da agenda e conexão do frontend** — definir fonte de verdade e reconciliação de `events`, `variable_venues` e `other_venues` entre Supabase e PostgreSQL antes do corte das escritas; conectar `authStore` e composables administrativos à nova API por sessão e CSRF. `agendaService.ts` já consulta `/api/agenda`. Validar dados públicos, novo login, nova administração e agenda pública antes de retirar qualquer dependência Supabase.
11. **Passo 11 — Verificação complementar** — ampliar testes unitários, de integração e manuais dos fluxos completos, além dos testes de segurança já exigidos nos Passos 7–9. Confirmar rollback dos dados da agenda e do frontend, incluindo o efeito sobre contas criadas apenas na nova base administrativa, antes de desativar o Supabase.

## Comandos Úteis (Gradle)

```sh
# Rodar o backend (fora do Docker, em dev)
./gradlew bootRun

# Build
./gradlew build

# Testes
./gradlew test

# Limpar e rebuild
./gradlew clean build

# Rodar apenas testes de integração
./gradlew test --tests "*IntegrationTest"
```

## Frontend: pontos de mudança

Ao conectar o frontend ao backend Java, estes arquivos mudam:

| Arquivo | Mudança |
|---|---|
| `src/services/supabase.ts` | Manter enquanto auth e escritas administrativas dependerem do Supabase; remover somente após o corte validado |
| `src/services/agendaService.ts` | Já usa `GET /api/agenda`; preservar o contrato público e configurar apenas a origem da API quando definida |
| `src/stores/authStore.ts` | Substituir o login Supabase por estado de sessão fornecido pelo backend; tratar 401, 403 e logout |
| `src/composables/usePendingAdmins.ts` e composables administrativos | Substituir chamadas Supabase por operações protegidas com cookie e CSRF, após endpoints e dados estarem validados |
| `.env` e `package.json` | Ajustar origem pública da API; manter variáveis e SDK Supabase até a validação dos substitutos; segredo Google fica apenas no backend |

## Rollback

Antes do corte, identificar a fonte de verdade de `events`, `variable_venues` e `other_venues`, obter backup e comparar os dados públicos importados. Durante a transição, evitar escritas simultâneas sem reconciliação definida: a agenda pública já lê PostgreSQL no backend, enquanto o painel ainda escreve no Supabase. As tabelas administrativas legadas não são fonte de dados para importação.

Se o corte falhar, interromper novas escritas na agenda de destino, reconciliar as alterações públicas ocorridas desde o último ponto comum e só então redirecionar o frontend para o fluxo anterior. Reverter chamadas ou URL sem reconciliar dados pode perder alterações ou deixar a agenda pública divergente. O retorno temporário ao login e à administração Supabase usa somente as contas e aprovações que ainda existirem lá: administradores criados apenas no backend não terão acesso legado automaticamente, e mudanças de aprovação entre as bases exigem revisão manual. Manter o Supabase operacional até validar o substituto e o procedimento de retorno. Mudanças de esquema devem ser aditivas e reversíveis sem editar a V1 aplicada.

## Decisões diferidas (fora do escopo deste plano)

- **Valkey + RabbitMQ:** ver `PLAN-VALKEY-RABBITMQ.md`. Adicionar apenas quando houver problema real que eles resolvem (cache de leitura, rate-limiting, filas para notificação). Prematuro para um CRUD de 5 tabelas.
- **Sessões em múltiplas instâncias:** definir persistência compartilhada somente se a topologia final exigir; iniciar com uma instância é hipótese, não implantação aprovada.
- **Esquema administrativo novo:** definir a migration aditiva e o contrato dos identificadores textuais após confirmar o estado do PostgreSQL; não há vínculo legado a migrar. Detalhar o procedimento interno de bootstrap antes de disponibilizar operações administrativas.
- **Rate limiting no endpoint público:** adicionar depois se houver abuso. Por ora, o Spring MVC + Postgres local aguenta o tráfego.
