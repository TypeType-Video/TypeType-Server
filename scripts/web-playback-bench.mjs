#!/usr/bin/env node
import { readFileSync, writeFileSync } from "node:fs";
import { chromium } from "playwright-core";

const usage = `Usage: node scripts/web-playback-bench.mjs --base <url> --videos <file> [options]

Plays each video in Chromium through the TypeType web app as a guest and reports
stream extraction, player start, first media response, first frame, seek time and stalls.

  --base <url>        Instance URL, for example https://beta.typetype.video
  --videos <file>     One public watch id per line (BV..., sm...); # starts a comment
  --chromium <path>   Chromium executable (default: /usr/bin/chromium)
  --play <seconds>    Playback watched for stalls after the seek (default: 30)
  --warm              Open every video a second time to measure the warm path
  --out <file>        Write every measurement as JSON lines
  --hover <ms>        Ask for the stream as a card hover does, then open the video after <ms>`;

const options = { chromium: "/usr/bin/chromium", play: 30, warm: false };
const argv = process.argv.slice(2);
for (let i = 0; i < argv.length; i++) {
  const flag = argv[i];
  if (flag === "--warm") options.warm = true;
  else if (["--base", "--videos", "--chromium", "--play", "--out", "--hover"].includes(flag)) options[flag.slice(2)] = argv[++i];
  else {
    console.error(usage);
    process.exit(64);
  }
}
if (!options.base || !options.videos) {
  console.error(usage);
  process.exit(64);
}
const base = options.base.replace(/\/$/, "");
const videos = readFileSync(options.videos, "utf8")
  .split("\n")
  .map((line) => line.replace(/#.*/, "").trim())
  .filter(Boolean);

const pageProbe = () => {
  const marks = {};
  const stalls = [];
  window.__bench = { marks, stalls };
  const now = () => performance.now();
  const attach = (video) => {
    if (video.__bench) return;
    video.__bench = true;
    marks.loadstart ??= now();
    video.addEventListener("waiting", () => {
      if (marks.firstFrame && !video.seeking) stalls.push({ start: now(), end: null });
    });
    video.addEventListener("playing", () => {
      const open = stalls.at(-1);
      if (open && open.end === null) open.end = now();
    });
    const frame = () => {
      if (video.currentTime > 0 && video.readyState >= 2) marks.firstFrame ??= now();
      else video.requestVideoFrameCallback(frame);
    };
    video.requestVideoFrameCallback(frame);
  };
  new MutationObserver(() => {
    const video = document.querySelector("video");
    if (video) attach(video);
  }).observe(document, { childList: true, subtree: true });
};

const seekProbe = () =>
  new Promise((resolve) => {
    const video = document.querySelector("video");
    if (!video || !Number.isFinite(video.duration) || video.duration < 20) return resolve(null);
    const target = Math.floor(video.duration / 2);
    const started = performance.now();
    const poll = setInterval(() => {
      if (video.currentTime > target + 0.05 && video.currentTime < target + 30 && !video.seeking) {
        clearInterval(poll);
        resolve(performance.now() - started);
      }
    }, 25);
    setTimeout(() => {
      clearInterval(poll);
      resolve(-1);
    }, 20000);
    video.currentTime = target;
    if (video.paused) void video.play().catch(() => undefined);
  });

async function measure(browser, id, pass) {
  const context = await browser.newContext({ viewport: { width: 1600, height: 900 } });
  const page = await context.newPage();
  const timings = { stream: null, firstMedia: null, mediaErrors: 0 };
  page.on("requestfinished", async (request) => {
    const url = request.url();
    const timing = request.timing();
    const end = timing.startTime > 0 && timing.responseEnd >= 0 ? timing.startTime + timing.responseEnd : null;
    const status = (await request.response().catch(() => null))?.status() ?? 0;
    if (/\/api\/streams\/(bilibili|niconico)/.test(url) && end && timings.streamEnd == null) timings.streamEnd = end;
    if (/\/api\/media\//.test(url)) {
      if (status >= 400) timings.mediaErrors++;
      else if (end && timings.firstMediaEnd == null) timings.firstMediaEnd = end;
    }
  });
  page.on("requestfailed", (request) => {
    if (/\/api\/media\//.test(request.url())) timings.mediaErrors++;
  });
  await page.addInitScript(pageProbe);
  const result = { id, pass, base };
  try {
    if (pass === "hover") {
      const source = id.startsWith("BV") ? `https://www.bilibili.com/video/${id}` : `https://www.nicovideo.jp/watch/${id}`;
      const path = id.startsWith("BV") ? "bilibili" : "niconico";
      fetch(`${base}/api/streams/${path}?url=${encodeURIComponent(source)}&intent=preview`).catch(() => undefined);
      await new Promise((resolve) => setTimeout(resolve, Number(options.hover)));
    }
    await page.goto(`${base}/watch?v=${encodeURIComponent(id)}`, { waitUntil: "commit" });
    const origin = await page.evaluate(() => performance.timeOrigin);
    await page.waitForFunction(() => window.__bench?.marks.firstFrame, null, { timeout: 30000 });
    const marks = await page.evaluate(() => window.__bench.marks);
    result.firstFrameMs = Math.round(marks.firstFrame);
    result.playerStartMs = Math.round(marks.loadstart);
    if (timings.streamEnd) result.streamMs = Math.round(timings.streamEnd - origin);
    if (timings.firstMediaEnd) result.firstMediaMs = Math.round(timings.firstMediaEnd - origin);
    await page.waitForTimeout(2000);
    const seek = await page.evaluate(seekProbe);
    result.seekMs = seek == null ? null : Math.round(seek);
    await page.waitForTimeout(Number(options.play) * 1000);
    const stalls = await page.evaluate(() =>
      window.__bench.stalls.map((stall) => (stall.end ?? performance.now()) - stall.start),
    );
    result.stalls = stalls.length;
    result.longestStallMs = Math.round(Math.max(0, ...stalls));
    result.mediaErrors = timings.mediaErrors;
  } catch (error) {
    result.error = error.message.split("\n")[0];
  }
  await context.close();
  return result;
}

const percentile = (values, fraction) => {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.ceil(fraction * sorted.length) - 1)];
};

const browser = await chromium.launch({
  executablePath: options.chromium,
  args: ["--autoplay-policy=no-user-gesture-required"],
});
const results = [];
for (const pass of options.hover ? ["hover"] : options.warm ? ["cold", "warm"] : ["cold"]) {
  for (const id of videos) {
    const result = await measure(browser, id, pass);
    results.push(result);
    console.log(JSON.stringify(result));
  }
}
await browser.close();
if (options.out) writeFileSync(options.out, results.map((r) => JSON.stringify(r)).join("\n") + "\n");

const services = { bilibili: (id) => id.startsWith("BV"), niconico: (id) => /^(sm|so|nm)\d/.test(id) };
for (const pass of options.hover ? ["hover"] : options.warm ? ["cold", "warm"] : ["cold"]) {
  for (const [service, matches] of Object.entries(services)) {
    const rows = results.filter((r) => r.pass === pass && matches(r.id));
    if (!rows.length) continue;
    const ok = rows.filter((r) => !r.error);
    const column = (key) => ok.map((r) => r[key]).filter((v) => typeof v === "number" && v >= 0);
    const line = ["streamMs", "playerStartMs", "firstMediaMs", "firstFrameMs", "seekMs", "longestStallMs"]
      .map((key) => `${key} p50=${percentile(column(key), 0.5)} p95=${percentile(column(key), 0.95)}`)
      .join(" | ");
    console.log(`SUMMARY ${pass} ${service} n=${rows.length} failed=${rows.length - ok.length} | ${line}`);
  }
}
