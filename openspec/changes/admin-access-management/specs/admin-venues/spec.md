# Spec Delta

## MODIFIED Requirements

### Requirement: Demais rotas administrativas bloqueadas
Rotas em `/api/admin/**` fora de `/api/admin/events/**`, `/api/admin/variable-venues/**`, `/api/admin/other-venues/**` e `/api/admin/access/**` SHALL continuar bloqueadas, inclusive para sessões `APPROVED`. Cada um desses prefixos é regido pelos requisitos da sua própria capability.

#### Scenario: Rota administrativa desconhecida
- **WHEN** uma sessão aprovada solicita, com CSRF válido, uma rota como `GET /api/admin/admins` ou `POST /api/admin/venues`
- **THEN** o sistema nega a requisição com `403` e não executa nenhuma operação
