---
type: adr
numero: "006"
titulo: Publicar o backend no Cloud Run com banco no Neon
data: 2026-11-03
status: aceita
---

# ADR-006: Publicar o backend no Cloud Run com banco no Neon

## Contexto

O ADR-005 previa uma VM do Oracle Cloud Always Free em São Paulo, a região principal da conta. O relatório de capacidade mostrou São Paulo sem A1 e sem E2.1.Micro, e as tentativas de criação falharam. A região principal não pode ser trocada. A VM `e2-micro` do Google também não é gratuita, porque o IPv4 em uso custa cerca de US$ 3,65 por mês.

## Decisão

- Publicar o backend no Google Cloud Run, em `us-east1`, com 1 vCPU, 512 MiB, escala a zero e no máximo uma instância recebendo tráfego.
- Publicar o PostgreSQL no plano gratuito do Neon, em `aws-us-east-1`.
- Manter `api.vemdancarjp.com.br` com o mapeamento de domínio do Cloud Run.
- Expor só `GET /api/agenda`, com um filtro na aplicação, já que não há proxy próprio.
- Limitar o risco de custo com alerta de US$ 1 no projeto e teto de gasto de US$ 5 no Cloud Run.

## Consequências

- Este ADR substitui a decisão de provedor do ADR-005. As demais decisões do ADR-005 continuam valendo.
- O Google exige cartão, então o custo zero não é garantido. Alerta e teto têm atraso, e o teto pode não cobrir a saída de dados. Se atingido, o teto deixa a agenda fora do ar até o próximo mês, salvo edição do orçamento.
- O primeiro acesso depois de um tempo parado é lento, porque a aplicação precisa subir, e a API fica nos EUA, longe do público.
- O mapeamento de domínio está em Preview, e o Google não o recomenda para produção. Se ele for lento demais ou retirado, o site passa a usar o endereço `run.app` com um novo build.
- A imagem continua portátil, e a agenda continua recriável pela carga versionada.

## Alternativas consideradas

- Oracle A1 ou Micro: sem capacidade em São Paulo.
- Google `e2-micro`: o IPv4 é cobrado.
- Render + Neon: leva cerca de 1 minuto para acordar, depois de 15 minutos parado.
- AWS e Azure: os planos gratuitos com VM não são permanentes.
- Balanceador de carga do Google para o domínio: é o caminho recomendado, mas não é gratuito.

Veja também: ADR-005 e `openspec/changes/backend-production-deploy/`.
