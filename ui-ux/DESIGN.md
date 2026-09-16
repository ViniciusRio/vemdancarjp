# DESIGN.md — Contrato de marca

> Este arquivo é a fonte da verdade do design system. Canvas (Penpot) e código são **vistas** dele.
> Qualquer agente que gerar UI deve ler este arquivo primeiro e obedecê-lo sem improvisar.
> Direção: **limpo e editorial** (referências mentais: Stripe, Notion). Produto: **app web / dashboard SaaS**.
> Versão 0.1 — inicial, gerado para ser substituído por extração a partir de referências reais.

---

## 1. Princípios (ordem de prioridade)

1. **Densidade legível.** É um dashboard: informação primeiro. Mas densidade nunca vira aperto — o espaço em branco é o que separa "profissional" de "genérico".
2. **Restrição cromática.** 1 acento + neutros quentes. Cor só entra quando codifica significado (estado, ação, dado). Se tudo é colorido, nada tem prioridade.
3. **Hierarquia por tipografia e espaço, não por caixas.** Antes de adicionar borda, sombra ou fundo, tente resolver com peso, tamanho e espaçamento.
4. **Zero ornamento.** Sem ilustrações, ícones decorativos, gradientes, glassmorphism ou sombras dramáticas. Nada entra sem função.
5. **Números são conteúdo de primeira classe.** Toda métrica usa numerais tabulares e alinhamento à direita em colunas.
6. **Acessibilidade não é opcional.** WCAG AA: 4.5:1 em texto corrido, 3:1 em texto grande. Estado nunca é comunicado só por cor.

### Anti-padrões (o que faz UI de IA parecer genérica)

- Card com sombra grande + borda + fundo cinza ao mesmo tempo (escolha **um**: borda fina).
- Gradiente roxo-azul em herói, botão ou ícone.
- Raio de canto acima de 12px em componentes de dado; pílulas em tudo.
- Emoji como ícone.
- Três pesos de fonte na mesma tela sem razão.
- Centralizar texto corrido.
- Sombra colorida (`box-shadow` com o acento).
- Sidebar escura colada em conteúdo claro sem transição de superfície.

---

## 2. Cor

Light-first. Neutros com temperatura levemente quente (não cinza azulado) — é o que dá o tom "editorial" em vez de "template de admin".

### Superfícies e texto (light)

| Token | Hex | Uso | Contraste |
|---|---|---|---|
| `bg.canvas` | `#FFFFFF` | fundo da área de conteúdo | — |
| `bg.subtle` | `#FBFAF9` | cards, painéis, linhas zebradas | — |
| `bg.muted` | `#F5F3F0` | sidebar, cabeçalho de tabela, estados hover | — |
| `border.default` | `#E4E0DA` | divisórias, borda de card e input | — |
| `border.strong` | `#CFC9C0` | borda de input em foco-repouso, separador de seção | — |
| `text.primary` | `#1A1815` | corpo, títulos, valores | 17.7:1 em branco |
| `text.secondary` | `#6B6660` | rótulos, legendas, placeholders | 5.7:1 |
| `text.tertiary` | `#7D776E` | desabilitado, metadados ≥16px | 4.4:1 — não usar em texto pequeno crítico |
| `text.inverse` | `#FFFFFF` | sobre acento e sobre superfícies escuras | 6.6:1 sobre acento |

### Acento e semântica

| Token | Hex | Uso | Contraste em branco |
|---|---|---|---|
| `accent.default` | `#1F4FD8` | ação primária, link, foco, série 1 de gráfico | 6.6:1 |
| `accent.hover` | `#1740B8` | hover/active do primário | 8.5:1 |
| `accent.subtle` | `#EDF1FE` | fundo de item selecionado, badge informativo | acento sobre ele: 5.9:1 |
| `success.default` | `#17693F` | confirmação, delta positivo | 6.7:1 |
| `warning.default` | `#8A5300` | atenção, degradação | 6.3:1 |
| `error.default` | `#B02A37` | erro, destrutivo, delta negativo | 6.5:1 |

Regras:
- **Um** botão de acento por tela (ou por seção de card). Ações secundárias são `border.default` + `text.primary`.
- Estado (success/warning/error) sempre acompanha ícone ou rótulo textual.
- `accent.default` é reservado para interação. Não pinte títulos, ícones de navegação ou bordas decorativas com ele.

### Modo escuro (derivado, não invertido)

| Token | Hex |
|---|---|
| `bg.canvas` | `#141311` |
| `bg.subtle` | `#1A1917` |
| `bg.muted` | `#211F1D` |
| `border.default` | `#332F2B` |
| `border.strong` | `#4A453F` |
| `text.primary` | `#EDEAE5` |
| `text.secondary` | `#A39D95` |
| `text.tertiary` | `#7A746C` |
| `accent.default` | `#7C9BFF` |
| `accent.hover` | `#9DB4FF` |
| `accent.subtle` | `#1B2540` |
| `success.default` | `#5DBE86` |
| `warning.default` | `#D9A047` |
| `error.default` | `#E8737F` |

### Série de gráficos (derivada do acento, ordem fixa)

`#1F4FD8` → `#B0522F` → `#1B4B5A` → `#8A5300` → `#5B4B8A` → `#17693F`

Máximo 5 séries por gráfico; acima disso use small multiples. Série destacada em 100% de opacidade, as outras em 45%.

---

## 3. Tipografia

