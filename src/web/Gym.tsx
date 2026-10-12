import { useEffect, useState } from "react";
import { api } from "./api";
import { PERIODS, readSchoolDay, type Cell } from "../shared/schoolOcr";
import { DayStrip, Empty, LargeHeader } from "./ui";
import { defaultSchoolDay, longDate, minutes, mondayOf, nowMinutesPrague, todayPrague, weekdayIndex } from "./time";
import { useCachedFetch } from "./useFetch";

const ROOMS = [
  { code: "T0", name: "Tělocvična", icon: "🏐" },
  { code: "P0", name: "Posilovna", icon: "🏋️" },
] as const;

const DAY_CODES = ["PO", "UT", "ST", "CT", "PA"];

async function readImage(file: string): Promise<Cell[]> {
  const blob = await (await fetch(`/api/school/${file}.jpg`)).blob();
  const bmp = await createImageBitmap(blob);
  const canvas = new OffscreenCanvas(bmp.width, bmp.height);
  const ctx = canvas.getContext("2d", { willReadFrequently: true })!;
  ctx.drawImage(bmp, 0, 0);
  const img = ctx.getImageData(0, 0, bmp.width, bmp.height);
  return readSchoolDay(img).filter((c) => ROOMS.some((r) => r.code === c.room));
}

export function Gym({ onError }: { onError: (e: unknown) => boolean }) {
  const [selected, setSelected] = useState(defaultSchoolDay);
  const weeks = useCachedFetch("school-weeks", api.schoolWeeks, onError);
  const week = weeks.data?.weeks.find((w) => w.start <= selected && selected <= w.end);
  const file = week ? `${DAY_CODES[weekdayIndex(selected)]}${week.week}` : null;

  // Keyed by date as well: the same file name means a different day once the week rolls over.
  const cells = useCachedFetch<Cell[]>(file ? `gym:${file}:${selected}` : null, () => (file ? readImage(file) : Promise.resolve([])), onError);

  const [now, setNow] = useState(nowMinutesPrague);
  useEffect(() => {
    const t = setInterval(() => setNow(nowMinutesPrague()), 30_000);
    return () => clearInterval(t);
  }, []);
  const live = selected === todayPrague();

  const occupants = (room: string, period: number) => (cells.data ?? []).filter((c) => c.room === room && c.period === period);
  const currentPeriod = live ? PERIODS.findIndex(([s, e]) => minutes(s) <= now && now < minutes(e)) : -1;
  const lastPeriod = Math.max(6, ...(cells.data ?? []).map((c) => c.period));

  return (
    <div className="screen">
      <LargeHeader title="Gym" subtitle={longDate(selected)} loading={weeks.loading || cells.loading}>
        <DayStrip monday={mondayOf(selected)} selected={selected} onSelect={setSelected} />
      </LargeHeader>
      <div className="scroll">
        {weeks.data && !week ? (
          <Empty icon="🗓️" title="Mimo rozsah" text="Škola zveřejňuje obsazenost jen na tento a příští týden." />
        ) : cells.error ? (
          <Empty icon="⚠️" title="Rozvrh školy se nepodařilo načíst" />
        ) : (
          <>
            <div className="room-cards">
              {ROOMS.map((r) => {
                const busyPeriods = new Set((cells.data ?? []).filter((c) => c.room === r.code).map((c) => c.period)).size;
                const occ = currentPeriod >= 0 ? occupants(r.code, currentPeriod) : [];
                // Today during lessons: live state. Otherwise: how busy the room is that day.
                const state = currentPeriod >= 0 ? (occ.length ? "busy" : "free") : "idle";
                return (
                  <div key={r.code} className={`room-card ${state}`}>
                    <span className="room-icon" aria-hidden="true">{r.icon}</span>
                    <span className="room-name">{r.name}</span>
                    <span className="room-state">
                      {state === "busy" ? "Obsazeno" : state === "free" ? "Teď volno" : busyPeriods ? `${busyPeriods} ${busyPeriods === 1 ? "hodina" : busyPeriods < 5 ? "hodiny" : "hodin"} obsazeno` : "Celý den volno"}
                    </span>
                    {occ.length > 0 && <span className="room-who">{occ.map((o) => `${o.cls} · ${o.teacher}`).join(", ")}</span>}
                  </div>
                );
              })}
            </div>

            <div className="gym-table" role="table" aria-label="Obsazenost po hodinách">
              <div className="gym-row gym-head" role="row">
                <span role="columnheader">Hodina</span>
                {ROOMS.map((r) => (
                  <span key={r.code} role="columnheader">{r.name}</span>
                ))}
              </div>
              {PERIODS.slice(0, lastPeriod + 1).map(([s, e], p) => (
                <div key={p} role="row" className={`gym-row ${p === currentPeriod ? "current" : ""} ${live && minutes(e) <= now ? "past" : ""}`}>
                  <span className="gym-period" role="cell">
                    <b>{p}.</b> {s}
                  </span>
                  {ROOMS.map((r) => {
                    const occ = occupants(r.code, p);
                    return (
                      <span key={r.code} role="cell" className={`gym-slot ${occ.length ? "busy" : "free"} ${occ.some((o) => o.changed) ? "changed" : ""}`}>
                        {occ.length ? (
                          occ.map((o, i) => (
                            <span key={i} className="gym-occ">
                              <b>{o.cls}</b> {o.teacher}
                            </span>
                          ))
                        ) : (
                          <span className="gym-free">Volno</span>
                        )}
                      </span>
                    );
                  })}
                </div>
              ))}
            </div>
            <p className="list-footer">Data se čtou z veřejného rozvrhu školy. Mimo vyučování (večer, o přestávkách) rozvrh obsazenost nezná.</p>
          </>
        )}
      </div>
    </div>
  );
}
