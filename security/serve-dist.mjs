// Serves the production build (dist/) with the security headers from
// vercel.json, read at startup - not a fourth hand-written copy of them (see
// M12 in docs/SECURITY_AUDIT.md). /api and /uploads are forwarded to the lab
// proxy, which keeps the API same-origin so the production CSP applies as-is.
//
// Lab use only: no caching, no compression, no range requests.
import { createServer, request } from "node:http";
import { readFile } from "node:fs/promises";
import { readFileSync } from "node:fs";
import { extname, join, normalize, resolve } from "node:path";

const root = resolve(process.env.DIST_DIR || "dist");
const upstream = new URL(process.env.API_UPSTREAM || "http://proxy:80");
const port = Number(process.env.PORT || 8080);

const vercel = JSON.parse(readFileSync(process.env.VERCEL_JSON || "vercel.json", "utf8"));
// Only the catch-all rule exists today; apply every rule's headers to every
// response rather than reimplementing Vercel's path matching.
const headers = Object.fromEntries(vercel.headers.flatMap((rule) => rule.headers.map((h) => [h.key, h.value])));
if (!headers["Content-Security-Policy"]) throw new Error("vercel.json has no Content-Security-Policy");

const types = { ".html": "text/html; charset=utf-8", ".js": "text/javascript", ".css": "text/css",
  ".json": "application/json", ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg",
  ".webp": "image/webp", ".svg": "image/svg+xml", ".ico": "image/x-icon", ".woff2": "font/woff2" };

const forward = (req, res) => {
  const peer = req.socket.remoteAddress || "";
  const xff = req.headers["x-forwarded-for"] ? `${req.headers["x-forwarded-for"]}, ${peer}` : peer;
  const proxied = request({ host: upstream.hostname, port: upstream.port, method: req.method, path: req.url,
    headers: { ...req.headers, host: upstream.host, "x-forwarded-for": xff } }, (upstreamRes) => {
    res.writeHead(upstreamRes.statusCode, upstreamRes.headers);
    upstreamRes.pipe(res);
  });
  proxied.on("error", () => { res.writeHead(502); res.end(); });
  req.pipe(proxied);
};

createServer(async (req, res) => {
  let path;
  try {
    path = decodeURIComponent(new URL(req.url, "http://lab").pathname);
  } catch {
    // Malformed escapes: a scanner will send them, and an exception here
    // would take the server down.
    res.writeHead(400);
    return res.end();
  }
  if (path.startsWith("/api/") || path === "/api" || path.startsWith("/uploads/")) return forward(req, res);

  let file = normalize(join(root, path));
  if (!file.startsWith(root)) { res.writeHead(400); return res.end(); }
  let body;
  try { body = await readFile(file); } catch {
    // SPA fallback, as vercel.json's rewrite does.
    file = join(root, "index.html");
    body = await readFile(file);
  }
  res.writeHead(200, { ...headers, "Content-Type": types[extname(file)] || "application/octet-stream" });
  res.end(req.method === "HEAD" ? undefined : body);
}).listen(port, () => console.log(`serve-dist: ${root} on :${port}, API -> ${upstream.origin}`));
