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

### Integração local frontend ↔ backend (entre Passo 5 e Passo 6)

**Conceito:** Antecipamos a conexão do frontend Vue com o backend Java (prevista formalmente no Passo 10) só pra agenda pública, pra validar ponta-a-ponta. Três conceitos importantes:

1. **Proxy do Vite** (`vite.config.ts: server.proxy`) — em dev, o frontend roda na porta 5174 (Vite) e o backend na 8080 (Spring Boot). Sem proxy, o browser bloquearia requisições cross-origin (CORS). O proxy faz o Vite encaminhar qualquer `/api/*` pra `http://localhost:8080`, então o browser pensa que tá falando com o próprio Vite — sem CORS, sem configuração extra no backend. Em produção, o frontend é build estático e servido pelo mesmo host do backend (ou um nginx/CDN), então CORS deixa de ser questão.

2. **Troca do `agendaService`** — antes: 3 chamadas Supabase em paralelo (`supabase.from('events').select(...)`, etc.) com agrupamento e tradução de labels no frontend. Depois: 1 única chamada `fetch('/api/agenda')` que devolve o JSON já montado pelo `AgendaService` do backend. O shape é idêntico, então stores e components não mudaram. O `API_URL` vem de `VITE_API_URL` no `.env` (vazio em dev = usa o proxy relativo; em prod aponta pra URL real).

3. **Integração parcial** — só a agenda pública migrou pro backend Java. A auth (login Google, `pending_admins`, `isAdmin`) ainda usa Supabase (`authStore.ts` e `supabase.ts` intactos). Isso é intencional: a auth só migra no Passo 7. Por ora, a home vê dados do backend Java, mas o admin ainda fala com o Supabase.

**Fluxo de dev local (3 serviços):**
- Banco: `docker compose up -d` (container `postgres_db`, porta 5433)
- Backend: `cd backend && ./gradlew bootRun` (processo nativo, porta 8080)
- Frontend: `npm run dev` (processo nativo, porta 5173/5174)

Backend e frontend rodam nativos (não no Docker) pra ter hot reload rápido. Dockerizar o backend está previsto pro Passo 10 (criar `backend/Dockerfile` multi-stage + adicionar serviço `backend` no `docker-compose.yml`). O frontend não vai pro Docker nem em produção — em prod, o Vite faz build estático (`dist/`) servido por nginx/CDN.

**Comandos:**
- `nohup npm run dev > /tmp/vemdancarjp-frontend.log 2>&1 &` — sobe o frontend em background.
- `curl -s http://localhost:5174/api/agenda` — testa o proxy Vite → backend (deve retornar o JSON da agenda).
- `ps aux | grep -E "gradlew|npm run dev|vite" | grep -v grep` — lista os processos nativos de backend e frontend.

**Erros:**
- Nenhum. Type-check passou, proxy funcionou, agenda renderizou no browser com os dados inseridos via Beekeeper Studio.

**Referências:**
- `vite.config.ts:22-28` — configuração do `server.proxy` encaminhando `/api` pra `http://localhost:8080`.
- `src/services/agendaService.ts:1-16` — `fetch('/api/agenda')` substituindo as 3 chamadas Supabase.
- `PLAN.md:440` — Passo 10 (conectar frontend) é onde a integração completa estava prevista.
- `PLAN.md:262-314` — Dockerização do backend (ainda não implementada).

### Passo 5 — `AgendaService` + `AgendaController`

**Conceito:** Este passo criou o primeiro endpoint de negócio da API: `GET /api/agenda` (público, sem auth). Três camadas precisam ser entendidas juntas:

1. **Controller** (`AgendaController`) é a porta de entrada HTTP. Anotado com `@RestController` (= `@Controller` + `@ResponseBody`), mapeia `GET /api/agenda` para o método `getAgenda()`. O Spring usa o **Jackson** (incluso no `spring-boot-starter-web`) pra converter o objeto Java retornado em JSON automaticamente. O controller **não contém lógica de negócio** — só recebe a requisição, delega pro service e devolve a resposta (princípio SRP, `PLAN.md:391`).

