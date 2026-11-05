// SideQuesty: when there's time to go somewhere (from the timetable) and whether the trip fits in it.
import { openAt, type Week } from "./places";
import type { Journey } from "./transit";

/** What SideQuesty needs of a timetable day (the SIS Day). */
export interface DayLike {
  lessons: { start: string; end: string; cancelled: boolean }[];
  lunch: { start: string; end: string } | null;
}

export type WindowKind = "now" | "before" | "gap" | "after" | "custom";

export interface Window {
  kind: WindowKind;
  label: string;
  /** Leave at (minutes). */
  start: number;
  /** Back at school by (the next lesson), null = no need to come back. */
  end: number | null;
  /** Starts at school (breaks, after school) rather than where the phone is. */
  fromSchool: boolean;
}

/** A break shorter than this is no time for a trip. */
const MIN_GAP = 20;

const mins = (t: string) => {
  const [h, m] = t.split(":").map(Number);
  return h * 60 + m;
};
export const clock = (m: number) => `${Math.floor(m / 60) % 24}:${String(m % 60).padStart(2, "0")}`;

/**
 * The day's chances for a trip: before school (from home), free periods and the lunch break, after school
 * (from school). `now`: minutes of today when the day is today (past windows are dropped, the current one
 * starts now), null for another day.
 */
export function windows(day: DayLike | null, now: number | null): Window[] {
  const lessons = (day?.lessons ?? []).filter((l) => !l.cancelled).map((l) => ({ start: mins(l.start), end: mins(l.end) }));
  lessons.sort((a, b) => a.start - b.start);
  const out: Window[] = [];
  if (!lessons.length) {
    if (now !== null) out.push({ kind: "now", label: "Teď", start: now, end: null, fromSchool: false });
    return out;
  }
  const first = lessons[0].start;
  const last = Math.max(...lessons.map((l) => l.end));

  if (now === null || now < first) {
    const start = now ?? first - 60;
    out.push({ kind: "before", label: "Před školou", start, end: first, fromSchool: false });
  }

  // Free periods: gaps between one lesson's end and the next one's start (lessons can overlap in groups).
  let busyUntil = lessons[0].end;
  for (const l of lessons.slice(1)) {
    if (l.start - busyUntil >= MIN_GAP) {
      const lunch = day?.lunch && mins(day.lunch.start) < l.start && mins(day.lunch.end) > busyUntil;
      out.push({ kind: "gap", label: lunch ? "Oběd" : "Volno", start: busyUntil, end: l.start, fromSchool: true });
    }
    busyUntil = Math.max(busyUntil, l.end);
  }

  // Short breaks (10 minutes) are no trips, but a phone during a lesson still deserves "teď, zpátky na…".
  if (now !== null && now >= first && now < last) {
    const next = lessons.find((l) => l.start > now)?.start ?? null;
    const inGap = out.some((w) => w.kind === "gap" && w.start <= now && now < (w.end ?? Infinity));
    if (!inGap && next !== null) out.unshift({ kind: "now", label: "Teď", start: now, end: next, fromSchool: false });
  }
  out.push({ kind: "after", label: "Po škole", start: last, end: null, fromSchool: true });

  if (now === null) return out;
  const live = out
    .filter((w) => w.end === null || w.end - Math.max(w.start, now) >= MIN_GAP)
    .map((w) => (w.start < now ? { ...w, start: now } : w));
  // Once school is over the phone isn't at school any more: "Po škole" becomes "Teď" from wherever it is.
  if (now >= last) return [{ kind: "now", label: "Teď", start: now, end: null, fromSchool: false }];
  return live;
}

export type Level = "ok" | "tight" | "no";

export interface Verdict {
  level: Level;
  title: string;
  text: string;
  /** Minutes at the place (open), when a way back is needed. */
  stay?: number;
}

/** Enough time to buy something; under it the trip is "těsně". */
const COMFORT = 15;
const BARE = 5;

/**
 * Does the trip fit? `there`: the fastest way there, `back`: the latest way back to school (null = there is none
 * in time), `hours`: the place's week (null = unknown), `weekday`: Monday = 0.
 */
export function verdict(w: Window, there: Journey, back: Journey | null | undefined, hours: Week | null, weekday: number): Verdict {
  const arrive = there.end;
  const open = hours && openAt(hours, weekday, arrive);
  const opensLater = open && !open.open && open.opens !== null ? open.opens : null;

  if (w.end !== null) {
    if (!back || back.start < arrive)
      return { level: "no", title: "Nestihneš", text: `Tam jsi v ${clock(arrive)}, zpátky do ${clock(w.end)} se už nedostaneš.` };
    // Time at the place while it's open.
    let from = arrive;
    let until = back.start;
    if (open) {
      if (open.open) until = Math.min(until, open.closes);
      else if (opensLater !== null) from = opensLater;
      else return { level: "no", title: "Zavřeno", text: `V ${clock(arrive)} už je zavřeno a dnes neotevře.` };
    }
    const stay = until - from;
    const backText = `Odjeď nejpozději v ${clock(back.start)}, ve škole budeš v ${clock(back.end)}.`;
    if (stay >= COMFORT) return { level: "ok", title: `Stihneš, máš tam ${stay} min`, text: backText, stay };
    if (stay >= BARE) return { level: "tight", title: `Těsně, jen ${stay} min na místě`, text: backText, stay };
    if (open && !open.open) return { level: "no", title: "Nestihneš", text: `Otevírá až v ${clock(from)}, to už musíš zpátky.`, stay: Math.max(0, stay) };
    return { level: "no", title: "Nestihneš", text: `Na místě bys měl jen ${Math.max(0, stay)} min. ${backText}`, stay: Math.max(0, stay) };
  }

  if (!open) return { level: "ok", title: `Dorazíš v ${clock(arrive)}`, text: "Otevírací dobu tohohle místa neznám." };
  if (open.open) {
    const left = open.closes - arrive;
    if (left >= COMFORT) return { level: "ok", title: `Dorazíš v ${clock(arrive)}`, text: `Otevřeno do ${clock(open.closes)}.` };
    return { level: "tight", title: `Těsně, zavírá v ${clock(open.closes)}`, text: `Dorazíš v ${clock(arrive)}, zbude ti ${left} min.` };
  }
  if (opensLater !== null) return { level: "tight", title: `Otevírá až v ${clock(opensLater)}`, text: `Dorazíš v ${clock(arrive)}, počkáš ${opensLater - arrive} min.` };
  return { level: "no", title: "Zavřeno", text: `V ${clock(arrive)} už je zavřeno a dnes neotevře.` };
}
