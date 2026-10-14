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

/**
 * SIS doesn't show the student's class, so guess it: match the personal timetable against the rows of the
 * school-wide one, the row that shares the most lessons (period + subject) wins. Only a suggestion, the student confirms.
 */
export async function guessClass(): Promise<string | null> {
  const monday = mondayOf(todayPrague());
  const timetable = cache.get<TimetableResponse>(`timetable:${monday}`) ?? (await api.timetable(monday));
  const { weeks } = await api.schoolWeeks();
  const week = weeks.find((w) => w.start <= monday && monday <= w.end) ?? weeks[0];
  if (!week) return null;
  const score = new Map<string, number>(CLASSES.map((c) => [c, 0]));
  // Two days with the most lessons are plenty to tell the classes apart.
  const days = [...timetable.days].sort((a, b) => b.lessons.length - a.lessons.length).slice(0, 2);
  for (const day of days) {
    const idx = timetable.days.indexOf(day);
    if (idx < 0 || idx > 4 || !day.lessons.length) continue;
    const cells = await readFile(`${DAY_CODES[idx]}${week.week}`);
    for (const l of day.lessons) {
      const p = periodOf(l);
      for (const c of cells) if (c.period === p && base(c.subject) === base(l.subject)) score.set(c.cls, score.get(c.cls)! + 1);
    }
  }
  const [cls, best] = [...score].sort((a, b) => b[1] - a[1])[0];
  return best > 0 ? cls : null;
}
