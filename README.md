# UNICEP Portal — Painel Mobile, Web e Notificador de Notas

Versão própria e otimizada do Portal do Aluno da UNICEP (`cdseducacional.com.br`), licenciada sob **GNU GPLv3** (`LICENSE`), construída com:
- **Backend em Go + SQLite** empacotado em **Docker** (`git.muriloscolari.me/murilo/unicep-portal:latest`) na porta interna `47832`, com segurança endurecida para exposição atrás de Cloudflare + Nginx/Tunnel.
- **Front-end único em Kotlin + Compose Multiplatform** gerando:
  - **App Android (`.apk`)** compilado com **R8** (`1,8 MB`), login automático persistente via Android Keystore e checagem periódica de novas notas via `WorkManager`.
  - **Site Web (`wasmJs`)** servido diretamente pelo próprio container Go na raiz `/`.
- **Design System dual:** interface estilo **Apple (Cupertino)** otimizada com números tabulares (`tnum`) e **fallback automático para Material Design 3** em aparelhos com Android < 12, pouca RAM ou modo economia de energia (ou via alternador manual em *Central → Aparência*).

---

## Rodando no Homelab com Atualização Automática (Forgejo Registry + Watchtower)

1. Copie [`docker-compose.yml`](docker-compose.yml) e [`.env.example`](.env.example) para uma pasta no seu servidor (ex: `~/unicep-portal`) e crie o `.env`:
   ```bash
   cp .env.example .env
   openssl rand -hex 32   # coloque o valor gerado em APP_SECRET
   ```
   Certifique-se de que o `.env` tenha `APP_SECRET` com 32+ caracteres (obrigatório em produção).

2. *(Se o pacote no Forgejo for privado)* Faça login no registry do Forgejo no homelab e descomente a linha do `/config.json:ro` no `watchtower` em [`docker-compose.yml`](docker-compose.yml):
   ```bash
   docker login git.muriloscolari.me -u murilo
   ```
   *(Se o pacote estiver marcado como **público** no Forgejo, não precisa de `docker login`.)*

3. Baixe e suba o app + Watchtower:
   ```bash
   docker compose pull
   docker compose up -d
   ```
   A partir daí, o serviço `watchtower` verifica o Forgejo Registry a cada 5 minutos (`300s`), baixa a imagem nova assim que você atualizar o código e reinicia o container `unicep-portal` preservando `./data/portal.db`.

4. Exponha `http://127.0.0.1:47832` via **Cloudflare Tunnel (`cloudflared`)** *(recomendado, zero portas abertas no roteador)* ou via Nginx com [deploy/nginx-unicep.conf.example](deploy/nginx-unicep.conf.example) + Cloudflare **Full (Strict)** + **Authenticated Origin Pulls (mTLS)**.

---

## Publicando a Imagem Manualmente para o Forgejo

```bash
docker login git.muriloscolari.me -u murilo
docker build -t git.muriloscolari.me/murilo/unicep-portal:1.2.0 -t git.muriloscolari.me/murilo/unicep-portal:latest .
docker push git.muriloscolari.me/murilo/unicep-portal:1.2.0
docker push git.muriloscolari.me/murilo/unicep-portal:latest
```

O workflow em [`.forgejo/workflows/docker.yml`](.forgejo/workflows/docker.yml) também automatiza esse build/push no Forgejo Actions sempre que houver push em `main` ou nova tag `v*` (requer um Forgejo Runner ativo na sua instância).

---

## Compilando Localmente (Android R8 / Go / Web)

- **App Android R8:**
  ```bash
  cd app && ./gradlew :androidApp:assembleRelease
  ```
- **Site Web/Wasm (sem Android SDK):**
  ```bash
  cd app && ./gradlew :composeApp:wasmJsBrowserDistribution -PwebOnly=true
  ```
- **Testes Go com race detector:**
  ```bash
  cd backend && go test -race ./...
  ```
- **Documentação técnica do portal original:** veja [DOCUMENTACAO_PORTAL.md](DOCUMENTACAO_PORTAL.md).

---

## Licença

Este projeto é distribuído sob os termos da licença **GNU General Public License v3.0 (GPL-3.0-or-later)**. Veja o arquivo [LICENSE](LICENSE).
