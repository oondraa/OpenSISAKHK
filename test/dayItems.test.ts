import { describe, expect, it } from "vitest";
import { dayItems } from "../src/web/Timetable";

const lesson = (subject: string, start: string, end: string, cancelled = false) =>
  ({ subject, start, end, room: "211", group: "", teacher: "HU", changed: false, cancelled, added: false, meet: null });
const day = (lessons: ReturnType<typeof lesson>[], lunch: { start: string; end: string } | null) =>
  ({ date: "2026-10-14", label: "14. středa", lessons, lunch }) as Parameters<typeof dayItems>[0];
const shape = (d: Parameters<typeof dayItems>[0]) => dayItems(d).map((i) => `${i.kind} ${i.start}-${i.end}`);

describe("dayItems", () => {
  it("splits a long break into lunch and free time", () => {
    expect(shape(day([lesson("MAT", "10:45", "11:30"), lesson("AGJ", "13:45", "14:30")], { start: "11:30", end: "13:30" }))).toEqual([
      "lesson 10:45-11:30",
      "lunch 11:30-12:00",
      "free 12:00-13:45",
      "lesson 13:45-14:30",
    ]);
  });

  it("shows free time before lunch when the canteen opens later", () => {
    expect(shape(day([lesson("MAT", "9:45", "10:30"), lesson("AGJ", "12:45", "13:30")], { start: "11:30", end: "12:40" }))).toEqual([
      "lesson 9:45-10:30",
      "free 10:30-11:30",
      "lunch 11:30-12:00",
      "free 12:00-12:45",
      "lesson 12:45-13:30",
    ]);
  });

  it("leaves ordinary breaks alone and keeps lunch after the last lesson", () => {
    expect(shape(day([lesson("MAT", "8:00", "8:45"), lesson("AGJ", "8:55", "9:40")], { start: "11:30", end: "13:30" }))).toEqual([
      "lesson 8:00-8:45",
      "lesson 8:55-9:40",
      "lunch 11:30-12:00",
    ]);
  });

  it("keeps a cancelled lesson in the list without it taking up time", () => {
    const d = day([lesson("MAT", "8:45", "9:30", true), lesson("CJL", "9:45", "10:30"), lesson("FYZ", "8:45", "9:30", true), lesson("AGJ", "8:45", "9:30")], null);
    expect(dayItems(d).map((i) => (i.kind === "lesson" ? `${i.lesson.subject}${i.lesson.cancelled ? " x" : ""}` : i.kind))).toEqual([
      "AGJ",
      "MAT x",
      "FYZ x",
      "CJL",
    ]);
  });
});