2. **Service** (`AgendaService`) é onde mora a lógica. Anotado com `@Service`, é injetado no controller via constructor. O `AgendaService` busca os 3 tipos de registro (events, variable_venues, other_venues) nos respectivos repositórios, agrupa os eventos por `day_id`, traduz os labels pra português (`monday` → `Segunda-feira`) igual ao `agendaService.ts:4-12`, e monta o `AgendaResponse`. Separar controller e service permite reusar a lógica fora de HTTP (testes, jobs, outros endpoints).

3. **DTO** (Data Transfer Object) é o "contrato da API", diferente da entidade JPA. O `AgendaResponse` é um **record** Java (imutável por design) com exatamente os campos que o frontend espera — sem `id`, sem `sort_order`, sem campos internos do banco. Entidades JPA representam o banco; DTOs representam a API. Mantê-los separados evita acoplar o schema do banco ao contrato da API (`PLAN.md:403`).

**Por que records e não classes com Lombok:** records são nativos do Java (desde 16), não precisam de processador de annotations, são imutáveis por design e geram constructor/getters/equals/hashCode/toString automaticamente. Para DTOs de resposta, que são criados uma vez e nunca modificados, records é a escolha idiomática moderna.

**Por que `lastUpdated` é a data de hoje e não a data real da última modificação:** o schema atual não tem colunas `updated_at`/`created_at` em nenhuma tabela. O frontend atual contorna isso usando `new Date().toISOString().split('T')[0]` (`agendaService.ts:61`). O backend replica o mesmo comportamento: `LocalDate.now().toString()`. Isso significa "a agenda está atualizada até hoje", não "foi modificada em tal data".

> **Débito técnico (futuro):** quando quiser que `lastUpdated` reflita a data real da última modificação, será preciso: (1) criar migration `V2__add_timestamps.sql` adicionando `updated_at TIMESTAMPTZ DEFAULT now()` nas tabelas `events`, `variable_venues`, `other_venues`; (2) fazer o `AgendaService` consultar `SELECT MAX(updated_at)` das 3 tabelas. Registrar como melhoria futura.

> **Débito técnico (futuro):** criar seed estruturado. Para dados reais (pós-migração do Supabase no Passo 10), criar `V2__seed_data.sql` no Flyway contendo os dados reais exportados — vira o "backup inicial" versionado no repo. Padrão adotado pela comunidade Spring Boot + Flyway. Para testes de integração, usar `@Sql` do Spring Boot Test ou Testcontainers com datasets em YAML.

**Decisão — buscar 3 tabelas sequencialmente e não em paralelo:** são 3 queries simples em tabelas pequenas (dezenas de registros). O overhead de coordenar threads com `CompletableFuture` seria maior que o ganho. Em volume real (milhares de registros), valeria paralelizar ou usar cache (caso onde Valkey/RabbitMQ entram, `PLAN.md:462`).

**Refatoração — constantes extraídas do service:** após a implementação inicial, duas mudanças de design:

1. **Dias da semana → enum `DayOfWeek`** (`entity/DayOfWeek.java`). Antes, `DAY_LABELS` (Map) e `DAY_ORDER` (List) eram constantes soltas no service. Agora, um enum type-safe junta `id` e `label` numa estrutura só — impossível usar um `day_id` inválido em compilação. O service itera sobre `DayOfWeek.values()` em vez de indexar um Map.

2. **Cidade, título, subtítulo → `application.yml` + `@ConfigurationProperties`.** Antes, eram `private static final String` no service. Agora, vivem no `application.yml` sob o namespace `agenda:` e são vinculados ao record `AgendaProperties` via `@ConfigurationProperties(prefix = "agenda")`. A classe main tem `@ConfigurationPropertiesScan` pra escanear todos os records de config do pacote. Vantagens: mudar cidade não precisa recompilar; é tipado (não é `@Value` espalhado); é testável; prepara pra multi-cidade no futuro.

