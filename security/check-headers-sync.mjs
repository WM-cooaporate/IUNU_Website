// Fails unless vercel.json, public/_headers and render.yaml's headers block
// declare the same security headers with byte-identical values.
//
// The site is deployed to three hosts, each reading a different file (M12 in
// docs/SECURITY_AUDIT.md). A header changed in one file protects one
// deployment; a CSP that differs between them is a site that works on one
// host and is broken, or unprotected, on another. This turns "keep the three
// in sync by hand" into a CI failure.
//
// No dependencies: render.yaml is read with a parser that understands only
// the `headers:` list shape this repository uses, and says so if that shape
// ever changes.
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (path) => readFileSync(join(root, path), "utf8");

function fromVercel() {
  const config = JSON.parse(read("vercel.json"));
  const rules = config.headers || [];
  if (rules.length !== 1 || rules[0].source !== "/(.*)") {
    throw new Error("vercel.json: expected exactly one headers rule for /(.*)");
  }
  return new Map(rules[0].headers.map((h) => [h.key, h.value]));
}

function fromNetlify() {
  const headers = new Map();
  let inBlock = false;
  for (const line of read("public/_headers").split("\n")) {
    if (line.startsWith("#") || !line.trim()) continue;
    if (!line.startsWith(" ") && !line.startsWith("\t")) {
      if (line.trim() !== "/*") throw new Error(`public/_headers: unexpected path ${line.trim()}`);
      inBlock = true;
      continue;
    }
    if (!inBlock) throw new Error("public/_headers: header before any path");
    const colon = line.indexOf(":");
    headers.set(line.slice(0, colon).trim(), line.slice(colon + 1).trim());
  }
  return headers;
}

function fromRender() {
  const lines = read("render.yaml").split("\n");
  const start = lines.findIndex((line) => /^\s+headers:\s*$/.test(line));
  if (start < 0) throw new Error("render.yaml: no headers: block");
  const indent = lines[start].search(/\S/);
  const headers = new Map();
  let entry = {};
  const flush = () => {
    if (entry.name !== undefined) {
      if (entry.path !== "/*") throw new Error(`render.yaml: header ${entry.name} is for ${entry.path}, expected /*`);
      headers.set(entry.name, entry.value);
    }
    entry = {};
  };
  for (const line of lines.slice(start + 1)) {
    if (!line.trim() || line.trim().startsWith("#")) continue;
    if (line.search(/\S/) <= indent) break;
    const match = line.match(/^\s*(-\s+)?(path|name|value):\s*(.*)$/);
    if (!match) throw new Error(`render.yaml: unexpected line in headers block: ${line.trim()}`);
    if (match[1]) flush();
    let value = match[3].trim();
    if (value.startsWith('"') && value.endsWith('"')) value = JSON.parse(value);
    else if (value.startsWith("'") && value.endsWith("'")) value = value.slice(1, -1).replaceAll("''", "'");
    entry[match[2]] = value;
  }
  flush();
  return headers;
}

const sources = { "vercel.json": fromVercel(), "public/_headers": fromNetlify(), "render.yaml": fromRender() };
const names = new Set(Object.values(sources).flatMap((headers) => [...headers.keys()]));
const problems = [];
for (const name of [...names].sort()) {
  const values = Object.entries(sources).map(([file, headers]) => [file, headers.get(name)]);
  const distinct = new Set(values.map(([, value]) => value));
  if (distinct.size > 1) {
    problems.push(`${name}:\n${values.map(([file, value]) => `    ${file.padEnd(16)} ${value ?? "(missing)"}`).join("\n")}`);
  }
}
if (!sources["vercel.json"].has("Content-Security-Policy")) problems.push("Content-Security-Policy is missing everywhere");

if (problems.length) {
  console.error(`Security headers differ between the three host configs:\n\n  ${problems.join("\n\n  ")}\n`);
  console.error("Make the three files agree - see M12 in docs/SECURITY_AUDIT.md.");
  process.exit(1);
}
console.log(`Security headers in sync across ${Object.keys(sources).join(", ")}: ${[...names].sort().join(", ")}`);
