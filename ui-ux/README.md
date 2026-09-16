# Stack de design agêntico — Penpot + MCP + GLM 5.2 / Kimi K3

Canvas bidirecional entre você e seus agentes, sem depender de assento pago de vendor
nem de modelo fechado. Você edita com a mão, o agente lê e escreve de volta,
e o contrato de marca vive em texto versionado no git.

```
                    DESIGN.md  +  tokens/*.json      <-- fonte da verdade (git)
                            |            ^
                 aplica     v            | promove decisões
                 ┌──────────────────────────────┐
   Penpot  <---> │  agente: Kimi K3 / GLM 5.2   │ <---> código do produto
   (canvas)  MCP └──────────────────────────────┘
      ^
      └── suas edições manuais
```

## Conteúdo

| Caminho | O que é |
|---|---|
| `DESIGN.md` | Contrato de marca. Direção limpa/editorial para dashboard SaaS. É o arquivo que o agente lê antes de qualquer coisa. |
| `tokens/core.json` | Tokens no formato DTCG, prontos para importar no painel Tokens do Penpot. |
| `tokens/dark.json` | Conjunto dark, para importar como segundo set/tema. |
| `config/env.example` | Chaves e endpoints do GLM e do Kimi, com atalhos `use_glm` / `use_kimi`. |
| `config/opencode.jsonc` | Providers GLM 5.2 e Kimi K3 no OpenCode + MCP da Penpot. |
| `config/mcp-stdio.json` | Config para clientes MCP que só falam stdio. |
| `scripts/01-penpot-up.sh` | Sobe o Penpot self-hosted. |
| `scripts/02-mcp-up.sh` | Sobe o servidor MCP local da Penpot. |
| `prompts/01..04` | Os quatro prompts do loop: materializar, auditar, sincronizar, extrair de referências. |

---

## Passo 1 — Penpot self-hosted

