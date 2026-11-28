# Documentação Técnica — Fluxo de Upload e Envio de Tarefas no D2L (`salavirtual.unicep.edu.br`)

Documentação técnica do fluxo de envio de arquivos em atividades (Dropbox / Tarefas) no **UnicepVirtual** (`https://salavirtual.unicep.edu.br`), baseado em **D2L Brightspace v20.26.11**, para servir de referência em uma futura implementação de envio pelo aplicativo **UNICEP Portal**.

> **Status atual no projeto:** A integração existente em `backend/internal/d2l/` permanece estritamente **somente leitura** (ver [DOCUMENTACAO_SALA_VIRTUAL.md](./DOCUMENTACAO_SALA_VIRTUAL.md)). Este documento registra apenas a anatomia observada do fluxo nativo na interface Web e os pontos de atenção para a implementação futura.

---

## 1. Visão Geral do Fluxo em 4 Etapas

O envio de arquivos pela interface Web do Brightspace não ocorre em um único `POST` direto com o arquivo no formulário final; ele usa uma **área temporária (`ImmutableTemp`)** dentro de um modal com `iframe`:

```text
1. GET  /d2l/lms/dropbox/user/folder_submit_files.d2l?db={dbId}&grpid=0&isprv=0&bp=0&ou={ou}
        └── Página principal da tarefa (formulário #d2l_form + tabela #z_q)

2. Clique em "Adicionar Arquivo" (#z_r)
        └── Abre modal div.ddial_o[role="dialog"] com iframe.ddial_c_frame
            (form action: main.d2l?d2l_body_type=2&ou={ou}&area=mycomputer&...)
        └── Controle #MFI_control (d2l-fileinput) envia o arquivo selecionado ("Carregar")
            para staging temporário:
            /d2l/common/viewFile.d2lfile/ImmutableTemp/<base64_sem_padding>/<arquivo>?ou={ou}

3. Clique em "Adicionar" (rodapé do modal)
        └── Fecha o modal e injeta o item na tabela #z_q + inputs ocultos z_q_* no #d2l_form
        └── Habilita o botão "Enviar" (#z_a)

4. Clique em "Enviar" (#z_a)
        └── POST multipart/form-data para folder_submit_files.d2l?db={dbId}&grpid=0&isprv=0&bp=0&ou={ou}
        └── Persiste o arquivo em /d2l/common/viewFile.d2lfile/Database/<id_base64>/<arquivo>?ou={ou}
        └── Redireciona/exibe recibo em:
            /d2l/lms/dropbox/user/folder_submit_files_results.d2l?ou={ou}&bp=0&sid={submissionId}
```

---

## 2. Detalhamento de Cada Etapa (DOM, Rotas e Campos)

### 2.1. Metadados Globais da Página (`<html>`)
- `data-app-version="20.26.11"`
- `data-cdn="https://s.brightspace.com/apps/brightspace/20.26.11.27286/"`
- `data-global-context`: JSON com `orgUnitId`, `orgId` e `userId`
- `data-oslo`: endpoints `/d2l/api/oslo/batch?languageId=19` e `/d2l/api/oslo/collection?languageId=19`
- `data-he-context`: inclui `maxFileSize: 2147483648` (2 GiB)
- `data-logging-endpoint`: `/d2l/lp/logger/provision?sessionToken=<token>`

---

### 2.2. Etapa 1 — Página da Atividade (`folder_submit_files.d2l`)
- **URL:**
  ```text
  GET /d2l/lms/dropbox/user/folder_submit_files.d2l?db={dbId}&grpid=0&isprv=0&bp=0&ou={ou}
  ```
- **Formulário principal:**
  ```html
  <form method="post"
        action="folder_submit_files.d2l?db={dbId}&grpid=0&isprv=0&bp=0&ou={ou}"
        id="d2l_form"
        enctype="multipart/form-data">
  ```
- **Campos `<input type="hidden">` iniciais no `#d2l_form`:**
  - **Controle de estado/RPC do framework D2L:**
    - `d2l_action`
    - `d2l_actionparam`
    - `d2l_hitCode`
    - `d2l_rf`
    - `d2l_controlMapPrev`
    - `d2l_controlMap`
    - `d2l_state`
    - `d2l_referrer` (token XSRF/CSRF)
  - **Metadados da tarefa:**
    - `confMessage` (`#z_c`)
    - `confWarning` (`#z_d`)
    - `dropboxId` (`#z_e`)
    - `hasStartedSubmission` (`#z_f`)
  - **Anexos do enunciado (existentes na atividade):**
    - `attachments_fsid`
    - `attachments_{attachmentFileId}_data`
    - `attachments_existing`
  - **Fila de arquivos da submissão:**
    - `z_q_fsid`
  - **Editor de comentários (`d2l-htmleditor`):**
    - `REDT_comments$id`
    - `REDT_comments$htmlOrgUnitId`
    - `REDT_comments$html` (`class="d2l-htmleditor-html"`)
    - `REDT_comments_hc`
