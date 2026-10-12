// Strava.cz (school canteen orders). No official API: this is the JSON API behind app.strava.cz,
// read off its web app. Shape captured with scripts/strava-probe.ts.

const STRAVA = "https://app.strava.cz/api";
/** Jídelna SŠ a VOŠ aplikované kybernetiky. */
export const CANTEEN = "0288";

export interface StravaCred {
  cislo: string;
  jmeno: string;
  heslo: string;
}

export interface LunchDay {
  date: string; // YYYY-MM-DD
  /** The ordered meal, or null when nothing is ordered for that day. */
  ordered: { label: string; number: number | null; name: string } | null;
}

export class StravaBadCredentials extends Error {}

// A JSON body *without* a content-type header, like the web app: with application/json the API answers 555.
async function call<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${STRAVA}/${path}`, { method: "POST", body: JSON.stringify(body) });
  const json = (await res.json().catch(() => null)) as (T & { state?: string; number?: number | string; message?: string }) | null;
  if (!json) throw new Error(`Strava ${path} returned ${res.status}`);
  if (json.state === "error") {
    if (path === "login" && json.number === 20) throw new StravaBadCredentials();
    throw new Error(`Strava ${path}: ${json.message ?? json.number}`);
  }
  return json;
}

interface Item {
  datum: string; // "12.10.2026"
  druh: string; // "PO" soup, "O1", "O2", …
  druh_popis: string; // "Oběd 2"
  nazev: string;
  pocet: number;
}

const iso = (d: string) => {
  const [day, m, y] = d.split(".");
  return `${y}-${m.padStart(2, "0")}-${day.padStart(2, "0")}`;
};

/** `/api/objednavky` → one entry per day: `{ table0: [items of 12. 10.], table1: [...] }`. */
export function parseOrders(orders: Record<string, Item[]>): LunchDay[] {
  return Object.values(orders)
    .filter((items) => Array.isArray(items) && items.length)
    .map((items) => {
      const meal = items.find((i) => i.pocet > 0 && i.druh !== "PO");
      return {
        date: iso(items[0].datum),
        ordered: meal
          ? { label: meal.druh_popis, number: Number(meal.druh.match(/\d+/)?.[0]) || null, name: meal.nazev.replace(/\s+/g, " ").trim() }
          : null,
      };
    })
    .sort((a, b) => a.date.localeCompare(b.date));
}

/** Throws StravaBadCredentials on a wrong name/password. */
export async function checkLogin(cred: StravaCred) {
  const s = await login(cred);
  await logout(cred, s);
}

async function login(cred: StravaCred) {
  return call<{ sid: string; s5url: string; ignoreCert: unknown }>("login", {
    ...cred,
    zustatPrihlasen: false,
    environment: "W",
    lang: "CZ",
  });
}

async function logout(cred: StravaCred, s: { sid: string; s5url: string; ignoreCert: unknown }) {
  await call("logOut", { sid: s.sid, cislo: cred.cislo, url: s.s5url, lang: "CZ", ignoreCert: s.ignoreCert }).catch(() => {});
}

/** Logs in, reads the orders (Strava shows the next ~3 weeks) and logs out again. */
export async function fetchLunches(cred: StravaCred): Promise<LunchDay[]> {
  const s = await login(cred);
  try {
    const orders = await call<Record<string, Item[]>>("objednavky", {
      cislo: cred.cislo,
      sid: s.sid,
      s5url: s.s5url,
      lang: "CZ",
      konto: 0,
      podminka: "",
      ignoreCert: s.ignoreCert,
    });
    return parseOrders(orders);
  } finally {
    await logout(cred, s);
  }
}
