# UNICEP Portal — Painel Mobile, Web e Notificador de Notas

Versão própria e otimizada do Portal do Aluno da UNICEP (`cdseducacional.com.br`), construída com:
- **Backend em Go + SQLite** empacotado em **Docker** na porta rara `47832` (atrás de Nginx HTTPS em `unicep.muriloscolari.me`).
- **Front-end único em Kotlin + Compose Multiplatform** gerando:
  - **App Android (`.apk`)** compilado com **R8** (`1,8 MB`), login automático persistente via Android Keystore e checagem periódica de novas notas via `WorkManager`.
  - **Site Web (`wasmJs`)** servido diretamente pelo próprio container Go na raiz `/`.
- **Design System dual:** interface estilo **Apple (Cupertino)** otimizada com números tabulares (`tnum`) e **fallback automático para Material Design 3** em aparelhos com Android < 12, pouca RAM ou modo economia de energia (ou via alternador manual em *Mais → Ajustes*).

---

## Subindo o Backend + Site no Homelab (Docker + Nginx)

1. Copie e configure o `.env`:
   ```bash
   cp .env.example .env
   openssl rand -hex 32   # coloque o valor gerado em APP_SECRET
   ```
2. Suba o container (porta interna `47832` vinculada a `127.0.0.1:47832`):
   ```bash
   docker compose up -d --build
   ```
3. Configure o Nginx usando [deploy/nginx-unicep.conf.example](deploy/nginx-unicep.conf.example) para apontar `https://unicep.muriloscolari.me` (`80`/`443`) para `http://127.0.0.1:47832`.

---

## Compilando o App Android (R8)

O Gradle da máquina já está configurado em `~/.gradle/gradle.properties` e `app/gradle.properties`. Para gerar o APK minificado com R8:

```bash
cd app
./gradlew :androidApp:assembleRelease
```

O APK final estará em `app/androidApp/build/outputs/apk/release/androidApp-release.apk`.

---

## Desenvolvimento e Testes Locais

- **Testes do backend Go:**
  ```bash
  cd backend && go test ./...
  ```
- **Compilar apenas o site Web/Wasm (sem precisar do Android SDK):**
  ```bash
  cd app && ./gradlew :composeApp:wasmJsBrowserDistribution -PwebOnly=true
  ```
- **Documentação técnica do portal original:** veja [DOCUMENTACAO_PORTAL.md](DOCUMENTACAO_PORTAL.md).
