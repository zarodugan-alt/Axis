# :act — action / UI driver (P2)

Builds in **Phase 2**. Will own everything AXIS does on-device:

- UI driver: tap / long-press / swipe / scroll via `dispatchGesture`,
  typing via `ACTION_SET_TEXT`, global actions (back/home/recents/shade/lock)
- Verification framework (`ActionResult`, wait-for-condition, evidence types)
- Fallback chain: node → OCR tap → template tap → vision LLM → escalate
- Device controls (brightness, volume, DND, torch, media)
- `FLAG_SECURE` + protected-app sensing guards

Depends on `:kernel` only. The tool registry (`:agent`, P3) calls into this
module; the agent loop never touches accessibility APIs directly.
