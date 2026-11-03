---
name: tdd
description: Use ao implementar funcionalidades ou corrigir bugs com testes primeiro no Vem Dançar JP, incluindo testes de integração. Trabalhe em pequenas fatias red → green que comprovem comportamento observável.
---

# Desenvolvimento orientado por testes

Origem: [mattpocock/skills, revisão 49dd158d1076134a641b33efb035946536778336](https://github.com/mattpocock/skills/blob/49dd158d1076134a641b33efb035946536778336/skills/engineering/tdd/SKILL.md). Adaptação para o Codex e para este projeto.

## Autoridade

Siga `AGENTS.md`, o contrato explícito do usuário e o fluxo de aprendizado de `PLAN.md`. Essas instruções prevalecem sobre esta skill. O usuário aprova as mudanças; esta skill não autoriza commit, push, merge, descarte de alterações locais nem instalação de dependências.

## Ciclo

1. Identifique o comportamento solicitado e a fronteira pública pela qual ele será observado. Reutilize contratos e fronteiras de teste já aprovados no pedido, em `PLAN.md` ou nas ADRs. Se uma escolha nova de fronteira mudar materialmente o escopo, apresente-a ao usuário antes de escrever o teste.
2. Escreva um teste pequeno que falhe pelo comportamento ausente. Execute-o e confirme que a falha corresponde ao requisito, não a uma configuração quebrada.
3. Implemente somente o necessário para o teste passar. Execute o teste novamente e observe o resultado verde.
4. Repita com a próxima fatia de comportamento. Refatore apenas quando houver motivo concreto e preserve o resultado dos testes.
5. Execute as verificações aplicáveis de `AGENTS.md` e `PLAN.md`.

## Qualidade dos testes

- Verifique resultados observáveis por usuários ou consumidores da interface. Evite testar métodos privados, contagem de chamadas internas e detalhes que mudariam em um refactor sem alterar o comportamento.
- Use valores esperados independentes da implementação; evite testes que recalculam a resposta com o mesmo algoritmo.
- Simule serviços externos, tempo ou aleatoriedade quando necessário. Para persistência, prefira um banco de teste quando isso comprovar o contrato; prepare dados diretamente no banco apenas quando fizer sentido para o cenário.
- Escolha Vitest para a interface Vue e MockMvc/Testcontainers para contratos HTTP e persistência do backend, conforme a área alterada. Não adicione testes que apenas espelham uma edição textual ou uma implementação trivial.

Relate as fatias testadas, os comandos executados e seus resultados. Não crie commit, push ou merge sem autorização explícita do usuário.