- **Controles visíveis principais:**
  - `<table id="z_q" class="dcs">` — lista de arquivos preparados para envio
  - `<button type="button" class="d2l-button" id="z_r">Adicionar Arquivo</button>`
  - Barra flutuante (`<d2l-floating-buttons>`):
    - `<button type="button" primary="" class="d2l-button" id="z_a">Enviar</button>`
    - `<button type="button" class="d2l-button" id="z_b">Cancelar</button>`

---

### 2.3. Etapa 2 — Modal de Upload (`iframe.ddial_c_frame` e `ImmutableTemp`)
Ao acionar **Adicionar Arquivo** (`#z_r`), o Brightspace abre `<div class="ddial_o" role="dialog">` contendo um `<iframe class="ddial_c_frame">` (com `src` inicial `/d2l/tools/blank.html` populado dinamicamente) e os botões de rodapé **Adicionar**, **Voltar** e **Cancelar**.

- **Formulário dentro do `iframe`:**
  - `id="d2l_form"`, `method="post"`, `enctype="multipart/form-data"`
  - `action` observado:
    ```text
    main.d2l?d2l_body_type=2&ou={ou}&area=mycomputer&f=0&af=mycomputer%2cmylocker%2cgrouplocker%2clearningportfolio%2cnewgooglefiledownloader&am=1&fsc=0&asc=0&mfs=0&afid=0&path=&ext=&d2l_stateScopes=...&d2l_stateGroups=%5b%22page%22%5d&d2l_statePageId=73&d2l_state_page=%7b%22Name%22%3a%22page%22%7d&d2l_change=0
    ```
  - Campos ocultos do modal: `d2l_action`, `d2l_actionparam`, `d2l_hitCode`, `d2l_rf`, `d2l_controlMapPrev`, `z_a`..`z_i`, `z_o`, `d2l_controlMap`, `d2l_state`, `d2l_referrer`.

