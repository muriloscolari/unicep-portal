# AGENTS.md — Guia de Desenvolvimento do UNICEP Portal

Este documento orienta agentes e desenvolvedores sobre a arquitetura, convenções, segurança, testes, build e deploy do projeto **UNICEP Portal** (`git.muriloscolari.me/murilo/unicep-portal`, licença **GNU GPLv3**).

---

## 1. Arquitetura Geral

O projeto é um monorepo com dois blocos principais:

1. **Backend (`backend/`) — Go 1.24 + SQLite (`modernc.org/sqlite`)**
   - Atua como BFF (Backend-for-Frontend), proxy autenticado para o portal legado da UNICEP (`cdseducacional.com.br`, ASP/IIS em Windows-1252/ISO-8859-1) e servidor de arquivos estáticos para o frontend Web (`wasmJs`).
   - Armazena credenciais criptografadas em AES-256-GCM, snapshots acadêmicos e notificações em SQLite (`$DATA_DIR/portal.db`).
   - Realiza polling periódico de notas/faltas em segundo plano (`poller/worker.go`).

2. **Frontend (`app/`) — Kotlin + Compose Multiplatform**
   - **`composeApp` (`commonMain`)**: UI compartilhada em 3 abas (`Semestre`, `Curso`, `Central`) com Design System dual (**Apple/Cupertino** + **Material Design 3**).
   - **`androidApp` (`androidMain`)**: App Android nativo (`minSdk 26`, `targetSdk 35`) com R8/ProGuard, armazenamento seguro via Android Keystore (`AndroidSecureStore.kt`), notificações periódicas via `WorkManager` (`GradeSyncWorker.kt`) e auto-update via Forgejo Releases (`AppUpdater.android.kt`).
   - **`wasmJsMain`**: Site Web compilado para WebAssembly (`unicep-portal.js` + `.wasm`) servido diretamente pelo backend Go na raiz `/`.

---

## 2. Mapa de Arquivos Principais

### Backend (`backend/`)
- `cmd/server/main.go`: bootstrap do servidor HTTP, timeouts, graceful shutdown e inicialização do poller.
- `internal/config/config.go`: leitura de variáveis de ambiente (`PORT`, `APP_SECRET`, `REQUIRE_SECRET`, `CORS_ORIGIN`, `ALLOW_DEV_CORS`, `TRUSTED_PROXY_CIDRS`, `DATA_DIR`, `WEB_DIR`, `POLL_INTERVAL_MINUTES`, `NOTIFY_ABSENCES`).
- `internal/crypto/crypto.go`: derivação de chaves HMAC (`aes-gcm:v1`, `jwt-hs256:v1`), AES-256-GCM e assinatura/validação JWT (`HS256`, `iss`, `aud`).
- `internal/storage/sqlite.go`: schema SQLite, permissões `0700`/`0600`, controle de `token_version` (revogação real no logout) e notificações.
- `internal/portal/client.go`, `models.go`, `parser.go`: scraping e parsing HTML do portal da UNICEP (Windows-1252 → UTF-8, `encodeRawQueryISO88591`, bloqueio de redirects externos).
- `internal/httpapi/router.go`: rotas REST `/api/*`, rate limiting (IP + RA com mapa limitado), tickets opacos de uso único (`/api/proxy/ticket`), headers globais (`CSP`, `X-Frame-Options`, `COOP`, `nosniff`) e servidor estático Web.
- `internal/poller/worker.go`: comparação de snapshots e geração de notificações de notas/médias/faltas.

### Frontend (`app/`)
- `composeApp/src/commonMain/kotlin/me/muriloscolari/unicep/`:
  - `App.kt`: navegação principal (3 abas), integração com histórico (`#semestre`, `#semestre/frequencia-limite`, `#curso`, `#central`), diálogo de auto-update Android.
  - `data/api/Models.kt`, `PortalApiClient.kt` e `data/repo/PortalRepository.kt`: cliente HTTP Ktor e estado reativo.
  - `platform/Platform.kt`: contrato `expect object PlatformBridge`.
  - `ui/designsystem/Theme.kt`, `Components.kt`, `Icons.kt`: tokens de cor, tipografia tabular (`tnum`) e componentes Apple vs Material 3.
  - `ui/screens/LoginScreen.kt`, `GradesScreen.kt`, `AcademicScreen.kt`, `MoreScreen.kt`: telas de Login, Semestre (incluindo `AttendanceLimitModal` e `SubjectDetailModal`), Curso e Central.
- `composeApp/src/androidMain/kotlin/.../platform/`: `Platform.android.kt`, `AndroidSecureStore.kt`, `GradeSyncWorker.kt`, `AppUpdater.android.kt`.
- `composeApp/src/wasmJsMain/`: `Main.kt`, `Platform.wasmJs.kt`, `resources/index.html`, `loader.js`, `favicon.svg`, `favicon-32.png`, `favicon.ico`, `apple-touch-icon.png`.
- `androidApp/`: `build.gradle.kts` (`versionCode`, `versionName`, `signingConfigs`), `src/main/AndroidManifest.xml`, `res/xml/network_security_config.xml`, `res/xml/file_paths.xml`, ícones adaptativos.

