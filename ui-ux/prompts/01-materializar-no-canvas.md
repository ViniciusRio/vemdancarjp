# Prompt 1 — Materializar o design system no canvas

Use com o agente conectado ao MCP da Penpot, com um arquivo de design aberto.
Motor sugerido: **GLM 5.2** (trabalho estrutural, muitas chamadas de ferramenta).

---

Leia `DESIGN.md` na raiz do projeto e trate-o como contrato: nenhum valor de cor, espaço, raio ou tipografia pode ser inventado fora dele.

No arquivo Penpot aberto, crie uma página chamada `Foundations` contendo, nesta ordem, em frames separados e nomeados:

1. `Cores` — swatches de todos os tokens de cor, agrupados por família (bg, border, text, accent, feedback), cada um com nome do token, hex e razão de contraste anotada em 12px.
2. `Tipografia` — a escala completa da seção 3, um item por linha, mostrando papel, tamanho/linha e peso, usando o texto real "Receita recorrente mensal" em vez de lorem ipsum.
3. `Espaco e raio` — barras representando a escala de espaço e quadrados representando os três raios, rotulados.
4. `Elevacao` — os três níveis (flat, raised, overlay) em cards idênticos, lado a lado.

Depois crie uma página `Componentes` com um frame por componente da seção 5 (Botão, Input, Card, Tabela, KPI, Navegação). Para cada um:

- construa como **componente Penpot reutilizável**, não como grupo solto;
- aplique **design tokens** existentes no arquivo, nunca valores literais;
- inclua todos os estados listados no DESIGN.md como variantes nomeadas (`default`, `hover`, `active`, `focus`, `disabled`, `loading`, `empty`, `error`);
- use conteúdo realista em português (nomes de métrica, valores em BRL, datas em pt-BR).

Regras de execução:

- use Auto Layout em tudo que empilha ou alinha; nada posicionado em absoluto;
- nomeie camadas em kebab-case, espelhando os nomes que serão usados no código (`kpi-card`, `data-table-row`);
- não crie ícones desenhados à mão — deixe placeholders quadrados de 16px nomeados `icon-slot`;
- ao terminar, liste em texto quais tokens você usou e quais componentes criou.

Não avance para telas de produto neste passo.
