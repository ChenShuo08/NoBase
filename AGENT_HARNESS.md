# NoBase Agent Harness

> Public-facing architecture note for the agent harness that powers NoBase's AI assistant experience.
> This repository shares the design, protocol, and integration contract without exposing internal platform source.

## Overview

The agent harness turns a chat message into an auditable execution flow. It can invoke built-in tools or MCP tools, stream partial results to the UI, and require explicit confirmation before risky actions run.

Instead of asking the model to return JSON in an ad hoc way, NoBase uses a custom `CONTROL` protocol embedded in the normal text stream. When the model wants the harness to act, it emits a control line:

```text
CONTROL {"action":"list_projects","arguments":{}}
CONTROL {"action":"execute_project_sql","arguments":{"project":"demo","sql":"SELECT 1"}}
```

If no valid control line appears, the harness returns a safe fallback message. That keeps ungrounded output from triggering actions.

## What This Document Covers

- architecture and responsibilities
- control streaming protocol
- tool registry schema
- risk model and confirmation flow
- MCP tool bridge design
- frontend event contract
- context management
- audit and observability
- security model
- roadmap for incremental open-sourcing

## Architecture

```text
Frontend Agent Page
    |
    v
AgentChatController
    |
    v
AgentLlmOrchestrationService
    |
    +-- LLM stream / CONTROL parser
    |
    +-- AgentChatService
    |       |
    |       +-- built-in tool execution
    |       +-- AgentSessionService
    |       +-- AgentAuditService
    |       +-- AgentKnowledgeService
    |
    +-- AgentMcpToolBridge
            |
            +-- database / auth / storage / function / cron / memory / ai / deployment
```

### Responsibilities

- **AgentChatController** handles auth, streaming response shape, and confirmation CRUD.
- **AgentLlmOrchestrationService** runs the agent loop, manages model calls, parses the control stream, and decides when to continue, pause, or finish.
- **AgentChatService** executes built-in platform actions.
- **AgentMcpToolBridge** exposes external MCP tools to the harness with project context and authorization.
- **AgentToolRegistry** declares actions, descriptions, schemas, risk levels, and confirmation requirements.
- **AgentAuditService** records tool execution outcomes for review and replay.
- **AgentSessionService** manages conversation history and deduplication.
- **AgentContextAssembler** assembles, prunes, compacts, and measures the context sent to the model.

## Core Flow

1. The frontend opens a streaming chat session.
2. The backend assembles system prompt, live context, memory, conversation history, and project scope.
3. The orchestration service sends the assembled messages to the model.
4. While streaming, the backend scans for `CONTROL` lines.
5. When a control line appears, the harness parses it, resolves the tool, checks authorization, and executes.
6. Tool results are injected back into the stream as structured events.
7. If the tool is risky, a confirmation record is created and execution pauses.
8. The session ledger records every action for audit.

## Control Protocol

A control message is a single JSON object on a single line prefixed with `CONTROL`. The harness scans streamed output and extracts control lines before rendering text to the user.

### Example Model Output

```text
先看一下有哪些项目...

CONTROL {"action":"list_projects","arguments":{}}

下面是结果。
```

### Example Stream Events

```json
{"event":"status","text":"查询项目中..."}
{"event":"thinking","content":"用户想要列出项目..."}
{"event":"tool.call","callId":"abc","name":"list_projects","title":"列出项目","riskLevel":"READ","requiresConfirm":false}
{"event":"tool.result","callId":"abc","ok":true,"summary":"共 2 个项目","durationMs":128}
{"event":"done"}
```

### Parser Behavior

- Only line-start `CONTROL` tokens count.
- The stream gate releases safe text as `delta` events.
- If a control payload is malformed, the harness asks the user to rephrase instead of guessing.
- Some models return long reasoning before the first visible token; the parser keeps a configurable read timeout.
- Some gateways ignore `stream=true`; the harness can recover from a non-streaming JSON response.
- The model may hit output length limits; the harness can continue automatically with a continuation nudge instead of failing the whole turn.

### Why Not Standard Function Calling

NoBase uses a custom protocol for two reasons:

- the harness needs to stream visible text, thinking, status, tool results, and confirmations in one stable channel
- the model should not hallucinate tool outputs; results are injected back into the stream by the backend

## Tool Registry

Tools are declared with a registry entry that includes:

- action name
- description
- category
- risk level
- required role
- argument schema
- confirmation requirement
- execution handler

### Built-in Tool Categories

- `create_project`
- `list_projects`
- `execute_project_sql`
- `reset_user_password`
- schema inspection
- metadata queries

### MCP Tool Categories

- database
- auth
- storage
- function
- cron
- memory
- ai gateway
- deployment

### Schema Expectations

