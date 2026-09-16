# NoBase Agent Harness

> This directory documents the agent harness that powers NoBase's AI backend assistant.  
> It is intentionally kept minimal in this public repository so the architecture and integration surface can be reviewed without exposing internal platform services.

## Overview

The agent harness is responsible for turning a chat message into an auditable execution plan, invoking built-in tools or external MCP tools, streaming partial results back to the UI, and enforcing confirmation gates for risky operations.

Instead of treating the model as a dumb text generator, NoBase uses a custom CONTROL protocol over the normal chat stream. The model is expected to emit control lines when it wants the harness to take an action:

`	ext
CONTROL {"action":"list_projects","arguments":{}}
CONTROL {"action":"execute_project_sql","arguments":{"project":"demo","sql":"SELECT 1"}}
`

If the stream contains no valid control line, the harness safely returns a fallback message instead of executing anything. That constraint keeps ungrounded chat harmless.

## What This README Covers

- the high-level agent architecture
- the control streaming flow
- the tool registry and risk model
- MCP tool bridging
- confirmation flow
- frontend integration contract
- a path for incremental open-sourcing

## Architecture

`	ext
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
            +-- database / auth / storage / function / cron / memory / ai / deployment tools
`

## Core Flow

1. The frontend opens a streaming chat session.
2. The backend assembles context, system prompt, memory, and project scope.
3. The orchestration service sends the conversation to the model.
4. While streaming, the backend looks for control lines.
5. When a control line appears, the harness parses it, looks up the tool, checks authorization, and executes.
6. Tool results are injected back into the stream as structured events.
7. If a tool is risky, a confirmation record is created and execution is paused until the user approves.
8. The session ledger records every action for audit.

## Control Protocol

A control message is a single JSON object on one line prefixed with CONTROL. The harness scans streamed output and extracts it before rendering to the user.

Example assistant output:

`	ext
Thinking about how to show projects...

CONTROL {"action":"list_projects","arguments":{}}

Here are the projects I found.
`

The harness then responds with structured events such as:

`json
{"event":"status","text":"查询项目中..."}
{"event":"data","tool":"list_projects","result":[{"id":"1","name":"demo"}]}
{"event":"done"}
`

This contract lets the frontend render thinking, status, tool results, and confirmation cards without inventing ad-hoc prompts.

## Tool Registry

Tools are declared with a registry entry that includes:

- action name
- description
- risk level
- required role
- argument schema
- confirmation requirement
- execution handler

Built-in tool categories typically include:

- create_project
- list_projects
- xecute_project_sql
- eset_user_password
- schema inspection tools
- metadata query tools

MCP tool categories typically include:

- database
- auth
- storage
- function
- cron
- memory
- ai gateway
- deployment

## Risk Model

Not every tool can run freely. The harness uses three risk levels:

- read-only operations
- write operations
- destructive operations

Write and destructive operations can require an explicit confirmation flow. Confirmation state is stored outside the chat session so it survives retries and stream interruptions.

## MCP Tool Bridge

The MCP bridge translates harness tool calls into platform service calls. It keeps a narrow public surface by exposing only authenticated metadata to the model. Service-role secrets are never sent to the model context.

The bridge also normalizes errors into structured tool results so the LLM can recover instead of failing silently.

## Confirmation Flow

`	ext
User -> risky tool
    |
    v
Backend creates confirmation record
    |
    v
Frontend shows confirmation card
    |
    +-- approve -> execute and continue
    +-- reject -> send rejection event to stream
    +-- cancel -> end tool flow
`

Confirmation records keep the original arguments, tool metadata, requester, and timestamp. This makes the agent behavior auditable and replayable.

## Frontend Integration

The frontend agent page consumes streamed events and maintains local UI state for:

- message bubbles
- thinking blocks
- status updates
- tool cards
- confirmation prompts
- error fallbacks

The backend should return stable event names so the frontend does not need to guess semantics from raw text.

## Open Source Scope

For this initial public release, the repository keeps the harness intentionally lightweight:

- architecture and design description
- protocol specification
- integration guide
- future module roadmap

Later commits can expand the public surface with:

- standalone harness interfaces
- example tool implementations
- frontend integration package
- local MCP bridge demo
- testing harness

## Getting Help

If you want to integrate the NoBase agent harness into your own platform, start with:

1. design your control protocol contract
2. implement a streaming controller
3. build a tool registry with risk levels
4. add a confirmation flow for risky tools
5. expose only safe metadata to the model
6. log every tool execution for audit
