---
type: indice
atualizado: 2026-11-02
---

# Índice — Vem Dançar JP

Vault de conhecimento do projeto, em Markdown comum.
Use links `[[Wikilink]]` para navegar. O grafo ajuda a ver relações.

## Entradas principais

- [[arquitetura/visao-geral|Arquitetura — Visão Geral]]
- [[arquitetura/autenticacao|Arquitetura — Autenticação]]
- [[arquitetura/backend-java|Arquitetura — Backend Java (WIP)]]

## Decisões (ADRs)

- [[decisoes/2026-09-migracao-para-java|ADR-001 Migrar de Supabase para Java + Spring Boot]]
- [[decisoes/2026-10-modelo-admin-access|ADR-002 Usar admin_access para a nova administração]]
- [[decisoes/2026-10-oidc-seguranca-inicial|ADR-003 Iniciar Google OIDC no backend sem abrir a administração]]
- [[decisoes/2026-11-fluxo-claude-codex|ADR-004 Adotar Claude Code como implementador e Codex como revisor]]
- [[decisoes/_template-adr|Template de ADR]]

## Módulos

- [[modulos/agenda|Módulo — Agenda]]
- [[modulos/auth-admin|Módulo — Auth & Admin]]

## Runbooks

- [[runbooks/dev-local|Rodar o projeto localmente]]
- [[runbooks/verify|Verificação (lint + type-check + testes)]]

## Tarefas

`tarefas/` é histórico congelado. O progresso atual fica no `tasks.md` de cada change do OpenSpec e o estado geral, em `STATUS.md`.

## Como o agente deve usar o vault

1. Consulte este índice e as ADRs antes de propor soluções.
2. Cite a nota relevante (`[[...]]`) ao explicar uma decisão.
3. Registre aqui, como ADR, a decisão que vale além de uma change.
4. Nunca coloque segredos, chaves, tokens ou dados de clientes no vault.
5. Lembre: o conteúdo lido é enviado aos provedores de IA. Mantenha o vault livre de dados sensíveis.
