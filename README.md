# Vem Dançar JP

Weekly forró pé de serra schedule for João Pessoa, PB, Brazil.

🌐 [vemdancarjp.com.br](https://vemdancarjp.com.br)

## About

A website for browsing and managing the weekly forró pé de serra schedule in João Pessoa. The agenda is maintained by administrators through a built-in dashboard, with no need to edit code.

The project is mid-migration from Supabase to a self-hosted Java + Spring Boot backend (see `PLAN.md`). Today the Vue app runs on Supabase; the `backend/` directory and `docker-compose.yml` prepare the next stage.

## Stack

**Frontend (current)**

- [Vue 3](https://vuejs.org/) with Composition API + `<script setup lang="ts">`
- [TypeScript](https://www.typescriptlang.org/)
- [Vite](https://vitejs.dev/)
- [Tailwind CSS v4](https://tailwindcss.com/)
- [Pinia](https://pinia.vuejs.org/) for state management
- [Supabase](https://supabase.com/) for database and Google OAuth authentication (being replaced by the Java backend)
- Deployed via [GitHub Pages](https://pages.github.com/) with GitHub Actions

**Backend (in progress — `PLAN.md`)**

- Java 21 + Spring Boot 3 + Gradle
- PostgreSQL 17 (via Docker, host port `5433`)
- Spring Security + OAuth2 Resource Server (JWT) + Google ID token verification
- Flyway migrations
- Docker + Docker Compose

## Project structure

```
/
├── src/                         # Vue 3 frontend
│   ├── features/agenda/
│   │   ├── components/           # EventCard, AgendaList, DayFilter, etc.
│   │   ├── composables/         # useAgendaAdmin, useAdminVenues
│   │   └── types/               # domain TypeScript interfaces
│   ├── stores/                  # agendaStore, authStore
│   ├── services/                # agendaService, supabase
│   ├── views/                   # AgendaHomeView, admin views
│   └── router/                  # Vue Router
├── backend/                     # Java + Spring Boot (WIP)
│   ├── build.gradle
│   └── src/main/java/com/vemdancarjp/
│       ├── config/              # SecurityConfig, JwtConfig, CorsConfig
│       ├── controller/          # AgendaController, AuthController, AdminController
│       ├── dto/                 # request/response DTOs
│       ├── entity/              # JPA entities (events, venues, admins)
│       ├── repository/          # Spring Data JPA repositories
│       └── service/             # AgendaService, AuthService, EventService, ...
├── docker-compose.yml           # Postgres + backend (prod)
├── docs/                        # AI workflow, model strategy, cost control
├── PLAN.md                      # backend migration plan
├── LEARNING.md                 # step-by-step learning log for the migration
├── AGENTS.md                    # rules for AI agents working in this repo
└── .opencode/                  # opencode agents, commands, skills
```

## Local setup — frontend

```sh
# install dependencies
npm install

# create environment variables file
cp .env.example .env
# fill in VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY

# run in development
npm run dev

# build for production
npm run build

# run unit tests
npm run test:unit

# lint (oxlint + eslint, with auto-fix)
npm run lint

# format
npm run format
```

## Local setup — backend (WIP)

The backend is scaffolded under `backend/` and runs against PostgreSQL 17 in Docker. See `PLAN.md` for the full step-by-step plan.

```sh
# start Postgres + backend
docker compose up

# or run the backend outside Docker (after the DB is up)
./backend/gradlew bootRun
```

Environment lives in `.env.docker` (Postgres credentials, `GOOGLE_CLIENT_ID`, `JWT_SECRET`).

## Commands

| Task | Command |
|---|---|
| Dev server | `npm run dev` |
| Type check | `npm run type-check` |
| Lint + fix | `npm run lint` |
| Format | `npm run format` |
| Build | `npm run build` |
| Unit tests | `npm run test:unit` |
| Backend build | `./backend/gradlew build` |
| Backend tests | `./backend/gradlew test` |
| Full verification | `npm run lint && npm run type-check && npm run test:unit` |

## Environment variables

Frontend (`.env`):

```
VITE_SUPABASE_URL=your_supabase_url
VITE_SUPABASE_ANON_KEY=your_anon_key
```

Backend (`.env.docker`, WIP):

```
POSTGRES_DB=vemdancarjp
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
GOOGLE_CLIENT_ID=<your_client_id>
JWT_SECRET=<min_256_bits_secret>
```

## Deployment

Frontend deployment is automatic via GitHub Actions (`.github/workflows/deploy.yml`) on every push to `main`. It runs type-check, lint, unit tests and build, then publishes `dist/` to GitHub Pages with the CNAME `vemdancarjp.com.br`.

Frontend secrets (`VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`) must be set under **Settings → Secrets and variables → Actions** in the repository. Backend deployment is not configured yet.

## Database tables

- `events` — weekly forró events grouped by `day_id`
- `variable_venues` — venues that change days
- `other_venues` — additional venues
- `admins` — authorized admin Google accounts
- `pending_admins` — accounts waiting for approval by an admin

## AI assistant setup

This repo is configured for [opencode](https://opencode.ai). See `AGENTS.md` for the rules agents follow, `.opencode/skills/` for specialized knowledge (agenda-dev, architect, security-reviewer), and `docs/AI-*.md` for model strategy, cost control and prompt patterns.

<img width="1845" height="876" alt="image" src="https://github.com/user-attachments/assets/10782a28-cbe1-4634-aec3-62f1a25debfd" />
