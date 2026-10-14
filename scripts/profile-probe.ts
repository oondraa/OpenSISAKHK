// One-off: logs in to SIS and lists every field of /Account/UserProfile (label → value), to see what the profile
// offers besides the name (class, field of study, …). Reads the login from .sis.local (gitignored), like sis-probe.ts:
//
//   u=jmeno
//   p=heslo
//
// Run: npx tsx scripts/profile-probe.ts  → also saves the page to profile.local.html (gitignored).
import { readFileSync, writeFileSync } from "node:fs";
import { parse } from "node-html-parser";
import { get, login } from "../src/worker/sis";

const conf = Object.fromEntries(
  readFileSync(".sis.local", "utf8")
    .split(/\r?\n/)
    .filter((l) => l.includes("="))
    .map((l) => [l.slice(0, l.indexOf("=")).trim(), l.slice(l.indexOf("=") + 1).trim()]),
);

const { jar, userId } = await login(conf.u, conf.p);
const { html } = await get(jar, "/Account/UserProfile");
writeFileSync("profile.local.html", html);
console.log(`SIS user id ${userId}\n/Account/UserProfile fields:`);
for (const tr of parse(html).querySelectorAll("tr")) {
  const cells = tr.querySelectorAll("td, th").map((td) => td.text.replace(/\s+/g, " ").trim());
  if (cells.some(Boolean)) console.log(" ", cells.join(" | "));
}
