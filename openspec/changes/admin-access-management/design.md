# Design

## Context

Ver `proposal.md` para a motivação e as specs `admin-access` e `admin-venues` para o contrato.

O que existe hoje:
- `admin_access` (V2) tem `id UUID`, `google_sub` único, `status` (`PENDING` ou `APPROVED`), `requested_at`, `email` e `name`. Nenhum código cria linhas fora dos testes.
- `AdminAccessAuthorizationManager` valida o token OIDC do Google (registro `google`, emissor `https://accounts.google.com` e `sub` não vazio) e depois exige `APPROVED` no banco a cada requisição.
- `GET /api/auth/session` devolve `accessStatus` (`NONE`, `PENDING` ou `APPROVED`).
- `SecurityConfig` deriva os matchers admin da lista `ADMIN_RESOURCES`, com `GET` e `POST` na coleção e `PUT` e `DELETE` no item com segmento único. O filtro de `no-store` roda antes do `CsrfFilter`.
- `AdminRequestValidation` valida corpo e `{id}` numérico cru. O advice de `400` vale só para os controllers listados nele.

## Goals / Non-Goals

**Goals:**

- Nenhum caminho HTTP aprova ninguém, exceto `PUT /api/admin/access/{id}` feito por um admin já aprovado.
- Nunca ficar sem admin, mesmo sob concorrência, garantido pelo banco e não só pela aplicação.

**Non-Goals:**

- Novo status (`REJECTED`), histórico ou auditoria de quem aprovou. Rejeitar e revogar apagam a linha, por decisão do Vinícius.
- Notificar quem pediu acesso, convites e papéis diferentes de admin.
- Importar admins ou pendências do Supabase, o que o plano proíbe.

## Decisions

### 1. Métodos por recurso em `SecurityConfig`

`ADMIN_RESOURCES` vira um mapa de recurso para métodos permitidos na coleção e no item:
- `events`, `variable-venues` e `other-venues`: `GET` e `POST` na coleção, `PUT` e `DELETE` no item;
- `access`: só `GET` na coleção, `PUT` e `DELETE` no item.

Assim, `POST /api/admin/access` cai no `denyAll`. **Alternativa descartada:** deixar `POST` mapeado e rejeitar no controller. A rota ficaria liberada na camada de segurança sem existir.

### 2. Identidade Google separada da aprovação

A checagem de token Google sai de `AdminAccessAuthorizationManager` para um helper compartilhado. Um novo `GoogleIdentityAuthorizationManager` usa só essa checagem e protege `POST /api/auth/access-request`. O manager de admin passa a ser "identidade Google + `APPROVED`". O CSRF segue a regra padrão, e o filtro de `no-store` passa a cobrir também essa rota.

### 3. Pedido de acesso sem corrida

A ordem é:
1. Se já existe registro para o `sub`, responder `200` com o status atual, sem alterar nada e sem olhar o e-mail.
2. Se não existe e o token não tem `email` não vazio com `email_verified == true`, responder `403 EMAIL_NOT_VERIFIED` sem gravar.
3. Caso contrário, `INSERT ... ON CONFLICT (google_sub) DO NOTHING RETURNING id`, por query nativa. Se voltou uma linha, a resposta é `201 PENDING`. Se não, um pedido simultâneo criou o registro: ler o status e responder `200`.

Dois pedidos simultâneos do mesmo usuário não geram erro de chave única. O controller nunca lê o corpo. O e-mail vem da claim `email` e é sempre preenchido num pedido criado. O nome vem da claim `name` e pode ficar nulo.

### 4. Aprovação condicional com reconferência do executor

Numa transação:
1. `SELECT ... FROM admin_access WHERE status = 'APPROVED' FOR UPDATE`, a mesma trava usada pela remoção.
2. Se o `sub` de quem executa não está entre os travados, responder `403 FORBIDDEN`. Isso fecha a janela entre a autorização, que acontece no filtro, e a escrita.
3. `UPDATE admin_access SET status = 'APPROVED' WHERE id = :id AND status = 'PENDING'`, seguido da leitura do registro. Se não existe, a resposta é `404`. Se já estava aprovado, a resposta é `200` sem mudança. A atualização condicional torna aprovações simultâneas inofensivas. Como aprovação e remoção travam o mesmo conjunto de linhas, uma revogação em andamento faz a aprovação do revogado esperar e, ao seguir, falhar com `403`.

### 5. Remoção com trava nos aprovados

Numa transação:
1. Rodar `SELECT id, google_sub FROM admin_access WHERE status = 'APPROVED' FOR UPDATE`. Se o executor não está entre os travados, responder `403 FORBIDDEN`.
2. Ler o alvo. Se não existe, responder `204`.
3. Comparar o `google_sub` do alvo com o `sub` da sessão. Se for o mesmo, responder `409 CANNOT_REMOVE_SELF`.
4. Checagem defensiva: se o alvo é `APPROVED` e a contagem travada é `<= 1`, lançar um erro interno (`5xx`), sem apagar. Pelos passos 1 e 3, isso não deveria acontecer: o executor está aprovado e é diferente do alvo, então a contagem é pelo menos 2.
5. Caso contrário, apagar.

