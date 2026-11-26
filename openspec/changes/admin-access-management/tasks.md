# Tasks

## 1. Segurança

- [ ] 1.1 Transformar `ADMIN_RESOURCES` em um mapa de métodos por recurso, mantendo os métodos atuais de eventos e locais e acrescentando `access` com `GET` na coleção e `PUT` e `DELETE` no item. Verificação: as suítes de eventos e locais passam sem mudança.
- [ ] 1.2 Extrair a checagem de identidade Google para um helper, criar o `GoogleIdentityAuthorizationManager`, proteger `POST /api/auth/access-request` com ele e incluir a rota no filtro de `no-store`. Verificação: `AdminAccessAuthorizationManagerTest` passa e testes da seção 3.

## 2. Domínio

- [ ] 2.1 Criar o `AdminBootstrap` (`ApplicationRunner`, validação de formato com falha ao subir, advisory lock, upsert só sem `APPROVED`, log sem o `sub`). Documentar `APP_BOOTSTRAP_ADMIN_SUB`, o procedimento para obter o `sub` e a recuperação no `.env.example` e no README do backend. Incluir `subject` em `GET /api/auth/session`. Verificação: teste 3.1.
- [ ] 2.2 Criar o `AdminAccessService` com pedido (`ON CONFLICT DO NOTHING`, exigindo `email_verified`), listagem ordenada, aprovação e remoção que travam os `APPROVED` com `FOR UPDATE` e reconferem o executor (`403`), aprovação condicional, `CANNOT_REMOVE_SELF` e `LAST_ADMIN`. Verificação: testes 3.2 a 3.7.
- [ ] 2.3 Criar o controller de pedido de acesso e o `AdminAccessController` (`GET`, `PUT /{id}` e `DELETE /{id}`), com `rawUuid` em `AdminRequestValidation` e o controller incluído no advice de `400`. Verificação: testes da seção 3.

## 3. Testes

- [ ] 3.1 Primeiro admin: base vazia cria `APPROVED`; `PENDING` do mesmo `sub` vira `APPROVED` sem duplicar; com `APPROVED` existente nada muda; variável vazia ou ausente não faz nada; formato inválido falha sem citar o valor; a aplicação sobe com a variável definida; o log não contém o `sub`; recuperação (apagar `APPROVED`, corrigir a variável e rodar de novo) aprova o `sub` correto; `GET /api/auth/session` traz `subject`.
- [ ] 3.2 Pedido de acesso: `201` cria `PENDING` com e-mail e nome do token; sem `email` ou com `email_verified` falso ou ausente `403 EMAIL_NOT_VERIFIED` sem criar; repetição e admin recebem `200` sem mudança, mesmo com corpo `{"status":"APPROVED"}`; `401` sem sessão; `403` para não OIDC e para emissor diferente; `CSRF_INVALID`; `no-store` em tudo; pedidos simultâneos do mesmo `sub` geram um único registro.
- [ ] 3.3 Listagem: pendentes antes de aprovados, cada grupo por `requestedAt`; exatamente os campos `id`, `email`, `name`, `status` e `requestedAt`; nenhum valor de `sub` no corpo.
- [ ] 3.4 Aprovação: pendente vira `APPROVED` e a pessoa acessa `GET /api/admin/events` na requisição seguinte; já aprovado `200` sem mudança; inexistente `404`; corpos inválidos (`PENDING`, outro status, campo extra, não objeto, JSON malformado) `400`; aprovações simultâneas terminam em `200` com um único registro.
- [ ] 3.5 Executor revogado no meio da operação, de forma determinística: um teste segura a trava dos `APPROVED` numa transação, revoga B, dispara o `PUT` e o `DELETE` de B (bloqueados) e só então confirma. Os dois recebem `403`, e nada muda.
- [ ] 3.6 Remoção: rejeitar pendente `204` e a pessoa pode pedir de novo; revogar admin com sessão ativa faz a próxima requisição dessa sessão dar `403`; remoção repetida `204`; remover a si mesmo `409 CANNOT_REMOVE_SELF`; último admin `409 LAST_ADMIN`; revogação cruzada simultânea entre dois admins deixa pelo menos um `APPROVED` e no máximo uma remoção concluída, repetida várias vezes.
- [ ] 3.7 `{id}` inválido (maiúsculas, sem hífens, `%` codificado, numérico, tamanho errado) `400` no `PUT` e no `DELETE`, sem alterar registros.
- [ ] 3.8 Autorização e isolamento: `401` e `403` nas três rotas de gestão, revogação na mesma sessão, `CSRF_INVALID` no `PUT` e no `DELETE`, `POST /api/admin/access`, `GET` e `PATCH` no item e subcaminho negados, `no-store` exato em todas as respostas.
- [ ] 3.9 Falha na consulta de aprovação devolve `5xx` com `no-store` nas rotas de gestão, pela cadeia HTTP real.

## 4. Fechamento

- [ ] 4.1 Rodar a suíte completa (`cd backend && ./gradlew test -q`), atualizar o `STATUS.md` e os ADRs (`2026-10-modelo-admin-access.md` e `2026-10-oidc-seguranca-inicial.md`) com o primeiro admin, o pedido de acesso e a gestão. Verificação: suíte verde e diffs no PR.

## Workflow follow-up

- Revisão do Codex no PR e merge pelo Vinícius.
- Arquivar a change após o merge.
