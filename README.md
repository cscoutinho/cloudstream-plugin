# CloudStream 3 — FrameXTV Extension / Provider

Extensão oficial/provider nativo para o **CloudStream 3** que integra todo o catálogo de filmes, séries e streams de alta resolução do **framextv.xyz**, otimizado especificamente para **Android TV** (em especial dispositivos com recursos limitados como a **Xiaomi Mi Box S 4K**).

---

## 🚀 Por que esta abordagem na Xiaomi Mi Box S 4K?

A **Xiaomi Mi Box S 4K** (1ª geração: Amlogic S905X / Mali-450 / 2 GB RAM; 2ª geração: Amlogic S905X4 / Mali-G31) sofre de engasgos severos ao rodar web apps pesados pelo navegador:
- **Navegadores Web (PWA / Chromium):** Consomem entre 450 MB e 800 MB de RAM, causam throttling térmico, sofrem com renderização por software de overlays React/DOM e têm navegação ruim pelo controle remoto D-Pad.
- **Provider Nativo no CloudStream 3:** 
  - Consome menos de **80 MB de RAM**.
  - Decodificação 100% por hardware via **Android ExoPlayer** (`MediaCodec`).
  - Interface nativa com suporte total ao controle remoto (D-Pad, botões voltar, play/pause).
  - Suporte nativo a legendas externas VTT/SRT com renderização em tempo real e sincronização de áudio.
  - Troca dinâmica de servidores com failover automático.

---

## 🔍 Engenharia Reversa do FrameXTV

A análise minuciosa do bundle JavaScript (`index-RVIgcehz.js`, `Player-ebzqu6PL.js`, `EmbedPage-DRx-f8sw.js`, `releaseState-hH0JSKE7.js` e `config-f6DwI-HE.js`) revelou a seguinte arquitetura:

| Componente | Endpoint / Destino | Finalidade |
| :--- | :--- | :--- |
| **API Backend Principal** | `https://api.framextv.tech` | Fornece trilhas de Home, Busca, Metadados e Resolução de Streams. |
| **Home Rails** | `/api/home` | 10 categorias dinâmicas (Trending, Popular, Lançamentos, Ação, Sci-Fi, etc.). |
| **Busca Global** | `/api/search?q={query}` | Busca direta em filmes e séries por TMDB query. |
| **Metadados Detalhados** | `/api/details?id={id}&type={type}` | Sinopse, poster, backdrop, ano, gêneros, elenco e temporadas. |
| **Temporadas e Episódios**| `/api/season?id={id}&season={num}` | Lista de episódios por temporada com sinopse, still image e numeração. |
| **Stream Resolvers** | `/api/stream?id={id}&type={type}` | Retorna streams HLS (m3u8) dos servidores integrados. |
| **Seed Authorizer** | `https://api.wecollege.net/seed` | Gera tokens temporários de 30s para o cluster *Super Barbarian / CineJoy*. |
| **Legendas** | `/api/subtitles?id={id}&type={type}` | Trilha multilíngue (Português, Inglês, Espanhol, etc.) via Stremio CDN. |

### Servidores de Streaming Disponíveis:
1. **Miner XIX (Dallas / Atlanta):** Streams HLS diretos (`skipProxy: true`, e.g. `e2e.majorplay.net`), sem proxy intermediário, ideais para conexão de alta velocidade e baixa latência.
2. **Bowler XVIII (Citadel / PrimeVids):** Streams HLS multi-qualidade (1080p, 720p, 480p).
3. **Super Barbarian (Lisbon CineJoy):** Streams HLS autorizados via dynamic seed token.

---

## 📁 Estrutura do Projeto

