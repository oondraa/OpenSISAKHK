import { describe, expect, it } from "vitest";
import { cancelledLessons, extractUserId, gradeToNumber, isLoginPage, parseGrades, parseTimetable } from "../src/worker/parse";

// Synthetic fixtures that mirror the markup SIS serves (structure only, no real data).
const card = (subject: string, time: string, room: string, group: string, teacher: string, extra = "") =>
  `<a href="https://meet.google.com/abc-defg-hij" rel="noreferrer" class="hour-card ${extra}" data-debugid="1" style="top: calc(6.9%)">
    <div class="subject-name">${subject}</div><div class="time">${time}</div>
    <div class="room-name">${room}</div><div class="group-name">${group}</div><div class="teacher">${teacher}</div></a>`;

const timetable = (mondayCards: string) => `
  <div class="col col1 active"><div class="hour-lines"><div class="hour-line"><label>7:00</label></div></div>
    <div class="hour-cards"><div class="absent-card"></div>${mondayCards}
      <div class="lunch-card" style="top: calc(44.8611111111111%);height: calc(4.02777777777778%)"><div class="lunch-text">Oběd</div></div>
    </div><label>12. pondělí</label></div>
  <div class="col col2 "><div class="hour-cards">
    <div class="hour-card "><div class="subject-name">DIC</div><div class="time">8:45 - 9:30</div><div class="room-name">306</div><div class="group-name">1</div><div class="teacher">HU</div></div>
  </div><label>13. úterý</label></div>`;

describe("parseTimetable", () => {
  const actual = parseTimetable(
    timetable(card("ELZ", "7:50 - 8:35", "211", "", "HU") + card("AGJ", "9:45 - 10:30", "205", "1", "SA", "changed-card")),
    "2026-10-12",
  );

  it("reads days, dates and lessons", () => {
    expect(actual.map((d) => [d.date, d.label])).toEqual([
      ["2026-10-12", "12. pondělí"],
      ["2026-10-13", "13. úterý"],
    ]);
    expect(actual[0].lessons[0]).toMatchObject({
      subject: "ELZ", start: "7:50", end: "8:35", room: "211", teacher: "HU", changed: false,
      meet: "https://meet.google.com/abc-defg-hij",
    });
    expect(actual[0].lessons[1]).toMatchObject({ subject: "AGJ", group: "1", changed: true });
    expect(actual[1].lessons[0]).toMatchObject({ subject: "DIC", meet: null });
  });

  it("derives lunch time from card position", () => {
    expect(actual[0].lunch).toEqual({ start: "12:23", end: "12:52" });
  });

  it("finds lessons dropped from the permanent timetable", () => {
    const permanent = parseTimetable(
      timetable(card("ELZ", "7:50 - 8:35", "211", "", "HU") + card("MAT", "9:45 - 10:30", "202", "", "TI")),
      "2026-10-12",
    );
    const cancelled = cancelledLessons(actual, permanent);
    expect(cancelled.get("2026-10-12")?.map((l) => l.subject)).toEqual(["MAT"]);
    expect(cancelled.has("2026-10-13")).toBe(false);
  });
});

describe("parseGrades", () => {
  const html = `<div class="classification-container">
    <div class="panel panel-default classification"><div class="panel-heading">Matematika</div>
      <table class="table"><tbody>
        <tr><th>Datum</th><th>Známka</th><th>Typ</th><th>Váha</th><th>Název</th><th>Komentář</th></tr>
        <tr><td>9.9.</td><td>3-</td><td title="Písemné zkoušení">p</td><td>5</td><td class="name">Test</td><td class="comment"></td></tr>
        <tr><td>6.10.</td><td>x</td><td title="Domácí úkol">d</td><td>2</td><td class="name">Úkol</td><td class="comment">neodevzdáno</td></tr>
        <tr><td>Dnes</td><td>3.50</td><td>!</td><td></td><td class="name">Vypočtená známka</td><td class="comment">Tato známka má pouze informativní charakter!</td></tr>
      </tbody></table></div></div>`;

  it("reads subjects, grades and the computed average", () => {
    const [s] = parseGrades(html);
    expect(s.name).toBe("Matematika");
    expect(s.average).toBe(3.5);
    expect(s.grades).toHaveLength(2);
    expect(s.grades[0]).toMatchObject({ date: "9.9.", value: "3-", numeric: 3.5, type: "p", typeName: "Písemné zkoušení", weight: 5 });
    expect(s.grades[1]).toMatchObject({ value: "x", numeric: null, comment: "neodevzdáno" });
  });
});

describe("helpers", () => {
  it("parses grade values", () => {
    expect(gradeToNumber("1")).toBe(1);
    expect(gradeToNumber("4-")).toBe(4.5);
    expect(gradeToNumber("N")).toBeNull();
  });
  it("extracts user id from page script", () => {
    expect(extractUserId(`data: { date: d, userid:12345}`)).toBe("12345");
  });
  it("detects the login page", () => {
    expect(isLoginPage(`<form action="/Account/Login"><input name="Password" type="password" />`)).toBe(true);
    expect(isLoginPage(`<div class="timetable"></div>`)).toBe(false);
  });
});
