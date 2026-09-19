# AXIS — AI-first Android launcher

100% Kotlin + Jetpack Compose, minSdk 28 / targetSdk 28, for non-rooted
Android 9 devices. Dark high-tech HUD aesthetic, glassmorphism, 3D
transitions, spring-physics motion. No backend servers: AI via BYOK
(Groq, Mistral, Gemini + Unreal Speech). Open-source libraries only.

> **Status: all four phases implemented and CI-green** — P1 shell + design
> system, P2 perception (notification listener, accessibility, device
> telemetry), P3 agent (BYOK gateway, streaming chat, tools, safety gates,
> audit) and P4 automation (routines, Flow Studio, voice, usage dashboard).
> Every route in the nav graph is a real screen; there are no placeholders
> left. Verified by `assembleDebug` + `testDebugUnitTest` + `lintDebug` on
> every push (see `.github/workflows/ci.yml`).

## Modules

| Module | Owns | Phase |
|---|---|---|
| `:app` | Activity, nav graph, every screen, DI wiring, manifest (services, permissions, queries) | P1–P4 |
| `:ui` | Design system: tokens, glass/glow, motion, components, circuit board | P1 |
| `:kernel` | Event bus, capability manifest, JSON codec, fuzzy search, tool protocol | P1–P3 |
| `:sense` | Device telemetry, notification listener + inbox + triage, accessibility service | P2 |
| `:act` | System actions (torch/DND/brightness/rotation/ringer), routines + engine | P2–P4 |
| `:agent` | BYOK provider catalog, HTTP/SSE, protocol adapters, LLM gateway, tools, agent loop, TTS | P3 |
| `:safety` | Policy engine, confirmation gates, rate limits, kill switch, audit log | P3 |

## What the phases do

**P2 — perception.** `AxisNotificationListenerService` captures every posted
notification into a bounded 200-record inbox and triages it (priority apps,
keywords, quiet hours). `AxisAccessibilityService` reports the foreground
package and window title only — no text capture, no gesture injection.
`DeviceStateRepository` samples battery, thermals, RAM, storage, load,
network, torch, DND, rotation and brightness on a 2s tick — everything the
circuit board's telemetry strip displays.

**P3 — the agent.** Paste a key (Groq has a free tier) in *Settings → AI
Providers*; keys are sealed with the Android Keystore (AES-256-GCM) and
never leave the device except to the provider you chose. The gateway speaks
OpenAI, Gemini and Anthropic wire formats, streams SSE, fails over only
before the first token, and logs every request to the usage ledger. Chat
runs the agent loop with 15 real device tools; anything above the configured
risk threshold blocks on a confirmation dialog (silence = refusal), and
every decision lands in the audit log.

**P4 — automation.** Routines (Good Night, Good Morning, Focus, Drive,
Battery Saver, plus your own) trigger on time, battery, charging, Wi-Fi, app
launch or notification and run real actions. Flow Studio edits them with the
exact trigger/action vocabulary the engine implements. Voice uses the
platform recogniser and the cloud or on-device speaker. The usage dashboard
charts latency, tokens and failures per provider.

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
- **Side-Car systems rows report real permission states** — accessibility,
  notification access, overlay, usage stats, DND, write-settings and the
  provider count are live platform checks with real Fix actions.
- **The home board shows no apps.** It is AXIS's own surface: AI core,
  subsystem modules, a physical tool rail, a terminal (app search, ask,
  web) and live telemetry. Apps live in the drawer, one swipe away.

## License

TBD by the maintainers (all dependencies are Apache 2.0 / MIT / BSD).
