// Run by .github/workflows/school-changes.yml (no CPU limit there, unlike the free Worker):
// reads every remaining day of the public school timetable, diffs it against the previous reading
// stored in D1 and sends each subscriber a push with the changes that concern their class.
//
// Env: API_URL, INGEST_TOKEN, VAPID_PUBLIC_KEY, VAPID_PRIVATE_JWK
import jpeg from "jpeg-js";
import webpush from "web-push";
import { readSchoolDay, type Cell } from "../src/shared/schoolOcr";
import { describe, diffDay, relevantChanges } from "../src/shared/diff";
import { SCHOOL_FILES, schoolWeeks } from "../src/worker/school";
import { SIS } from "../src/worker/sis";

const env = (name: string) => {
  const v = process.env[name];
  if (!v) throw new Error(`Missing env ${name}`);
  return v;
};
const API = env("API_URL").replace(/\/$/, "");
const auth = { authorization: `Bearer ${env("INGEST_TOKEN")}` };
webpush.setVapidDetails("https://github.com/oondraa/OpenSISAKHK", env("VAPID_PUBLIC_KEY"), JSON.parse(env("VAPID_PRIVATE_JWK")).d);

const DAYS = ["Po", "Út", "St", "Čt", "Pá"];
const today = new Intl.DateTimeFormat("sv-SE", { timeZone: "Europe/Prague" }).format(new Date());
const addDays = (iso: string, n: number) => {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
};
const label = (iso: string, i: number) => {
  const [, m, d] = iso.split("-").map(Number);
  return iso === today ? `Dnes (${DAYS[i]})` : iso === addDays(today, 1) ? `Zítra (${DAYS[i]} ${d}. ${m}.)` : `${DAYS[i]} ${d}. ${m}.`;
};

interface Subscription {
  endpoint: string;
  p256dh: string;
  auth: string;
  cls: string;
  subjects: string[];
}

async function readDay(file: string): Promise<Cell[] | null> {
  if (!SCHOOL_FILES.includes(file)) return null;
  const res = await fetch(`${SIS}/TimeTable/${file}.jpg`);
  if (!res.ok || res.headers.get("content-type") !== "image/jpeg") return null;
  try {
    return readSchoolDay(jpeg.decode(new Uint8Array(await res.arrayBuffer()), { useTArray: true }));
  } catch (e) {
    console.warn(`${file}: ${(e as Error).message}`);
    return null;
  }
}

const now: Record<string, { cells: Cell[]; label: string }> = {};
for (const w of await schoolWeeks()) {
  for (let i = 0; i < 5; i++) {
    const date = addDays(w.start, i);
    if (date < today) continue;
    const cells = await readDay(`${["PO", "UT", "ST", "CT", "PA"][i]}${w.week}`);
    if (cells) now[date] = { cells, label: label(date, i) };
  }
}
console.log(`read ${Object.keys(now).length} days`);

const stateRes = await fetch(`${API}/api/internal/state`, { headers: auth });
if (!stateRes.ok) throw new Error(`state: ${stateRes.status}`);
const state = (await stateRes.json()) as { days: Record<string, Cell[]>; subscriptions: Subscription[] };

const changesByDate = Object.entries(now)
  .filter(([date]) => state.days[date]) // first reading of a day = nothing to compare yet
  .map(([date, { cells, label }]) => ({ date, label, cells, changes: diffDay(state.days[date], cells) }))
  .filter((d) => d.changes.length);
console.log(changesByDate.map((d) => `${d.date}: ${d.changes.length} changes`).join("\n") || "no changes");

const gone: string[] = [];
let sent = 0;
for (const sub of state.subscriptions) {
  const lines = changesByDate.flatMap(({ label, cells, changes }) => {
    const mine = relevantChanges(changes, sub, cells);
    return mine.length ? [`${label}: ${mine.map(describe).join(", ")}`] : [];
  });
  if (!lines.length) continue;
  const payload = JSON.stringify({ title: `Změna rozvrhu ${sub.cls}`, body: lines.join("\n"), tag: `changes-${sub.cls}` });
  try {
    await webpush.sendNotification({ endpoint: sub.endpoint, keys: { p256dh: sub.p256dh, auth: sub.auth } }, payload, {
      TTL: 12 * 3600,
      urgency: "high",
    });
    sent++;
  } catch (e) {
    const status = (e as { statusCode?: number }).statusCode;
    if (status === 404 || status === 410) gone.push(sub.endpoint);
    else console.warn(`push failed: ${status ?? (e as Error).message}`);
  }
}
console.log(`sent ${sent} notifications, ${gone.length} expired subscriptions`);

const save = await fetch(`${API}/api/internal/state`, {
  method: "POST",
  headers: { ...auth, "content-type": "application/json" },
  body: JSON.stringify({ days: Object.fromEntries(Object.entries(now).map(([d, v]) => [d, v.cells])), gone }),
});
if (!save.ok) throw new Error(`save: ${save.status}`);
