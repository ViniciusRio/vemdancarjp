---
name: diagnosing-bugs
description: Use para investigar bugs, falhas de teste ou regressões no Vem Dançar JP. Reproduza o sintoma, teste hipóteses verificáveis e confirme a correção com uma prova de regressão quando aplicável.
---

# Diagnóstico de bugs

Origem: [mattpocock/skills, revisão 49dd158d1076134a641b33efb035946536778336](https://github.com/mattpocock/skills/blob/49dd158d1076134a641b33efb035946536778336/skills/engineering/diagnosing-bugs/SKILL.md). Adaptação para o Codex e para este projeto.

## Autoridade

Siga `AGENTS.md`, o pedido do usuário, `PLAN.md` e as ADRs pertinentes. Essas instruções prevalecem sobre esta skill. Preserve alterações locais; não execute ações destrutivas, instale dependências ou crie commit, push ou merge sem autorização explícita.

## Investigação

1. Construa uma reprodução que alcance o caminho real do bug e detecte o sintoma relatado. Prefira um teste focado, uma chamada HTTP local ou outro comando repetível. Execute a reprodução antes de propor uma correção; registre comando, resultado e limite da evidência.
2. Reduza o cenário enquanto o sintoma continuar reproduzível. Se não for possível reproduzir, informe o que foi tentado e quais dados ou acesso faltam, sem apresentar uma causa como confirmada.
3. Formule hipóteses ordenadas e falsificáveis. Para cada uma, indique qual observação a confirmaria ou refutaria; investigue uma variável por vez.
4. Identifique a causa apoiada pela reprodução. Quando houver uma fronteira adequada, crie um teste de regressão que falhe, implemente a correção e confirme o teste passando, seguindo o ciclo de TDD. Se não houver, explique a limitação e mantenha uma verificação reproduzível.
5. Reexecute o cenário original e as verificações aplicáveis. Remova instrumentação temporária. Se a correção concluir um passo de `PLAN.md`, registre o aprendizado em `LEARNING.md`.

## Dados sensíveis

Antes de mostrar comandos, logs ou artefatos, substitua segredos, tokens, cookies e dados pessoais por `<REDACTED>`. Não grave credenciais em testes, relatórios ou no vault. Use apenas as linhas necessárias para demonstrar o resultado.

Entregue causa confirmada ou hipóteses ainda abertas, evidências, teste de regressão e resultados das verificações. Não dependa de scripts externos que não existam neste repositório.
