# Documentação Técnica — Sala Virtual UNICEP (`salavirtual.unicep.edu.br`)

Documentação de engenharia reversa **estritamente passiva (somente leitura)** do ambiente virtual de aprendizagem **UnicepVirtual** (`https://salavirtual.unicep.edu.br/d2l/home`), baseado em **D2L Brightspace v20.26.11**, voltada para integração somente leitura no aplicativo UNICEP Portal.

> **Política de Uso Passivo:** Nenhum endpoint de escrita, envio de atividades (`POST .../submissions/`), início/envio de questionários, postagem em fóruns ou alteração de perfil/configurações deve ser invocado pelo cliente automatizado até decisão explícita em contrário.

---

## 1. Visão Geral da Arquitetura

| Componente | Detalhe observado |
| :--- | :--- |
| **Plataforma** | **D2L Brightspace v20.26.11** |
| **Host** | `https://salavirtual.unicep.edu.br` |
| **Unidade Organizacional Raiz** | `OrgUnitId = 6606` (Instituição) |
| **Versões de API REST descobertas** | `GET /d2l/api/versions/` → `le: 1.100` (1.0–1.100), `lp: 1.64` (1.0–1.64), `ep: 2.5`, `bas: 1.6`, `lti: 1.3`, `rp: 1.4` |
| **Charset / Formato** | **UTF-8** (`application/json` nas APIs `/d2l/api/*` e `/d2l/le/manageCourses/api/*`; widgets legados retornam JSON prefixado por `while(1);`) |
| **Autenticação** | Formulário nativo Brightspace (`/d2l/lp/auth/login/login.d2l`) ou SSO via `cdseducacional.com.br/d2l_login.php` |

---

## 2. Fluxo de Autenticação e Sessão

### 2.1. Redirecionamento Não Autenticado
- `GET https://salavirtual.unicep.edu.br/d2l/home` sem cookies de sessão retorna HTML/JS que redireciona para:
  `https://salavirtual.unicep.edu.br/d2l/login?sessionExpired=0&target=%2fd2l%2fhome`

### 2.2. Login Direto (Formulário Nativo)
- **Endpoint:** `POST https://salavirtual.unicep.edu.br/d2l/lp/auth/login/login.d2l`
- **Content-Type:** `application/x-www-form-urlencoded`
- **Campos do formulário:**
  - `d2l_referrer`: `""` (vazio no login inicial)
  - `target`: `/d2l/home`
  - `loginPath`: `/d2l/login`
  - `userName`: RA do aluno
  - `password`: senha do aluno
- **Resposta:**
  - Sucesso: `302 Found` com `Location: /d2l/home` (ou `target`).
  - Falha: redireciona ou permanece em `/d2l/login` (ex.: `failed=1`).

### 2.3. Cookies de Sessão e Token CSRF
- **Cookies `HttpOnly; Secure` definidos na sessão:**
  - `d2lSessionVal` (`path=/; secure; HttpOnly; SameSite=None`)
  - `d2lSecureSessionVal` (`path=/; secure; HttpOnly; SameSite=None`)
  - `d2lSameSiteCanaryA=1` (`SameSite=None`) e `d2lSameSiteCanaryB=1` (`SameSite=Lax`)
- **Token CSRF / XSRF:**
  - Páginas HTML autenticadas gravam `XSRF.Token` no `localStorage`.
  - Requisições `GET` nas APIs `/d2l/api/*` e `/d2l/le/*` autenticam apenas com os cookies `d2lSessionVal` + `d2lSecureSessionVal`.
  - Requisições `POST`/`PUT`/`DELETE` da UI enviam o header `X-Csrf-Token: <XSRF.Token>` (APIs REST) ou `d2l_referrer=<XSRF.Token>` (endpoints RPC legados).
- **Expiração:** 60 minutos de inatividade.

### 2.4. Logout
- `GET https://salavirtual.unicep.edu.br/d2l/logout` encerra a sessão e redireciona para `/d2l/lp/auth/session/Expired?isLogout=1&...`.

---

## 3. Endpoints Globais (Home `ou=6606` e Usuário)

Todos os endpoints REST abaixo funcionam via **`GET`** usando os cookies da sessão autenticada:

