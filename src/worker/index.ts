import { Hono, type Context } from "hono";
import { deleteCookie, getCookie, setCookie } from "hono/cookie";
import { parseAbsence, parseGrades, parseProfileName, parseTimetable } from "./parse";
import { BadCredentials, SessionExpired, get, getBinary, login, timetablePath } from "./sis";
import { seal, unseal, type Session } from "./session";
import { SCHOOL_FILES, fetchSchoolImage, schoolWeeks, sha256 } from "./school";
import { sendPush } from "./push";

type Bindings = {
  SESSION_SECRET: string;
  VAPID_PUBLIC_KEY: string;
  VAPID_PRIVATE_JWK: string;
  /** Shared with the GitHub Action that reads the school timetable and sends targeted pushes. */
  INGEST_TOKEN: string;
  /** Fine-grained GitHub token allowed to trigger that Action (optional; without it the Action runs on its own schedule). */
  GITHUB_TOKEN?: string;
  GITHUB_REPO: string;
  DB: D1Database;
};
type Env = { Bindings: Bindings; Variables: { session: Session } };

const COOKIE = "osis";
const MAX_AGE = 400 * 24 * 3600; // browsers cap cookies at 400 days; refreshed on every SIS cookie change

const app = new Hono<Env>().basePath("/api");

async function writeSession(c: Context<Env>, s: Session) {
  setCookie(c, COOKIE, await seal(s, c.env.SESSION_SECRET), {
    httpOnly: true,
    secure: new URL(c.req.url).protocol === "https:",
    sameSite: "Strict",
    path: "/api",
    maxAge: MAX_AGE,
  });
}

/** Runs `fn` against SIS; if the SIS session expired, logs in again with the stored credentials and retries once. */
async function withSis<T>(c: Context<Env>, fn: (s: Session) => Promise<{ value: T; changed: boolean }>): Promise<T> {
  const s = c.get("session");
  try {
    const { value, changed } = await fn(s);
    if (changed) await writeSession(c, s);
    return value;
  } catch (err) {
    if (!(err instanceof SessionExpired)) throw err;
    const fresh = await login(s.cred.u, s.cred.p); // BadCredentials here = password changed in SIS
    const next = { ...s, jar: fresh.jar };
    c.set("session", next);
    const { value } = await fn(next);
    await writeSession(c, next);
    return value;
  }
}

