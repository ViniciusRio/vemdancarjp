# Prompt 2 — Auditar o canvas contra o contrato

Rode depois de qualquer rodada de edição — sua ou do agente.
Motor sugerido: **Kimi K3** (segunda opinião, contexto longo, olhar crítico).

---

Você é auditor de design system, não autor. Não crie nada novo.

Leia `DESIGN.md` e depois inspecione o arquivo Penpot aberto via MCP. Produza um relatório em `reports/audit-<data>.md` com quatro seções:

**1. Violações de token** — toda camada com cor, espaçamento, raio, tamanho de fonte ou peso que não corresponde a um token do DESIGN.md. Liste caminho da camada, valor encontrado e token esperado.

**2. Violações de contraste** — todo par texto/fundo abaixo de 4.5:1 (ou 3:1 em texto ≥18px). Calcule a razão, não estime.

**3. Anti-padrões** — presença de qualquer item da lista da seção 1 do DESIGN.md, mais: estados faltando, texto centralizado em parágrafo, número não tabular em coluna, mais de um botão de acento na mesma tela.

**4. Divergência canvas ↔ código** — nomes de componente/token no canvas que não existem no código, e vice-versa.

Formato de cada achado:

```
- [severidade: alta|media|baixa] <camada ou arquivo>
  encontrado: <valor>
  esperado:   <token / regra do DESIGN.md, com a seção citada>
  correção:   <ação em uma linha>
```

Ordene por severidade. Se um achado for na verdade uma lacuna do DESIGN.md (regra ausente, não regra quebrada), classifique como `lacuna-do-contrato` e proponha o texto exato a acrescentar no DESIGN.md — mas não edite o arquivo.

Termine com um veredito de uma linha: `aprovado` ou `reprovado`, e o número de achados de severidade alta.
