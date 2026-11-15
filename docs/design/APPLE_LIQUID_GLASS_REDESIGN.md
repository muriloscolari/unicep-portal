# Redesign do Modo Apple — "Liquid Glass" (UNICEP Portal)

> **Para:** agente de implementação (Gemini)
> **Escopo:** apenas o modo de design **Apple** (`DesignStyle.APPLE`). O modo **Material 3** deve continuar idêntico.
> **Liberdade:** você pode mudar layout, hierarquia, componentes e fluxos **desde que a usabilidade melhore**. Na dúvida, prefira o comportamento nativo do iOS 26 / macOS Tahoe.
> **Status deste documento:** local, não versionado. **Não faça commit dele.**

---

## 0. TL;DR (leia primeiro)

O modo Apple atual parece um "site escuro com cards de borda fina" e não um app da Apple. As causas principais, em ordem de impacto:

1. **Fonte Roboto padrão.** Não existe `fontFamily`. → Embutir **Inter** (OFL).
2. **Zero translucidez.** Barra superior, tab bar e sidebar são cores sólidas. → **Liquid Glass** com a biblioteca **Haze** (backdrop blur real).
3. **O conteúdo não passa por baixo das barras.** O `Scaffold` aplica `innerPadding`, então não há nada para o vidro desfocar. → Layout edge‑to‑edge, com o conteúdo rolando *sob* as barras.
4. **Borda de 0,5dp em todo card.** → Células agrupadas sem borda (estilo *inset grouped*).
5. **Top bar genérica** ("UNICEP Portal" + chip "iOS") e títulos duplicados. → **Large Title** que colapsa para título inline com vidro.
6. **Modais centralizados tipo web.** → **Sheets** de vidro de baixo para cima (grabber + detents + arrastar para fechar).
7. **Nomes de disciplina em CAIXA ALTA**, timestamps ISO crus e chips "P1 —" vazios. → *Title Case*, datas relativas e mostrar só o que tem informação.

