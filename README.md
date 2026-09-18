# AXIS — AI-first Android launcher

100% Kotlin + Jetpack Compose, minSdk 28 / targetSdk 28, for non-rooted
Android 9 devices. Dark high-tech HUD aesthetic, glassmorphism, 3D
transitions, spring-physics motion. No backend servers: AI via BYOK
(Groq, Mistral, Gemini + Unreal Speech). Open-source libraries only.

> **Status: Phase 1 in progress** — UI skeleton (theme, motion, components,
> RootPager with 3D transition, Side-Car, nav shell). The build spec this
> repo implements lives with the maintainers; each phase lands behind the
> module READMEs below.

## Modules

| Module | Owns | Phase |
|---|---|---|
| `:app` | Activity, nav graph, screens, DI wiring, manifest | P1→ |
| `:ui` | Design system: tokens, glass/glow, motion, components | P1 |
| `:kernel` | Event bus, capability manifest, fuzzy search, shared models | P1→ |
| `:sense` | Accessibility + notification + context perception | P2 (stub) |
| `:act` | UI driver, verification, device controls | P2 (stub) |
| `:agent` | LLM gateway, router, tools, agent loop, playbooks | P3 (stub) |
| `:safety` | Redaction firewall, audit, gates | P3 (stub) |

## Building

**CI (authoritative):** every push runs `.github/workflows/ci.yml` —
`assembleDebug` + `testDebugUnitTest` + `lintDebug` (NewApi gate) on JDK 17.
The debug APK is uploaded as an artifact.

**Android Studio:** open the repo root, let it sync (JDK 17, AGP 8.3.2,
Gradle 8.4), run `:ui:downloadFonts` once if the IDE flags missing fonts,
then run `app`.

**Command line:** no wrapper jar is checked in yet, so use a local
Gradle 8.4+ install: `gradle assembleDebug`.

Requirements: compileSdk 34, minSdk/targetSdk 28, JVM 17, KSP (never kapt).

## P1 notes & deliberate deviations

- **Fonts:** Space Grotesk / Inter / JetBrains Mono are OFL variable TTFs
  fetched at build time by `:ui:downloadFonts` from the `google/fonts`
  mirror (binaries are git-ignored). Weights are sliced with
  `fontVariationSettings` (API 26+; minSdk is 28).
- **Glass is faked** (gradient + border + shadow) — `RenderEffect` blur is
  API 31+ and banned. Same for every API-31+ API: `NewApi` lint fails CI.
- **Sparkline** (Side-Car telemetry) is a hand-rolled Canvas, not Vico —
  closer to the HUD aesthetic and zero extra deps. Vico gets revisited for
  the P4 usage dashboard.
- **App icons** are served by `PackageManager` + an in-memory LRU cache;
  Coil arrives in P2 if disk caching proves necessary.
- **Icons** are Compose Material Icons Extended; true Material Symbols
  need a font bundle — revisit if the glyph gap matters visually.
- **Side-Car systems rows** report real permission states; rows for P2+
  services show honest "not built yet" states, never fake greens.

## License

TBD by the maintainers (all dependencies are Apache 2.0 / MIT / BSD).
