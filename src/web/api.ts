import type { Absence, Day, Subject } from "../worker/parse";

export type TimetableDay = Day;
export interface TimetableResponse {
  week: string;
  days: TimetableDay[];
}
export interface GradesResponse {
  subjects: Subject[];
}

export class Unauthorized extends Error {}

async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api${path}`, { credentials: "same-origin", ...init });
  const body = (await res.json().catch(() => ({}))) as { error?: string; message?: string };
  if (res.status === 401) throw new Unauthorized(body.error);
  if (!res.ok) throw new Error(body.message ?? body.error ?? `HTTP ${res.status}`);
  return body as T;
}

export const api = {
  login: (username: string, password: string) =>
    call<{ ok: true }>("/login", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ username, password }),
    }),
  logout: () => call<{ ok: true }>("/logout", { method: "POST" }),
  timetable: (week?: string) => call<TimetableResponse>(`/timetable${week ? `?week=${week}` : ""}`),
  /** Permanent timetable (no substitutions or cancellations) of the week starting `week`. */
  permanentTimetable: (week: string) => call<TimetableResponse>(`/timetable?week=${week}&static=1`),
  grades: () => call<GradesResponse>("/grades"),
  absence: () => call<Absence>("/absence"),
  me: () => call<{ name: string | null; hasPhoto: boolean; pushDevices: number }>("/me"),
  schoolWeeks: () => call<{ weeks: { start: string; end: string; week: 1 | 2 }[] }>("/school/weeks"),
  pushKey: () => call<{ key: string }>("/push/key"),
  pushSubscribe: (subscription: PushSubscriptionJSON, cls: string, subjects: string[]) =>
    post("/push/subscribe", { subscription, cls, subjects }),
  pushUnsubscribe: (endpoint: string) => post("/push/unsubscribe", { endpoint }),
  pushTest: () => call<{ sent: number }>("/push/test", { method: "POST" }),
};

function post(path: string, body: unknown) {
  return call<{ ok: true }>(path, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) });
}

// Last successful responses, so the app opens instantly and works offline.
export const cache = {
  get<T>(key: string): T | null {
    try {
      const raw = localStorage.getItem(`osis:${key}`);
      return raw ? (JSON.parse(raw) as T) : null;
    } catch {
      return null;
    }
  },
  set(key: string, value: unknown) {
    try {
      localStorage.setItem(`osis:${key}`, JSON.stringify(value));
    } catch {
      /* storage full or blocked */
    }
  },
  clear() {
    try {
      Object.keys(localStorage)
        .filter((k) => k.startsWith("osis:"))
        .forEach((k) => localStorage.removeItem(k));
    } catch {
      /* ignore */
    }
  },
};