Precisa de `docker` e `docker compose`. O compose oficial fica em um único arquivo,
baixado do repositório da Penpot ([docs](https://help.penpot.app/technical-guide/getting-started/docker/)):

```bash
chmod +x scripts/*.sh
./scripts/01-penpot-up.sh
```

A instância sobe em `http://localhost:9001`. Se preferir criar o usuário por CLI em vez do
fluxo de registro, o `manage.py` faz isso dentro do container:

```bash
docker ps   # descubra o nome exato do container do backend
docker exec -ti penpot-penpot-backend-1 python3 manage.py create-profile --skip-tutorial --skip-walkthrough
```

Atualizar depois: `docker compose -f docker-compose.yaml pull` e subir de novo — a recomendação
oficial é atualizar em incrementos pequenos, não pular várias versões de uma vez.

## Passo 2 — MCP da Penpot

Há dois caminhos, e vale conhecer os dois.

**Remoto (mais simples).** Em `Sua conta → Integrações → MCP Server`, ative o toggle e gere a
chave MCP — ela aparece **uma única vez**, então já cole em `config/.env.local`. A URL tem o formato
`https://<seu-dominio>/mcp/stream?userToken=SUA_CHAVE`, e o registro no cliente é uma linha
([docs](https://help.penpot.app/mcp/)):

```bash
npx -y add-mcp -g -n penpot "$PENPOT_MCP_URL"
```

**Local (mais controle).** Requer Node 22:

```bash
./scripts/02-mcp-up.sh
```

O servidor expõe `http://localhost:4401/mcp` (streamable HTTP) e `http://localhost:4401/sse` (legado).
Clientes que só falam stdio precisam do proxy `mcp-remote` — é o que está em `config/mcp-stdio.json`
([referência](https://github.com/penpot/penpot-mcp)).

Em qualquer um dos dois caminhos, o passo final é o mesmo: abra um arquivo de design e conecte o
plugin MCP (`File → MCP Server → Connect`, ou carregando `http://localhost:4400/manifest.json` no
menu Plugins na versão local). **Não feche a UI do plugin** — fechar derruba a conexão. Chromium 142+
pede permissão de acesso à rede local; aprove o popup. No Brave, desligue o Shield no domínio do Penpot.

## Passo 3 — Seus modelos como motor

```bash
cp config/env.example config/.env.local   # preencha as chaves
source config/.env.local
```

**Kimi K3.** O Kimi Code CLI instala por script e autentica com `/login`
([docs](https://www.kimi.com/code/docs/en/kimi-code-cli/guides/getting-started.html)):

```bash
curl -fsSL https://code.kimi.com/kimi-code/install.sh | bash
kimi          # depois: /login
```

**GLM 5.2.** Duas rotas, use a que preferir:

- *Via OpenCode* — copie `config/opencode.jsonc` para `~/.config/opencode/opencode.jsonc`. Ele declara
  `zai/glm-5.2` e `moonshot/kimi-k3` como providers OpenAI-compatíveis e já registra o MCP da Penpot.
  Depois é `/connect` para gravar as chaves e `/models` para escolher o motor
  ([docs](https://opencode.ai/docs/providers/)).
- *Via endpoint Anthropic-compatível* — o GLM Coding Plan expõe uma rota que fala a Messages API, então
  qualquer cliente estilo Claude Code aponta para lá com troca de variável de ambiente, sem alterar código
  ([Developers Digest](https://www.developersdigest.tech/blog/glm-5-2-free-and-cheap-access-2026)).
  O Kimi tem endpoint equivalente ([Kimi Platform](https://platform.kimi.ai/docs/guide/claude-code-kimi)).
  É para isso que servem os atalhos `use_glm` e `use_kimi` no `env.example`:

```bash
use_glm    # motor: GLM 5.2
use_kimi   # motor: Kimi K3
```

Trocar de modelo passa a ser trocar de chave. É a vantagem prática de não estar preso a um vendor.

## Passo 4 — Tokens no canvas

No arquivo Penpot, painel **Tokens → importar**, e escolha `tokens/core.json`. O Penpot usa o nome do
arquivo como nome do set, aceita referências entre tokens no formato `{palette.blue-600}` e permite
exportar, editar em editor de texto e reimportar sem perder o vínculo com as camadas — **desde que você
não renomeie os tokens** ([guia do formato](https://penpot.app/blog/a-practical-guide-to-the-design-tokens-json-format/)).
Repita para `tokens/dark.json` como segundo set.

## Passo 5 — O loop

| Momento | Prompt | Motor sugerido |
|---|---|---|
| Construir foundations e componentes no canvas | `prompts/01-materializar-no-canvas.md` | GLM 5.2 |
| Revisar o que existe contra o contrato | `prompts/02-auditar.md` | Kimi K3 |
| Depois de você editar à mão | `prompts/03-ler-de-volta.md` | GLM 5.2 |
| Trocar o sistema inicial pelo seu gosto real | `prompts/04-extrair-de-referencias.md` | Kimi K3 |

Regra que sustenta o loop: **quando canvas e contrato divergem, quem decide é você.** O agente
classifica a divergência em promover / corrigir / ignorar e espera confirmação antes de escrever.

---

## Duas coisas que valem dizer com clareza

**O DESIGN.md que veio aqui é um andaime, não o seu design.** Ele está bem construído — paleta com
contraste verificado, escala disciplinada, lista de anti-padrões — mas foi derivado de uma direção
genérica ("limpo e editorial"), não do seu gosto. Rode o prompt 4 com 2–3 referências reais o quanto
antes. Sistema sem referência converge para a média da internet, que é exatamente o que você quer
abandonar.

**A Penpot dá o canvas aberto, não o polimento do Figma.** Se em algum momento você precisar de
handoff com designers de fora ou de plugins do ecossistema Figma, o Figma MCP também escreve no canvas
desde fevereiro de 2026 e cria primitivas nativas — frames, componentes e variables
([Figma](https://developers.figma.com/docs/figma-mcp-server/write-to-canvas/)). O custo é assento pago
e limites de chamada por plano ([limites](https://developers.figma.com/docs/figma-mcp-server/rate-limits-access/)).
A arquitetura deste repositório não muda: só o adaptador de canvas.
