# Playback Startup Performance

## Target

The target is less than 3 seconds from selecting a video on `beta.typetype.video` to its first rendered video frame. The browser trace's `playback.first_frame` event measures this interval from the click trace; a direct watch-page load starts at route entry instead.

This target is not yet demonstrated. A successful build or fast API response is not a playback result. Validate against the deployed Beta build in Chromium and Firefox, using the same video and recording cold and warm starts separately. Do not infer a pass from a cached thumbnail or `loadedmetadata`; require `first_frame`.

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

## Acceptance

A measured run passes only when click-to-`playback.first_frame.elapsedMs` is below 3000 ms on Beta. Keep the matching browser events and Server/Token records by trace ID with the video and whether the run was cold or warm. Until those deployed measurements exist, the startup target remains unverified.