- **Componente de upload (`#MFI_control`):**
  ```html
  <div id="MFI_control"
       class="d2l-fileinput ... d2l-filedrop ..."
       data-d2l-ismultiple="true"
       data-d2l-msg-uploadcomplete="Concluído."
       data-d2l-validatefocus="...">
  ```
  - O seletor de arquivo é acionado dinamicamente pelo botão **Carregar** (não há `<input type="file">` estático visível antes da interação).
  - Após o envio assíncrono do arquivo no modal, ele aparece dentro de `<ul class="d2l-fileinput-filelist">` em um `<li data-d2l-name="<nome_arquivo>">` com:
    - **4 campos ocultos por arquivo:**
      - `MFI_control$files$ActionType`
      - `MFI_control$files$PluginKey`
      - `MFI_control$files$Id`
      - `MFI_control$files$FileSize`
      *(seguido de um `<li class="d2l-fileinput-placeholder">` com `MFI_control$files`1$ActionType`, `PluginKey`, `Id`, `FileSize`)*
    - **Identificador composto de staging:**
      ```text
      <uuid>;<nome_arquivo>;<tamanho_bytes>
      ```
    - **URL temporária gerada (`ImmutableTemp`):**
      ```text
      /d2l/common/viewFile.d2lfile/ImmutableTemp/<base64_sem_padding(uuid;nome_arquivo;tamanho_bytes)>/<nome_arquivo>?ou={ou}
      ```

---

### 2.4. Etapa 3 — Confirmação no Modal ("Adicionar")
Ao clicar em **Adicionar** no diálogo:
1. O modal `div.ddial_o` fecha.
2. Na página principal (`folder_submit_files.d2l`), a tabela `<table id="z_q" class="dcs">` passa a exibir o contador `(1) arquivo(s) a serem enviado(s)` e uma linha `<tr class="dcs_r vui-changed">` contendo:
   - Link para `/d2l/common/viewFile.d2lfile/ImmutableTemp/<base64>/<nome_arquivo>?ou={ou}`
   - Origem: `Origem: Meu Computador` (`span.dfl_s`)
   - Ação de remoção (`title="Remover <nome_arquivo>"`)
3. O `#d2l_form` recebe os campos ocultos que referenciam o arquivo em staging:
   - `z_q_fsid`
   - `z_q_<uuid>;<nome_arquivo>;<tamanho_bytes>_data`
   - `z_q_new`
4. O botão **Enviar** (`button#z_a`) passa para o estado habilitado.

---

### 2.5. Etapa 4 — Submissão Final ("Enviar") e Recibo
Ao clicar em **Enviar** (`button#z_a`):
1. O formulário `#d2l_form` submete `POST multipart/form-data` para:
   ```text
   POST /d2l/lms/dropbox/user/folder_submit_files.d2l?db={dbId}&grpid=0&isprv=0&bp=0&ou={ou}
   ```
2. O D2L move o arquivo de `ImmutableTemp` para armazenamento persistente em:
   ```text
   /d2l/common/viewFile.d2lfile/Database/<fileId_base64>/<nome_arquivo>?ou={ou}
   ```
3. A página final de confirmação é exibida em:
   ```text
   GET /d2l/lms/dropbox/user/folder_submit_files_results.d2l?ou={ou}&bp=0&sid={submissionId}
   ```
4. **Estrutura do recibo na tela de confirmação:**
   - Cabeçalho: `Analise o envio de atividade` / **`Envio de arquivo com êxito`**
   - Metadados renderizados:
     - `ID de envio` (`{submissionId}`)
     - `Envio(s)` (link para `/d2l/common/viewFile.d2lfile/Database/<fileId_base64>/<nome_arquivo>?ou={ou}` + tamanho)
     - `Comentários`
     - `Atividade`
     - `Data de envio de atividades`
     - `Enviado por`
     - `Tamanho de Arquivo Total`
   - Botões pós-envio: `Concluído` (`#z_a`), `Exibir Histórico` (`#z_b`), `Carregar Mais Arquivos` (`#z_c`).

---

## 3. Pontos Não Capturados na Inspeção Passiva

Por restrições do ambiente de captura utilizado (buffer circular limitado às últimas 50 requisições, ausência de corpo de `POST` no log e redação automática de valores de `<input type="hidden">` e `<script>` inline):
- **Endpoint intermediário do botão "Carregar":** a rota exata chamada pelo `#MFI_control` dentro do modal `main.d2l` para enviar os bytes do arquivo e obter o `<uuid>` em `ImmutableTemp` não ficou registrada no buffer (evictada pelos carregamentos de scripts `bsi` da página final).
- **Valores/formato exato dos parâmetros D2L no `POST`:** os valores exatos de `d2l_action`, `d2l_actionparam`, `d2l_hitCode`, `d2l_controlMap`, `d2l_state` e `z_q_*_data` precisam ser observados via DevTools/HAR caso seja necessário implementar o fallback pelo fluxo Web clássico.

---

## 4. Caminhos para Futura Implementação no UNICEP Portal

O cliente Go atual (`backend/internal/d2l/`) já mantém `CookieJar` autenticado via `/d2l/lp/auth/login/login.d2l`, já resolve `{ou}` e `{dbId}` em `fetchers.go`, e já utiliza a API REST Valence (`le/1.74`, `lp/1.43`).

### Opção A — API REST Oficial Valence (testar primeiro)
- **Endpoint padrão do Brightspace:**
  ```text
  POST /d2l/api/le/1.74/{ou}/dropbox/folders/{dbId}/submissions/mysubmissions/
  ```
  - Corpo `multipart/mixed`: parte JSON com descrição/comentário (`Text` / `Html`) + partes binárias com os arquivos.
  - Autenticação: mesmos cookies da sessão + cabeçalho `X-Csrf-Token` (obtido via `XSRF.Token` / endpoint de token CSRF do Brightspace).
- **Ponto crítico a validar antes de adotar:** conforme registrado em [DOCUMENTACAO_SALA_VIRTUAL.md](./DOCUMENTACAO_SALA_VIRTUAL.md) (seção 4.2), `GET .../submissions/mysubmissions/` retorna `403` com a sessão Web de aluno nesta instância. É necessário testar se o `POST .../submissions/mysubmissions/` (com `X-Csrf-Token`) também retorna `403` ou se é aceito. Se for aceito, elimina completamente a necessidade de emular `ImmutableTemp` e os campos de estado `d2l_*`.

### Opção B — Emulação do Fluxo Web Nativo (fallback)
Caso a Opção A retorne `403`, será necessário reproduzir o fluxo mapeado neste documento:
1. Capturar via DevTools/HAR os detalhes faltantes do upload intermediário do `#MFI_control` (`Carregar` → `ImmutableTemp`) e os valores de `d2l_action`/`d2l_actionparam`/`z_q_*_data`.
2. Executar `GET folder_submit_files.d2l` para extrair estado/tokens da sessão (`d2l_hitCode`, `d2l_state`, `d2l_controlMap`, `d2l_referrer`, `z_q_fsid`).
3. Enviar o arquivo para o endpoint de staging (`ImmutableTemp`) e submeter o `POST multipart/form-data` para `folder_submit_files.d2l`.
4. Confirmar o sucesso verificando o redirecionamento/HTML de `folder_submit_files_results.d2l?...&sid={submissionId}` (e invalidar o cache de overview do aluno em `backend/internal/d2l/client.go`).

### Requisitos de Segurança no BFF (quando for implementado)
- Validar tamanho máximo e tipo de arquivo no backend antes de encaminhar ao D2L.
- Exigir autenticação `Authorization: Bearer` padrão do BFF e nunca expor cookies da sessão D2L ao cliente.
- Atualizar `backend/internal/d2l/security.go` apenas quando houver endpoints de escrita explicitamente autorizados e auditados.
