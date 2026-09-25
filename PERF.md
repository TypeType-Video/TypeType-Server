# Playback Startup Performance

## Target

The target is less than 3 seconds from selecting a video on `beta.typetype.video` to its first rendered video frame. The browser trace's `playback.first_frame` event measures this interval from the click trace; a direct watch-page load starts at route entry instead.

Two prewarmed Beta browser samples below are under 3 seconds. This does not establish cold-start behavior or a percentile target. Do not infer a pass from a cached thumbnail or `loadedmetadata`; require `first_frame`.

## PipePipe Reference

The checked upstream PipePipe Client is commit `bf92523449024b349ff6dc1a3ea4f6f68327bb23`, version `5.4.0-beta2`; PipePipeExtractor is `df983df0e5a72f022f9bd2f066a42117861e4455`. PipePipe's production `PlaybackStartupTrace` marks detail click, stream resolution, player setup, SABR source setup, and first frame. Its Android instrumentation benchmark and request dumper provide deeper test-only Media3 and SABR measurements.

TypeType runs in a browser with separate Server and Token processes, so the implementation correlates those boundaries with an opt-in `X-Playback-Trace-ID`. It is TypeType instrumentation, not a PipePipe code change or a claim that the two players have equivalent startup time.

## Enable Browser Trace

Before starting a video, open the browser developer console on Beta and run:

```js
localStorage.setItem("typetype-debug-console", "1");
location.reload();
```

Start a video from its card. In the console, collect all `[typetype] playback.*` records for the `traceId` emitted by `playback.trace_start`, through `playback.first_frame`. Remove the setting with `localStorage.removeItem("typetype-debug-console")` to stop tracing. Browser records are kept in session storage for up to 30 minutes, capped at 800 playback entries.

## Correlated Logs

The same trace ID is sent on selected stream, comments, subtitle, SABR prewarm, and SABR MSE requests. Server echoes it and forwards it, with the request ID, to Token. Filter Server and Token runtime logs for `[playback_trace]` and the same `traceId`.

Browser events include API start/end/status/duration, video readiness and playback events, the first rendered frame, same-origin SABR resource timing and transfer sizes, and supported long-task observations. Server events include normalized HTTP route/status/duration, SABR info resolution and session preparation. Token events include session cache/singleflight state and durations for visitor data, BotGuard, GenerateIT, PO-token minting, YouTube session creation, and SABR phases.

Compare the browser's full API duration with the Server request duration and the nested Token phases. This separates browser/MSE waiting, Server work, and Token work without treating a fast response header as a rendered frame.

Detailed records are emitted only for requests carrying a valid trace ID. They do not include bearer tokens, cookies, PO-token values, session bindings, request bodies, or signed query strings. Subtitle timing is observational; this harness does not make subtitle loading a prerequisite for video playback.

## Measurements (2026-09-25)

The public Beta instance reported Server revision `d596c984`. With the same video and fresh browser contexts, both runs used the SABR prewarm path:

| Browser | Click to first frame | Click to playing | Playback advanced | Result |
| --- | ---: | ---: | ---: | --- |
| Chromium | 1,731 ms | 1,759 ms | 3.94 s | Pass |
| Firefox | 2,114 ms | 2,159 ms | 4.03 s | Pass |

Both rendered a 1920x1080 frame, continued playing without a media error, and had about 5.34 seconds buffered at `loadeddata`, growing to about 10.68 seconds. Two text tracks were attached and zero were visible at first frame in both runs. This demonstrates that subtitles did not block these starts; it does not establish behavior for every subtitle format or cold start.

These are two prewarmed samples, not cold-start measurements or a percentile. Browser SABR stream-info calls took 369 ms in Chromium and 50 ms in Firefox; the measured SABR create requests took 94/10 ms and 36/26 ms respectively (prewarm/consumer). Segment requests completed in 25-38 ms in Chromium and 26-66 ms in Firefox.

A separate cold direct Stack Beta stream-info request returned 200 in 3,585 ms. Its correlated Server log measured the PO-token request at 1,079 ms and the SABR Token session at 196 ms. Token logs broke the cold token refresh into visitor-data fetch 35 ms, BotGuard challenge 178 ms, BotGuard execution 806 ms, GenerateIT fetch 40 ms, and token minting under 10 ms. The later SABR session phases totaled 184 ms (Innertube 105 ms, player 59 ms, session build 19 ms). This endpoint measures extraction, not click-to-first-frame; the timings identify BotGuard as a significant measured phase but do not attribute the entire extraction delay to Token.

The direct Stack request correlated the same trace and request IDs through Server and Token. Browser trace IDs from the public Beta runs did not appear in the Stack-local Server or Token container logs, so public-runtime log correlation is not yet verified. Keep this distinction when comparing browser timings with backend phases.

## Acceptance

A measured run passes only when click-to-`playback.first_frame.elapsedMs` is below 3000 ms on Beta. The two prewarmed samples above pass. Keep matching browser events and Server/Token records by trace ID, with the video and whether the run was cold or prewarmed. The overall target remains unverified until cold starts and representative network conditions are measured.