**Comandos:**
- `curl -s http://localhost:8080/api/agenda | python3 -m json.tool` — chama o endpoint e formata o JSON pra leitura. `python3 -m json.tool` indenta e organiza o JSON.
- `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/agenda` — retorna só o HTTP status code (útil pra scripts). `000` significa "conexão recusada" (ninguém ouvindo na porta).
- `ps aux | grep -E "gradle|java" | grep -v grep` — lista processos Java/Gradle rodando. Útil pra confirmar se o backend está ativo e achar o PID pra `kill`.
- `docker compose exec -T postgres psql -U postgres -d vemdancarjp << 'SQL' ... SQL` — executa múltiplos INSERTs via heredoc (bloco de texto enviado como input pro `psql`).
- `docker compose exec -T postgres psql -U postgres -d vemdancarjp -c "DELETE FROM events; ..."` — limpa os dados mockados após o teste.

**Erros:**
- Nenhum. O endpoint retornou o JSON no shape exato esperado pelo frontend, com 7 dias da semana (mesmo vazios), eventos agrupados por `day_id` com labels em português, `variableVenues` e `otherVenues` separados, e `instagram: null` onde aplicável (bate com `string | null` do TypeScript).

**Referências:**
- `PLAN.md:435` — definição do Passo 5.
- `PLAN.md:230-258` — shape do `AgendaResponse` alinhado com o frontend.
- `src/services/agendaService.ts:4-12` — `DAY_LABELS` e `DAY_ORDER` originais (replicados no `AgendaService`).
- `src/features/agenda/types/index.ts:28-36` — interface `Agenda` do frontend (espelhada no `AgendaResponse` record).
- `backend/src/main/java/com/vemdancarjp/service/AgendaService.java` — lógica de agrupamento e tradução de labels.
- `backend/src/main/java/com/vemdancarjp/controller/AgendaController.java` — `@RestController` + `@GetMapping`.
- `backend/src/main/java/com/vemdancarjp/dto/response/AgendaResponse.java` — DTO record com shape da API.

### Passo 4 — Entidades + Repositórios

**Conceito:** Este passo criou a ponte entre o banco relacional e o código Java. Dois conceitos centrais precisam ser entendidos juntos:

1. **JPA (Java Persistence API)** é uma especificação que mapeia objetos Java a tabelas relacionais. O Spring Boot usa o **Hibernate** como implementação concreta. Cada classe anotada com `@Entity` vira uma tabela; cada campo com `@Column(name = "...")` vira uma coluna. Quando o backend sobe, o Hibernate lê essas anotações e constrói o mapeamento. Quando você chama `repository.findById(1)`, o Hibernate gera `SELECT * FROM events WHERE id = 1` por baixo dos panos, converte o resultado num objeto `Event`, e te devolve. Por que `@Column(name = "day_id")` é necessário: Java usa `camelCase` (`dayId`), Postgres usa `snake_case` (`day_id`). Sem a anotação explícita, o Hibernate poderia mapear pra coluna errada.

2. **Spring Data JPA** é uma camada acima do JPA que elimina boilerplate. Em vez de escrever queries manualmente, você declara uma **interface** (sem implementação) que estende `JpaRepository<Entity, IdType>`. O Spring Data, em runtime, **gera a implementação automaticamente** baseada no nome dos métodos. Por exemplo, `findAllByOrderBySortOrderAsc()` vira `SELECT * FROM events ORDER BY sort_order ASC` — só pelo nome do método. O `JpaRepository` já vem com ~15 métodos prontos (`save`, `findById`, `findAll`, `deleteById`, `count`, etc.); declaro só os extras que não existem.

**Validação de schema no startup:** a configuração `ddl-auto: validate` (Passo 2) faz o Hibernate comparar as entidades com o schema do banco quando o backend sobe. Se houver divergência (campo com nome errado, tipo incompatível), o backend **não sobe** e mostra erro. É uma rede de segurança: se a entidade e a tabela não batem, ninguém consegue rodar a app com modelo inconsistente. Neste passo, o backend subiu sem erros — as 5 entidades batem com as 5 tabelas criadas no Passo 3.

