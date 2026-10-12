// Run by .github/workflows/school-changes.yml (no CPU limit there, unlike the free Worker):
// reads every remaining day of the public school timetable, diffs it against the previous reading
// stored in D1 and sends each subscriber a push with the changes that concern their class.
//
// Env: API_URL, INGEST_TOKEN, VAPID_PUBLIC_KEY, VAPID_PRIVATE_JWK
import jpeg from "jpeg-js";
import { readSchoolDay, type Cell } from "../src/shared/schoolOcr";
import { describe, diffDay, relevantChanges } from "../src/shared/diff";
import { SCHOOL_FILES, schoolWeeks } from "../src/worker/school";
import { SIS } from "../src/worker/sis";
import { getInternal, postInternal, sendPush, type PushTarget } from "./action";

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

interface Subscription extends PushTarget {
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

const state = await getInternal<{ days: Record<string, Cell[]>; subscriptions: Subscription[] }>("/state");

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
  const result = await sendPush(sub, { title: `Změna rozvrhu ${sub.cls}`, body: lines.join("\n"), tag: `changes-${sub.cls}` });
  if (result === "sent") sent++;
  if (result === "gone") gone.push(sub.endpoint);
}
console.log(`sent ${sent} notifications, ${gone.length} expired subscriptions`);

await postInternal("/state", { days: Object.fromEntries(Object.entries(now).map(([d, v]) => [d, v.cells])), gone });
