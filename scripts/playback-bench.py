#!/usr/bin/env python3
import argparse
import concurrent.futures
import json
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

SERVICES = {"youtube": 0, "bilibili": 5, "niconico": 6}
MAX_VOD_SECONDS = 3 * 3600
FIRST_BYTES = 512 * 1024
SEARCH_FALLBACK = ("documentary", "cooking recipe", "science explained", "travel vlog", "piano")
UNAVAILABLE_CODES = {"geographic_restriction", "paid_content", "private_content", "content_unavailable", "age_restricted"}


class Client:
    def __init__(self, base, timeout):
        self.base = base.rstrip("/")
        self.timeout = timeout

    def url(self, path):
        if path.startswith("http"):
            return path
        if path.startswith("/api/") and not self.base.endswith("/api"):
            path = path[len("/api"):]
        return self.base + path

    def call(self, method, path, body=None, headers=None, limit=None):
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(self.url(path), data=data, method=method)
        if data is not None:
            request.add_header("Content-Type", "application/json")
        for key, value in (headers or {}).items():
            request.add_header(key, value)
        started = time.monotonic()
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                payload = response.read(limit) if limit else response.read()
                return response.status, payload, time.monotonic() - started
        except urllib.error.HTTPError as error:
            return error.code, error.read(), time.monotonic() - started
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
            return 0, str(error).encode(), time.monotonic() - started

    def json(self, method, path, body=None):
        status, payload, elapsed = self.call(method, path, body)
        try:
            return status, json.loads(payload), elapsed
        except ValueError:
            return status, None, elapsed


def quote(value):
    return urllib.parse.quote(value, safe="")


def is_vod(item):
    return 0 < (item.get("duration") or 0) <= MAX_VOD_SECONDS and not item.get("isShortFormContent")


def pick_videos(client, service, count):
    status, items, _ = client.json("GET", f"/trending?service={SERVICES[service]}")
    if status != 200 or not isinstance(items, list):
        raise SystemExit(f"trending {service} failed with HTTP {status}")
    vods = list(dict.fromkeys(item["url"] for item in items if is_vod(item)))
    for query in SEARCH_FALLBACK:
        if len(vods) >= count:
            break
        status, page, _ = client.json("GET", f"/search?q={quote(query)}&service={SERVICES[service]}")
        if status == 200 and isinstance(page, dict):
            vods.extend(item["url"] for item in page.get("items", []) if is_vod(item) and item["url"] not in vods)
    return vods[:count]


def choose(streams, prefix, max_height=1080):
    candidates = [s for s in streams if (s.get("codec") or "").startswith(prefix)]
    if not candidates:
        candidates = streams
    capped = [s for s in candidates if (s.get("height") or 0) <= max_height] or candidates
    return max(capped, key=lambda s: (s.get("height") or 0, s.get("bitrate") or 0))


def youtube_run(client, url, deadline):
    result = {"url": url}
    status, info, elapsed = client.json("GET", f"/streams/youtube/sabr/bootstrap?url={quote(url)}")
    result["bootstrap"] = elapsed
    started = time.monotonic() - elapsed
    if status != 200 or not info:
        result["error"] = f"bootstrap HTTP {status}"
        return result
    stream_status, _, stream_elapsed = client.json("GET", f"/streams/youtube/sabr?url={quote(url)}")
    result["streams"] = stream_elapsed
    if stream_status != 200:
        result["error"] = f"streams HTTP {stream_status}"
    video = choose(info.get("videoOnlyStreams") or [], "avc1")
    audio = choose(info.get("audioStreams") or [], "mp4a")
    body = {"videoItag": video["itag"], "audioItag": audio["itag"], "startTimeMs": 0}
    status, created, elapsed = client.json("POST", f"/sabr/playback/{info['id']}", body)
    result["create"] = elapsed
    if status not in (200, 202) or not created:
        result["error"] = f"create HTTP {status}"
        return result
    window = {
        "generation": created["generation"],
        "playerTimeMs": 0,
        "videoItag": created["videoItag"],
        "audioItag": created["audioItag"],
        "audioTrackId": created.get("audioTrackId"),
        "bufferGoalMs": 10_000,
        "backBufferMs": 0,
    }
    session = created["sessionId"]
    while time.monotonic() - started < deadline:
        status, ready, _ = client.json("POST", f"/sabr/playback/{session}/prefetch", window)
        if status not in (200, 202) or not ready:
            result["error"] = f"prefetch HTTP {status}"
            return result
        if ready.get("terminalError"):
            result["error"] = f"terminal {ready['terminalError']}"
            return result
        if ready.get("ready"):
            break
        time.sleep(min((ready.get("retryAfterMs") or 250) / 1000, 1.0))
    else:
        result["error"] = "window timeout"
        return result
    status, segments, _ = client.json("POST", f"/sabr/playback/{session}/segments", window)
    track = (segments or {}).get("video")
    if status != 200 or not track or not track.get("segments"):
        result["error"] = f"segments HTTP {status}"
        return result
    for path in (track["initUrl"], track["segments"][0]["url"]):
        status, _, _ = client.call("GET", path)
        if status != 200:
            result["error"] = f"segment HTTP {status}"
            return result
    result["firstFrame"] = time.monotonic() - started
    return result