| Recurso | Método e Rota | Descrição / Observações |
| :--- | :--- | :--- |
| **Versões de API** | `GET /d2l/api/versions/` | Lista produtos e versões suportadas (`le`, `lp`, `ep`, etc.). |
| **Usuário logado** | `GET /d2l/api/lp/1.0/users/whoami` | Retorna `Identifier` (UserId), `FirstName`, `LastName`, `UniqueName` (RA), `ProfileIdentifier`. |
| **Perfil próprio** | `GET /d2l/api/lp/1.47/profile/myProfile` | Perfil do aluno (`GET /users/{id}` retorna `403` para perfil aluno). |
| **Semestres** | `GET /d2l/le/manageCourses/api/mysemesters?isAscending=true&widgetId=97` | Array JSON com `OrgUnitId`, `Name`, `Code` (ex.: `2/2026`, `1/2026`). |
| **Cursos do widget** | `GET /d2l/le/manageCourses/api/mycourses?pageSize=20&sort=current&autoPinCourses=false&orgUnitTypeId=3&promotePins=true&embedDepth=0&widgetId=97` | Lista cursos com `OrgUnitId`, `Name`, `Code`, `IsActive`, `StartDate`, `EndDate`, `SemesterName`, `CanAccessCourse`, `LastAccessed`, `LastModifiedDate`, `FormattedImageLink`. |
| **Matrículas (Valence)** | `GET /d2l/api/lp/1.43/enrollments/myenrollments/` | Lista paginada `{ PagingInfo, Items: [{ OrgUnit, Access, PinDate }] }`. |
| **Avisos Institucionais** | `GET /d2l/api/le/1.74/6606/news/` | Lista de comunicados da instituição (`OrgUnitId = 6606`). |
| **Calendário Global** | `GET /d2l/api/le/1.74/calendar/events/myEvents/?startDateTime=...&endDateTime=...&orgUnitIdsCSV=6606,...` | Eventos de calendário filtrados pelos `OrgUnitId`s (parâmetro `orgUnitIdsCSV` obrigatório; sem ele retorna `400`). |
| **Sininho de Alertas** | `GET /d2l/NavigationArea/6606/ActivityFeed/GetAlertsDaylight?Category={0\|1\|2}&isXhr=true` | `0`=Subscrições, `1`=Atualizações (prazos, notas, conteúdo, avisos), `2`=Mensagens. Retorna JSON prefixado por `while(1);` com `Payload.Html`. |
| **Paginação de Alertas** | `GET /d2l/NavigationArea/6606/ActivityFeed/GetMoreAlerts?Category={cat}&LastMessage={ISO8601}` | Próxima página de alertas do sininho. |
| **Mensagens (Pager)** | `GET /d2l/lms/pager/messageList.d2l?ou=6606` | Interface web de mensagens instantâneas. |

---

## 4. Endpoints por Disciplina (`ou = <OrgUnitId>`)

### 4.1. Conteúdo / Módulos e Arquivos
- **Web UI:** `/d2l/le/content/{ou}/Home` e `/d2l/le/content/{ou}/viewContent/{topicId}/View`
- **REST API:**
  - `GET /d2l/api/le/1.74/{ou}/content/toc` — árvore completa de `Modules[]` (recursiva) e `Topics[]` (`TopicId`, `Title`, `TypeIdentifier`, `Url`, `Unread`, `ActivityType`, `LastModifiedDate`).
  - `GET /d2l/api/le/1.74/{ou}/content/root/` — módulos raiz.
  - `GET /d2l/api/le/1.74/{ou}/content/topics/{topicId}` — metadados do tópico.
- **Download / Leitura de Arquivos:**
  - **Via API REST (preferencial):** `GET /d2l/api/le/1.74/{ou}/content/topics/{topicId}/file?stream=false` (`200 OK`, stream binário direto com `Content-Type` original).
  - **Via caminho estático:** `GET /content/enforced/{ou}-{CourseCode}/{Arquivo}?ou={ou}` (`200 OK` com cookies de sessão).

### 4.2. Atividades / Tarefas (Dropbox) — Somente Leitura
- **Web UI:**
  - Lista: `/d2l/lms/dropbox/user/folders_list.d2l?ou={ou}&isprv=0`
  - Detalhes: `/d2l/lms/dropbox/user/folder_submit_files.d2l?db={dbId}&grpid=0&isprv=0&bp=0&ou={ou}`
  - Histórico: `/d2l/lms/dropbox/user/folders_history.d2l?db={dbId}&grpid=0&isprv=0&bp=0&ou={ou}`
  - Feedback: `/d2l/lms/dropbox/user/folder_user_view_feedback.d2l?ou={ou}&db={dbId}&grpid=0`