Execute nas fases da [seção 9](#9-plano-de-execução-em-fases). Cada fase precisa compilar e ficar visualmente consistente sozinha.

---

## 1. Contexto técnico

| Item | Valor |
|------|-------|
| UI | Kotlin + Compose Multiplatform **1.12.2**, Material3 `1.12.0-alpha03`, Kotlin **2.4.21** |
| Targets | `androidMain` (minSdk 26, compileSdk 37) e `wasmJsMain` (servido pelo backend Go em `/`) |
| Build Web | `cd app && ./gradlew :composeApp:wasmJsBrowserDistribution -PwebOnly=true` |
| Build Android | `cd app && ./gradlew :androidApp:assembleRelease :composeApp:wasmJsBrowserDistribution` |
| Catálogo de versões | `app/gradle/libs.versions.toml` |
| Módulo de UI | `app/composeApp/build.gradle.kts` |

### 1.1 Arquivos relevantes

```
app/composeApp/src/commonMain/kotlin/me/muriloscolari/unicep/
├── App.kt                          # navegação, top bar, AppleFloatingTabDock, DesktopSideNav, diálogo de update
├── platform/Platform.kt            # DesignStyle, DeviceCapabilities.supportsAppleEffects
└── ui/
    ├── designsystem/
    │   ├── Theme.kt                # PortalColors, AppleTypography, PortalTheme
    │   ├── Components.kt           # PortalCard, SegmentedControl, Badge, Buttons, TextField, ListRow
    │   └── Icons.kt                # PortalIcons (vetores stroke próprios)
    └── screens/
        ├── LoginScreen.kt          # SplashLoadingScreen + LoginScreen
        ├── GradesScreen.kt         # SemesterScreen, UnifiedSubjectCard, PortalOverlayModal,
        │                           # AttendanceLimitModal, SubjectDetailModal
        ├── AcademicScreen.kt       # Curso (Histórico / Grade / Horário)
        └── MoreScreen.kt           # CentralScreen
```

Capturas atuais para referência visual: `docs/screenshots/semestre-apple.png`, `central-apple.png`, `frequencia-limite.png`.

### 1.2 Regras invioláveis (do `AGENTS.md`)

- **Material 3 intacto.** Toda mudança visual nova fica atrás de `LocalDesignStyle.current == DesignStyle.APPLE`. Se precisar mudar a estrutura de uma tela para os dois estilos, o Material tem de continuar com a mesma aparência e o mesmo comportamento.
- **Navegação por histórico.** Preserve as rotas (`semestre`, `semestre/subject/<code>`, `semestre/frequencia-limite`, `curso/<n>`, `central`), o Mouse 4/5 no navegador e o botão Voltar do Android (`PlatformBridge.setAndroidBackHandler`). Sheets novos precisam fechar com Voltar e com `popstate`, como os modais atuais.
- **CSP Web:** `script-src 'self' 'wasm-unsafe-eval'` sem `unsafe-inline`; `font-src 'self' data:`; `connect-src 'self' data: blob:`. Nada de CDN de fontes nem JS inline no `index.html`. Fontes entram via `composeResources` (mesma origem).
- **Fallback de efeitos.** `PlatformBridge.capabilities().supportsAppleEffects` é `false` no Android < 12, em aparelhos com pouca RAM e no modo economia de energia. Nesses casos o vidro **tem de** cair para um material sólido translúcido sem blur (ver §4.4).
- Links externos continuam via `repo.openPortalLink` (fluxo de ticket). Não mude a camada de dados nem a segurança.
- Não versione `.env`, `data/`, `*.apk`, `keystore.properties` etc.

---

## 2. Princípios do Liquid Glass (o que estamos imitando)

1. **Duas camadas.** O **conteúdo** fica embaixo, e os **controles e a navegação** flutuam por cima em vidro (nav bar, tab bar, sheets, botões flutuantes, toasts, menus). Cards de conteúdo **não** são de vidro: são superfícies sólidas agrupadas. Vidro em tudo vira poluição visual. É o erro nº 1 a evitar.
2. **O material de vidro** é a soma de:
   - backdrop blur (o que está atrás fica desfocado);
   - tint translúcido adaptado ao modo claro/escuro;
   - **borda especular**: um traço interno em gradiente, mais claro no topo e à esquerda, que simula luz;
   - brilho interno sutil no topo (*inner glow*);
   - sombra suave e difusa;
   - opcional: **lente/refração** nas bordas.
3. **Formas concêntricas e em cápsula.** Os controles são cápsulas ou círculos, e o raio interno é o raio externo menos o padding.
4. **Fluidez.** Elementos de vidro fazem *morph*: a "lente" da tab bar desliza com spring, e a tab bar encolhe ao rolar para baixo e volta ao rolar para cima.
5. **Scroll edge effect.** Quando o conteúdo passa sob a barra superior, ele desfoca ou esmaece progressivamente, em vez de existir uma linha divisória dura.
6. **Hierarquia por tipografia e espaço**, não por bordas e caixas.

---

## 3. Fundação (tokens)

### 3.1 Tipografia: Inter

**Por quê:** a SF Pro não pode ser redistribuída pela licença da Apple. Inter (SIL OFL 1.1, compatível com GPLv3) é a alternativa livre mais próxima. Use **Inter Display** (incluída no Inter 4.x) para tamanhos ≥ 20sp e **Inter** para texto.

**Setup:**

1. Baixe o Inter 4.x (https://github.com/rsms/inter/releases) e copie **apenas** os pesos usados, em `.ttf`, com nomes em minúsculo e underscore:
   ```
   app/composeApp/src/commonMain/composeResources/font/
   ├── inter_regular.ttf          (400)
   ├── inter_medium.ttf           (500)
   ├── inter_semibold.ttf         (600)
   ├── inter_bold.ttf             (700)
   ├── inter_display_semibold.ttf (600)
   └── inter_display_bold.ttf     (700)
   ```
   Inclua `OFL.txt` em `composeResources/font/` ou em `app/third_party/inter/`.
2. Adicione Compose Resources ao `commonMain`. Hoje ele **não** está configurado:
   ```toml
   # libs.versions.toml
   compose-components-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "composeMultiplatform" }
   ```
   ```kotlin
   // composeApp/build.gradle.kts → commonMain.dependencies
   implementation(libs.compose.components.resources)
   ```
   O acessor gerado é `Res.font.inter_regular` (pacote `unicep_portal.composeapp.generated.resources` ou similar; confira o nome gerado após o primeiro build).
3. `org.jetbrains.compose.resources.Font(...)` é `@Composable`, então a `Typography` Apple tem de ser criada **dentro** de `PortalTheme`, com `remember`.

**Escala (baseada no Dynamic Type "Large" do iOS, com tracking ajustado para Inter):**

| Token | Tamanho/Altura | Peso | Família | letterSpacing | Uso |
|-------|----------------|------|---------|---------------|-----|
| `largeTitle` | 34/41 | Bold | Display | -0.8sp | Título grande da tela |
| `title1` | 28/34 | Bold | Display | -0.6sp | Hero numbers, Login |
| `title2` | 22/28 | Bold | Display | -0.4sp | Média em destaque |
| `title3` | 20/25 | SemiBold | Display | -0.3sp | Título de sheet |
| `headline` | 17/22 | SemiBold | Text | -0.3sp | Nome da disciplina, título de célula |
| `body` | 17/22 | Regular | Text | -0.3sp | Texto corrido, linhas de lista |
| `callout` | 16/21 | Regular | Text | -0.2sp | Texto secundário de destaque |
| `subheadline` | 15/20 | Regular | Text | -0.15sp | Subtítulo de linha |
| `footnote` | 13/18 | Regular | Text | -0.05sp | Metadados (código, turma) |
| `caption1` | 12/16 | Regular | Text | 0sp | Labels de métricas |
| `caption2` | 11/13 | Medium | Text | 0.05sp | Labels de tab bar, chips |
| `sectionHeader` | 13/18 | Regular, **sem caps** | Text | -0.05sp | Cabeçalho de seção agrupada (iOS 16+ não usa CAIXA ALTA) |

- Números (notas, faltas, médias): sempre `fontFeatureSettings = "tnum"`. Já existe `tabularNumberStyle()`; mantenha.
- Exponha os tokens num `AppleType` (`staticCompositionLocalOf`) **e** mapeie para `MaterialTheme.typography` (`headlineLarge = largeTitle`, `titleMedium = headline`, `bodyMedium = body`, `labelMedium = caption1` …) para o código existente continuar funcionando.
- **Elimine os `fontSize = xx.sp` soltos** nas telas do modo Apple e use os tokens. Hoje há dezenas de tamanhos arbitrários (10.5, 11.5, 12.5, 13.5…), e isso é parte do motivo de o app parecer "desalinhado".
- Não fixe alturas em containers com texto; use `heightIn(min = …)` para respeitar a escala de fonte do sistema.

### 3.2 Cores (system colors do iOS)

Substitua/expanda `PortalColors` para o modo Apple. Mantenha os nomes atuais (`background`, `surface`, `accent`, `textPrimary`…) como aliases para não quebrar o Material.

| Token | Light | Dark |
|-------|-------|------|
| `groupedBackground` (= `background`) | `#F2F2F7` | `#000000` |
| `secondaryGroupedBackground` (= `surface`, células) | `#FFFFFF` | `#1C1C1E` |
| `tertiaryGroupedBackground` (células aninhadas, chips) | `#F2F2F7` | `#2C2C2E` |
| `label` (= `textPrimary`) | `#000000` | `#FFFFFF` |
| `secondaryLabel` (= `textSecondary`) | `#3C3C43` @ 60% | `#EBEBF5` @ 60% |
| `tertiaryLabel` | `#3C3C43` @ 30% | `#EBEBF5` @ 30% |
| `quaternaryLabel` | `#3C3C43` @ 18% | `#EBEBF5` @ 16% |
| `separator` (= `divider`) | `#3C3C43` @ 29% | `#545458` @ 60% |
| `opaqueSeparator` | `#C6C6C8` | `#38383A` |
| `systemFill` (press highlight, trilhas) | `#787880` @ 20% | `#787880` @ 36% |
| `secondaryFill` (segmented track) | `#787880` @ 16% | `#787880` @ 32% |
| `tertiaryFill` (search field) | `#767680` @ 12% | `#767680` @ 24% |
| `blue` (= `accent`) | `#007AFF` | `#0A84FF` |
| `green` | `#34C759` | `#30D158` |
| `orange` | `#FF9500` | `#FF9F0A` |
| `red` | `#FF3B30` | `#FF453A` |
| `yellow` | `#FFCC00` | `#FFD60A` |
| `teal` | `#30B0C7` | `#40C8E0` |
| `indigo` | `#5856D6` | `#5E5CE6` |
| `purple` | `#AF52DE` | `#BF5AF2` |
| `pink` | `#FF2D55` | `#FF375F` |
| `gray` | `#8E8E93` | `#8E8E93` |

**Novos tokens de vidro:**

| Token | Light | Dark |
|-------|-------|------|
| `glassTint` | `#FFFFFF` @ 55% | `#1C1C1E` @ 50% |
| `glassTintFallback` (sem blur) | `#F9F9F9` @ 94% | `#1C1C1E` @ 94% |
| `glassStrokeTop` (especular) | `#FFFFFF` @ 70% | `#FFFFFF` @ 28% |
| `glassStrokeBottom` | `#FFFFFF` @ 15% | `#FFFFFF` @ 6% |
| `glassShadow` | `#000000` @ 12% | `#000000` @ 45% |
| `glassLens` (bolha selecionada na tab bar) | `#000000` @ 6% | `#FFFFFF` @ 12% |
| `scrim` (atrás de sheets) | `#000000` @ 20% | `#000000` @ 45% |

- **Atenção:** o `accentSoft` atual (azul @ 11%) é usado como fundo de seleção em quase tudo, e isso dá cara de "web admin". No modo Apple, use‑o só em badges informativos. Seleção vira lente de vidro, thumb branco/cinza ou texto/ícone na cor de destaque.
- O `index.html` usa `background: #F2F2F7` e `#000000`, que continuam corretos.

### 3.3 Forma, espaçamento e elevação

- **Grid:** múltiplos de 4. Margem horizontal das telas: **16dp** no mobile e **20dp** a partir de 640dp de largura (com conteúdo limitado a `widthIn(max = 720.dp)` e centralizado no desktop, porque hoje os cards esticam até 1000+px).
- **Espaço entre seções:** 28–32dp. Entre cards: 10–12dp. Do cabeçalho de seção até o card: 6dp.
- **Raios:**
  - Células/cards agrupados: **20dp** (nas telas grandes, 22dp).
  - Elementos internos (chips de nota, campos): 12dp. Respeite a concentricidade (raio interno = raio externo − padding).
  - Botões e controles: cápsula (`CircleShape`/`RoundedCornerShape(50)`).
  - Sheets: **34dp** no topo (e nos 4 cantos quando o sheet flutua com inset).
  - Ícone do app: 22,37% do lado (squircle iOS). Ex.: 80dp → 18dp.
- **Elevação:** cards de conteúdo **sem sombra e sem borda** no dark. No light, sombra opcional muito sutil (`0 1dp 2dp` @ 4%). Só elementos de vidro têm sombra perceptível: `Modifier.shadow(16.dp, shape, ambientColor = glassShadow, spotColor = glassShadow)`.

### 3.4 Movimento

```kotlin
object AppleMotion {
    val standard = spring<Float>(dampingRatio = 0.86f, stiffness = 380f)  // transições gerais
    val snappy   = spring<Float>(dampingRatio = 0.72f, stiffness = 520f)  // lente da tab bar, thumb do segmented
    val sheet    = spring<Float>(dampingRatio = 0.90f, stiffness = 300f)  // abrir/fechar sheet
    val press    = spring<Float>(dampingRatio = 0.60f, stiffness = 900f)  // scale de toque
    const val PRESS_SCALE = 0.97f
}
```

- Use `spring` também para `Dp`/`Offset` (`animateDpAsState`, `animateOffsetAsState`), com os mesmos parâmetros.
- Troca de aba: crossfade de 180ms + leve `scale` de 0,98 → 1. **Nada** de slide lateral de tela inteira entre abas (o iOS não faz isso).
- Android: `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.TextHandleMove)` ao trocar aba ou segmento (sem efeito no Web, o que é aceitável).

---

## 4. Infraestrutura de vidro: Haze + `GlassSurface`

### 4.1 Dependência

Use **Haze** (`dev.chrisbanes.haze`), que suporta Compose Multiplatform incluindo **wasmJs** e Android.

```toml
# libs.versions.toml — ⚠️ escolha a versão estável MAIS RECENTE compatível com CMP 1.12.x
haze = "<verificar>"
[libraries]
haze = { module = "dev.chrisbanes.haze:haze", version.ref = "haze" }
haze-materials = { module = "dev.chrisbanes.haze:haze-materials", version.ref = "haze" }
```

> **Tarefa obrigatória:** antes de codar, confira o changelog/README do Haze para a versão escolhida. A API mudou entre versões. Nas versões 1.x a API é `Modifier.hazeSource(state)` + `Modifier.hazeEffect(state, style)` + `rememberHazeState()`; versões antigas usavam `haze()` / `hazeChild()`. Rode o build Web **e** o Android logo depois de adicionar a dependência.

No Android, o blur real depende de `RenderEffect` (API 31+). Abaixo disso o Haze usa scrim, mas nós já desligamos via `supportsAppleEffects` (§4.4).

### 4.2 Estado global

Crie `ui/designsystem/Glass.kt`:

```kotlin
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }
val LocalGlassEnabled = staticCompositionLocalOf { false }
```

Em `App.kt`, no modo Apple:

```kotlin
val hazeState = rememberHazeState()
val glassEnabled = style == DesignStyle.APPLE && PlatformBridge.capabilities().supportsAppleEffects
CompositionLocalProvider(LocalHazeState provides hazeState, LocalGlassEnabled provides glassEnabled) {
    Box(Modifier.fillMaxSize()) {
        // CAMADA 1: conteúdo (fonte do blur), desenhado edge-to-edge
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) { bodyContent() }
        // CAMADA 2: controles de vidro por cima
        AppleNavBar(…)          // topo
        AppleTabBar(…)          // base (mobile)
        AppleToastHost(…)       // banners
        AppleSheetHost(…)       // sheets (ver §5.9)
    }
}
```

> **Crítico:** para o vidro funcionar, o conteúdo **precisa rolar por baixo** das barras. No modo Apple, remova o `Scaffold(topBar=…)` com `innerPadding` e passe `contentPadding` para cada `LazyColumn`:
> `top = statusBarInset + navBarHeight (52dp) + 8dp`, `bottom = navigationBarInset + tabBarHeight (~64dp) + 24dp`.
> Crie um `LocalAppleContentInsets` (`PaddingValues`) e faça as telas o lerem. O Material continua usando `Scaffold` normalmente.

### 4.3 `GlassSurface`

```kotlin
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    tint: Color = LocalPortalColors.current.glassTint,
    shadowElevation: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val c = LocalPortalColors.current
    val haze = LocalHazeState.current
    val enabled = LocalGlassEnabled.current && haze != null

    Box(
        modifier
            .shadow(shadowElevation, shape, clip = false, ambientColor = c.glassShadow, spotColor = c.glassShadow)
            .clip(shape)
            .then(
                if (enabled) Modifier.hazeEffect(haze!!) {
                    blurRadius = 24.dp
                    backgroundColor = c.background        // cor base para áreas sem conteúdo
                    tints = listOf(HazeTint(tint))
                    noiseFactor = 0f                       // ruído é caro e pouco visível em tela pequena
                    // inputScale = HazeInputScale.Auto     // downsampling se a versão suportar (performance)
                } else Modifier.background(c.glassTintFallback)
            )
            // brilho interno no topo (luz)
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (c.isDark) 0.06f else 0.18f),
                    0.45f to Color.Transparent
                )
            )
            // borda especular
            .border(
                width = 0.75.dp,
                brush = Brush.linearGradient(
                    colors = listOf(c.glassStrokeTop, c.glassStrokeBottom, c.glassStrokeTop.copy(alpha = c.glassStrokeTop.alpha * 0.5f)),
                    start = Offset.Zero,
                    end = Offset.Infinite
                ),
                shape = shape
            ),
        content = content
    )
}
```

Variações a expor:
- `GlassSurface` (regular): barras e sheets.
- `GlassButton` (circular 44dp ou cápsula, ícone/label): botões da nav bar e ações flutuantes. Ao pressionar, `scale 0.94` + tint um pouco mais claro.
- `GlassProminentButton`: vidro tingido com `accent` @ 85% e texto branco, para CTA ("Entrar").

**Opcional (fase 7, atrás de flag):** lente/refração nas bordas via `RuntimeShader` (AGSL no Android 13+ via `RenderEffect.createRuntimeShaderEffect`; no Wasm/Skiko via `RuntimeEffect`/`ImageFilter` do Skia). Só implemente se as fases 1–6 estiverem perfeitas. O ganho é pequeno perto do custo.

### 4.4 Fallback e performance

- `LocalGlassEnabled == false` → fundo `glassTintFallback`, mantendo borda especular e sombra. Fica bonito mesmo sem blur.
- No máximo **3 `hazeEffect` ativos ao mesmo tempo** (ex.: nav bar + tab bar + sheet). Os botões de vidro **dentro** de uma barra de vidro não aplicam blur de novo: use só tint + borda.
- Teste o scroll em Android de entrada e no Chrome. Se cair abaixo de ~50fps, reduza `blurRadius` para 16dp e/ou ative o downsampling.

---

## 5. Especificação por componente

> Convenção: **Antes** = como está hoje. **Depois** = o que implementar no modo Apple. Os componentes ficam em `Components.kt` (ou em arquivos novos dentro de `designsystem/`).

### 5.1 `PortalCard` → célula agrupada

- **Antes:** `Surface` com borda de 0,5dp `divider`, raio 20dp e scale 0,98 ao pressionar.
- **Depois:**
  - Fundo `secondaryGroupedBackground`, **sem borda**, raio 20dp, padding 16dp.
  - Pressionado (com `onClick`): overlay `systemFill` (fade 120ms) **+** `scale 0.97` com `AppleMotion.press`. O iOS faz highlight; o scale fica restrito a cards grandes e tocáveis.
  - Parâmetro novo `style: CardStyle = Grouped | Plain | Tinted(color)`. `Tinted` gera fundo `color @ 12%`, para alertas como "frequência no limite".

### 5.2 `PortalListRow` → linha estilo Ajustes

- Altura mínima **44dp** (52dp com subtítulo), padding horizontal 16dp.
- Slot `leading` opcional: **ícone em squircle colorido** de 30dp, raio 7dp, fundo em cor do sistema e glifo branco de 18dp (como no app Ajustes). É a mudança mais "Apple" possível na tela Central.
- Título `body`, subtítulo `footnote` `secondaryLabel`.
- `trailing`: valor em `secondaryLabel`, badge ou chevron (`tertiaryLabel`, 13dp, peso semibold).
- Pressionado: fundo `systemFill`, sem ripple.
- Separador: `separator` 0,5dp com **inset à esquerda alinhado ao texto** (16dp sem ícone, 16 + 30 + 12 = 58dp com ícone). Parâmetro `showDivider`.
- Variante `destructive` com título em `red`, centralizado e sem chevron (para "Sair").

### 5.3 `PortalSectionHeader`

- **Sem `uppercase()`**: estilo iOS 16+, `footnote`/`sectionHeader`, `secondaryLabel`, padding `start = 16dp` (alinha com o texto da célula), `bottom = 6dp`.
- Variante `prominent` (`title3` bold, `label`) para cabeçalhos de seção na tela Semestre ("Disciplinas").
- Suporte a `footer` (texto explicativo `footnote` abaixo do card), como em Ajustes. Use‑o para o texto da Aparência.

### 5.4 `PortalSegmentedControl`

- Track: `secondaryFill`, cápsula ou raio 9dp, padding 2dp, altura 32dp.
- Thumb: **um único** elemento que **desliza** entre posições (`animateDpAsState` com `AppleMotion.snappy`), fundo `#FFFFFF` (light) / `#636366` (dark), raio 7dp, sombra `0 3dp 8dp @12%` + `0 3dp 1dp @4%`. **Sem borda.**
- Label: `subheadline`, selecionado em SemiBold e `label`, os demais em Medium e `label` (não `secondaryLabel`; no iOS o contraste é dado pelo thumb).
- Divisórias verticais finas (`separator`) entre segmentos não selecionados, que somem ao lado do thumb.
- Pressionar um segmento: o thumb faz `scale 0.95`.
- Mais de 4 opções ou rótulos longos: troque por um **menu pull‑down de vidro** (ver §5.10).

### 5.5 Botões

| Componente | Visual Apple |
|-----------|--------------|
| `PortalPrimaryButton` | Cápsula, altura 50dp, `accent`, texto `headline` branco. Pressionado: alpha 0,85 + scale 0,97. Loading: `AppleActivityIndicator` branco no lugar do texto (não ao lado). Variante `glassProminent` quando estiver sobre fundo colorido (Login). |
| `PortalSecondaryButton` | Cápsula, altura 44dp, fundo `tint @ 15%`, texto `tint` SemiBold. |
| `PortalTextButton` (novo) | Só texto `accent` `body`, para ações inline ("Editar", "Ver todas"). |
| `GlassButton` (novo) | Círculo 44dp ou cápsula, em vidro, ícone `label`/`accent`. Usado na nav bar. |

### 5.6 `PortalTextField`

- Sem label acima. Use **placeholder** dentro do campo, como no iOS.
- Em formulários (Login), agrupe os campos numa única célula `secondaryGroupedBackground` com separador inset entre eles: "RA" | "Senha".
- Campo de busca (`PortalSearchField`, novo): altura 36dp, `tertiaryFill`, raio 10dp, ícone de lupa `secondaryLabel` 16dp, placeholder "Buscar", botão "×" circular (`tertiaryLabel`) quando houver texto.
- Foco: **sem borda azul grossa**. O cursor `accent` basta. Para indicar foco, no máximo um anel `accent @ 35%` de 3dp por fora.
- Senha: botão de olho (mostrar/ocultar) no trailing.

### 5.7 `PortalBadge`, progresso e indicadores

- Badge: cápsula, `caption2` SemiBold, fundo `color @ 15%`, texto `color`. Use com parcimônia: no máximo um por célula.
- **Status com ponto colorido** (`●` de 8dp + texto `footnote`) em vez de badge, para estados de lista como Aprovado, Cursando e Reprovado.
- Barra de progresso: cápsula de 4dp, trilha `systemFill`, preenchimento animado (`animateFloatAsState`, 600ms ease‑out) na primeira composição.
- `ProgressRing` (novo, `Canvas`): anel com `StrokeCap.Round`, espessura 6dp, trilha `color @ 18%`. Usado para média e progresso do curso.
- `AppleActivityIndicator` (novo, `Canvas`): 8 raios em rotação discreta (estilo `UIActivityIndicatorView`). Substitui `CircularProgressIndicator` e `LinearProgressIndicator` no modo Apple (topo, splash, botões).

### 5.8 Navegação

#### 5.8.1 Nav bar + Large Title (mobile e desktop)

- **Remover** "UNICEP Portal" + chip "iOS" da barra.
- Cada tela começa com o **Large Title** (`largeTitle`) como primeiro item da lista: "Semestre", "Curso", "Central". Na tela Semestre a saudação vai logo abaixo (ver §6.2).
- Ao rolar além do título (~40dp), aparece uma **barra inline** fixa no topo:
  - altura 52dp + status bar;
  - título `headline` centralizado, com fade‑in de 0→1 conforme o progresso de scroll;
  - fundo: **scroll edge effect**, ou seja, `GlassSurface` retangular (sem cantos, sem sombra, sem borda lateral) cuja opacidade vai de 0 → 1 com o scroll. No topo da lista a barra é invisível e só os botões aparecem.
- Ações à direita como **`GlassButton`s circulares de 44dp** (sempre visíveis, flutuando): sino (com ponto vermelho de não lidas) e, no desktop, atualizar.
- **Atualizar:** no mobile, troque o botão de refresh por **pull‑to‑refresh** (`PullToRefreshBox` do Material3, com indicador customizado `AppleActivityIndicator` no modo Apple). É mais natural e libera espaço. Mantenha o atalho no desktop/web.
- Implementação: cada tela recebe/expõe um `LazyListState`. O `App.kt` lê `firstVisibleItemIndex`/`firstVisibleItemScrollOffset` com `derivedStateOf` para calcular `collapseProgress`.

#### 5.8.2 Banner de status/erro → toast de vidro

- **Antes:** faixa colorida de largura total abaixo da top bar, com "OK".
- **Depois:** cápsula `GlassSurface` flutuante abaixo da nav bar, centralizada, com `maxWidth` de 420dp, ícone (✓ info / ⚠ erro em `red`) + texto `subheadline`. Entra com slide de cima + fade e some sozinho após 4s (erros ficam até o toque). Arrastar para cima dispensa. Continua chamando `repo.clearBanner()`.

#### 5.8.3 Tab bar Liquid Glass (mobile, largura < 640dp)

Substitui `AppleFloatingTabDock`:

- Cápsula `GlassSurface` flutuante, margem lateral de 20dp (ou largura intrínseca centralizada), 12dp acima do `navigationBars` inset e **altura 62dp**.
- Itens com **ícone em cima (24dp) e label embaixo (`caption2`)**, empilhados verticalmente. Hoje estão lado a lado, o que não é o padrão iOS.
- Item selecionado: ícone na **variante preenchida** (filled) e cor `accent`, label `accent`. Os demais ficam em `label` (não cinza claro).
- **Lente:** cápsula `glassLens` atrás do item selecionado, que **desliza** com `AppleMotion.snappy` ao trocar de aba e estica levemente (scaleX 1,15) durante o movimento, voltando ao normal no fim (efeito "gota").
- **Minimizar ao rolar:** rolando para baixo mais de 24dp, a tab bar encolhe (scale 0,86 e altura menor, mostrando só os ícones). Rolando para cima, volta. Use `NestedScrollConnection` no `App.kt`.
- Toque na aba já ativa → rola a lista até o topo (comportamento iOS).
- Acessibilidade: `Modifier.semantics { role = Role.Tab; selected = … }`.

#### 5.8.4 Sidebar desktop (largura ≥ 640dp), estilo macOS Tahoe

- **Antes:** coluna opaca de 220dp com divisor vertical.
- **Depois:** painel `GlassSurface` **flutuante** com inset de 8dp em todos os lados, raio 20dp e largura 240dp. O conteúdo rola **por trás** dele (o `hazeSource` cobre a janela toda, e o conteúdo tem `start padding` = largura da sidebar + 16dp).
- Topo da sidebar: ícone do app (squircle 28dp) + "UNICEP Portal" (`headline`), que substituem o título da top bar removido.
- Itens: altura 36dp, raio 10dp, ícone 18dp `accent` + label `body`. Selecionado com fundo `glassLens` (ou `accent @ 18%`) e texto `label` SemiBold.
- Rodapé: avatar com iniciais + nome curto + RA (`footnote`). Clicar leva à Central.

### 5.9 Sheets (substituem `PortalOverlayModal`)

Novo `AppleSheet` + `AppleSheetHost` em `designsystem/Sheet.kt`:

- **Mobile:** entra de baixo com `AppleMotion.sheet`. Detents:
  - `Medium` (~55% da altura): sheet **flutuante** com inset de 8dp nas laterais e na base, 4 cantos com 34dp, **fundo de vidro**;
  - `Large` (topo a status bar + 10dp): encosta nas bordas, cantos superiores com 34dp e fundo mais opaco (`secondaryGroupedBackground @ 96%`).
  - Abre em `Medium`. Rolar ou arrastar para cima leva a `Large`.
- Grabber: 36×5dp, cápsula, `tertiaryLabel`, 6dp do topo.
- Header: título `title3` alinhado à esquerda e botão **fechar** como `GlassButton` circular de 30dp com "×" à direita (padrão iOS 26).
- **Arrastar para baixo** fecha (limiar: 25% da altura ou velocidade > 1200dp/s). Use `AnchoredDraggableState` do foundation ou `draggable` + `Animatable`.
- Scrim `scrim` atrás. Tocar no scrim fecha. Com o sheet aberto, o conteúdo de trás faz `scale 0.94` e ganha cantos arredondados (efeito *card stack* do iOS), apenas no mobile.
- **Desktop (≥ 640dp):** *form sheet* centralizado, `widthIn(max = 560.dp)`, raio 28dp, vidro e entrada com `scale 0.92 → 1` + fade.
- **Histórico:** mantenha exatamente as variáveis de estado atuais (`selectedSubjectCode`, `showRiskyAttendanceModal`). O sheet é só a apresentação. Fechar por gesto chama os mesmos `onDismiss`, então Voltar/popstate continuam funcionando.
- O conteúdo do sheet usa células agrupadas (`tertiaryGroupedBackground` sobre o vidro).

**Diálogo de atualização** (`AlertDialog` em `App.kt`): no modo Apple, troque por um `AppleAlert` com largura de 300dp, `GlassSurface` com raio 28dp, título `headline` centralizado, mensagem `footnote` centralizada, barra de progresso de cápsula ao baixar e botões em **cápsula** lado a lado ("Depois" `secondaryFill`, "Atualizar" `accent`).

### 5.10 Menu pull‑down (novo, opcional)

`GlassMenu`: popup de vidro (raio 20dp, largura 250dp) ancorado num botão, com itens de 44dp (ícone + texto, checkmark no selecionado). Use para o seletor de período quando houver mais de 4 períodos e para ações secundárias ("Testar notificação", "Marcar todas como lidas").

### 5.11 Ícones (`Icons.kt`)

- Padronize: viewport 24, stroke **1.75**, `Round` caps/joins e glifos centrados opticamente.
- Adicione **variantes preenchidas** para os ícones das abas (`GradesFill`, `AcademicFill`, `MoreFill`).
- Ícones novos necessários: `Search`, `Person`, `CreditCard`, `Document`, `Gear`/`Paintbrush`, `Logout` (seta saindo), `Sparkles`/`Bolt` (testar notificação), `Calendar`, `Clock`, `CheckCircle`, `Warning`, `Eye`/`EyeOff`, `Link`/`ArrowUpRight` (externo), `Globe` (servidor), `Download`.
- **Não copie paths do SF Symbols** (a licença proíbe uso fora de plataformas Apple). Desenhe os próprios ou adapte do **Lucide** (licença ISC, compatível com GPLv3), com atribuição num comentário no topo do arquivo.

---

## 6. Especificação por tela

### 6.1 Splash + Login (`LoginScreen.kt`)

**Splash**
- Fundo: o mesmo *mesh gradient* do Login (abaixo), ícone do app em squircle de 88dp e `AppleActivityIndicator` 24dp abaixo. O texto "Conectando automaticamente..." vira "Entrando…" (`footnote`, `secondaryLabel`), com fade‑in só após 600ms (evita flash em logins rápidos).

**Login**
- **Fundo:** gradiente animado sutil com 3–4 blobs grandes desfocados (azul `#0A84FF`, índigo `#5E5CE6`, teal `#40C8E0`) sobre `groupedBackground`, desenhados em `Canvas` com `Brush.radialGradient`, movendo‑se lentamente (ciclo de 20s, `rememberInfiniteTransition`). Isso dá ao vidro algo para refratar. Respeite "reduzir movimento" quando detectável (senão, deixe o movimento bem lento).
- O `Box` do fundo é o `hazeSource` da tela de login. Ela tem seu próprio `HazeState`, porque fica fora da árvore principal.
- **Hierarquia:**
  1. Ícone do app de 80dp (gradiente azul→índigo, glifo de capelo branco, raio 18dp, sombra suave).
  2. "UNICEP Portal" em `title1` e o subtítulo "Notas, faltas e avisos num só lugar" em `callout` `secondaryLabel`.
  3. **Card de vidro** (`GlassSurface`, raio 28dp, padding 20dp, `widthIn(max = 400.dp)`) contendo:
     - Segmented "Graduação | Pós‑graduação" no **topo** (escolha de contexto vem antes dos dados).
     - Campos agrupados RA | Senha (§5.6), com olho na senha. RA com `KeyboardType.Number`.
     - Erro inline: ícone ⚠ + texto `footnote` `red`, com animação de *shake* horizontal do card ao falhar.
     - Botão **"Entrar"** (`PrimaryButton`, largura total, 50dp). O texto atual "Entrar e Salvar Sessão" é longo demais. Abaixo dele, `caption1` `secondaryLabel` centralizado: "Sua sessão fica salva com segurança neste dispositivo."
  4. "Servidor" vira um link discreto `footnote` "Configurações avançadas" abaixo do card, que expande um campo de URL com ícone de globo. No Web, **esconda** completamente (já é `isWeb`).

### 6.2 Semestre (`GradesScreen.kt`)

Ordem proposta (de cima para baixo):

1. **Large Title** "Semestre", com seletor de período à direita na mesma linha: botão cápsula de vidro "2/2026 ▾" que abre o `GlassMenu`. Isso libera a linha inteira do segmented atual. Se preferir manter o segmented, coloque‑o logo abaixo do resumo.
2. **Saudação** "Olá, Aluno" em `title3` + linha `footnote` "RA 123 · Campus Miguel Petroni".
3. **Resumo em widgets** (grid de 2 colunas, cards de raio 20dp e altura mínima 96dp, ou 3–4 colunas no desktop):
   - **Média geral** (média das médias disponíveis, calcule no composable): número `title1` tabular colorido (verde ≥ 6, vermelho < 6) + `caption1` "Média geral".
   - **Frequência**: "Tranquila" em `green` com ícone ✓, ou "2 no limite" em `orange`/`red` com chevron. Abre o sheet de frequência (comportamento atual). Use `CardStyle.Tinted(orange)` quando houver risco.
   - **Disciplinas**: contagem.
   - (opcional) **Próxima avaliação / sem notas**: "3 sem notas lançadas".
   - Ícone pequeno de 20dp no canto superior esquerdo de cada widget, com cor do sistema.
4. **Novidades** (se houver não lidas): célula com ponto azul de 8dp à esquerda, "Novas notas" `headline`, a última alteração em `subheadline` ("Sistemas Operacionais · P1 → 9,5"), badge com a contagem e chevron. Leva à Central.
5. **Seção "Disciplinas"** (`PortalSectionHeader` prominent) com um card por disciplina (redesenhar `UnifiedSubjectCard`):
   ```
   ┌─────────────────────────────────────────────────────┐
   │ Sistemas Operacionais                       ╭───╮   │
   │ 19280405 · Turma A                          │7,5│   │  ← ProgressRing 48dp (média/10),
   │                                             ╰───╯   │    número title3 tabular no centro
   │ ┌────┐ ┌────┐ ┌────┐                                │
   │ │ P1 │ │ P2 │ │ T1 │   ← só avaliações COM nota;    │
   │ │ 8,0│ │ 7,0│ │ 9,5│     se nenhuma: "Sem notas     │
   │ └────┘ └────┘ └────┘      lançadas" (footnote)      │
   │                                                     │
   │ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬░░░░░░░                  │  ← barra de 4dp
   │ 12 de 15 faltas                     Restam 3 ⚠      │  ← footnote; "Restam" colorido se ≤ 3
   └─────────────────────────────────────────────────────┘
   ```
   - **Nome em Title Case pt‑BR.** Crie `fun String.toDisplayTitle(): String` em `ui/util/Text.kt`: minúsculas, primeira letra de cada palavra maiúscula, preposições em minúsculo (`de, da, do, das, dos, e, em, para, a, o, à, ao, com`) exceto no início, numerais romanos em maiúsculo (`I, II, III, IV, V, VI…`) e siglas conhecidas preservadas (`TCC`, `TI`, `ABNT`, `UNICEP`, `D2L`, `AVA`). Use em **todas** as telas (Histórico, Grade, notificações, sheets).
   - Sem média: anel vazio cinza com "—".
   - Status "Cursando" é o padrão e não precisa aparecer. Mostre só status diferentes ("Aprovado", "Reprovado", "Trancado") como ponto colorido.
   - **Ordenação sugerida:** disciplinas em risco (frequência ≤ 3 restantes ou média < 6) primeiro, com borda esquerda/realce `orange`; depois as demais em ordem alfabética. Avalie se isso melhora a usabilidade e mantenha a ordem do portal caso fique confuso.
6. **Atividades complementares:** célula com ícone (squircle `purple`), título e horas como valor no trailing.
7. **Avisos da Coordenação:** seção com células de texto `subheadline`, expansíveis se passarem de 4 linhas ("Mais").

**Sheet "Detalhes da disciplina"** (antigo `SubjectDetailModal`):
- Header: nome (Title Case) + `footnote` "Código · Turma".
- Hero: `ProgressRing` de 88dp com a média e o rótulo "Média parcial" ou "Média final".
- Seção "Avaliações": lista agrupada, uma linha por avaliação (`P1` · "Peso 30%" em `footnote` · nota no trailing em `headline` tabular). Linhas sem nota mostram "—" em `tertiaryLabel`.
- **Simulador** ("Para fechar com 6,0"): célula `Tinted(accent)` com ícone ✦ e a frase atual. Se viável, adicione um *stepper*/slider para mudar a meta (5,0–10,0). É uma melhora de usabilidade bem-vinda.
- Seção "Frequência": barra + grade 2×2 de métricas (Faltas, Restam, Frequência %, Carga horária) e `caption1` "Atualizado em 08/10/2026".

**Sheet "Frequência no limite":** lista agrupada das disciplinas em risco, cada linha com nome, barra e "Restam N" colorido. Tocar troca para o sheet da disciplina (como hoje).

### 6.3 Curso (`AcademicScreen.kt`)

1. Large Title "Curso".
2. **Hero de progresso:** card com `ProgressRing` de 96dp (estilo anel do app Atividade, verde) mostrando `pct%` no centro e, ao lado, `title2` "34 de 52" + `footnote` "disciplinas concluídas" + `footnote` "6 em andamento".
3. Segmented "Histórico | Grade | Horário" (thumb deslizante).
4. `PortalSearchField` (só Histórico/Grade). Pode ficar *sticky* abaixo da nav bar ao rolar (`stickyHeader`).
5. **Histórico:** agrupe por `yearSem` com cabeçalhos de seção ("2º semestre de 2025"), mais recente primeiro. Cada linha mostra o nome (Title Case), `footnote` "60h · Média 8,5 · Freq. 95%" e status como ponto colorido no trailing. Linhas com ementa ganham chevron/ícone ↗.
6. **Grade:** agrupe por período ("1º período", "2º período"…). Pré‑requisito em `footnote` com ícone de cadeado.
7. **Horário:** agrupe por dia da semana. Cada linha tem horário à esquerda em `subheadline` tabular `secondaryLabel` com coluna fixa de 72dp, e disciplina e sala à direita. Estado vazio com ícone de calendário grande `tertiaryLabel` + texto centralizado.
8. **Estados vazios** (todas as abas): ilustração com ícone de 48dp + título `headline` + texto `subheadline`, centralizados, sem card.

### 6.4 Central (`MoreScreen.kt`), estilo app Ajustes

1. Large Title "Central".
2. **Cartão de perfil** (como o "Apple ID" do Ajustes): avatar circular de 56dp com iniciais sobre gradiente, nome (Title Case) `headline`, `footnote` "RA · Curso/Campus". Sem ação, ou com chevron se houver tela de perfil futura.
3. **Acesso rápido:** a linha "UnicepVirtual" (ícone squircle `indigo`, ícone externo ↗) substitui o botão azul gigante, que domina a tela sem necessidade.
4. **Notificações** (seção agrupada):
   - "Verificar novas notas" (ícone `blue`, trailing `AppleActivityIndicator` enquanto sincroniza);
   - "Enviar notificação de teste" (ícone `orange`);
   - "Marcar todas como lidas" (só se houver não lidas);
   - footer: "Verificamos automaticamente a cada 15 minutos."
5. **Histórico de alertas:** mostre 5 e um "Ver todos" que abre um sheet com a lista completa. Cada linha tem ponto azul se não lida, disciplina (Title Case) `headline`, alteração em `subheadline` com cor ("P1: — → **9,5**", valor novo em `green`/`red` conforme ≥/< 6, ou `orange` para faltas) e **data relativa** no trailing em `footnote` ("agora", "há 5 min", "há 2 h", "ontem", "12/11"). Crie `formatRelative(isoString)` em `ui/util/Time.kt`; hoje o app mostra `2026-11-14T22:42:16Z` cru.
6. **Financeiro:** linhas "Parcela 03 · vence 10/12" com valor tabular no trailing e status como ponto (verde pago, laranja em aberto, vermelho vencido). Links financeiros com ícone `green` de cartão.
7. **Documentos e Serviços:** linhas com ícone `gray` de documento, agrupadas por `category` quando houver.
8. **Aparência:** segmented "Apple | Material 3 | Auto" + footer explicativo (texto atual). Remova o badge "Estilo Apple ativo", que é redundante.
9. **Sobre:** "Versão" com valor `v1.2.1` no trailing e "Verificar atualizações" (ou "Atualizar para v1.3.0" com badge verde). Progresso de download inline.
10. **"Sair"**: célula destrutiva centralizada em `red`, isolada na última seção. Peça **confirmação** com `AppleAlert` ("Sair da conta? Sua sessão salva será removida.").

---

## 7. Microinterações e polimento

- Toda área tocável ≥ 44×44dp.
- Feedback de toque em **tudo** que é clicável (highlight ou scale). Hoje algumas linhas e segmentos não têm nenhum.
- `animateContentSize()` nos cards que expandem (avisos, configurações avançadas).
- Primeira carga: **skeletons** (blocos `systemFill` com shimmer suave) no lugar das listas vazias, em vez de mostrar "Nenhuma disciplina encontrada" enquanto carrega.
- Números que mudam após refresh: `AnimatedContent` com slide vertical curto.
- Web/desktop: cursor `PointerIcon.Hand` em elementos clicáveis e hover sutil (`systemFill @ 50%`) em linhas e células.
- Status bar e navigation bar do Android transparentes (edge‑to‑edge) com ícones claros/escuros conforme o tema. Confira `MainActivity`/`enableEdgeToEdge` no `androidApp`.

---

## 8. Acessibilidade e qualidade

- Contraste mínimo de 4,5:1 para texto sobre vidro. Se o conteúdo atrás for muito colorido, aumente o `glassTint` alpha (nunca abaixo de 45%).
- `contentDescription` em todos os ícones acionáveis. Ícones decorativos ficam com `null`.
- Semântica de `Role.Tab`, `Role.Button` e `selected` na tab bar, no segmented e na sidebar.
- Respeitar a escala de fonte: sem alturas fixas em containers de texto e com `maxLines` + `Ellipsis` onde fizer sentido.
- Light **e** dark precisam ficar bons. Teste os dois em todas as telas.

---

## 9. Plano de execução em fases

Cada fase termina com **build Web + Android passando** e com o **modo Material sem regressão**.

| Fase | Entregas | Arquivos principais |
|------|----------|---------------------|
| **F1 – Tokens e fonte** | Compose Resources, Inter, `AppleType`, cores expandidas, `AppleMotion`, utilitários `toDisplayTitle()` e `formatRelative()` | `build.gradle.kts`, `libs.versions.toml`, `Theme.kt`, `ui/util/*` |
| **F2 – Infra de vidro** | Haze, `LocalHazeState`, `LocalGlassEnabled`, `GlassSurface`, `GlassButton`, fallback | `Glass.kt`, `App.kt` |
| **F3 – Navegação** | Layout edge‑to‑edge, insets, Large Title colapsável, nav bar de vidro, tab bar Liquid Glass (lente + minimizar), sidebar flutuante, toast, pull‑to‑refresh | `App.kt`, telas (contentPadding + LazyListState) |
| **F4 – Componentes** | Card, ListRow (ícones squircle), SectionHeader, Segmented deslizante, botões, campos, busca, badges, ProgressRing, ActivityIndicator, ícones novos | `Components.kt`, `Icons.kt` |
| **F5 – Sheets e alertas** | `AppleSheet` (detents, gesto, desktop form sheet), `AppleAlert`, migração dos 2 modais + diálogo de update + confirmação de logout | `Sheet.kt`, `GradesScreen.kt`, `App.kt`, `MoreScreen.kt` |
| **F6 – Telas** | Login/Splash, Semestre, Curso e Central conforme a §6 | `screens/*` |
| **F7 – Polimento** | Skeletons, hover/cursor, animações de número, revisão de contraste, performance; refração opcional | todos |

---

## 10. Checklist de aceitação

**Visual / Apple**
- [ ] Inter carregada no Android **e** no Web (confirme no DevTools que `composeResources/.../font/*.ttf` responde 200 e **não** devolve `index.html`: o servidor Go faz fallback de SPA para arquivos inexistentes).
- [ ] Nenhum card de conteúdo com borda. Vidro apenas em nav bar, tab bar, sidebar, sheets, toasts, menus e botões flutuantes.
- [ ] O conteúdo rola visivelmente **por baixo** da nav bar e da tab bar, com blur.
- [ ] Large Title colapsa para título inline. A tab bar tem lente deslizante e minimiza ao rolar.
- [ ] Modais viraram sheets com grabber, detents e arrastar para fechar. No desktop ficam centralizados.
- [ ] Nenhum texto em CAIXA ALTA vindo do portal. Nenhuma data ISO crua.
- [ ] Sem chip "iOS", sem título duplicado e sem `fontSize` arbitrário nas telas Apple.
- [ ] Light e dark revisados em todas as telas.

**Funcional / regressão**
- [ ] Modo **Material 3** visualmente idêntico ao anterior (compare com `docs/screenshots/central-material3.png`).
- [ ] Voltar do Android fecha sheet → volta para Semestre → sai do app. Mouse 4/5 e popstate restauram abas e sheets.
- [ ] Com `supportsAppleEffects == false` (ative o modo economia de energia no Android), o vidro vira material sólido sem crash.
- [ ] Login, logout (com confirmação), troca de período, abrir links (ticket proxy), UnicepVirtual SSO, notificação de teste e auto‑update funcionam.
- [ ] Sem `unsafe-inline` nem recursos de terceiros no Web (console sem violações de CSP).

**Comandos**
```bash
cd app && ./gradlew :composeApp:wasmJsBrowserDistribution -PwebOnly=true
cd app && ./gradlew :androidApp:assembleRelease :composeApp:wasmJsBrowserDistribution
cd backend && go test -count=1 -race ./...   # sanity, backend não deve mudar
```

Para testar o Web localmente, rode o backend apontando `WEB_DIR` para `app/composeApp/build/dist/wasmJs/productionExecutable` e abra `http://localhost:<PORT>`.

**Entrega final:** atualize as capturas em `docs/screenshots/` (Semestre, Frequência e Central no modo Apple, light e dark), censurando nome e RA como nas atuais.
