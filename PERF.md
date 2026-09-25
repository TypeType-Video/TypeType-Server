# Playback Startup Performance

## Target

The target is less than 3 seconds from selecting a video on `beta.typetype.video` to its first rendered video frame. The browser trace's `playback.first_frame` event measures this interval from the click trace; a direct watch-page load starts at route entry instead.

Two prewarmed Beta browser samples below are under 3 seconds. This does not establish cold-start behavior or a percentile target. Do not infer a pass from a cached thumbnail or `loadedmetadata`; require `first_frame`.

## PipePipe Reference

The checked upstreams are the official `InfinityLoop1308` repositories: [PipePipe Client `c2a166f7`](https://github.com/InfinityLoop1308/PipePipeClient/commit/c2a166f7df05e5ace4c80fb3e3fd27702cfc3e56), declared version `5.4.0`, and [PipePipeExtractor `c68e10e2`](https://github.com/InfinityLoop1308/PipePipeExtractor/commit/c68e10e2e97495877832d8df6cbac55478083019), version `5.4.0`. In Client, `SabrDashMediaSource` calls `SabrMediaBridge.prepareTimelines`; the bridge requests initial audio and video timelines together and retains returned media. Its `PlaybackStartupTrace` marks production player phases, while `SabrPlaybackSmokeTest` and `YoutubePlaybackBenchmarkTest` provide Android test instrumentation. Those harnesses do not measure TypeType's browser or Beta backend. Extractor owns the UMP/SABR session and request logic; its [SABR research log](https://github.com/InfinityLoop1308/PipePipeExtractor/issues/66) is issue-based rather than a standalone current guide.

TypeType's relevant difference is its separate HTTP orchestration: browser requests for stream metadata/bootstrap, Server-side Token resolution, a prewarm-to-consumer session handoff, then browser MSE requests for init and segments. The PipePipe sources are a protocol and instrumentation reference, not evidence that TypeType has the same startup profile or that an upstream change is needed.

## Enable Browser Trace

Before starting a video, open the browser developer console on Beta and run:

```js
localStorage.setItem("typetype-debug-console", "1");
location.reload();
```

Start a video from its card. In the console, collect all `[typetype] playback.*` records for the `traceId` emitted by `playback.trace_start`, through `playback.first_frame`. Remove the setting with `localStorage.removeItem("typetype-debug-console")` to stop tracing. Browser records are kept in session storage for up to 30 minutes, capped at 800 playback entries.

## Correlated Logs

The same trace ID is sent on selected stream, comments, subtitle, SABR prewarm, and SABR MSE requests. Server echoes it and forwards it, with the request ID, to Token. Filter Server and Token runtime logs for `[playback_trace]` and the same `traceId`.

Browser events include API start/end/status/duration; video readiness/playback and first-frame callbacks; same-origin SABR resource timing/transfer sizes; and supported long-task observations. MSE events include `mse_state`, `mse_manifest` (generation and segment count), `mse_quality`, `mse_segment_appended` (track, init/media phase, start and duration), `mse_buffer`, `mse_seek`, and `mse_error`. Segment URLs are omitted. Server events include normalized HTTP route/status/duration, SABR info resolution and session preparation. Token events include session cache/singleflight state and durations for visitor data, BotGuard, GenerateIT, PO-token minting, YouTube session creation, and SABR phases.

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

A later card-click sample on video `ynKvUYpu1Qw` measured 1,892 ms to first frame in Chromium and 1,913 ms in Firefox. Playback started at 1,908/1,941 ms, continued for 3.94/4.01 seconds, and rendered 1920x1080 without a media error. Chromium's stream-info call took 1,291 ms and bootstrap took 172 ms; session creation took 318 ms for the consumer and 139 ms for prewarm. Firefox ran after Chromium had used the same video: stream-info took 43 ms, session calls 29-30 ms, and init calls 26-27 ms. No text tracks were attached at first frame. Firefox was cache-warm, and Chromium's origin-side cache state was not measured, so these results do not prove a cold-start pass.

The browser MSE trace was also captured for `GlosP5N7DkA` in fresh Chromium and Firefox contexts. A separate `stream_resolve` request was active around navigation from search results, and the click path used SABR prewarm plus session handoff; these are not cold-start measurements.

| Browser | Click to first frame | Click to playing | Playback advanced | Result |
| --- | ---: | ---: | ---: | --- |
| Chromium | 1,678 ms | 1,713 ms | 3.96 s | Pass |
| Firefox | 1,539 ms | 1,549 ms | 4.04 s | Pass |

Both rendered a 1920x1080 frame with no media error or intercepted pause call. Firefox's click trace recorded the MSE manifest ready at 1,436 ms, `loadeddata` at 1,512 ms, and `first_frame` at 1,541 ms; its bootstrap trace duration was 284 ms and the prewarm/consumer session requests took 26/25 ms. Both traces showed two text tracks and zero visible tracks at first frame. This confirms a sub-3-second start for these prewarmed samples, not every subtitle format or a cold start.

The TypeType SABR extractor configuration disables supplemental subtitle lookup. This shows subtitles did not gate this start, not that every subtitle path is non-blocking.

A separate cold direct Stack Beta stream-info request returned 200 in 3,585 ms. Its correlated Server log measured the PO-token request at 1,079 ms and the SABR Token session at 196 ms. Token logs broke the cold token refresh into visitor-data fetch 35 ms, BotGuard challenge 178 ms, BotGuard execution 806 ms, GenerateIT fetch 40 ms, and token minting under 10 ms. The later SABR session phases totaled 184 ms (Innertube 105 ms, player 59 ms, session build 19 ms). This endpoint measures extraction, not click-to-first-frame; the timings identify BotGuard as a significant measured phase but do not attribute the entire extraction delay to Token.

The direct Stack request correlated the same trace and request IDs through Server and Token. Browser trace IDs from the public Beta runs did not appear in the Stack-local Server or Token container logs, so public-runtime log correlation is not yet verified. The new trace-gated Server events split provider setup, page fetch, stream extraction, SponsorBlock, supplemental subtitles, authenticated resolution, SABR bootstrap metadata, and format preparation. Verify they appear with the browser trace ID after the change reaches Beta before attributing public latency to a backend phase.

## Acceptance

A measured run passes only when click-to-`playback.first_frame.elapsedMs` is below 3000 ms on Beta. The four browser samples above pass individually. Keep matching browser events and Server/Token records by trace ID, with the video and whether the run was cold or prewarmed. The overall target remains unverified until cold starts and representative network conditions are measured.
