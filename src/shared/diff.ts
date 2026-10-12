import type { Cell } from "./schoolOcr";

export type Change =
  | { kind: "added"; cell: Cell }
  | { kind: "removed"; cell: Cell }
  | { kind: "changed"; before: Cell; after: Cell };

const key = (c: Cell) => `${c.cls}|${c.period}|${c.slot}`;
const same = (a: Cell, b: Cell) => a.subject === b.subject && a.teacher === b.teacher && a.room === b.room;

/** Lesson-level differences between two readings of the same school day. */
export function diffDay(before: Cell[], after: Cell[]): Change[] {
  const prev = new Map(before.map((c) => [key(c), c]));
  const next = new Map(after.map((c) => [key(c), c]));
  const out: Change[] = [];
  for (const [k, b] of prev) {
    const a = next.get(k);
    if (!a) out.push({ kind: "removed", cell: b });
    else if (!same(a, b)) out.push({ kind: "changed", before: b, after: a });
  }
  for (const [k, a] of next) if (!prev.has(k)) out.push({ kind: "added", cell: a });
  return out.sort((x, y) => cellOf(x).period - cellOf(y).period || cellOf(x).slot - cellOf(y).slot);
}

const cellOf = (c: Change) => (c.kind === "changed" ? c.after : c.cell);

export interface Audience {
  cls: string;
  /** Subjects the student has, with their group glued on: "MAT", "AGJ", "AGJ1", "PRGr"… Empty = everything. */
  subjects: string[];
}

const base = (subject: string) => subject.replace(/[0-9a-z]+$/, "");

/** Does a lesson subject concern this student? Errs on the side of notifying. */
function concerns(subject: string, who: Audience): boolean {
  if (!who.subjects.length || !subject) return true;
  const mine = new Set(who.subjects);
  if (mine.has(subject)) return true;
  // A numbered group the student isn't in (AGJ2 when they have AGJ1).
  if (/\d$/.test(subject)) return false;
  // Lowercase suffixes are spelled differently in the personal timetable, so match on the base subject.
  const bases = new Set(who.subjects.map(base));
  return bases.has(base(subject));
}

export function relevantChanges(changes: Change[], who: Audience, day: Cell[]): Change[] {
  return changes.filter((ch) => {
    const cells = ch.kind === "changed" ? [ch.before, ch.after] : [ch.cell];
    if (cells[0].cls !== who.cls) return false;
    if (cells.some((c) => concerns(c.subject, who))) return true;
    // A brand-new whole-class lesson (nothing else in that period) concerns everyone in the class.
    if (ch.kind !== "removed") {
      const c = cellOf(ch);
      return !day.some((o) => o.cls === c.cls && o.period === c.period && o.slot !== c.slot);
    }
    return false;
  });
}

const lesson = (c: Cell) => [c.subject, c.teacher, c.room].filter(Boolean).join(" ");

/** One short line per change, e.g. "3. MAT → FYZ LA 211". */
export function describe(ch: Change): string {
  if (ch.kind === "added") return `${ch.cell.period}. nově ${lesson(ch.cell)}`;
  if (ch.kind === "removed") return `${ch.cell.period}. ${ch.cell.subject} odpadá`;
  const { before: b, after: a } = ch;
  if (a.subject !== b.subject) return `${a.period}. ${b.subject} → ${lesson(a)}`;
  const parts = [a.room !== b.room && `učebna ${b.room} → ${a.room}`, a.teacher !== b.teacher && `učí ${a.teacher}`].filter(Boolean);
  return `${a.period}. ${a.subject}: ${parts.join(", ")}`;
}