def dash_run(client, service, url, deadline):
    result = {"url": url}
    status, info, elapsed = client.json("GET", f"/streams/{service}?url={quote(url)}")
    result["streams"] = elapsed
    started = time.monotonic() - elapsed
    if info and info.get("code") in UNAVAILABLE_CODES:
        result["unavailable"] = info["code"]
        return result
    if status != 200 or not info:
        result["error"] = f"streams HTTP {status}"
        return result
    videos = info.get("videoOnlyStreams") or info.get("videoStreams") or []
    if videos:
        video = choose(videos, "avc1")
        init_end = video.get("indexEnd") or video.get("initEnd") or 0
        headers = {"Range": f"bytes=0-{max(init_end, FIRST_BYTES)}"}
        status, _, _ = client.call("GET", video["url"], headers=headers, limit=FIRST_BYTES * 2)
        if status not in (200, 206):
            result["error"] = f"media HTTP {status}"
            return result
    elif info.get("hlsUrl"):
        status, playlist, _ = client.call("GET", info["hlsUrl"])
        if status != 200:
            result["error"] = f"hls HTTP {status}"
            return result
    else:
        result["error"] = "no playable stream"
        return result
    result["firstFrame"] = time.monotonic() - started
    if result["firstFrame"] > deadline:
        result["error"] = "first frame over deadline"
    return result


def run_one(client, service, url, deadline):
    try:
        if service == "youtube":
            return youtube_run(client, url, deadline)
        return dash_run(client, service, url, deadline)
    except (KeyError, IndexError, TypeError) as error:
        return {"url": url, "error": f"unexpected response: {error!r}"}


def percentile(values, fraction):
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round(fraction * (len(ordered) - 1))))
    return ordered[index]


def summarize(label, runs):
    skipped = sum(1 for r in runs if "unavailable" in r)
    lines = [f"{label}: {len(runs)} runs, {sum(1 for r in runs if 'error' in r)} errors, {skipped} unavailable"]
    for metric in ("bootstrap", "streams", "create", "firstFrame"):
        values = [r[metric] for r in runs if metric in r and "error" not in r]
        if values:
            lines.append(
                f"  {metric:<10} p50 {statistics.median(values) * 1000:7.0f} ms"
                f"   p95 {percentile(values, 0.95) * 1000:7.0f} ms   max {max(values) * 1000:7.0f} ms"
            )
    for run in runs:
        if "error" in run:
            lines.append(f"  error {run['error']}: {run['url']}")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Measure TypeType playback start latency")
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--services", default="youtube,bilibili,niconico")
    parser.add_argument("--count", type=int, default=20)
    parser.add_argument("--concurrency", type=int, default=1)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--deadline", type=float, default=30.0)
    parser.add_argument("--json", help="write raw results to this file")
    args = parser.parse_args()
    client = Client(args.base, args.timeout)
    report = {}
    for service in args.services.split(","):
        urls = pick_videos(client, service, args.count)
        for phase in ("cold", "warm"):
            with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:
                runs = list(pool.map(lambda u: run_one(client, service, u, args.deadline), urls))
            report[f"{service}/{phase}"] = runs
            print(summarize(f"{service} {phase}", runs), flush=True)
    if args.json:
        with open(args.json, "w") as output:
            json.dump(report, output, indent=2)
    return 1 if any("error" in r for runs in report.values() for r in runs) else 0


if __name__ == "__main__":
    sys.exit(main())