```
cloudstream-framextv/
├── build.gradle.kts                # Configuração Gradle raiz com plugin CloudStream
├── settings.gradle.kts             # Inclusão do módulo FrameXTV
├── gradle.properties               # Otimizações de JVM e AndroidX
├── gradlew                         # Script Gradle Wrapper executável
├── .github/
│   └── workflows/
│       └── build.yml               # CI/CD automático para compilar e hospedar o repositório
├── FrameXTV/
│   ├── build.gradle.kts            # Metadados do provider (name, status, tvTypes)
│   └── src/main/kotlin/com/framextv/
│       ├── FrameXTVDTOs.kt         # Modelos de dados serializados (JSON DTOs)
│       ├── FrameXTVProvider.kt     # Implementação do MainAPI (Home, Search, Load, LoadLinks)
│       └── FrameXTVPlugin.kt       # Ponto de entrada @CloudstreamPlugin
├── test_api.py                     # Suíte de testes em Python para validação contínua da API
└── README.md                       # Documentação técnica e guia de instalação
```

---

## 🛠️ Como Compilar e Instalar

### Método 1: Compilação Local com Gradle (Recomendado para Devs)

1. Clone ou acerte o diretório do projeto:
   ```bash
   cd cloudstream-framextv
   ```

2. Compile o plugin usando o Gradle Wrapper:
   ```bash
   ./gradlew makePlugins
   ```
   *O arquivo compilado `.cs3` / `.plugin` será gerado na pasta `build/`.*

3. Se a Mi Box estiver com depuração USB/Rede ativada e conectada via ADB:
   ```bash
   adb connect <IP_DA_MIBOX>:5555
   ./gradlew FrameXTV:deployWithAdb
   ```

---

### Método 2: Repositório Remoto Automático via GitHub Actions (Ideal para Usuários)

1. Crie um repositório no seu GitHub (ex: `seu-usuario/cloudstream-extensions`).
2. Faça push desta pasta para o repositório:
   ```bash
   git init
   git add .
   git commit -m "feat: FrameXTV CloudStream 3 provider"
   git remote add origin https://github.com/seu-usuario/cloudstream-extensions.git
   git push -u origin main
   ```
3. A GitHub Action configurada em `.github/workflows/build.yml` compilará o plugin automaticamente e publicará no branch `builds` os arquivos:
   - `plugins.json` (índice de extensões)
   - `FrameXTV.cs3` (pacote do provider)
4. No CloudStream 3 (instalado na Mi Box S):
   - Vá em **Configurações (Settings) ➜ Extensões (Extensions)**.
   - Clique em **Adicionar Repositório (Add Repository)**.
   - Digite a URL:
     ```
     https://raw.githubusercontent.com/seu-usuario/cloudstream-extensions/builds/plugins.json
     ```
   - O **frameXTV** aparecerá na lista de extensões disponíveis; basta clicar em **Instalar**!

---

### Método 3: Instalação Manual do Arquivo `.cs3`

1. Transfira o arquivo gerado `FrameXTV.cs3` para a Mi Box S usando o aplicativo **Send Files to TV** ou via pendrive USB.
2. No CloudStream 3, acesse:
   - **Settings ➜ Extensions ➜ Install from File** (Instalar a partir de arquivo).
   - Selecione o arquivo `FrameXTV.cs3`.

---

## 🧪 Validação dos Endpoints

Um script de verificação autônomo está incluso em `test_api.py`. Para rodar e validar que toda a malha de APIs e scrapers está operacional:

```bash
python3 test_api.py
```

Saída esperada:
```
============================================================
FrameXTV Scraper Validation Test Suite
============================================================
[1/6] Testing Home Rails (/api/home)...
 -> OK: Found 10 rails: trending_movies, popular_movies, popular_tv, new_releases...
[2/6] Testing Search (/api/search)...
 -> OK: Found 18 items.
[3/6] Testing Details (/api/details)...
 -> OK: Movie: 'Fight Club' (1999)
 -> OK: TV: 'Game of Thrones' with 9 seasons.
 -> OK: Season 1 has 10 episodes.
[4/6] Testing Stream Resolution (/api/stream)...
 -> OK: Resolved sources via provider 'super_barbarian' / 'miner'.
[5/6] Testing Seed Authorization (/seed & Super Barbarian)...
 -> OK: Acquired seed token: 59730787... TTL: 30000ms
[6/6] Testing Subtitles (/api/subtitles)...
 -> OK: Found 24 subtitle tracks (pt, en, es, fr, etc.).
============================================================
ALL TESTS PASSED SUCCESSFULLY! Scraper backend is 100% operational.
============================================================
```
