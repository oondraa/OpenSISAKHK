import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import jpeg from "jpeg-js";
import { readSchoolDay } from "../src/shared/schoolOcr";

// Real images from https://sis.ssakhk.cz/TimeTable/School are not committed (they name teachers).
// Download them into test/fixtures/ to run this: curl -o test/fixtures/PO1.jpg https://sis.ssakhk.cz/TimeTable/PO1.jpg
const fixture = "test/fixtures/PO1-2026-10-12.jpg";

describe.skipIf(!existsSync(fixture))("readSchoolDay (Monday 12.10.2026)", () => {
  const cells = existsSync(fixture) ? readSchoolDay(jpeg.decode(readFileSync(fixture))) : [];
  const at = (cls: string, period: number, slot = 0) => cells.find((c) => c.cls === cls && c.period === period && c.slot === slot);

  it("reads gym lessons", () => {
    expect(at("G1", 1)).toMatchObject({ subject: "TEV", teacher: "MH", room: "T0", changed: false });
    expect(at("T1", 3)).toMatchObject({ subject: "TEV", teacher: "MH", room: "P0" });
    expect(at("G4", 5)).toMatchObject({ subject: "TEV", teacher: "RO", room: "P0" });
  });

  it("reads group lessons and changes", () => {
    expect(at("T1", 5, 1)).toMatchObject({ subject: "AGJ2", teacher: "KT", room: "403" });
    expect(at("T2", 4)).toMatchObject({ subject: "ELZ", teacher: "HU", room: "B13", changed: true });
    expect(at("G1", 3)).toMatchObject({ subject: "CJL", room: "206", changed: true });
  });

  it("only sees gym lessons in T0 and P0", () => {
    const gym = cells.filter((c) => c.room === "T0" || c.room === "P0");
    expect(gym.length).toBeGreaterThan(5);
    expect(gym.every((c) => c.subject.startsWith("TEV"))).toBe(true);
  });
});