- **REST API (`GET`):**
  - `GET /d2l/api/le/1.74/{ou}/dropbox/folders/` — lista atividades (`Id`, `Name`, `CustomInstructions.{Text,Html}`, `Attachments[]`, `DueDate`, `Availability`, `Assessment`, `DropboxType`).
  - `GET /d2l/api/le/1.74/{ou}/dropbox/folders/{dbId}` — detalhes de uma atividade.
  - `GET /d2l/api/le/1.74/{ou}/dropbox/folders/{dbId}/attachments/{fileId}` — download de anexo do enunciado (`200 OK`, binário direto).
  - `GET /d2l/api/le/1.74/{ou}/dropbox/folders/{dbId}/submissions/` — status do envio do aluno (`Status`, `Submissions[{Id, SubmissionDate, Comment, Files[]}]`, `Feedback.{Score, Feedback.{Text,Html}, Files[]}`, `CompletionDate`).
    - *Nota:* `/submissions/mysubmissions/` retorna `403` e `/feedback/` retorna `404` com sessão web de aluno; `/submissions/` retorna `200 OK`.
  - `GET /d2l/api/le/1.74/{ou}/dropbox/folders/{dbId}/submissions/{submissionId}/files/{fileId}` — download de arquivo enviado pelo aluno (`200 OK`, binário direto com sessão do aluno).

### 4.3. Notas (Gradebook do AVA)
- **Web UI:** `/d2l/lms/grades/my_grades/main.d2l?ou={ou}`
- **REST API (`GET`):**
  - `GET /d2l/api/le/1.74/{ou}/grades/` — definição dos itens de nota (`Id`, `Name`, `MaxPoints`, `Weight`, `GradeType`, `AssociatedTool`).
  - `GET /d2l/api/le/1.74/{ou}/grades/values/myGradeValues/` — notas do aluno (`GradeObjectIdentifier`, `GradeObjectName`, `PointsNumerator`, `PointsDenominator`, `DisplayedGrade`, `Comments`, `PrivateComments`, `LastModified`, `ReleasedDate`).

### 4.4. Questionários (Quizzes) — Somente Leitura
- **Web UI:** `/d2l/lms/quizzing/user/quizzes_list.d2l?ou={ou}` e histórico `/d2l/lms/quizzing/user/quiz_submissions.d2l?ou={ou}&qi={quizId}`
- **REST API (`GET`):**
  - `GET /d2l/api/le/1.74/{ou}/quizzes/` — `{ Objects: [...], Next }` (`QuizId`, `Name`, `Description`, `IsActive`, `StartDate`, `EndDate`, `DueDate`, `TimeLimit`, `AttemptsAllowed`).
  - *Nota:* `/quizzes/{qi}/attempts/` retorna `403` para aluno (permissão docente).

### 4.5. Avisos, Discussões, Lista de Classe, Calendário e Progresso
- **Avisos do curso:** `GET /d2l/api/le/1.74/{ou}/news/` (Web: `/d2l/lms/news/main.d2l?ou={ou}`)
- **Discussões (Fóruns):** `GET /d2l/api/le/1.74/{ou}/discussions/forums/` (Web: `/d2l/le/{ou}/discussions/List`)
- **Lista de classe:** `GET /d2l/api/le/1.74/{ou}/classlist/` (Web: `/d2l/lms/classlist/classlist.d2l?ou={ou}`)
- **Calendário do curso:** `GET /d2l/api/le/1.74/{ou}/calendar/events/?startDateTime=...&endDateTime=...` (Web: `/d2l/le/calendar/{ou}`)
- **Progresso do aluno:** Web `/d2l/le/userprogress/{userId}/{ou}/Summary`
- **E-mail interno:** Web `/d2l/lms/email/frame.d2l?ou={ou}`

---

## 5. Regras de Segurança na Integração BFF

1. **Bloqueio de Redirects Externos:** O cliente HTTP do Go para o D2L só pode seguir redirecionamentos dentro do próprio host (`salavirtual.unicep.edu.br`, HTTPS).
2. **Download Seguro de Arquivos:** Anexos e PDFs do AVA passam pelo BFF com ticket opaco de uso único (`POST /api/d2l/ticket` → `GET /api/d2l/file?ticket=...`), `Content-Security-Policy: sandbox; ...`, `X-Content-Type-Options: nosniff` e sanitização de `Content-Type`.
3. **Somente Leitura:** O backend expõe exclusivamente endpoints `GET` para leitura de cursos, conteúdos, atividades, notas, questionários, fóruns, avisos e calendário — sem rotas de upload ou submissão.
