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

