U want to know the current position of TypeType about AI ? Go check [this](https://github.com/TypeType-Video/TypeType/blob/dev/AI_TRANSPARENCY.md).

<div align="center">
  <img src="https://raw.githubusercontent.com/TypeType-Video/TypeType/main/assets/banner.svg" alt="TypeType" width="100%">
  <h1>TypeType Server</h1>
  <p>Extraction, API, and private user data backend for TypeType.</p>
</div>

TypeType-Server is the Kotlin/Ktor HTTP API behind TypeType. It wraps [PipePipeExtractor](https://github.com/InfinityLoop1308/PipePipeExtractor) for supported media services and stores instance and user data in PostgreSQL.

If you want to run a complete TypeType instance, use the [central stack](https://github.com/TypeType-Video/TypeType) rather than deploying this service alone.

## Responsibilities

| Area | Examples |
| --- | --- |
| Extraction | Streams, manifests, search, suggestions, trending, comments, and channels |
| Playback sessions | YouTube SABR sessions, formats, segments, seeks, and recovery state |
| User data | Accounts, history, subscriptions, playlists, favorites, progress, and settings |
| Imports | YouTube Takeout and PipePipe backup ingestion |
| Integrations | TypeType-Token, TypeType-Downloader, media proxying, OIDC, and notifications |
| Administration | Instance settings, users, sessions, allow lists, and bug reports |

The React frontend communicates with this service over HTTP. Frontend source code does not belong in this repository. Download execution belongs in [TypeType-Downloader](https://github.com/TypeType-Video/TypeType-Downloader), and YouTube token and decoder work belongs in [TypeType-Token](https://github.com/TypeType-Video/TypeType-Token).

## Stack

| Role | Technology |
| --- | --- |
| Language and runtime | Kotlin on JDK 25 |
| HTTP server | Ktor with Netty |
| Extraction | PipePipeExtractor |
| Persistence | PostgreSQL, Exposed, and HikariCP |
| Cache | Dragonfly through the Redis protocol |
| Build and tests | Gradle, JUnit, Testcontainers, and JaCoCo |

## API contract

The OpenAPI entry point is [openapi.yaml](openapi.yaml). Split schemas and paths live under [`openapi/`](openapi/).

The API includes public extraction routes and authenticated routes for user data and administration. Authentication, configuration, reverse proxy, and self-hosting instructions live in the [TypeType documentation](https://typetype-video.github.io/Docs-TypeType/).

## Development

Requirements:

- JDK 25
- Docker Engine and Docker Compose v2 for PostgreSQL and Dragonfly

```sh
cp .env.example .env
docker compose up -d postgres dragonfly
./gradlew shadowJar
java -jar build/libs/typetype-server-all.jar
```

The server listens on `http://localhost:8080` by default.

For a complete development stack, clone the [central TypeType repository](https://github.com/TypeType-Video/TypeType) with its submodules.

## Checks

```sh
./gradlew test
./gradlew shadowJar
./gradlew validateOpenApi
```

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Bug reports and feature requests belong in the [central issue tracker](https://github.com/TypeType-Video/TypeType/issues).

## License

TypeType-Server is licensed under [GPL-3.0](LICENSE), as required by its PipePipeExtractor integration.