### Infraestrutura e Documentação
- `Dockerfile` e `docker-compose.yml`: imagem multi-stage (`eclipse-temurin:21-jdk` com `libatomic1` + `golang:1.24-alpine` + `alpine:3.21`), volume nomeado `unicep_data:/app/data`, container `read_only: true`, `cap_drop: ALL`, `no-new-privileges: true` e `watchtower` para auto-update.
- `scripts/publish-image.sh`: build e push local da imagem Docker para `git.muriloscolari.me/murilo/unicep-portal`.
- `deploy/nginx-unicep.conf.example`: modelo Nginx para Cloudflare + mTLS.
- `DOCUMENTACAO_PORTAL.md`: mapeamento detalhado das páginas/endpoints originais da UNICEP.

---

## 3. Regras Invioláveis de Segurança e Código

1. **Segredos e arquivos ignorados:**
   - **Nunca** commitar `.env`, `data/`, `backend/server`, `app/keystore.properties`, `*.jks`, `*.keystore` ou `*.apk`.
   - Em produção (`REQUIRE_SECRET=true`), `APP_SECRET` é obrigatório e deve ter ≥ 32 caracteres.
2. **Autenticação e Proxy:**
   - Autenticação estrita via `Authorization: Bearer` (nunca reintroduzir cookie de sessão `portal_token` por risco de CSRF).
   - Endpoints externos do portal via proxy devem sempre passar pelo fluxo de ticket opaco de uso único (`POST /api/proxy/ticket` -> `GET /api/proxy?ticket=...`) com `Content-Security-Policy: sandbox`.
   - Links externos no Web Wasm devem abrir com `noopener,noreferrer`.
3. **CSP e Headers Web:**
   - `script-src 'self' 'wasm-unsafe-eval'` **sem** `'unsafe-inline'`. Todo JS em `wasmJsMain/resources/index.html` deve ficar em arquivo externo (`loader.js`).
4. **IP Real e Rate Limiting:**
   - `CF-Connecting-IP` só é aceito quando o `RemoteAddr` pertence a `TRUSTED_PROXY_CIDRS` (`127.0.0.1/8,::1/128,172.16.0.0/12` por padrão atrás de Nginx/Docker).
5. **Android Transport Security:**
   - `network_security_config.xml` bloqueia HTTP cleartext na internet; HTTP só é permitido em `127.0.0.1` / `localhost` / `10.0.2.2` para desenvolvimento local.
   - O auto-updater (`AppUpdater.android.kt`) só aceita releases e downloads `.apk` sob o prefixo `https://git.muriloscolari.me/`.
6. **Qualidade e Limites do Portal Legado:**
   - Preservar `encodeRawQueryISO88591` em `backend/internal/portal/client.go` (o IIS da UNICEP retorna HTTP 400 se receber UTF-8 percent-encoded em parâmetros como `boletim_detalhado.asp`).
   - Manter compatibilidade visual tanto no modo **Apple** quanto no modo **Material 3**, além de preservar a navegação por histórico no browser (`Mouse 4/5`) e botão Voltar no Android.

---

## 4. Comandos de Verificação e Build

Sempre execute os checks relevantes antes de commitar ou publicar:

- **Testes do Backend Go (obrigatório rodar com `-race`):**
  ```bash
  cd backend && go test -count=1 -v -race ./...
  ```
- **Build Web Wasm (standalone, sem precisar de Android SDK):**
  ```bash
  cd app && ./gradlew :composeApp:wasmJsBrowserDistribution -PwebOnly=true
  ```
- **Build Android Release (R8) + Web Wasm:**
  ```bash
  cd app && ./gradlew :androidApp:assembleRelease :composeApp:wasmJsBrowserDistribution
  ```
  - APK gerado em: `app/androidApp/build/outputs/apk/release/androidApp-release.apk`
  - **Importante:** mantenha `app/keystore.properties` (não versionado) configurado para que novas versões mantenham a mesma assinatura e o auto-update instale por cima sem erro de assinatura.
- **Validação do Compose:**
  ```bash
  docker compose config -q
  ```

---

## 5. Fluxo de Desenvolvimento, Versionamento e Deploy

1. **Git / Forgejo (`origin`):**
   - Repositório público: `https://git.muriloscolari.me/murilo/unicep-portal.git` (branch `main`).
   - Sempre usar commits convencionais (`feat(...)`, `fix(...)`, `chore(...)`, `sec(...)`) e fazer `git push origin main` após testar.
2. **Deploy do Servidor no Homelab (sem Forgejo Actions):**
   - O build da imagem Docker **é feito localmente neste computador**, não no Forgejo.
   - Quando quiser atualizar o servidor de produção (`https://unicep.muriloscolari.me`):
     ```bash
     ./scripts/publish-image.sh
     ```
   - O script compila a imagem e envia para `git.muriloscolari.me/murilo/unicep-portal:latest`.
   - No homelab (`192.168.0.30` em `~/docker/portal-unicep`), o container `unicep-watchtower` verifica novas imagens a cada 5 minutos (`300s`) e reinicia `unicep-portal` automaticamente mantendo o volume `unicep_data`.
3. **Nova Release do App Android (Auto-Update In-App):**
   - Incremente `versionCode` e `versionName` em `app/androidApp/build.gradle.kts` (ex: `1.2.2`).
   - Compile o APK release (`cd app && ./gradlew :androidApp:assembleRelease`).
   - Crie a tag (`git tag -a v1.2.2 -m "..."`), faça push (`git push origin main v1.2.2`) e publique a release no Forgejo com o APK anexado:
     ```bash
     tea release create --tag v1.2.2 --title "UNICEP Portal v1.2.2" --note "Resumo das novidades" --asset app/androidApp/build/outputs/apk/release/androidApp-release.apk
     ```
   - Assim que a release estiver no ar, todos os celulares com o app instalado detectarão automaticamente a atualização ao abrir o aplicativo.
