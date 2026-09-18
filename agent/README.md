# :agent — intelligence (P3)

Builds in **Phase 3**. Will own the LLM gateway, routing and autonomy:

- `LlmProvider` adapters: Groq, Mistral, Gemini (OkHttp + SSE, normalized
  `ToolCall` model), Unreal Speech TTS client
- Tier router (T0 pre-router → T1/T2/T3 chains) with failover + audit
- Tool registry + risk gates (`low`/`medium`/`high` → confirm dialog)
- Agent loop (plan → execute → verify → recover), loop detection,
  screen-hash ring buffer, prompt-injection guard
- Playbooks (compile + replay + self-heal handoff), memory store
- Chat brain + streaming

Depends on `:kernel` (events, capability manifest). `:safety` redacts every
outbound payload before any adapter sends it.