A reconferência vem antes da checagem de remover a si mesmo. Assim, um executor revogado depois da autorização recebe `403`, e não `409`, inclusive quando o alvo é o próprio registro, como apontou a revisão do Codex no PR.

No PostgreSQL em `READ COMMITTED`, a segunda transação espera a trava e, ao seguir, enxerga a linha já apagada pela primeira. Numa revogação cruzada entre dois admins, quem perde a disputa já não está entre os `APPROVED` travados, então a reconferência do passo 1 responde `403 FORBIDDEN`. Nunca se fica sem admin por causa dos passos 1 e 3: remover a si mesmo é proibido, e o executor precisa estar aprovado e travado. Por isso não existe um erro `LAST_ADMIN` no contrato. O passo 4 só protege contra um bug futuro nessas duas regras.

### 6. Primeiro admin

`AdminBootstrap`, um `ApplicationRunner`, lê `APP_BOOTSTRAP_ADMIN_SUB`. Se a variável vier vazia, não faz nada. Se o valor não casar `[A-Za-z0-9]{1,255}`, a aplicação falha ao subir, com uma mensagem que não contém o valor. Se vier preenchida, numa transação:
1. `pg_advisory_xact_lock` fixo, para serializar várias instâncias subindo ao mesmo tempo;
2. se não existe `APPROVED`, `INSERT ... ON CONFLICT (google_sub) DO UPDATE SET status = 'APPROVED'`.

O log diz só "aplicado" ou "ignorado", sem o `sub`. **Alternativas descartadas** (escolha do Vinícius): SQL manual e comando de CLI.

**Como obter o `sub`:** `GET /api/auth/session` passa a devolver `subject`, o `sub` da própria sessão. O procedimento fica documentado: subir sem a variável, fazer login, consultar a sessão, copiar o `subject`, definir a variável e reiniciar.

**Recuperação:** a ordem importa, porque o bootstrap aprova o valor de quem subir primeiro, e não o valor correto.
1. Corrigir a variável na configuração do serviço, não por instância, e implantar.
2. Confirmar que nenhuma instância com o valor antigo continua rodando. No Cloud Run, isso quer dizer 100% do tráfego na revisão nova e as instâncias antigas encerradas.
3. Só então apagar os registros `APPROVED` no banco.
4. Reiniciar.

Apagar antes de trocar a configuração permite que uma instância antiga reaprove o `sub` errado, como apontou a revisão do Codex. O procedimento vai para o README do backend. Os testes cobrem a recuperação e a disputa entre duas inicializações com valores diferentes, em que só uma aprova.

### 7. Identificador e validação

`AdminRequestValidation` ganha `rawUuid(request)`, que aceita só `[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}`, e o advice passa a incluir o controller de acesso. O corpo do `PUT` passa por `requireObjectWithFields(body, {"status"})` e exige `status == "APPROVED"`.

### 8. Provas

- **Integração HTTP** (MockMvc com PostgreSQL de teste): contrato, autorização, CSRF, `no-store`, isolamento, revogação de sessão ativa e ausência do `sub` nas respostas.
- **Concorrência:** duas threads com `CountDownLatch` chamando o serviço em transações próprias, repetido algumas vezes. As invariantes conferidas são: pelo menos um `APPROVED`, no máximo uma revogação concluída e um único registro por `sub`.
- **Primeiro admin:** o runner roda direto contra o banco em cada estado inicial. Um teste de contexto confirma que a aplicação sobe com a variável definida.
- **`5xx`:** no teste de falhas de autorização, pela cadeia HTTP real.

## Risks / Trade-offs

- **[Variável esquecida no ambiente]** Se alguém apagar todos os admins direto no banco, a variável volta a valer no próximo boot. → É o comportamento desejado para recuperação. O ADR deve registrar que ela pode ficar definida.
- **[Sem histórico]** Apagar a linha perde quem foi rejeitado. → Decisão do Vinícius. Pode ser revista com um status novo, em outra change.
- **[Aprovar a pessoa errada]** A listagem identifica o solicitante pelo e-mail. → O pedido só é aceito com `email` e `email_verified == true` no token (`403 EMAIL_NOT_VERIFIED` caso contrário), então todo pedido listado tem um e-mail verificado pelo Google.
- **[`subject` na sessão]** A resposta da sessão passa a incluir o próprio `sub`. → Ele só é mostrado ao dono da sessão, com `no-store`. O `sub` é um identificador, não uma credencial.

## Migration Plan

Não há migration. Para ativar, defina `APP_BOOTSTRAP_ADMIN_SUB` com o seu `sub` no `.env` e suba o backend. Para reverter, basta remover o recurso `access` da lista e os controllers novos. Os registros existentes continuam válidos.