**Decisão — `requestedAt` com `DEFAULT now()` do Postgres, não `@CreationTimestamp` do Hibernate:** a coluna `requested_at` no banco tem `DEFAULT now()`. Na entidade `PendingAdmin`, anotei o campo com `updatable = false` e `columnDefinition = "TIMESTAMPTZ NOT NULL DEFAULT now()"`. Isso significa: o Java não gera o timestamp; o Postgres gera quando a linha é inserida. Alternativa seria `@CreationTimestamp` (Hibernate gera o timestamp no Java), mas delegar pro banco é mais confiável — mesmo fuso, mesmo precisão, independe da JVM.

**Por que Lombok `@Getter @Setter` e não `@Data`:** `@Data` gera `equals`/`hashCode` que consideram todos os campos, mas entidades JPA devem ter `hashCode`/`equals` baseados apenas no `id` (ou identidade de objeto) pra evitar problemas com lazy loading e proxies do Hibernate. Por isso uso só `@Getter @Setter`.

**Por que `Long` (wrapper) e não `long` (primitivo) para `id`:** `Long` é nullable; `long` sempre tem valor (default 0). Antes de salvar, o `id` é `null` (ainda não foi gerado pelo banco). Depois de salvar, o Hibernate popula com o valor real. Com `long` primitivo, o `id` seria 0 antes de salvar — ambíguo. Mesma lógica pra `Integer` vs `int` em `sortOrder`.

**Por que `UUID` (java.util.UUID) e não `String` para `id` em `Admin`/`PendingAdmin`:** o banco tem `UUID` como tipo da coluna. O Hibernate mapeia `java.util.UUID` diretamente pra `UUID` do Postgres sem conversão. Se usasse `String`, haveria conversão toda vez e nenhuma validação de formato UUID em tempo de compilação.

**Por que `Instant` e não `LocalDateTime` para `requestedAt`:** o banco tem `TIMESTAMPTZ` (timestamp with time zone). `Instant` é um instante absoluto (UTC), que mapeia perfeitamente. `LocalDateTime` não tem informação de fuso — perderia o fuso do banco.

**Comandos:**
- `nohup ./gradlew bootRun > /tmp/vemdancarjp-backend.log 2>&1 &` — sobe o backend em background; os logs vão pro arquivo em vez do terminal.
- `curl http://localhost:8080/health` — prova de vida.
- `kill <PID>` — para o backend.
- `grep -iE "schema valid|exception|ERROR|started VemdancarJpApplication" /tmp/vemdancarjp-backend.log` — filtra o log do startup procurando erros de validação do Hibernate ou confirmação de que a app subiu.

**Erros:**
- Nenhum. O backend subiu sem erros, confirmando que as 5 entidades (`Event`, `VariableVenue`, `OtherVenue`, `Admin`, `PendingAdmin`) batem com as 5 tabelas criadas no Passo 3.

**Referências:**
- `PLAN.md:434` — definição do Passo 4.
- `PLAN.md:92-98` — tabela de mapeamento entre tabelas Postgres e entidades JPA.
- `PLAN.md:100` — convenção `snake_case` no banco vs `camelCase` nas entidades.
- `backend/src/main/java/com/vemdancarjp/entity/Event.java` — entidade com `@GeneratedValue(strategy = IDENTITY)` pra `BIGSERIAL`.
- `backend/src/main/java/com/vemdancarjp/entity/Admin.java` — entidade com `UUID` sem `@GeneratedValue` (ID vem do Google).
- `backend/src/main/java/com/vemdancarjp/entity/PendingAdmin.java:21` — `requestedAt` com `columnDefinition = "TIMESTAMPTZ NOT NULL DEFAULT now()"`.
- `backend/src/main/java/com/vemdancarjp/repository/EventRepository.java` — estende `JpaRepository` com query derivada do nome do método.

### Passo 3 — Flyway `V1__create_tables.sql`

**Conceito:** Este passo introduziu **migrations de schema**. Uma migration é um arquivo SQL versionado que descreve uma mudança no schema do banco. O **Flyway** é a ferramenta que aplica essas migrations automaticamente no startup do Spring Boot: ele lê a pasta `db/migration`, compara com a tabela `flyway_schema_history` no banco e roda **só o que ainda não rodou** (em ordem de versão). A tabela `flyway_schema_history` funciona como um "diário" do schema — registra qual migration rodou, quando, com que checksum.

