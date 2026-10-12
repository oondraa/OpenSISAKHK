// One-off: logs in to SIS and shows what the absence page's summary table really looks like, next to what
// parseAbsence makes of it. Reads the login from .sis.local (gitignored):
//
//   u=jmeno
//   p=heslo
//
// Run: npx tsx scripts/sis-probe.ts  → also saves the page to absence.local.html (gitignored).
import { readFileSync, writeFileSync } from "node:fs";
import { parse } from "node-html-parser";
import { parseAbsence } from "../src/worker/parse";
import { get, login } from "../src/worker/sis";

const conf = Object.fromEntries(
  readFileSync(".sis.local", "utf8")
    .split(/\r?\n/)
    .filter((l) => l.includes("="))
    .map((l) => [l.slice(0, l.indexOf("=")).trim(), l.slice(l.indexOf("=") + 1).trim()]),
);

const { jar } = await login(conf.u, conf.p);
const { html } = await get(jar, "/Absent/My");
writeFileSync("absence.local.html", html);

const table = parse(html).querySelector(".absent-sumarized-table");
console.log(table ? "summary table rows:" : "no .absent-sumarized-table on the page");
for (const tr of table?.querySelectorAll("tr") ?? [])
  console.log(" ", tr.querySelectorAll("td, th").map((c) => `${c.tagName.toLowerCase()}:${c.text.replace(/\s+/g, " ").trim()}`).slice(0, 5).join(" | "));

const a = parseAbsence(html);
console.log(`\nparsed: ${a.subjects.length} subjects, ${a.missed.length} missed lessons in the grids`);
console.log("total:", a.total);
for (const s of a.subjects.filter((s) => s.missed > 0)) console.log(" ", s);
