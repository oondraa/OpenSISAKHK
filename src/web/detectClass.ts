import { api, cache, type TimetableResponse } from "./api";
import { CLASSES, PERIODS, readSchoolDay, type Cell } from "../shared/schoolOcr";
import { minutes, mondayOf, todayPrague } from "./time";
import type { Lesson } from "../worker/parse";

const DAY_CODES = ["PO", "UT", "ST", "CT", "PA"];
const base = (s: string) => s.replace(/[0-9a-z]+$/, "");

async function readFile(file: string): Promise<Cell[]> {
  const bmp = await createImageBitmap(await (await fetch(`/api/school/${file}.jpg`)).blob());
  const canvas = new OffscreenCanvas(bmp.width, bmp.height);
  const ctx = canvas.getContext("2d", { willReadFrequently: true })!;
  ctx.drawImage(bmp, 0, 0);
  return readSchoolDay(ctx.getImageData(0, 0, bmp.width, bmp.height));
}

/** School-grid period whose start is closest to the lesson start (personal times are sometimes shifted). */
function periodOf(l: Lesson) {
  const start = minutes(l.start);
  let best = 0;
  PERIODS.forEach(([s], i) => {
    if (Math.abs(minutes(s) - start) < Math.abs(minutes(PERIODS[best][0]) - start)) best = i;
  });
  return best;
}

/** Subjects the student has, with groups glued on ("AGJ", "AGJ1"), as the push filter expects. */
export function subjectTokens(t: TimetableResponse): string[] {
  const out = new Set<string>();
  for (const d of t.days)
    for (const l of d.lessons) {
      if (!/^[0-9A-Za-z]+$/.test(l.subject)) continue;
      out.add(l.subject);
      const g = l.group.replace(/[^0-9A-Za-z]/g, "");
      if (g) out.add(l.subject + g);
    }
  return [...out].sort();
}

/**
 * Finds the student's class by matching their personal timetable against the rows of the
 * school-wide timetable: the row that shares the most lessons (period + subject) wins.
 */
export async function detectClass(): Promise<{ cls: string | null; subjects: string[] }> {
  const monday = mondayOf(todayPrague());
  const timetable = cache.get<TimetableResponse>(`timetable:${monday}`) ?? (await api.timetable(monday));
  const { weeks } = await api.schoolWeeks();
  const week = weeks.find((w) => w.start <= monday && monday <= w.end) ?? weeks[0];
  const score = new Map<string, number>(CLASSES.map((c) => [c, 0]));
  const read: { lessons: Lesson[]; cells: Cell[] }[] = [];

  // Two days with the most lessons are plenty to tell the classes apart.
  const days = [...timetable.days].sort((a, b) => b.lessons.length - a.lessons.length).slice(0, 2);
  for (const day of days) {
    const idx = timetable.days.indexOf(day);
    if (!week || idx < 0 || idx > 4 || !day.lessons.length) continue;
    const cells = await readFile(`${DAY_CODES[idx]}${week.week}`);
    read.push({ lessons: day.lessons, cells });
    for (const l of day.lessons) {
      const p = periodOf(l);
      for (const c of cells) if (c.period === p && base(c.subject) === base(l.subject)) score.set(c.cls, score.get(c.cls)! + 1);
    }
  }
  const [cls, best] = [...score].sort((a, b) => b[1] - a[1])[0];
  const subjects = new Set(subjectTokens(timetable));
  // Electives run in blocks: the school grid shows "B06" where the personal timetable shows MPT or GRA.
  for (const { lessons, cells } of read)
    for (const l of lessons)
      for (const c of cells) if (c.cls === cls && c.period === periodOf(l) && /^B\d+$/.test(c.subject)) subjects.add(c.subject);
  return { cls: best > 0 ? cls : null, subjects: [...subjects].sort() };
}