Duas famílias. A personalidade vem de uma família de texto bem desenhada e de escala disciplinada — não de fonte display exótica.

| Papel | Família | Fallback |
|---|---|---|
| UI e texto | **Satoshi** (400 / 500 / 700) | `ui-sans-serif, system-ui, sans-serif` |
| Números, código, IDs | **JetBrains Mono** (400 / 500) | `ui-monospace, monospace` |
| Editorial (opcional, só em empty states, onboarding e marketing) | **Instrument Serif** (400) | `Georgia, serif` |

Proibido: Inter/Roboto/Poppins/Montserrat como primária, qualquer script ou display decorativa, e mais de 3 pesos ativos numa tela.

### Escala (base 16px, densidade de dashboard)

| Papel | Tamanho / linha | Peso | Tracking |
|---|---|---|---|
| Título de página | 30px / 36px | 700 | -0.02em |
| Título de seção | 20px / 28px | 600 | -0.01em |
| Título de card | 16px / 24px | 600 | 0 |
| Corpo | 15px / 24px | 400 | 0 |
| Corpo compacto (tabela) | 14px / 20px | 400 | 0 |
| Rótulo / legenda | 13px / 18px | 500 | 0 |
| Overline (cabeçalho de tabela) | 12px / 16px | 600 | 0.06em, maiúsculas |
| Valor de KPI | 30px / 34px | 600, tabular | -0.02em |

Regras:
- Medida de leitura: 60–75 caracteres. Texto corrido nunca passa de `680px`.
- `font-variant-numeric: tabular-nums lining-nums` em toda tabela, KPI e gráfico.
- Nunca espacejar minúsculas. Overline maiúscula é a única exceção.
- Piso absoluto: 12px.

---

## 4. Espaço, raio, elevação

- **Escala de espaço (base 4):** 2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64. Nada fora dela.
- **Grid:** 12 colunas, gutter 24px, largura máxima de conteúdo 1280px, padding lateral 32px (desktop) / 16px (mobile).
- **Densidade vertical:** linha de tabela 44px, campo de formulário 40px, botão 36px (compacto) ou 40px (padrão).
- **Raio:** `sm` 4px (badge, input, botão) · `md` 8px (card, popover) · `lg` 12px (modal, drawer). Nunca pílula, exceto avatar e toggle.
- **Elevação:** três níveis, sempre neutra e discreta.
  - `flat`: sem sombra, apenas `1px solid border.default` — **padrão de card**
  - `raised`: `0 1px 2px rgba(26,24,21,.06), 0 1px 1px rgba(26,24,21,.04)` — dropdown, popover
  - `overlay`: `0 8px 24px rgba(26,24,21,.10)` — modal, drawer
- Sombra e borda não se acumulam no mesmo elemento além de `raised`.

---

## 5. Componentes — regras de comportamento

**Botão.** Primário: fundo `accent.default`, texto `text.inverse`, raio `sm`, altura 40px, padding 0 16px. Secundário: fundo transparente, borda `border.default`. Terciário: só texto, `accent.default`. Destrutivo: `error.default`. Foco visível: anel de 2px `accent.default` com offset 2px — nunca remover outline.

**Input.** Altura 40px, borda `border.default`, raio `sm`, label acima em 13px/500, texto de ajuda em 13px `text.secondary`. Erro: borda `error.default` + mensagem textual abaixo (não só cor). Placeholder usa `text.secondary`, não `tertiary`.

**Card.** `bg.canvas` + `1px solid border.default`, raio `md`, padding 20px, título 16px/600, sem sombra. Cabeçalho e corpo separados por espaço, não por linha, salvo se houver ações no cabeçalho.

**Tabela.** Cabeçalho em overline, fundo `bg.muted`, sticky. Linhas com divisória `border.default` de 1px, hover `bg.subtle`. Números à direita e tabulares, texto à esquerda. Zebra apenas em tabelas com mais de 15 linhas. Estado vazio nunca é célula em branco: frase curta + ação.

**KPI.** Valor dominante (30px/600 tabular), rótulo 13px `text.secondary` acima, delta abaixo com seta + percentual + rótulo do período. Verde para positivo, vermelho para negativo, `text.secondary` para estável — sempre com seta, nunca só cor.

**Navegação.** Sidebar em `bg.muted`, 240px, item ativo com fundo `accent.subtle` + texto `accent.default` + peso 500. Sem ícone colorido, sem badge decorativo.

**Estados obrigatórios em todo componente:** default, hover, active, focus-visible, disabled, loading (skeleton em `bg.muted`, sem spinner girando no meio da tela), empty, error.

**Movimento.** 120ms para hover/foco, 200ms para entrada de painel, 240ms para modal. Curva `cubic-bezier(.2,0,.38,.9)`. Respeitar `prefers-reduced-motion`. Nada anima mais que opacidade, transformação e cor.

---

## 6. Checklist de auditoria (o agente roda antes de entregar)

- [ ] Todo valor de cor, espaço, raio e tipo vem de um token deste arquivo — zero hex ou px solto
- [ ] Um único botão de acento por tela
- [ ] Contraste AA verificado em texto e ícone informativo
- [ ] Números tabulares e alinhados à direita
- [ ] Oito estados presentes nos componentes interativos
- [ ] Nenhum item da lista de anti-padrões da seção 1
- [ ] Nomes de componentes e tokens idênticos entre canvas e código
- [ ] Foco visível em navegação por teclado, ordem de tabulação correta
