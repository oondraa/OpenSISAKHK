// Run by .github/workflows/watch.yml every 10 minutes during the day and whenever the school timetable
// changes: for each student who switched it on, compares grades, absences and their own timetable (this and
// next week) with the last check and pushes what's new. It reuses the SIS session of the last check and logs in
// only when SIS has expired it. Runs here, not in the Worker: the free Worker has 10 ms CPU and 50 subrequests.
//
// Env: API_URL, INGEST_TOKEN, VAPID_PUBLIC_KEY, VAPID_PRIVATE_JWK
import { absenceMessage, compare, compareTimetable, gradesMessage, timetableMessage, type Seen } from "../src/shared/watch";
import { parseAbsence, parseGrades, parseTimetable } from "../src/worker/parse";
import { BadCredentials, SessionExpired, get, login, timetablePath, type Jar } from "../src/worker/sis";
import { addDays, mondayOf, todayPrague } from "../src/web/time";
import { getInternal, postInternal, sendPush, type PushTarget } from "./action";

const today = todayPrague();
const monday = mondayOf(today);

interface Watcher {
  /** The SIS user id (the app's users are keyed by it). */
  userId: string;
  cred: { u: string; p: string };
  /** SIS cookies of the last check, null before the first one. */
  jar: Jar | null;
  seen: Seen | null;
  subscriptions: PushTarget[];
}

const { watchers } = await getInternal<{ watchers: Watcher[] }>("/watchers");
const seen: Record<string, Seen> = {};
const jars: Record<string, Jar> = {};
let logins = 0;
const drop: string[] = [];
const gone: string[] = [];
let checked = 0;
let failed = 0;
let sent = 0;

async function notify(w: Watcher, payload: Parameters<typeof sendPush>[1]) {
  for (const sub of w.subscriptions) {
    const r = await sendPush(sub, payload);
    if (r === "sent") sent++;
    if (r === "gone") gone.push(sub.endpoint);
  }
}

/** Grades, absences and two weeks of timetable. `get` keeps the jar's cookies up to date as SIS renews them. */
async function load(jar: Jar, userId: string) {
  const grades = parseGrades((await get(jar, "/Classification/Student")).html);
  const absence = parseAbsence((await get(jar, "/Absent/My")).html);
  const days = [];
  for (const week of [monday, addDays(monday, 7)]) days.push(...parseTimetable((await get(jar, timetablePath(userId, week))).html, week));
  return { grades, absence, days };
}

/** With the last check's session while SIS still takes it, else after a fresh login. */
async function loadWithSession(w: Watcher) {
  if (w.jar) {
    try {
      return { jar: w.jar, data: await load(w.jar, w.userId) };
    } catch (e) {
      if (!(e instanceof SessionExpired)) throw e;
    }
  }
  const { jar, userId } = await login(w.cred.u, w.cred.p);
  logins++;
  return { jar, data: await load(jar, userId) };
}

async function check(w: Watcher) {
  try {
    const { jar, data: { grades, absence, days } } = await loadWithSession(w);
    jars[w.userId] = jar;
    const r = await compare(w.seen, grades, absence);
    const t = await compareTimetable(w.seen?.t, days, today);
    if (t.changes.length) await notify(w, timetableMessage(t.changes, today));
    if (r.grades.length) await notify(w, gradesMessage(r.grades));
    if (r.missed.length) await notify(w, absenceMessage(r.missed));
    seen[w.userId] = { ...r.next, t: t.next };
    checked++;
  } catch (e) {
    if (e instanceof BadCredentials) {
      // Password changed in SIS: stop checking until the student logs in to the app again.
      drop.push(w.userId);
      await notify(w, {
        title: "Kontrola známek je vypnutá",
        body: "Heslo do SIS se změnilo. Přihlas se do aplikace znovu a zapni ji v Nastavení.",
        tag: "watch",
        url: "/?tab=settings",
      });
    } else {
      failed++;
      console.warn(`check failed: ${(e as Error).message}`);
    }
  }
}

// A few at a time, to be gentle with SIS.
const queue = [...watchers];
await Promise.all(Array.from({ length: 4 }, async () => {
  for (let w = queue.shift(); w; w = queue.shift()) await check(w);
}));

console.log(`${watchers.length} watchers: ${checked} checked (${logins} logins), ${failed} failed, ${drop.length} dropped, ${sent} notifications`);
await postInternal("/watchers", { seen, jars, drop, gone });