/** Monday of the week containing `date` (YYYY-MM-DD), or of the current week in Prague. */
function weekStart(date?: string): string {
  const today = new Intl.DateTimeFormat("sv-SE", { timeZone: "Europe/Prague" }).format(new Date());
  const d = new Date(`${date && /^\d{4}-\d{2}-\d{2}$/.test(date) ? date : today}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() - ((d.getUTCDay() + 6) % 7));
  return d.toISOString().slice(0, 10);
}

app.onError((err, c) => {
  if (err instanceof SessionExpired || err instanceof BadCredentials) {
    const loggingIn = c.req.path === "/api/login";
    if (!loggingIn) deleteCookie(c, COOKIE, { path: "/api" });
    return c.json({ error: loggingIn ? "bad_credentials" : "session_expired" }, 401);
  }
  console.error(err);
  return c.json({ error: "upstream", message: err.message }, 502);
});

// ---------- public ----------

app.post("/login", async (c) => {
  const { username, password } = await c.req.json<{ username?: string; password?: string }>();
  if (!username || !password) return c.json({ error: "missing_fields" }, 400);
  const { jar, userId } = await login(username, password);
  const session: Session = { jar, userId, cred: { u: username, p: password } };
  await writeSession(c, session);
  c.executionCtx.waitUntil(saveProfile(c.env.DB, session).catch((e) => console.error("profile", e)));
  return c.json({ ok: true });
});

app.post("/logout", async (c) => {
  const token = getCookie(c, COOKIE);
  const s = token ? await unseal(token, c.env.SESSION_SECRET) : null;
  if (s) await c.env.DB.prepare("DELETE FROM push_subscriptions WHERE user_id = ?").bind(s.userId).run();
  deleteCookie(c, COOKIE, { path: "/api" });
  return c.json({ ok: true });
});

app.get("/school/weeks", async (c) => c.json({ weeks: await schoolWeeks() }));

app.get("/school/:file{[A-Z]{2}[12]\\.jpg}", async (c) => {
  const file = c.req.param("file").replace(".jpg", "");
  if (!SCHOOL_FILES.includes(file)) return c.notFound();
  const res = await fetchSchoolImage(file);
  if (!res.ok || res.headers.get("content-type") !== "image/jpeg") return c.json({ error: "upstream" }, 502);
  return new Response(res.body, { headers: { "content-type": "image/jpeg", "cache-control": "public, max-age=120" } });
});

app.get("/push/key", (c) => c.json({ key: c.env.VAPID_PUBLIC_KEY }));

// ---------- internal: used by the GitHub Action (.github/workflows/school-changes.yml) ----------

const internal = new Hono<Env>();
internal.use("/*", async (c, next) => {
  const token = c.req.header("authorization")?.replace(/^Bearer /, "");
  if (!c.env.INGEST_TOKEN || token !== c.env.INGEST_TOKEN) return c.json({ error: "forbidden" }, 403);
  await next();
});

internal.get("/state", async (c) => {
  const days = await c.env.DB.prepare("SELECT date, cells FROM school_days").all<{ date: string; cells: string }>();
  const subs = await c.env.DB.prepare(
    "SELECT endpoint, p256dh, auth, cls, subjects FROM push_subscriptions WHERE p256dh IS NOT NULL AND cls IS NOT NULL",
  ).all<{ endpoint: string; p256dh: string; auth: string; cls: string; subjects: string }>();
  return c.json({
    days: Object.fromEntries(days.results.map((d) => [d.date, JSON.parse(d.cells)])),
    subscriptions: subs.results.map((s) => ({ ...s, subjects: JSON.parse(s.subjects) as string[] })),
  });
});

internal.post("/state", async (c) => {
  const { days, gone } = await c.req.json<{ days: Record<string, unknown[]>; gone?: string[] }>();
  const now = Date.now();
  const today = new Intl.DateTimeFormat("sv-SE", { timeZone: "Europe/Prague" }).format(new Date());
  await c.env.DB.batch([
    ...Object.entries(days).map(([date, cells]) =>
      c.env.DB.prepare("INSERT OR REPLACE INTO school_days (date, cells, updated) VALUES (?, ?, ?)").bind(date, JSON.stringify(cells), now),
    ),
    ...(gone ?? []).map((e) => c.env.DB.prepare("DELETE FROM push_subscriptions WHERE endpoint = ?").bind(e)),
    c.env.DB.prepare("DELETE FROM school_days WHERE date < ?").bind(today),
  ]);
  return c.json({ ok: true });
});

app.route("/internal", internal);

// ---------- authenticated ----------

app.use("/*", async (c, next) => {
  const token = getCookie(c, COOKIE);
  const s = token ? await unseal(token, c.env.SESSION_SECRET) : null;
  if (!s?.cred) return c.json({ error: "unauthenticated" }, 401);
  c.set("session", s);
  await next();
});

app.get("/me", async (c) => {
  const { userId } = c.get("session");
  const row = await c.env.DB.prepare("SELECT name, photo IS NOT NULL AS hasPhoto FROM users WHERE id = ?")
    .bind(userId)
    .first<{ name: string; hasPhoto: number }>();
  await c.env.DB.prepare("UPDATE users SET last_seen = ? WHERE id = ?").bind(Date.now(), userId).run();
  const push = await c.env.DB.prepare("SELECT COUNT(*) AS n FROM push_subscriptions WHERE user_id = ?")
    .bind(userId)
    .first<{ n: number }>();
  return c.json({ name: row?.name ?? null, hasPhoto: !!row?.hasPhoto, pushDevices: push?.n ?? 0 });
});

app.get("/me/photo", async (c) => {
  const row = await c.env.DB.prepare("SELECT photo, photo_type FROM users WHERE id = ?")
    .bind(c.get("session").userId)
    .first<{ photo: ArrayBuffer | null; photo_type: string | null }>();
  if (!row?.photo) return c.notFound();
  return new Response(new Uint8Array(row.photo), {
    headers: { "content-type": row.photo_type ?? "image/jpeg", "cache-control": "private, max-age=86400" },
  });
});

app.get("/timetable", async (c) => {
  const week = weekStart(c.req.query("week"));
  const permanent = c.req.query("static") === "1";
  const html = await withSis(c, async (s) => {
    const { html, changed } = await get(s.jar, timetablePath(s.userId, week, permanent));
    return { value: html, changed };
  });
  return c.json({ week, days: parseTimetable(html, week) });
});

app.get("/grades", async (c) => {
  const html = await withSis(c, async (s) => {
    const { html, changed } = await get(s.jar, "/Classification/Student");
    return { value: html, changed };
  });
  return c.json({ subjects: parseGrades(html) });
});

app.get("/absence", async (c) => {
  const html = await withSis(c, async (s) => {
    const { html, changed } = await get(s.jar, "/Absent/My");
    return { value: html, changed };
  });
  return c.json(parseAbsence(html));
});

app.post("/push/subscribe", async (c) => {
  const { subscription, cls, subjects } = await c.req.json<{
    subscription?: { endpoint?: string; keys?: { p256dh?: string; auth?: string } };
    cls?: string;
    subjects?: string[];
  }>();
  const endpoint = subscription?.endpoint;
  if (!endpoint || !/^https:\/\//.test(endpoint) || !subscription.keys?.p256dh || !subscription.keys.auth)
    return c.json({ error: "bad_subscription" }, 400);
  if (!cls || !/^[A-Z]\d$/.test(cls)) return c.json({ error: "bad_class" }, 400);
  const cleanSubjects = (subjects ?? []).filter((s) => /^[0-9A-Za-z]{1,8}$/.test(s)).slice(0, 60);
  await c.env.DB.prepare(
    `INSERT OR REPLACE INTO push_subscriptions (endpoint, user_id, created, p256dh, auth, cls, subjects)
     VALUES (?, ?, ?, ?, ?, ?, ?)`,
  )
    .bind(endpoint, c.get("session").userId, Date.now(), subscription.keys.p256dh, subscription.keys.auth, cls, JSON.stringify(cleanSubjects))
    .run();
  return c.json({ ok: true });
});

app.post("/push/unsubscribe", async (c) => {
  const { endpoint } = await c.req.json<{ endpoint?: string }>();
  if (endpoint)
    await c.env.DB.prepare("DELETE FROM push_subscriptions WHERE endpoint = ? AND user_id = ?")
      .bind(endpoint, c.get("session").userId)
      .run();
  return c.json({ ok: true });
});

app.post("/push/test", async (c) => {
  const subs = await c.env.DB.prepare("SELECT endpoint FROM push_subscriptions WHERE user_id = ?")
    .bind(c.get("session").userId)
    .all<{ endpoint: string }>();
  await notifyAll(c.env, subs.results);
  return c.json({ sent: subs.results.length });
});

// ---------- profile & notifications ----------

async function saveProfile(db: D1Database, s: Session) {
  const { html } = await get(s.jar, "/Account/UserProfile");
  const name = parseProfileName(html) ?? s.cred.u;
  const photo = await getBinary(s.jar, "/Account/Photo.jpg").catch(() => null);
  const now = Date.now();
  await db
    .prepare(
      `INSERT INTO users (id, name, photo, photo_type, first_seen, last_seen) VALUES (?1, ?2, ?3, ?4, ?5, ?5)
       ON CONFLICT(id) DO UPDATE SET name = ?2, photo = COALESCE(?3, photo), photo_type = COALESCE(?4, photo_type), last_seen = ?5`,
    )
    .bind(s.userId, name, photo?.body ?? null, photo?.type ?? null, now)
    .run();
}

async function notifyAll(env: Bindings, subs: { endpoint: string }[]) {
  const keys = { publicKey: env.VAPID_PUBLIC_KEY, privateJwk: env.VAPID_PRIVATE_JWK };
  const gone: string[] = [];
  await Promise.all(subs.map(async ({ endpoint }) => (await sendPush(endpoint, keys)) || gone.push(endpoint)));
  if (gone.length)
    await env.DB.batch(gone.map((e) => env.DB.prepare("DELETE FROM push_subscriptions WHERE endpoint = ?").bind(e)));
}

/** Cron: cheap check whether SIS rewrote any remaining day; if so, wake the GitHub Action that reads and diffs it. */
async function checkSchoolImages(env: Bindings) {
  const prague = new Intl.DateTimeFormat("sv-SE", { timeZone: "Europe/Prague" }).format(new Date());
  const weeks = await schoolWeeks();
  const files = weeks.flatMap((w) =>
    ["PO", "UT", "ST", "CT", "PA"]
      .map((d, i) => ({ file: `${d}${w.week}`, date: addDays(w.start, i) }))
      .filter((f) => f.date >= prague),
  );
  const stored = new Map(
    (await env.DB.prepare("SELECT file, hash FROM school_images").all<{ file: string; hash: string }>()).results.map((r) => [
      r.file,
      r.hash,
    ]),
  );
  const now = Date.now();
  let changed = false;
  const writes: D1PreparedStatement[] = [];
  for (const { file } of files) {
    const res = await fetchSchoolImage(file);
    if (!res.ok) continue;
    const hash = await sha256(await res.arrayBuffer());
    if (stored.has(file) && stored.get(file) !== hash) changed = true;
    writes.push(env.DB.prepare("INSERT OR REPLACE INTO school_images (file, hash, checked) VALUES (?, ?, ?)").bind(file, hash, now));
  }
  if (writes.length) await env.DB.batch(writes);
  if (changed && env.GITHUB_TOKEN) {
    const res = await fetch(`https://api.github.com/repos/${env.GITHUB_REPO}/dispatches`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${env.GITHUB_TOKEN}`,
        accept: "application/vnd.github+json",
        "user-agent": "OpenSISAKHK",
      },
      body: JSON.stringify({ event_type: "school-changed" }),
    });
    if (!res.ok) console.error("dispatch failed", res.status, await res.text());
  }
}

function addDays(iso: string, days: number) {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

export default {
  fetch: app.fetch,
  async scheduled(_event: ScheduledController, env: Bindings, ctx: ExecutionContext) {
    ctx.waitUntil(checkSchoolImages(env));
  },
} satisfies ExportedHandler<Bindings>;
