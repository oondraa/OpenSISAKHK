import { describe as group, expect, it } from "vitest";
import { describe, diffDay, relevantChanges } from "../src/shared/diff";
import type { Cell } from "../src/shared/schoolOcr";

const cell = (cls: string, period: number, slot: number, subject: string, teacher = "XX", room = "100"): Cell => ({
  cls, period, slot, subject, teacher, room, changed: false,
});

group("diffDay", () => {
  it("finds added, removed and changed lessons in period order", () => {
    const before = [cell("T3", 1, 0, "MAT", "KR", "302"), cell("T3", 3, 0, "FYZ"), cell("T3", 4, 0, "ELZ", "HU", "211")];
    const after = [cell("T3", 1, 0, "MAT", "KR", "302"), cell("T3", 2, 0, "CJL"), cell("T3", 4, 0, "ELZ", "HU", "B13")];
    expect(diffDay(before, after).map(describe)).toEqual(["2. nově CJL XX 100", "3. FYZ odpadá", "4. ELZ: učebna 211 → B13"]);
  });

  it("ignores identical days", () => {
    const day = [cell("G1", 1, 0, "TEVt", "MH", "T0")];
    expect(diffDay(day, [...day])).toEqual([]);
  });

  it("describes a replaced subject and a new teacher", () => {
    expect(describe({ kind: "changed", before: cell("T3", 3, 0, "MAT", "KR", "302"), after: cell("T3", 3, 0, "FYZ", "LA", "211") })).toBe("3. MAT → FYZ LA 211");
    expect(describe({ kind: "changed", before: cell("T3", 3, 0, "MAT", "KR", "302"), after: cell("T3", 3, 0, "MAT", "TI", "302") })).toBe("3. MAT: učí TI");
  });
});

group("relevantChanges", () => {
  const who = { cls: "T3", subjects: ["MAT", "AGJ", "AGJ1", "PRG", "PRGr"] };

  it("keeps only the student's class", () => {
    const changes = diffDay([cell("T3", 1, 0, "MAT"), cell("G1", 1, 0, "MAT")], []);
    expect(relevantChanges(changes, who, []).map((c) => c.kind === "removed" && c.cell.cls)).toEqual(["T3"]);
  });

  it("skips numbered groups the student is not in", () => {
    const changes = diffDay([cell("T3", 2, 0, "AGJ1"), cell("T3", 2, 1, "AGJ2")], []);
    expect(relevantChanges(changes, who, []).map(describe)).toEqual(["2. AGJ1 odpadá"]);
  });

  it("matches lowercase-suffixed subjects by their base", () => {
    const changes = diffDay([cell("T3", 5, 0, "PRGs")], [cell("T3", 5, 0, "PRGs", "XX", "214")]);
    expect(relevantChanges(changes, who, [])).toHaveLength(1);
  });

  it("notifies about a new whole-class lesson even in an unknown subject", () => {
    const after = [cell("T3", 6, 0, "ZEL")];
    expect(relevantChanges(diffDay([], after), who, after)).toHaveLength(1);
  });

  it("notifies everyone when the student's subjects are unknown", () => {
    expect(relevantChanges(diffDay([cell("T3", 2, 1, "AGJ2")], []), { cls: "T3", subjects: [] }, [])).toHaveLength(1);
  });
});

group("relevantChanges with blocks", () => {
  it("notifies about a block the student attends but not other blocks", () => {
    const who = { cls: "T2", subjects: ["ELZ", "B06"] };
    const changes = diffDay([cell("T2", 8, 0, "B06"), cell("T2", 6, 0, "B09")], []);
    expect(relevantChanges(changes, who, []).map(describe)).toEqual(["8. B06 odpadá"]);
  });
});
