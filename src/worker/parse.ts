import { parse, type HTMLElement } from "node-html-parser";

// Personal timetable is laid out on a 7:00–19:00 axis; card positions are percentages of it.
const DAY_START_MIN = 7 * 60;
const DAY_SPAN_MIN = 12 * 60;

export interface Lesson {
  id: string | null;
  subject: string;
  start: string;
  end: string;
  room: string;
  group: string;
  teacher: string;
  changed: boolean;
  meet: string | null;
}

export interface Day {
  date: string;
  label: string;
  lessons: Lesson[];
  lunch: { start: string; end: string } | null;
}

export interface Grade {
  date: string;
  value: string;
  numeric: number | null;
  type: string;
  typeName: string;
  weight: number | null;
  name: string;
  comment: string;
}

export interface Subject {
  name: string;
  average: number | null;
  grades: Grade[];
}

const text = (el: HTMLElement | null) => el?.text.replace(/\s+/g, " ").trim() ?? "";

const fmt = (min: number) =>
  `${Math.floor(min / 60)}:${String(Math.round(min % 60)).padStart(2, "0")}`;

function percent(style: string, prop: string): number | null {
  const m = style.match(new RegExp(`${prop}:\\s*calc\\(([\\d.]+)%`));
  return m ? parseFloat(m[1]) : null;
}

function addDays(isoDate: string, days: number): string {
  const d = new Date(`${isoDate}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** Parses the `/TimeTable/PersonalNew/?partial=true` fragment. `weekStart` is the Monday (YYYY-MM-DD). */
export function parseTimetable(html: string, weekStart: string): Day[] {
  const root = parse(html);
  const days: Day[] = [];
  for (let i = 1; i <= 5; i++) {
    const col = root.querySelector(`.col${i}`);
    if (!col) continue;
    const lessons = col.querySelectorAll(".hour-card").map((card): Lesson => {
      const [start = "", end = ""] = text(card.querySelector(".time")).split("-").map((s) => s.trim());
      const href = card.getAttribute("href") ?? "";
      return {
        id: card.getAttribute("data-debugid") ?? null,
        subject: text(card.querySelector(".subject-name")),
        start,
        end,
        room: text(card.querySelector(".room-name")),
        group: text(card.querySelector(".group-name")),
        teacher: text(card.querySelector(".teacher")),
        changed: card.classList.contains("changed-card"),
        meet: href.startsWith("https://meet.google.com/") ? href : null,
      };
    });
    const lunchEl = col.querySelector(".lunch-card");
    let lunch: Day["lunch"] = null;
    if (lunchEl) {
      const style = lunchEl.getAttribute("style") ?? "";
      const top = percent(style, "top");
      const height = percent(style, "height");
      if (top !== null && height !== null) {
        const s = DAY_START_MIN + (top / 100) * DAY_SPAN_MIN;
        lunch = { start: fmt(s), end: fmt(s + (height / 100) * DAY_SPAN_MIN) };
      }
    }
    // Hour-line markers are <label>s too; the day caption is the one like "12. pondělí".
    const label = col.querySelectorAll("label").map(text).find((t) => /^\d+\.\s/.test(t)) ?? "";
    days.push({ date: addDays(weekStart, i - 1), label, lessons, lunch });
  }
  return days;
}

export function extractUserId(html: string): string | null {
  return html.match(/userid:\s*(\d+)/)?.[1] ?? null;
}

export function gradeToNumber(value: string): number | null {
  const m = value.trim().match(/^([1-5])(-)?$/);
  if (!m) return null;
  return parseInt(m[1]) + (m[2] ? 0.5 : 0);
}

/** Parses `/Classification/Student`. */
export function parseGrades(html: string): Subject[] {
  const root = parse(html);
  return root.querySelectorAll(".panel.classification").map((panel): Subject => {
    let average: number | null = null;
    const grades: Grade[] = [];
    for (const tr of panel.querySelectorAll("tr")) {
      const tds = tr.querySelectorAll("td");
      if (tds.length < 5) continue; // header row
      const [date, value, type, weight] = tds.map(text);
      const name = text(tr.querySelector("td.name"));
      if (type === "!") {
        const n = parseFloat(value.replace(",", "."));
        average = Number.isFinite(n) ? n : null;
        continue;
      }
      grades.push({
        date,
        value,
        numeric: gradeToNumber(value),
        type,
        typeName: tds[2].getAttribute("title") ?? "",
        weight: weight ? parseInt(weight) : null,
        name,
        comment: text(tr.querySelector("td.comment")),
      });
    }
    return { name: text(panel.querySelector(".panel-heading")), average, grades };
  });
}

/** Parses `/Account/UserProfile` — only the first and last name are used. */
export function parseProfileName(html: string): string | null {
  const root = parse(html);
  const field = (label: string) =>
    text(root.querySelectorAll("tr").find((tr) => text(tr.querySelector("label")) === label)?.querySelectorAll("td")[1] ?? null);
  const name = [field("Jméno"), field("Přijmení") || field("Příjmení")].filter(Boolean).join(" ");
  return name || null;
}

export function isLoginPage(html: string): boolean {
  return /name="Password"/.test(html) && /action="\/Account\/Login"/.test(html);
}
