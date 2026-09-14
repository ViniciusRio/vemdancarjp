# Plano: Backend Java com Spring Boot

## Contexto

Hoje a app Vue 3 usa Supabase para auth (Google OAuth), banco (PostgreSQL gerenciado) e RLS para autorização. Este plano substitui o Supabase por um backend Java próprio, mantendo o mesmo banco PostgreSQL (agora via Docker) e assumindo controle direto de auth, autorização e CRUD.

**Frontend permanece Vue 3 + Pinia + Vite + Tailwind v4.** Apenas a camada de service (`src/services/`) e o `authStore` trocam chamadas Supabase por chamadas HTTP ao backend Java.

## Stack

- **Backend:** Java 21 + Spring Boot 3 + Gradle
- **Banco:** PostgreSQL 17 (via Docker, porta `5433:5432`)
- **Auth:** Spring Security + OAuth2 Resource Server (JWT) + validação de Google ID token
- **Migrations:** Flyway
- **Container:** Docker + Docker Compose

## Estrutura do Projeto

```
/
├── docker-compose.yml          # prod (Postgres + backend)
├── docker-compose.override.yml # dev (hot reload, volumes)
├── .env.docker
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
│   │   │   ├── JwtConfig.java
│   │   │   └── CorsConfig.java
│   │   ├── controller/
│   │   │   ├── AgendaController.java       # GET /api/agenda (público)
│   │   │   ├── AuthController.java          # POST /api/auth/google, /refresh
│   │   │   ├── EventController.java         # CRUD admin
│   │   │   ├── VariableVenueController.java
│   │   │   ├── OtherVenueController.java
│   │   │   └── AdminController.java         # pending_admins approve/reject
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   │   ├── GoogleLoginRequest.java  # { idToken: string }
│   │   │   │   ├── EventRequest.java
│   │   │   │   ├── VenueRequest.java
│   │   │   │   └── PendingAdminRequest.java
│   │   │   └── response/
│   │   │       ├── AgendaResponse.java       # mesmo shape do Agenda do frontend
│   │   │       ├── EventResponse.java
│   │   │       ├── AuthResponse.java         # { accessToken, refreshToken }
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
│   │   │   ├── AdminService.java             # approve/reject pending
│   │   │   └── AuthService.java              # valida Google ID token, emite JWT
│   │   ├── security/
│   │   │   ├── JwtTokenProvider.java         # gera accessToken + refreshToken
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   ├── GoogleTokenVerifier.java      # valida Google ID token server-side
│   │   │   └── CustomUserDetailsService.java
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

> **Seed:** após criar as tabelas, inserir manualmente os registros existentes no Supabase (export via `pscopy` ou script de migração de dados). O Flyway roda apenas DDL; a migração de dados é um passo separado.

## Endpoints da API

### Públicos (sem auth)

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/agenda` | Busca agenda completa (eventos agrupados por `day_id` + venues) |

