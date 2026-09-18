# Architecture

TypeType-Server is moving from a single Gradle module to a modular monolith. GitHub remains the canonical repository for the main workflow, while dedicated branches may prepare larger migrations.

## Target shape

The server will be split by responsibility instead of by arbitrary file size:

- `server-domain`: pure business types and domain errors
- `server-core`: shared contracts and stable common models
- `server-cache`: Dragonfly and Redis infrastructure
- `server-db`: Exposed tables, PostgreSQL access, repositories
- `server-auth`: users, sessions, JWT and permissions
- `server-playback`: SABR, PO-token and playback sessions
- `server-token-gateway`: TypeType-Token client boundary
- `server-downloader-gateway`: TypeType-Downloader client boundary
- `server-portability`: import and export workflows
- `server-http`: public HTTP API routes
- `server-admin`: administrative routes
- `server-app`: configuration, wiring and executable entry point

## Dependency rules

Dependencies point toward the domain:

```text
app -> http -> services -> domain
```

Infrastructure such as Netty, PostgreSQL, Redis, Token and Downloader belongs to infrastructure modules. The domain must not depend on Ktor, Exposed, Redis or HTTP types.

## Current phase

`server-core` now contains stable, serialization-only request and response contracts that do not depend on Ktor, Exposed, Token or Downloader. The application depends on this module, allowing it to be built and tested independently while later modules are extracted.

The migration is incremental. Source files are moved only after their dependencies are understood, and every step must keep the existing deployment artifact intact.
