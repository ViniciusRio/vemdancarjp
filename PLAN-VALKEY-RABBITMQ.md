# Plano: Valkey + RabbitMQ (Fase Posterior)

> **Status:** **Diferido.** Este plano não deve ser implementado na fase 1 do backend Java. Adicionar Valkey + RabbitMQ apenas quando houver um requisito real que eles resolvam — cache de leitura com tráfego alto, rate-limiting de abuso, ou consumers assíncronos (notificação por email, sync externo). Para um CRUD de 5 tabelas com tráfego baixo, o Spring MVC + Postgres local é suficiente.

## Quando implementar

Implemente este plano **apenas** quando pelo menos uma destas condições for verdadeira:

- O endpoint `GET /api/agenda` receber >100 req/s e o Postgres começar a ser gargalo de leitura.
- Houver necessidade de notificar usuários por email/push quando um admin aprovar/rejeitar um pending.
- Houver necessidade de rate-limitar o endpoint público por IP/client.
- Houver consumers assíncronos (ex.: export de relatório, sync com serviço externo).

Se nenhuma condição for verdadeira, **não implemente.** Use `@Cacheable` do Spring com o cache `ConcurrentMapCacheManager` (in-memory, sem infra extra) se precisar de um cache simples agora.

## Stack Adicional

- **Valkey:** Cache + Rate Limiting (fork do Redis, compatível via Lettuce)
- **RabbitMQ:** Mensageria + Event-driven
- **Spring Boot Starter Data Redis** (compatível com Valkey via Lettuce)
- **Spring AMQP**

## Docker Compose (atualizado — alinhar com o existente)

> O `docker-compose.yml` atual usa Postgres 17 na porta `5433:5432`. Este override adiciona Valkey e RabbitMQ mantendo a mesma configuração do `PLAN.md`.

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

  valkey:
    image: valkey/valkey:8-alpine
    container_name: valkey_cache
    restart: always
    ports:
      - "6379:6379"
    volumes:
      - valkey_data:/data

  rabbitmq:
    image: rabbitmq:3-management-alpine
    container_name: rabbitmq_broker
    restart: always
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-guest}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:-guest}

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
      SPRING_DATA_REDIS_HOST: valkey
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_PORT: 5672
      SPRING_RABBITMQ_USERNAME: ${RABBITMQ_USER:-guest}
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-guest}
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
      JWT_SECRET: ${JWT_SECRET}
    depends_on:
      - postgres
      - valkey
      - rabbitmq

volumes:
  postgres_data:
  valkey_data:
```

## Dependências Gradle (adicionar)

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-amqp'
}
```

## `application.yml` (atualizar)

```yaml
spring:
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:localhost}
      port: ${SPRING_DATA_REDIS_PORT:6379}
      timeout: 60000
  rabbitmq:
    host: ${SPRING_RABBITMQ_HOST:localhost}
    port: ${SPRING_RABBITMQ_PORT:5672}
    username: ${SPRING_RABBITMQ_USERNAME:guest}
    password: ${SPRING_RABBITMQ_PASSWORD:guest}
```

## Estrutura de Pastas (adicionar ao backend existente)

```
backend/src/main/java/com/vemdancarjp/
├── config/
│   ├── ValkeyConfig.java
│   └── RabbitMQConfig.java
├── cache/
│   └── AgendaCacheService.java
├── messaging/
│   ├── EventProducer.java
│   └── EventConsumer.java
└── ...
```

## Valkey: Cache da Agenda Pública

### `ValkeyConfig.java`

```java
@Configuration
@EnableRedisRepositories
public class ValkeyConfig {

    @Bean
    public RedisTemplate<String, AgendaResponse> redisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, AgendaResponse> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
```

### `AgendaCacheService.java`

```java
@Service
public class AgendaCacheService {

    private final RedisTemplate<String, AgendaResponse> redisTemplate;
    private static final String CACHE_KEY = "agenda:all";
    private static final Duration TTL = Duration.ofMinutes(30);

    public AgendaCacheService(RedisTemplate<String, AgendaResponse> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<AgendaResponse> getAgenda() {
        return Optional.ofNullable(redisTemplate.opsForValue().get(CACHE_KEY));
    }

    public void saveAgenda(AgendaResponse agenda) {
        redisTemplate.opsForValue().set(CACHE_KEY, agenda, TTL);
    }

    public void invalidateAgenda() {
        redisTemplate.delete(CACHE_KEY);
    }
}
```

### `AgendaService.java` (atualizar para usar cache)

```java
@Service
public class AgendaService {

    private final EventRepository eventRepository;
    private final VariableVenueRepository variableVenueRepository;
    private final OtherVenueRepository otherVenueRepository;
    private final AgendaCacheService cacheService;

    public AgendaResponse getAgenda() {
        Optional<AgendaResponse> cached = cacheService.getAgenda();
        if (cached.isPresent()) {
            return cached.get();
        }

        AgendaResponse agenda = buildAgendaResponse();
        cacheService.saveAgenda(agenda);
        return agenda;
    }

    public Event save(EventRequest request) {
        Event event = eventRepository.save(mapper.toEntity(request));
        cacheService.invalidateAgenda();
        return event;
    }
}
```

