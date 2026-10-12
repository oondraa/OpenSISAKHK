import { describe, expect, it } from "vitest";
import { plan, remainingLessons, semesterEnd } from "../src/web/absencePlan";
import type { Day, Lesson } from "../src/worker/parse";

const lesson = (subject: string) => ({ subject }) as Lesson;
const week = (monday: string, perDay: string[][]): Day[] =>
  perDay.map((subjects, i) => ({
    date: new Date(Date.parse(monday) + i * 864e5).toISOString().slice(0, 10),
    label: "",
    lessons: subjects.map(lesson),
    lunch: null,
  }));

describe("plan", () => {
  it("counts lessons to attend to get under 30 %", () => {
    // 4 of 10 = 40 %; 4/14 = 28.6 % is the first share under 30 %.
    expect(plan({ missed: 4, taught: 10 }, null).needed).toBe(4);
    // exactly 30 % is not under the line
    expect(plan({ missed: 3, taught: 10 }, null).needed).toBe(1);
    expect(plan({ missed: 2, taught: 10 }, null).needed).toBe(0);
  });

  it("computes the reserve until the end of the semester", () => {
    // 20 lessons in total, under 30 % means at most 5 missed: 3 more.
    expect(plan({ missed: 2, taught: 10 }, 10)).toEqual({ needed: 0, remaining: 10, reserve: 3, best: 10 });
    // even attending everything leaves 10/12 > 30 %
    expect(plan({ missed: 10, taught: 10 }, 2)).toMatchObject({ needed: 24, reserve: 0, best: 83.3 });
  });
});

describe("remainingLessons", () => {
  const a = week("2026-10-12", [["MAT", "MAT"], ["FYZ"], [], [], ["TEV"]]);
  const b = week("2026-10-19", [["MAT"], [], [], [], []]);

  it("alternates the two weeks and skips holidays and weekends", () => {
    // from Tue 20. 10. to Fri 30. 10.: week B (Tue–Fri) then week A Mon 26. + Tue 27., 28.–30. are holidays
    const r = remainingLessons("2026-10-12", a, b, "2026-10-19", "2026-10-30");
    expect(Object.fromEntries(r)).toEqual({ MAT: 2, FYZ: 1 });
  });

  it("knows the semester ends", () => {
    expect(semesterEnd("2026-10-12")).toBe("2027-01-28");
    expect(semesterEnd("2027-03-01")).toBe("2027-06-30");
  });
});