Por que migrations em vez de criar tabelas manualmente no `psql`? Sem migrations, não há como saber qual versão do schema cada ambiente (dev, prod, máquina de outro dev) está rodando; não há reprodução garantida; não há ordem coordenada entre devs. Com Flyway, cada mudança de schema é um arquivo `V<n>__<descrição>.sql` comitado no repo — qualquer ambiente que suba o backend fica automaticamente no schema certo.

**Regra fundamental:** nunca editar uma migration que já rodou em produção. Se precisar alterar o schema, cria-se `V2__add_xxx.sql`, `V3__alter_yyy.sql`. O Flyway recalcula o checksum e quebra se o `V1` foi alterado depois de rodado. Isso garante que o schema seja imutável e auditar.

Por que `ddl-auto: validate` no Hibernate (configurado no Passo 2): o Hibernate só valida se as entidades batem com o schema do banco, **nunca cria/modifica tabelas**. Criar schema é função exclusiva do Flyway. Essa separação evita o Hibernate e o Flyway brigarem pelo schema.

**Decisão deste passo — `CHECK (day_id IN (...))` na tabela `events`:** adicionei uma constraint `CHECK` que restringe `day_id` a um dos 7 dias da semana (`monday` ... `sunday`). Isso garante integridade no nível do banco — mesmo se houver um bug no backend, o Postgres rejeita valores inválidos. Alternativa seria validar só no service (camada Java), mas isso deixaria uma brecha pra inserções feitas por outros meios (scripts manuais, por exemplo). O custo de ter as duas validações (banco + service) é baixo; o ganho em integridade é alto.

**Por que `BIGSERIAL` para `events`/`variable_venues`/`other_venues` e `UUID` para `admins`/`pending_admins`:** as três primeiras tabelas são CRUD administrativo — o `id` é gerado pelo Postgres (auto-incremento de inteiros). Já `admins` e `pending_admins` recebem o `id` de fora: o campo `sub` do Google ID token é um UUID, então o backend não gera esses IDs, ele os recebe. Daí `UUID PRIMARY KEY` nessas duas tabelas, sem default.

**Comandos:**
- `docker compose exec -T postgres psql -U postgres -d vemdancarjp -c "\dt"` — lista as tabelas do banco (mostra que V1 criou as 5 tabelas + a `flyway_schema_history`).
- `docker compose exec -T postgres psql -U postgres -d vemdancarjp -c "\d events"` — descreve a tabela `events` (colunas, tipos, constraints, índices). Útil pra confirmar que o `CHECK` e os índices foram aplicados.
- `docker compose exec -T postgres psql -U postgres -d vemdancarjp -c "SELECT * FROM flyway_schema_history;"` — mostra o histórico de migrations aplicadas (versão, descrição, checksum, quando rodou, sucesso/falha).

**Erros:**
- Nenhum. A warning `Flyway upgrade recommended: PostgreSQL 17.11 is newer than this version of Flyway` aparece porque a versão do Flyway no Spring Boot 3.3.4 testou até o Postgres 16; mas a migration rodou com sucesso — é só aviso.

**Referências:**
- `PLAN.md:433` — definição do Passo 3.
- `PLAN.md:104-147` — SQL original da migration V1 (transcrito com o `CHECK` adicional em `day_id`).
- `backend/src/main/resources/db/migration/V1__create_tables.sql` — a migration criada.
- `backend/src/main/resources/application.yml:15` — `flyway.enabled: true` e `locations: classpath:db/migration`.

### Passo 2 — Setup Gradle + Spring Boot