## RabbitMQ: Mensageria

### `RabbitMQConfig.java`

```java
@Configuration
public class RabbitMQConfig {

    public static final String EVENT_QUEUE = "event.queue";
    public static final String EVENT_EXCHANGE = "event.exchange";
    public static final String EVENT_ROUTING_KEY = "event.created";

    @Bean
    public Queue eventQueue() {
        return new Queue(EVENT_QUEUE, true);
    }

    @Bean
    public DirectExchange eventExchange() {
        return new DirectExchange(EVENT_EXCHANGE);
    }

    @Bean
    public Binding eventBinding(Queue eventQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(eventQueue)
                .to(eventExchange)
                .with(EVENT_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }
}
```

### `EventProducer.java`

```java
@Service
public class EventProducer {

    private final RabbitTemplate rabbitTemplate;

    public EventProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishEventCreated(EventResponse event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EVENT_EXCHANGE,
                RabbitMQConfig.EVENT_ROUTING_KEY,
                event);
    }

    public void publishEventUpdated(EventResponse event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EVENT_EXCHANGE,
                "event.updated",
                event);
    }

    public void publishEventDeleted(String eventId) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EVENT_EXCHANGE,
                "event.deleted",
                eventId);
    }
}
```

### `EventConsumer.java`

```java
@Component
public class EventConsumer {

    private final AgendaCacheService cacheService;
    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    public EventConsumer(AgendaCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @RabbitListener(queues = RabbitMQConfig.EVENT_QUEUE)
    public void handleEventCreated(EventResponse event) {
        log.info("Evento criado: {}", event.getName());
        cacheService.invalidateAgenda();
    }
}
```

## Fluxo Completo

1. Usuário abre home → `GET /api/agenda` → busca no Valkey → cache hit retorna em ms; cache miss busca no Postgres, salva no Valkey (TTL 30min), retorna.
2. Admin edita evento → `PUT /api/admin/events/{id}` → atualiza Postgres → publica `EVENT_UPDATED` no RabbitMQ → invalida cache Valkey.
3. Consumer RabbitMQ recebe a mensagem → invalida cache → (opcional) envia notificação.
4. Próxima busca no home → cache vazio → busca Postgres → salva no Valkey.

## Rate Limiting com Valkey

### `RateLimitInterceptor.java`

```java
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, String> redisTemplate;
    private static final int MAX_REQUESTS = 100;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    public RateLimitInterceptor(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        String clientId = getClientId(request);
        String key = "rate_limit:" + clientId;

        Long requests = redisTemplate.opsForValue().increment(key);
        if (requests != null && requests == 1) {
            redisTemplate.expire(key, WINDOW);
        }

        if (requests != null && requests > MAX_REQUESTS) {
            response.setStatus(429);
            response.getWriter().write("Rate limit exceeded");
            return false;
        }

        return true;
    }

    private String getClientId(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

> Registrar o interceptor em `WebMvcConfig` apenas para `/api/agenda` (endpoint público). Não aplicar rate limit aos endpoints admin (tráfego baixo, autenticado).

## Comandos Docker

```sh
# Rodar tudo (postgres + valkey + rabbitmq + backend)
docker compose up

# Acessar RabbitMQ Management
# http://localhost:15672 (guest/guest)

# Acessar Valkey CLI
docker compose exec valkey valkey-cli

# Ver filas RabbitMQ
docker compose exec rabbitmq rabbitmqctl list_queues
```

## Monitoramento

- **Valkey:** `valkey-cli MONITOR` (comandos em tempo real)
- **RabbitMQ:** http://localhost:15672 (dashboard web)
- **Spring Boot:** http://localhost:8080/actuator (se `spring-boot-starter-actuator` habilitado)

## Ordem de Implementação (quando decidir implementar)

1. **Docker Compose atualizado** — adicionar Valkey + RabbitMQ ao `docker-compose.yml` existente (Postgres 17, porta 5433).
2. **Dependências Gradle** — `spring-boot-starter-data-redis`, `spring-boot-starter-amqp`.
3. **`ValkeyConfig`** — conexão + serializer.
4. **`AgendaCacheService`** — `get`/`save`/`invalidate` com TTL.
5. **Atualizar `AgendaService`** — usar cache nas leituras; invalidar nas escritas.
6. **`RabbitMQConfig`** — filas, exchange, bindings.
7. **`EventProducer`** — publicar em `save`/`update`/`delete`.
8. **`EventConsumer`** — consumir e invalidar cache.
9. **`RateLimitInterceptor`** — proteger `/api/agenda` (apenas se houver abuso).
10. **Testes** — cache hit/miss, fluxo de mensagem, rate limit.

## Rollback

Se o Valkey/RabbitMQ causar problemas após implementar:

1. **Remover os serviços do `docker-compose.yml`** — voltar ao compose sem Valkey/RabbitMQ.
2. **Reverter `AgendaService`** para a versão sem cache (buscar direto no Postgres).
3. **Remover as dependências Gradle** (`spring-boot-starter-data-redis`, `spring-boot-starter-amqp`).
4. Os dados no Postgres **não são afetados** — o cache é volátil.
5. `git revert` do commit que adicionou Valkey/RabbitMQ.