A tool registry entry should be explicit enough that the frontend can render tool cards without parsing raw model text. Fields such as `title`, `summary`, `riskLevel`, and `requiresConfirm` are therefore part of the registry contract, not inferred from free-form LLM output.

## Risk Model

The harness uses three risk levels:

| Risk | Meaning | Typical Behavior |
| --- | --- | --- |
| READ | Read-only queries | Execute without confirmation |
| WRITE | Mutating state | Often requires confirmation |
| DESTRUCTIVE | Data loss or irreversible change | Requires confirmation |

Confirmation requirement is declared explicitly in the registry. Relying only on inferred risk is not enough because some write actions are user-initiated and safe, while some read-like queries can still be expensive or sensitive.

## MCP Tool Bridge

The MCP bridge converts harness tool calls into platform service calls.

### Design Goals

- keep a narrow public surface
- expose only authenticated metadata needed by the model
- never send `service_role` secrets or raw admin credentials into the model context
- normalize errors into structured tool results so the model can recover

### Project Context Isolation

Each tool invocation should carry project context separately from visible tool arguments. The backend resolves the project reference, validates ownership, initializes routed data access, and cleans up request-scoped state after the call.

### Error Normalization

Tool failures are returned as structured results with status, message, and retry hints. The harness prefers recoverable errors over silent failures.

## Confirmation Flow

```text
User / model triggers risky tool
    |
    v
Backend creates confirmation record
    |
    v
Frontend shows confirmation card
    |
    +-- approve -> execute and continue
    +-- reject  -> send rejection event to stream
    +-- cancel  -> end tool flow
```

### Persistence and Recovery

Confirmation records store:

- original arguments
- tool metadata
- requester
- timestamp
- current state

Because confirmation state lives outside the chat session, the flow survives retries, stream interruptions, and page refreshes. The frontend can reload pending confirmations and resume without losing context.

### Atomic State Transitions

Confirmation handling should use atomic state transitions. A pending confirmation should only execute if it is still pending at approval time. This prevents duplicate execution when users click multiple times or reload the page.

## Frontend Integration

The frontend agent page consumes streamed events and maintains local UI state for:

- message bubbles
- thinking blocks
- status updates
- tool cards
- confirmation prompts
- error fallbacks

### Stability Contract

The backend should return stable event names and stable identifiers such as `callId`. The frontend should not need to guess semantics from natural language text. Stable contracts also make it easier to add accessibility, keyboard navigation, and screen-reader support later.

### Session Recovery

The frontend should distinguish session expiration from other auth errors. When a session expires during a confirmation flow, the UI should preserve existing tasks and allow the user to re-authenticate and continue, rather than silently dropping the conversation.

## Context Management

Long agent conversations need more than a fixed message slice.

The harness should:

- assemble system prompt, live context, and history
- sanitize malformed or oversized messages
- prune oversized tool results while keeping head, tail, and a pruning marker
- estimate token usage before sending to the model
- compact older context when it approaches the model window
- never drop live context such as current task, pending confirmations, and recent successful actions

This keeps the model focused on the current request instead of repeating already completed work.

## Audit and Observability

Every meaningful tool execution should be logged with:

- user and project identity
- action and tool name
- risk level
- arguments, with secrets masked
- result summary or error
- duration
- confirmation id when applicable

Audit logging should be best-effort: failures in audit must not break the user-facing request.

Observability should cover:

- stream parsing failures
- model timeout or empty outputs
- confirmation approval or cancellation rates
- tool execution duration
- repeated action warnings

## Security Model

The harness is a privilege boundary between the model and the backend.

Key rules:

- never send admin or service-role secrets to the model context
- only expose project-scoped authenticated metadata needed for tool execution
- require confirmation for destructive or sensitive actions
- keep confirmation state durable and tamper-evident
- mask secrets in audit logs
- enforce authorization before tool execution, not after

## Open Source Scope

This public repository shares the harness design and protocol without releasing the full internal platform implementation.

Initially included:

- architecture description
- control protocol specification
- tool registry design
- confirmation flow design
- frontend integration contract
- future module roadmap

Future expansion may include:

- standalone harness interfaces
- example tool implementations
- frontend integration package
- local MCP bridge demo
- testing harness

## Roadmap

- P1: audit viewer in Studio
- P1: tool call timeout and cancellation
- P2: parallel tool calls
- P2: disconnect and resume support
- P2: structured LLM summarization for context compaction

## Contributing

If you want to integrate or extend the NoBase agent harness, start with:

1. define the control protocol contract
2. implement a streaming controller
3. build a tool registry with explicit risk levels
4. add a confirmation flow for risky tools
5. expose only safe metadata to the model
6. log every tool execution for audit
