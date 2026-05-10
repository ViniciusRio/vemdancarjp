# Vem Dançar JP

Weekly forró pé de serra schedule for João Pessoa, PB, Brazil.

🌐 [vemdancarjp.com.br](https://vemdancarjp.com.br)

## About

A website for browsing and managing the weekly forró pé de serra schedule in João Pessoa. The agenda is maintained by administrators through a built-in dashboard, with no need to edit code.

## Stack

- [Vue 3](https://vuejs.org/) with Composition API
- [TypeScript](https://www.typescriptlang.org/)
- [Vite](https://vitejs.dev/)
- [Tailwind CSS](https://tailwindcss.com/)
- [Pinia](https://pinia.vuejs.org/) for state management
- [Supabase](https://supabase.com/) for database and authentication
- Deployed via [GitHub Pages](https://pages.github.com/) with GitHub Actions

## Project structure

```
src/
  features/
    agenda/
      components/   # EventCard, AgendaList, DayFilter, etc.
      composables/  # useAgenda
      types/        # TypeScript interfaces
  stores/           # agendaStore, authStore
  services/         # agendaService, supabase
  composables/      # useAgenda
  views/            # AgendaHomeView, admin views
  router/           # Vue Router
```

## Local setup

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

# lint
npm run lint
```

## Environment variables

Create a `.env` file at the project root:

```
VITE_SUPABASE_URL=your_supabase_url
VITE_SUPABASE_ANON_KEY=your_anon_key
```

## Deployment

Deployment is automatic via GitHub Actions on every push to the `main` branch. Environment variables must be set under **Settings → Secrets and variables → Actions** in the repository.
