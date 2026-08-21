# Achados iniciais do PDF `main(2).pdf`

## Páginas 1 a 5 visualizadas

A página 1 é a capa do documento **Guia Universal de Integração**, com o subtítulo informando que se trata das rotas do painel Rencia para a totalidade dos aplicativos.

A página 2 traz o sumário, indicando que o documento cobre fluxo mínimo obrigatório, conexão e configuração por aplicativo, validação e listas, aparência e identidade visual, heartbeat, comandos remotos, atualização de APK, rotas auxiliares, diagnóstico rápido e checklist de entrega.

A página 3 define a regra central: o aplicativo deve usar **HTTPS** e **Media Access Control (MAC)** do aparelho, consultar configuração, status e listas, aplicar imagens e nome do próprio app, enviar presença e conteúdo atual, consultar vencimento/avisos/failover, buscar comandos remotos pendentes e consultar atualização. A tabela mostra explicitamente um fluxo periódico com heartbeat e comandos remotos.

A página 4 detalha conexão por aplicativo. Para **Evolux**, a configuração por MAC aparece em rota específica do painel `/api/v5/apps/evolux/config?mac={MAC}`. Também aparece um modelo de JSON de configuração genérica contendo campos como `registered`, `allowed`, `mac`, `app_id`, `app_name`, `logo_url`, `banner_url`, `background_url`, `message_image_url`, `icons`, `playlist_urls`, `server_api_url`, `apk_download_url` e `apk_version`.

A página 5 reforça a validação básica compatível em rota `/api/device/check?mac={MAC}` e informa que a resposta deve trazer MAC, estado de acesso, app atribuído, lista principal, **EPG** e vencimento. Também há a regra crítica para **M3U e Xtream**: o aplicativo só deve interpretar a fonte como lista válida quando ela realmente devolver conteúdo compatível, sem converter HTML de login, Cloudflare ou bloqueio em JSON.

## Implicações para o aplicativo nativo

1. O fluxo correto não é apenas colar uma M3U: a aplicação precisa aceitar **configuração remota por MAC**.
2. O painel principal deve continuar igual visualmente, mas os dados devem vir do contrato do servidor, incluindo nome, logos, fundo, ícones e listas.
3. O app deve suportar `playlist_urls` múltiplas, `server_api_url`, `apk_download_url` e `apk_version`.
4. O carregamento do catálogo deve respeitar M3U e Xtream, com detecção de resposta inválida.
5. O app precisa prever heartbeat, comandos remotos, atualização e EPG como partes do fluxo oficial.

## Páginas 6 a 9 visualizadas

A página 6 detalha o uso dos campos visuais de configuração no APK: `logo_url` como logo principal, `banner_url` como banner ou destaque, `background_url` como imagem de fundo, `message_title`, `message_text` e `message_image_url` para mensagem visual configurada no painel, grupos de ícones (`icons.live_tv`, `icons.movies`, `icons.series`), `server_api_url` como API adicional e `apk_download_url` com `apk_version` para atualização exclusiva do app. Em seguida, o documento define a seção **Heartbeat, mensagens, vencimento e failover**. O app deve enviar presença e conteúdo assistido ao abrir, ao trocar de conteúdo e a cada 60 segundos; também deve consultar vencimento, alertas e failover.

A página 7 completa esse bloco funcional. O painel pode emitir alertas com `notifications[]`, e o app deve mostrar o texto de maneira amigável e confirmar leitura por ACK. Há ainda a rota de **falha real de reprodução** para reportar erro ao painel, permitindo troca de lista ativa sem fechar o aplicativo. Na sequência, o PDF define **comandos remotos por MAC**. O app deve consultar uma rota própria do dispositivo e executar comandos como `refresh_playlist`, `switch_playlist`, `update_dns`, `show_message`, `restart_player` e `sync_access`, sempre enviando ACK depois da execução.

A página 8 trata de **atualização de APK**. Cada aplicativo consulta apenas a própria rota e compara a versão instalada para instalar somente o APK correspondente ao mesmo app. Também aparecem rotas auxiliares como último conteúdo assistido, itens mais vistos, recentemente vistos, atualização do conteúdo assistido, compatibilidade de expiração, compatibilidade de listas, integração Roku/GCPro, teste do Maximus no painel e dados públicos da loja de aplicativos.

A página 9 traz o **diagnóstico rápido** e o **checklist de entrega**. Os problemas esperados incluem `404 na configuração` quando o MAC não está cadastrado, `403 na configuração` quando o MAC está em outro app, `command: null` para ausência de ordem pendente, ordens travadas na fila quando o APK não envia ACK, erro ao aceitar HTML como se fosse playlist e nome visual incorreto por cache. O checklist confirma que o app final deve respeitar `registered` e `allowed`, nunca interpretar HTML como JSON/lista, enviar heartbeat a cada 60 segundos, suportar alertas e failover e puxar logo, fundo, banner e ícones da configuração do próprio app.

## Requisitos adicionais confirmados pelo PDF

1. O aplicativo precisa suportar **configuração remota visual completa**: logo, banner, background, mensagem e ícones.
2. O aplicativo precisa ter **MAC por dispositivo** como chave primária de configuração e comando.
3. O aplicativo deve ter **heartbeat periódico** e atualização do conteúdo atual assistido.
4. O aplicativo deve implementar **alertas**, **failover**, **comandos remotos com ACK** e **rotas de atualização de APK**.
5. A validação do catálogo deve bloquear respostas HTML indevidas, como páginas de login ou proteção, antes de tratá-las como lista M3U ou dados JSON.
6. A próxima APK deve preservar o layout principal, mas precisa aproximar o comportamento de configuração do contrato do PDF, não apenas da M3U manual.