### Autenticação

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/auth/google` | Recebe `{ idToken }` (Google ID token), valida, emite `{ accessToken, refreshToken }` |
| `POST` | `/api/auth/refresh` | Recebe `{ refreshToken }`, emite novo `{ accessToken }` |

### Admin (protegidos — requer JWT com role `ADMIN`)

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
| `POST` | `/api/admin/approve-admin/{id}` | Aprove admin (move `pending_admins` → `admins`) |
| `DELETE` | `/api/admin/reject-admin/{id}` | Rejeita admin (deleta de `pending_admins`) |

> **Autorização:** todos os endpoints sob `/api/admin/**` exigem JWT válido **e** role `ADMIN` (verificado via `CustomUserDetailsService` consultando a tabela `admins`). O `approve-admin` e `reject-admin` também exigem `ADMIN` — um `pending_admin` **não pode** se aprovar.

## Fluxo de Autenticação (Google OAuth → JWT próprio)

```
┌──────────┐       ┌──────────┐       ┌──────────────────┐
│ Frontend │──────▶│  Google  │──────▶│  Backend Java    │
│  (Vue)   │  1.   │  Sign-In │  2.   │                  │
│          │ login │          │ idToken│                  │
│          │  OAuth│          │       │ 3. valida idToken│
│          │       │          │       │    via Google API│
│          │       │          │       │ 4. consulta       │
│          │       │          │       │    admins/pending │
│          │◀──────│          │◀──────│ 5. emite JWT      │
│          │       │          │       │    (access+refresh)│
│          │ 6. armazena JWT, │       │                  │
│          │ envia no header  │       │                  │
│          │ Authorization    │       │                  │
└──────────┘       └──────────┘       └──────────────────┘
```

**Passos detalhados:**

1. Usuário clica em "Entrar com Google" no frontend Vue.
2. Frontend faz `signInWithGoogle()` usando Google Identity Services (não Supabase), recebe um **Google ID token** (`credential.idToken`).
3. Frontend envia `POST /api/auth/google` com `{ "idToken": "<google_id_token>" }`.
4. Backend `AuthService` chama `GoogleTokenVerifier` que valida o token via `https://oauth2.googleapis.com/tokeninfo?id_token=...` (ou `google-api-client`):
   - Verifica `aud` (client ID da app).
   - Verifica `iss` (`accounts.google.com` ou `https://accounts.google.com`).
   - Verifica `exp` (não expirado).
   - Extrai `sub` (Google user ID), `email`, `name`.
5. Backend consulta `admins` por `id = sub` (UUID do Google):
   - Se existe → role `ADMIN`, emite JWT com claim `role: "ADMIN"`.
   - Se não existe, consulta `pending_admins`:
     - Se existe → role `PENDING`, emite JWT com claim `role: "PENDING"`.
     - Se não existe → auto-insere em `pending_admins` (igual ao comportamento atual do `authStore.ts:57`), role `PENDING`.
   - Emite `{ accessToken (15min), refreshToken (7d) }` assinados com `HS256` ou `RS256`.
6. Frontend armazena `accessToken` em memória (Pinia `authStore`) e `refreshToken` em `httpOnly` cookie ou `sessionStorage` (decidir).
7. Em cada requisição autenticada, frontend envia `Authorization: Bearer <accessToken>`.
8. `JwtAuthenticationFilter` valida o token, popula `SecurityContext` com o `Authentication` (incluindo `role`).
9. Endpoints `/api/admin/**` bloqueiam se `role != ADMIN` via `@PreAuthorize("hasRole('ADMIN')")`.

**Refresh:** quando o `accessToken` expira, frontend chama `POST /api/auth/refresh` com o `refreshToken`; backend valida e emite novo `accessToken`.

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

O backend (`AgendaService`) faz o agrupamento por `day_id` e a tradução dos labels (`monday` → `Segunda-feira`) igual ao `agendaService.ts:4-12`. Assim o frontend só troca a URL base de `supabase.from(...)` para `fetch('/api/agenda')`.

## Docker

### `docker-compose.yml` (produção)

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
      JWT_SECRET: ${JWT_SECRET}
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

> Em **produção**, rodar com `docker compose -f docker-compose.yml up` (sem o override) ou usar `docker compose --profile prod up`. O Dockerfile multi-stage builda o `.jar` e roda sem mounts de código.

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

# JWT
JWT_SECRET=<chave_secreta_min_256_bits>
```

### Comandos Docker

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
6. **Passo 6 — CRUD admin (Service + Controller)** — events, venues. Sem auth ainda. Testar com Postman/Insomnia.
7. **Passo 7 — Spring Security + JWT + Google token verifier** — `SecurityConfig`, `JwtTokenProvider`, `GoogleTokenVerifier`, `AuthService`, `AuthController`. Implementar `POST /api/auth/google` e `/refresh`.
8. **Passo 8 — `@PreAuthorize("hasRole('ADMIN')")` nos endpoints admin** — validar que pending não pode aprovar.
9. **Passo 9 — `AdminController` (approve/reject pending)** — último, pois depende do auth estar completo.
10. **Passo 10 — Conectar frontend** — substituir `supabase` em `agendaService.ts` por `fetch('/api/agenda')`; substituir `authStore` para usar `POST /api/auth/google` + JWT. Atualizar `.env`.
11. **Passo 11 — Testes unitários e de integração** — `@WebMvcTest` para controllers, `@DataJpaTest` para repos, testes de segurança para `approve-admin`.

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
| `src/services/supabase.ts` | **Remover** (não há mais cliente Supabase) |
| `src/services/agendaService.ts` | Trocar `supabase.from('events')...` por `fetch('/api/agenda')` |
| `src/stores/authStore.ts` | Trocar `signInWithOAuth` por Google Identity Services + `POST /api/auth/google`; armazenar JWT em memória |
| `.env` | Remover `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`; adicionar `VITE_API_URL=http://localhost:8080/api` |
| `package.json` | Remover `@supabase/supabase-js`; adicionar Google Identity Services lib se necessário |

## Rollback

Se a migração para o backend Java falhar e for preciso voltar para Supabase:

1. O frontend Vue **não é deletado** — apenas a camada de service muda. Reverter `agendaService.ts` e `authStore.ts` para a versão Supabase via `git checkout`.
2. O `docker-compose.yml` atual (Postgres 17) permanece — pode rodar o banco localmente para desenvolvimento sem o backend Java.
3. Os dados no Supabase cloud **não são apagados** durante a migração (são copiados). O rollback é apenas trocar a URL de service de volta para Supabase.
4. `PLAN-VALKEY-RABBITMQ.md` fica **deferido** — não implementar Valkey/RabbitMQ nesta fase; adicionar quando houver requisito real (cache, rate-limit, mensageria).

## Decisões diferidas (fora do escopo deste plano)

- **Valkey + RabbitMQ:** ver `PLAN-VALKEY-RABBITMQ.md`. Adicionar apenas quando houver problema real que eles resolvem (cache de leitura, rate-limiting, filas para notificação). Prematuro para um CRUD de 5 tabelas.
- **Refresh token rotation / revocation:** implementar versão básica agora; adicionar denylist de refresh tokens quando houver necessidade de logout server-side.
- **Rate limiting no endpoint público:** adicionar depois se houver abuso. Por ora, o Spring MVC + Postgres local aguenta o tráfego.