**Conceito:** Este passo montou o esqueleto do backend Java. Três peças novas precisam ser entendidas juntas:
1. **Gradle** é a ferramenta de build (equivale ao `npm` no projeto Vue). O `build.gradle` lista as dependências (equivale ao `package.json`); o `gradlew` é o *Gradle Wrapper*, um script comitado no repo que baixa a versão exata do Gradle definida em `gradle-wrapper.properties`, pra qualquer dev rodar `./gradlew bootRun` sem instalar Gradle na mão.
2. **Spring Boot** é um framework que sobe um servidor HTTP (Tomcat) embutido no `.jar`, com auto-configuração: ele detecta as libs no classpath e configura tudo sozinho (ex.: tem `spring-boot-starter-web` → sobe Tomcat na 8080; tem `spring-boot-starter-data-jpa` → configura Hibernate; tem `flyway-core` → roda migrations no startup).
3. **SDKMAN** gerencia versões do JDK e do Gradle na sua máquina de dev, sem poluir o sistema (instala em `~/.sdkman/candidates/`). Em produção, o JDK vem dentro da imagem Docker (`eclipse-temurin:21-jre`).

O `@SpringBootApplication` na classe `VemdancarJpApplication` faz três coisas: component scan (encontra todos os `@RestController`, `@Service`, `@Repository` no pacote `com.vemdancarjp`), auto-configuração e ponto de entrada da JVM. O `SecurityConfig` libera todos os endpoints (`permitAll`) e desabilita CSRF temporariamente — nos Passos 7 e 8 vamos travar com JWT + `@PreAuthorize`.

**Decisão importante — `ddl-auto: validate`:** o Hibernate (JPA) só valida se as entidades batem com o schema do banco, **nunca cria/modifica tabelas**. Criar schema é função do Flyway (Passo 3). Se usássemos `update` ou `create`, o Hibernate e o Flyway poderiam brigar pelo schema.

**Decisão importante — Security desde já:** incluir `spring-boot-starter-security` neste passo (antes da auth no Passo 7) evita o problema de adicionar Security depois e quebrar todos os endpoints de uma vez. A config `permitAll` libera tudo temporariamente.

**Comandos:**
- `sdk install java 21-tem` — instala o JDK 21 (Eclipse Temurin) via SDKMAN.
- `sdk install gradle` — instala a versão estável mais recente do Gradle.
- `gradle wrapper --gradle-version 8.10` (dentro de `backend/`) — gera `gradlew`, `gradlew.bat` e `gradle/wrapper/gradle-wrapper.properties`. Travou a versão do Gradle em 8.10 no repo (para rodar o Spring Boot 3.3.4 é preciso Gradle 8.x; 9.x ainda não é totalmente compatível).
- `./gradlew bootRun` — compila e sobe o backend (Tomcat na 8080). É blocking (fica no foreground); para dev, rodar com `nohup ./gradlew bootRun > /tmp/vemdancarjp-backend.log 2>&1 &`.
- `curl http://localhost:8080/health` — prova de vida do backend.
- `kill <PID>` — para o backend rodando em background.

**Erros:**
- **SDKMAN precisou de `zip`/`unzip`** — o instalador do SDKMAN usa `unzip` para extrair o JDK. Resolvido com `sudo apt install -y zip unzip`.
- **`bootRun` é blocking e atingiu o timeout do shell (180s)** — o `./gradlew bootRun` fica no foreground rodando o servidor. Solução: rodar com `nohup ... &` em background e testar com `curl` em outro comando.

**Referências:**
- `PLAN.md:432` — definição do Passo 2.
- `backend/build.gradle` — dependências (starters `web`, `data-jpa`, `validation`, `security`, `flyway`, `postgresql`, `lombok`).
- `backend/src/main/java/com/vemdancarjp/VemdancarJpApplication.java:10` — `@SpringBootApplication` + `SpringApplication.run`.
- `backend/src/main/resources/application.yml` — datasource (porta 5433 do host), `ddl-auto: validate`, Flyway habilitado.
- `backend/src/main/java/com/vemdancarjp/config/SecurityConfig.java:15` — `permitAll()` temporário.
- `backend/src/main/java/com/vemdancarjp/controller/HealthController.java:11` — endpoint de prova de vida `GET /health`.
- `backend/gradle/wrapper/gradle-wrapper.properties:3` — `distributionUrl` travando Gradle 8.10.

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

