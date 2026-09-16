# Prompt 3 — Ler suas edições de volta e sincronizar

Rode depois de você mexer no canvas com a mão. É a metade humana do loop.
Motor sugerido: **GLM 5.2**.

---

Eu editei o arquivo Penpot manualmente. Leia o estado atual via MCP e reconcilie com `DESIGN.md` e com o código do projeto.

Passos, nesta ordem:

1. **Diff.** Compare o canvas com o `DESIGN.md` e liste o que eu mudou: valores, espaçamentos, estrutura de componente, nomes.

2. **Classifique cada mudança** em uma das três categorias e me mostre a lista antes de escrever qualquer arquivo:
   - `promover` — a mudança é uma decisão de design nova e boa; deve subir para o DESIGN.md como regra ou token;
   - `corrigir` — a mudança quebrou uma regra do contrato; o canvas deve voltar ao contrato;
   - `ignorar` — rascunho, exploração ou camada fora das páginas oficiais.

3. **Aguarde meu ok.** Não altere DESIGN.md, tokens ou canvas antes de eu confirmar a classificação.

4. Depois do ok:
   - para `promover`: atualize `DESIGN.md` e `tokens/core.json` (mantendo o formato DTCG com `$value`, `$type`, `$description`) e, se houver, o tema dark em `tokens/dark.json`; **nunca renomeie tokens existentes** — renomear quebra o vínculo com as camadas na Penpot;
   - para `corrigir`: ajuste o canvas via MCP, não o contrato;
   - propague para o código: atualize as CSS custom properties / config do Tailwind a partir dos tokens, sem tocar em lógica de componente.

5. Encerre com um resumo de três linhas: o que subiu para o contrato, o que voltou ao contrato, o que ficou pendente.

Regra dura: o DESIGN.md é a fonte da verdade. O canvas é uma vista. Quando os dois divergem, um dos dois está errado — e a decisão de qual é minha, não sua.
