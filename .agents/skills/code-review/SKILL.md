---
name: code-review
description: Use para revisar commits, branches ou mudanças locais do Vem Dançar JP, inclusive arquivos novos. Avalie separadamente padrões do projeto e aderência aos requisitos, com achados classificados por gravidade e veredito para revisão humana.
---

# Revisão de código

Origem: [mattpocock/skills, revisão 49dd158d1076134a641b33efb035946536778336](https://github.com/mattpocock/skills/blob/49dd158d1076134a641b33efb035946536778336/skills/engineering/code-review/SKILL.md). Adaptação para o Codex e para este projeto.

## Autoridade e escopo

`AGENTS.md` e as instruções explícitas do usuário prevalecem sobre esta skill. Revise sem alterar arquivos, instalar dependências ou executar commit, push ou merge. O veredito é uma recomendação; a aprovação das mudanças cabe ao usuário. Esta revisão funciona em um único agente, sem issue tracker ou subagentes.

1. Delimite o que será revisado. Para commits ou branches, valide a referência informada e a base de comparação. Se a base não for fornecida, identifique e declare a escolhida; se bases plausíveis diferentes mudarem materialmente o resultado, solicite confirmação antes da revisão. Para trabalho local, examine `git status`, `git diff --cached`, `git diff` e o conteúdo dos arquivos novos pertinentes. Separe mudanças preexistentes ou fora do escopo.
2. Reúna os critérios: pedido do usuário, `AGENTS.md`, seção relevante de `PLAN.md`, ADRs, contratos e testes existentes. Se faltar um requisito essencial, declare a limitação; não invente um issue tracker nem um contrato.
3. Revise o eixo **Padrões**: convenções documentadas, fronteiras de módulos, segurança, testes e fluxo de aprendizado. Verifique se `LEARNING.md` foi atualizado quando a mudança conclui um passo de `PLAN.md`.
4. Revise o eixo **Requisitos**: comportamentos pedidos, casos ausentes, regressões e alterações além do escopo. Confirme achados com trechos do diff e, quando útil, testes ou comandos de leitura.
5. Apresente os dois eixos separadamente. Para cada achado, informe gravidade (`bloqueante`, `alta`, `média` ou `baixa`), arquivo e linha, evidência, impacto e correção sugerida. Distinga violação comprovada de hipótese ou preferência de estilo.

Conclua com um veredito: **pronto para revisão humana**, **ajustes necessários** ou **inconclusivo**, justificando-o pelos achados. Informe também o escopo efetivamente examinado e as verificações executadas ou não executadas. Não aplique correções automaticamente.
