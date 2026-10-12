import type { AbsenceSubject, Day } from "../worker/parse";
import { addDays, mondayOf, weekdayIndex } from "./time";

/**
 * School rule: missing more than 30–35 % of a subject's lessons means the teacher may refuse to grade it
 * (opravné zkoušky). 30 % is the safe side of that range, so it is the line the app plans against.
 */
export const LIMIT = 30;
export const LIMIT_HARD = 35;

/** Days without lessons in school year 2026/27 (Královéhradecký kraj). Spring break isn't known yet. */
const HOLIDAYS: [string, string][] = [
  ["2026-10-28", "2026-10-30"], // státní svátek + podzimní prázdniny
  ["2026-11-17", "2026-11-17"],
  ["2026-12-23", "2027-01-01"], // vánoční prázdniny
  ["2027-01-29", "2027-01-29"], // pololetní prázdniny
  ["2027-03-25", "2027-03-29"], // velikonoční prázdniny, Velký pátek, Velikonoční pondělí
];

const isHoliday = (iso: string) => HOLIDAYS.some(([from, to]) => from <= iso && iso <= to);

/** Last teaching day of the semester `today` falls in. */
export function semesterEnd(today: string): string {
  const [y, m] = today.split("-").map(Number);
  const end = m >= 9 ? `${y + 1}-01-31` : m === 1 ? `${y}-01-31` : `${y}-06-30`;
  return end === "2027-01-31" ? "2027-01-28" : end; // pololetní vysvědčení 28. 1. 2027
}

/**
 * Lessons per subject from tomorrow until `end`, read off the permanent timetable of two consecutive
 * weeks (the school alternates week 1 and week 2) starting on `weekA`, skipping holidays.
 */
export function remainingLessons(weekA: string, a: Day[], b: Day[], today: string, end: string): Map<string, number> {
  const out = new Map<string, number>();
  for (let d = addDays(today, 1); d <= end; d = addDays(d, 1)) {
    const wd = weekdayIndex(d);
    if (wd > 4 || isHoliday(d)) continue;
    const weeks = Math.round((Date.parse(mondayOf(d)) - Date.parse(weekA)) / (7 * 864e5));
    const day = (weeks % 2 === 0 ? a : b).find((x) => weekdayIndex(x.date) === wd);
    for (const l of day?.lessons ?? []) out.set(l.subject, (out.get(l.subject) ?? 0) + 1);
  }
  return out;
}

export interface Plan {
  /** Lessons in a row the student must attend to get under LIMIT; 0 when already under. */
  needed: number;
  /** Lessons left in the semester, if known. */
  remaining: number | null;
  /** How many of the remaining lessons can still be missed while ending the semester under LIMIT. */
  reserve: number | null;
  /** Share missed at the end of the semester if no other lesson is missed. */
  best: number | null;
}

/** Integer arithmetic only, so 30.0 % is never misread as 29.999 %. */
export function plan(s: Pick<AbsenceSubject, "missed" | "taught">, remaining: number | null): Plan {
  const { missed: m, taught: t } = s;
  const over = 100 * m >= LIMIT * t && m > 0;
  const needed = over ? Math.floor((100 * m - LIMIT * t) / LIMIT) + 1 : 0;
  if (remaining === null) return { needed, remaining, reserve: null, best: null };
  const total = t + remaining;
  const reserve = Math.max(0, Math.min(remaining, Math.ceil((LIMIT * total - 100 * m) / 100) - 1));
  return { needed, remaining, reserve, best: total ? Math.round((1000 * m) / total) / 10 : 0 };
}
