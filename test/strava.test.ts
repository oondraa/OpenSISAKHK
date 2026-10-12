import { describe, expect, it } from "vitest";
import { parseOrders } from "../src/worker/strava";

const item = (datum: string, druh: string, druh_popis: string, nazev: string, pocet: number) => ({ datum, druh, druh_popis, nazev, pocet });

describe("parseOrders", () => {
  it("finds the ordered meal of each day, ignoring the soup", () => {
    const days = parseOrders({
      table1: [
        item("13.10.2026", "PO", "Polévka", "Polévka z cizrny", 0),
        item("13.10.2026", "O1", "Oběd 1", "Masové tortellini", 0),
        item("13.10.2026", "O2", "Oběd 2", "Alpský  knedlík, koktejl", 1),
      ],
      table0: [item("12.10.2026", "PO", "Polévka", "Polévka", 0), item("12.10.2026", "O1", "Oběd 1", "Rizoto", 0)],
    });
    expect(days).toEqual([
      { date: "2026-10-12", ordered: null },
      { date: "2026-10-13", ordered: { label: "Oběd 2", number: 2, name: "Alpský knedlík, koktejl" } },
    ]);
  });
});
