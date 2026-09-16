# NoBase Agent Harness

> Non-core, redistributable harness snippets for the NoBase agent runtime.
> This folder contains example interfaces, context utilities, and frontend type notes extracted from the internal implementation.

## Contents

- `java/ai/nobase/agent/harness/context` — context assembly utilities
- `java/ai/nobase/agent/harness/service` — lightweight harness service interfaces and helpers
- `frontend/agent-api-types.ts` — frontend event and API type notes

## Purpose

These files are intentionally small and self-contained. They are meant to help readers understand the public-facing harness design from `AGENT_HARNESS.md` without exposing the full internal platform source.

## Notes

- These files are illustrative, not a standalone production harness.
- Internal dependencies, platform secrets, and core business services are omitted.
- If you want to use these snippets, review them against your own security and architecture requirements.
