# Prompt 2 — Auditar o canvas contra o contrato

Rode depois de qualquer rodada de edição — sua ou do agente.

**Estrutura split para economizar contexto:** a coleta é mecânica (GPT Luna + scripts), o veredito é julgamento (GPT Sol lê só o arquivo de achados, não o canvas inteiro).

---

## Fase A — Coleta (GPT Luna + scripts determinísticos)

Você é coletor, não juiz. Não classifique severidade, não proponha correção de texto — só registre o fato bruto.

### A.1. Rodar os scripts determinísticos (não pergunte ao modelo o que script responde)

```bash
# Caça a cor/valor hardcoded no código
rg -n "green-|gray-|#[0-9a-fA-F]{3,6}" src/ > reports/raw-hardcoded-$(date +%F).txt

# Contraste WCAG (Python, saída em JSON)
python3 ui-ux/scripts/wcag-contrast.py ui-ux/tokens/core.json > reports/raw-contrast-$(date +%F).json
```

### A.2. Inspecionar o canvas via MCP (GPT Luna)

Com o Penpot + MCP de pé, percorra cada página (`Foundations`, `Componentes`). Para cada camada, registre em `reports/findings-<data>.md`:

- caminho da camada + valor encontrado (cor, espaçamento, raio, fonte, peso)
- divergência canvas ↔ código: nome de componente/token que existe num lado e não no outro

Não calcule contraste (script já fez). Não proponha correção. Só o fato.

### A.3. Fechar o arquivo de achados

`reports/findings-<data>.md` deve conter, em seções planas:

1. **Hardcoded no código** — saída do `rg` (cola o `.txt`).
2. **Contraste** — saída do script (cola o `.json`).
3. **Violações de token no canvas** — uma linha por camada: `camada | encontrado | esperado`.
4. **Anti-padrões** — presença de itens da seção 1 do `DESIGN.md` + estados faltando, texto centralizado em parágrafo, número não tabular, >1 botão de acento por tela.
5. **Divergência canvas ↔ código** — `nome | existe no canvas | existe no código`.

Pare aqui. **Não escreva veredito, não classifique severidade.** Entregue o arquivo fechado.

---

## Fase B — Veredito (GPT Sol, contexto curto)

Abra uma **sessão nova** com GPT Sol. Forneça como entrada **apenas**:

- `reports/findings-<data>.md` (achados brutos da Fase A)
- `DESIGN.md` (contrato, ~9k)

**Não** anexe o canvas, nem `src/`, nem histórico da sessão de coleta.

### Instruções ao K3

Você é auditor de design system, não autor. Não crie nada novo. A partir do arquivo de achados:

1. Atribua **severidade** (alta|media|baixa) a cada item, justificando em uma linha.
2. Para cada item, proponha **correção em uma linha** citando a seção do `DESIGN.md`.
3. Se um achado revelar uma regra ausente no `DESIGN.md` (lacuna, não quebra), classifique como `lacuna-do-contrato` e proponha o texto exato a acrescentar — mas não edite o arquivo.
4. Ordene por severidade.
5. Termine com veredito de uma linha: `aprovado` ou `reprovado` + número de achados de severidade alta.

Saída: `reports/audit-<data>.md`.

---

## Por que split

O julgamento pesado fica onde vale; a coleta mecânica fica no GPT Luna.
