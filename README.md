# NoBase

> Open-source, AI-native backend and deploy layer — turn AI-written code into real apps.

NoBase gives coding agents a real backend target: database, auth, storage, functions, assets, cron, memory, and an AI gateway, all in one self-hostable control plane.

## What Is NoBase

NoBase is designed for two operators at the same time:

- humans using the Studio dashboard
- AI coding agents using REST APIs and MCP tools

It combines eight capability modules in one backend:

- **Database** — Postgres per project with a PostgREST-style API and RLS
- **Auth** — Supabase-style auth, JWTs, OAuth/MFA, refresh tokens
- **Storage** — S3/R2-compatible object storage with Postgres metadata
- **Assets** — a public static CDN for publishing generated frontends
- **Functions** — edge functions with gateway routing, secrets, and invocation logs
- **AI Gateway** — OpenAI/Anthropic-compatible model routing with usage tracking
- **Memory** — durable, searchable, evolving memory with hybrid retrieval
- **cron** — scheduled jobs for edge functions or database functions

## Why NoBase

AI coding tools can generate UI and backend code quickly, but generated apps still need a durable runtime: data, identity, auth, file storage, backend logic, a place to publish the frontend, scheduled work, and a dashboard for humans to inspect and operate the system.

NoBase is built to be that runtime. It is also self-hostable and designed for many isolated projects on one control plane, rather than a single-project stack.

## Highlights

- one control plane manages many projects
- each project gets its own PostgreSQL database
- Supabase-style developer experience where it makes sense
- first-class memory for AI-native apps
- MCP-friendly surfaces for coding agents
- built-in deploy path from generated code to live app

## Quick Start

### Run your own Nubase

```bash
docker run -d --name nubase \
  -p 9999:9999 -p 5432:5432 \
  -v nubase_data:/data \
  <your-namespace>/nubase:latest
```

- Studio: http://localhost:9999/studio
- API: http://localhost:9999

### Start from source

Requirements: Java 17, Maven, Docker, Node.js, pnpm.

```bash
# 1. Start Postgres with pgvector
docker compose -f pg-docker-compose.yml up -d

# 2. Set secrets
export PGRST_ENCRYPTION_MASTER_KEY="$(openssl rand -base64 32)"
export METADATA_SERVICE_ROLE_KEY="replace-with-a-long-random-admin-token"

# 3. Start backend
mvn spring-boot:run

# 4. Start Studio
cd frontend && pnpm install && pnpm dev:studio
```

## Try It

```bash
# Create a table
curl -X POST "http://localhost:9999/rest/v1/todos" \
  -H "apikey: $NUBASE_SERVICE_KEY" -H "Content-Type: application/json" \
  -d '{"text":"Ship the first open-source release"}'

# Query
curl "http://localhost:9999/rest/v1/todos?select=*" \
  -H "apikey: $NUBASE_ANON_KEY"

# Write memory
curl -X POST "http://localhost:9999/mem/v1/memories" \
  -H "apikey: $NUBASE_SERVICE_KEY" -H "Content-Type: application/json" \
  -d '{"userId":"user-42","messages":[{"role":"user","content":"I prefer steak over sushi and my dog is named Mochi."}]}'
```

## Documentation

- [Getting started](docs/getting-started.md)
- [Deploy an AI-generated app](docs/deploy-ai-generated-apps.md)
- [Connect agents](docs/agent-connect.md)
- [MCP & agent guide](docs/mcp.md)
- [Edge Functions](docs/edge-functions.md)
- [Assets](docs/assets.md)
- [Scheduled Jobs](docs/scheduled-jobs.md)
- [Architecture](docs/architecture.md)
- [Product overview](docs/product-overview.md)
- [Docker](docs/docker-all-in-one.md)

## Agent Harness

This repository includes a public-facing document about the NoBase agent harness. See [AGENT_HARNESS.md](AGENT_HARNESS.md) for the architecture note, control protocol, tool registry design, confirmation flow, and frontend integration contract.

## Status

NoBase is early stage. Database, Auth, Storage, Assets, Functions, AI Gateway, Memory, cron, Studio, and MCP tooling are in place. Realtime and some enterprise operations are not yet implemented.

## License

[Apache-2.0](LICENSE)
