# Backend em produção

Guia de operação do servidor. As seções de preparação e recriação do ambiente ainda serão escritas.

## Atualizar a configuração do servidor

O workflow de deploy envia só o digest da imagem, porque a chave de automação não pode gravar arquivos no servidor. Por isso, o merge de uma mudança em `deploy/compose.prod.yml`, `deploy/Caddyfile` ou `deploy/deploy.sh` **não chega sozinho à produção**, mesmo com o workflow verde.

Depois do merge e do fim do workflow, a partir da raiz do repositório na `main` atualizada:

1. Copie os arquivos para o servidor, trocando `<IP>` pelo IP da VM:

   ```bash
   scp deploy/compose.prod.yml deploy/Caddyfile deploy/deploy.sh ubuntu@<IP>:/tmp/
   ```

2. No servidor, instale os arquivos e aplique a configuração. A trava impede que isso aconteça durante um deploy:

   ```bash
   sudo flock /opt/vemdancarjp/.deploy.lock sh -c '
     install -o deploy -g deploy -m 644 /tmp/compose.prod.yml /tmp/Caddyfile /opt/vemdancarjp/ &&
     install -o deploy -g deploy -m 755 /tmp/deploy.sh /opt/vemdancarjp/ &&
     cd /opt/vemdancarjp &&
     docker compose -f compose.prod.yml --env-file .env --env-file image.env up -d --force-recreate caddy &&
     docker compose -f compose.prod.yml --env-file .env --env-file image.env up -d
   '
   ```

   `--force-recreate caddy` é necessário porque o `Caddyfile` é montado como arquivo: o contêiner em execução continuaria vendo a versão antiga.

3. Confira o resultado de fora do servidor:

   ```bash
   curl -fsS -o /dev/null -w '%{http_code}\n' https://api.vemdancarjp.com.br/api/agenda
   curl -s -o /dev/null -w '%{http_code}\n' https://api.vemdancarjp.com.br/api/admin/events
   ```

   A agenda deve responder `200`, e a rota administrativa, `404`.

Para desfazer, repita os passos a partir de uma versão anterior desses arquivos. Um rollback por `workflow_dispatch` troca só a imagem e não reverte a configuração.
