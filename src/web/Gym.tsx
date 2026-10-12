import { useState } from "react";
import { api } from "./api";
import { PERIODS, readSchoolDay, type Cell } from "../shared/schoolOcr";
import { DayStrip, Empty, LargeHeader, SkeletonList } from "./ui";
import { defaultSchoolDay, inMinutes, longDate, minutes, mondayOf, todayPrague, weekdayIndex } from "./time";
import { useCachedFetch } from "./useFetch";
import { useNow, useScrollToFocus } from "./hooks";
import { AlertIcon, CalendarOffIcon, DumbbellIcon } from "./icons";

// Weight room. (T0 is the sports hall; nobody asked for it, but readSchoolDay sees it too.)
const ROOM = "P0";
const DAY_CODES = ["PO", "UT", "ST", "CT", "PA"];

async function readGym(file: string): Promise<Cell[]> {
  const blob = await (await fetch(`/api/school/${file}.jpg`)).blob();
  const bmp = await createImageBitmap(blob);
  const canvas = new OffscreenCanvas(bmp.width, bmp.height);
  const ctx = canvas.getContext("2d", { willReadFrequently: true })!;
  ctx.drawImage(bmp, 0, 0);
  return readSchoolDay(ctx.getImageData(0, 0, bmp.width, bmp.height)).filter((c) => c.room === ROOM);
}

export function Gym({ onError }: { onError: (e: unknown) => boolean }) {
  const [selected, setSelected] = useState(defaultSchoolDay);
  const weeks = useCachedFetch("school-weeks", api.schoolWeeks, onError);
  const week = weeks.data?.weeks.find((w) => w.start <= selected && selected <= w.end);
  const file = week ? `${DAY_CODES[weekdayIndex(selected)]}${week.week}` : null;
  // Keyed by date too: the same file name shows a different day once the week rolls over.
  const gym = useCachedFetch<Cell[]>(file ? `gym:${file}:${selected}` : null, () => (file ? readGym(file) : Promise.resolve([])), onError);

  const now = useNow();

  const live = selected === todayPrague();
  const cells = gym.data ?? [];
  const at = (p: number) => cells.filter((c) => c.period === p);
  const lastPeriod = Math.max(8, ...cells.map((c) => c.period));
  const periods = PERIODS.slice(0, lastPeriod + 1).map(([start, end], p) => ({ p, start, end, occ: at(p) }));
  // Today: jump to the period that's on now (or next), earlier ones stay above.
  const focus = live && gym.data ? periods.findIndex((x) => now < minutes(x.end)) : -1;
  const focusRef = useScrollToFocus(focus > 0);

  return (
    <div className="screen">
      <LargeHeader title="Posilovna" subtitle={longDate(selected)} loading={weeks.loading || gym.loading}>
        <DayStrip monday={mondayOf(selected)} selected={selected} onSelect={setSelected} />
      </LargeHeader>
      {week && !gym.error && (
        <div className="pinned">
          {gym.data ? <StatusCard live={live} now={now} periods={periods} loaded /> : <SkeletonList count={1} lines={2} side tall />}
        </div>
      )}
      <div className="scroll">
        {weeks.data && !week ? (
          <Empty icon={<CalendarOffIcon size={40} />} title="Mimo rozsah" text="Škola zveřejňuje rozvrh jen na tento a příští týden." />
        ) : gym.error ? (
          <Empty icon={<AlertIcon size={40} />} title="Rozvrh školy se nepodařilo načíst" />
        ) : (
          <>
            {!gym.data ? (
              <SkeletonList count={9} lines={1} />
            ) : (
            <div className="list gym-list">
              {periods.map(({ p, start, end, occ }) => (
                <div key={p} ref={p === focus ? (el) => void (focusRef.current = el) : undefined} className={`row gym-row ${live && minutes(start) <= now && now < minutes(end) ? "current" : ""} ${live && minutes(end) <= now ? "past" : ""}`}>
                  <span className="gym-period">
                    <b>{p}.</b>
                    <span>
                      {start}–{end}
                    </span>
                  </span>
                  {occ.length ? (
                    <span className={`pill-status busy ${occ.some((o) => o.changed) ? "changed" : ""}`}>
                      {occ.map((o) => o.cls).join(", ")}
                      <small>{occ.map((o) => o.teacher).join(", ")}</small>
                    </span>
                  ) : (
                    <span className="pill-status free">Volno</span>
                  )}
                </div>
              ))}
            </div>
            )}
            <p className="list-footer">Podle veřejného rozvrhu školy. Kroužky a akce mimo rozvrh tu nejsou.</p>
          </>
        )}
      </div>
    </div>
  );
}

function StatusCard({ live, now, periods, loaded }: {
  live: boolean;
  now: number;
  periods: { p: number; start: string; end: string; occ: Cell[] }[];
  loaded: boolean;
}) {
  if (!loaded) return null;
  const busy = periods.filter((x) => x.occ.length);
  let state: "busy" | "free" | "idle" = "idle";
  let title = busy.length ? `Obsazeno ${busy.length}× za den` : "Celý den volno";
  let detail = busy.length ? busy.map((x) => `${x.p}. ${x.occ.map((o) => o.cls).join("+")}`).join(" · ") : "Rozvrh tu nemá žádnou hodinu.";

  if (live) {
    const cur = periods.find((x) => minutes(x.start) <= now && now < minutes(x.end));
    const nextBusy = busy.find((x) => minutes(x.start) > now);
    if (cur?.occ.length) {
      state = "busy";
      title = "Teď obsazeno";
      detail = `${cur.occ.map((o) => `${o.cls} · ${o.teacher}`).join(", ")} · volno ${inMinutes(minutes(cur.end) - now)}`;
    } else {
      state = "free";
      title = "Teď volno";
      detail = nextBusy ? `Obsadí ${nextBusy.occ.map((o) => o.cls).join("+")} ${inMinutes(minutes(nextBusy.start) - now)}` : "Dnes už tam žádná hodina není.";
    }
  }

  return (
    <div className={`gym-status stagger ${state}`}>
      <span className="gym-status-icon">
        <DumbbellIcon size={28} />
      </span>
      <div>
        <p className="gym-status-title">{title}</p>
        <p className="gym-status-detail">{detail}</p>
      </div>
    </div>
  );
}
