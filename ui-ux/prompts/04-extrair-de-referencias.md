# Prompt 4 — Substituir o DESIGN.md inicial por extração de referências

Este é o passo que tira o design do território "genérico de IA".
Rode com 2 ou 3 referências suas (prints ou URLs de produtos cuja estética você quer).
Motor sugerido: **Kimi K3** (leitura visual e contexto longo).

---

Anexei referências: `refs/<arquivos ou links>`.

Sua tarefa é **extrair o sistema por trás delas**, não copiar telas. Não desenhe nada nesta etapa.

Para cada referência, documente:

1. **Paleta real** — amostre as cores efetivamente usadas, com hex, e classifique cada uma por papel (fundo, superfície, borda, texto primário, texto secundário, acento, semântica). Diga a proporção aproximada de área que cada uma ocupa.
2. **Temperatura do neutro** — os cinzas puxam para quente ou frio? Isso é metade da identidade.
3. **Tipografia** — famílias (ou o gênero delas, se não identificável), escala observada, pesos usados, tracking em títulos, medida de leitura.
4. **Ritmo espacial** — qual é a unidade base aparente (4? 8?), padding de card, altura de linha de tabela, densidade geral.
5. **Forma** — raios, uso de borda vs sombra, níveis de elevação.
6. **Regras implícitas** — o que essas referências claramente *não* fazem. Essa lista vale mais que a de cores.

Depois consolide num único sistema coerente:

- onde as referências divergem, escolha e **justifique em uma linha** — não faça média entre elas, média é o caminho de volta ao genérico;
- mantenha a estrutura de seções do `DESIGN.md` atual;
- garanta WCAG AA calculando as razões de contraste de cada par texto/fundo proposto, e ajuste os valores até passar;
- preserve os **nomes de token já existentes** em `tokens/core.json`, trocando apenas os valores — renomear quebra o vínculo com as camadas da Penpot.

Entregue três arquivos:

- `DESIGN.md` (substituindo o atual)
- `tokens/core.json` e `tokens/dark.json` atualizados, em formato DTCG
- `refs/analise.md` com a extração bruta de cada referência, para eu conferir seu raciocínio

Se as referências forem esteticamente incompatíveis entre si, diga isso na cara e me pergunte qual manda, em vez de conciliar.
