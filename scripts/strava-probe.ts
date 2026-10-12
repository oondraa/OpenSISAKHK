// One-off: logs in to app.strava.cz and saves what /api/objednavky returns, so the lunch card can be built
// against the real shape. Reads the login from .strava.local (gitignored):
//
//   cislo=0288
//   jmeno=...
//   heslo=...
//
// Run: npx tsx scripts/strava-probe.ts  → writes strava-sample.local.json (gitignored, session id removed).
import { readFileSync, writeFileSync } from "node:fs";

const conf = Object.fromEntries(
  readFileSync(".strava.local", "utf8")
    .split(/\r?\n/)
    .filter((l) => l.includes("="))
    .map((l) => [l.slice(0, l.indexOf("=")).trim(), l.slice(l.indexOf("=") + 1).trim()]),
);

async function call(path: string, body: unknown) {
  // Like the Strava web app: a JSON body without a content-type header. With one, the API answers 555.
  const res = await fetch(`https://app.strava.cz/api/${path}`, { method: "POST", body: JSON.stringify(body) });
  return { status: res.status, json: (await res.json().catch(() => null)) as Record<string, unknown> | null };
}

const login = await call("login", {
  cislo: conf.cislo,
  jmeno: conf.jmeno,
  heslo: conf.heslo,
  zustatPrihlasen: false,
  environment: "W",
  lang: "CZ",
});
if (!login.json || login.json.state === "error" || !login.json.sid) {
  console.log("login failed:", login.status, login.json?.message ?? login.json);
  process.exit(1);
}
const { sid, s5url, ignoreCert } = login.json as { sid: string; s5url: string; ignoreCert: unknown };
console.log("login ok, response keys:", Object.keys(login.json).join(", "));

const orders = await call("objednavky", { cislo: conf.cislo, sid, s5url, lang: "CZ", konto: 0, podminka: "", ignoreCert });
await call("logOut", { cislo: conf.cislo, sid, s5url });

const redact = (v: unknown): unknown =>
  Array.isArray(v) ? v.map(redact) : v && typeof v === "object" ? Object.fromEntries(Object.entries(v).map(([k, x]) => [k, k === "sid" ? "…" : redact(x)])) : v;
writeFileSync("strava-sample.local.json", JSON.stringify({ login: redact(login.json), orders: redact(orders.json) }, null, 2));
console.log("orders:", orders.status, "→ strava-sample.local.json");
