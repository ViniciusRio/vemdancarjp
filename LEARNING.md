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
