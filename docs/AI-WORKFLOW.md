# Fluxo de Trabalho

## Planejar antes de construir

Tarefa grande → plano primeiro, depois implementa. Evita retrabalho. Para decisões de arquitetura, use o agente `architect` (Kimi K3, somente leitura) ou o comando `/plan-feature`.

## Separar planejamento, execução e auditoria

### Tarefa simples

```
GLM 5.2 (agenda-dev) → implementar → /verify
```

### Tarefa complexa de arquitetura

```
Kimi K3 (architect)  → plano e trade-offs
GLM 5.2 (agenda-dev) → implementação
GLM 5.2 (agenda-dev) → testes
Kimi K3 (architect)  → revisão final, se o risco justificar
```

### Tarefa de segurança

```
Kimi K3 (security-reviewer) → modelo de ameaça e auditoria
GLM 5.2 (agenda-dev)         → correção mecânica, se simples
Kimi K3 (security-reviewer) → revisão da correção
```

Não use Kimi K3 como agente que edita o projeto inteiro por longos loops. Veja `docs/AI-COST-CONTROL.md`.

## Migração para Backend Java (WIP)

O projeto está migrando de Supabase para um backend Java + Spring Boot próprio. O plano completo está em `PLAN.md`.

**Fluxo de Aprendizado obrigatório:** ao executar qualquer passo da "Ordem de Implementação" do `PLAN.md`, seguir a seção "Fluxo de Aprendizado" lá definida:

1. explicar antes de escrever
2. aguardar confirmação
3. implementar
4. resumir o diff
5. verificar
6. **registrar aprendizado em `LEARNING.md`**
7. só então avançar

**`LEARNING.md`** é o caderno de anotações do projeto. Deve ser atualizado ao final de cada passo concluído, com a entrada mais recente no topo. Nunca pular essa etapa — mesmo em modo direto ("só faz").

**Modo direto:** se o usuário pedir "só faz" / "sem enrola", pular explicação prévia e resumo do diff, mas **nunca** pular a atualização do `LEARNING.md`.

## Verificação

Ao concluir uma implementação relevante, rode (ou use `/verify`):

```
npm run lint && npm run type-check && npm run test:unit
```

Não corrija falhas automaticamente sem explicar o que será alterado. Separe falhas preexistentes das introduzidas pela sua mudança.
