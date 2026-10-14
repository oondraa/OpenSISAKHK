import type { ReactNode, Ref } from "react";
import { api, cache, type CanteenResponse, type GradesResponse, type ProjectsResponse, type TimetableResponse } from "./api";
import type { Absence } from "../worker/parse";
import { PERIODS, type Cell } from "../shared/schoolOcr";
import { startDay } from "./Timetable";
import { tone } from "./Grades";
import { LIMIT } from "./absencePlan";
import { WEEKDAYS, addDays, inMinutes, minutes, mondayOf, nowMinutesPrague, todayPrague, weekdayIndex } from "./time";
import { useCachedFetch } from "./useFetch";
import { useNow } from "./hooks";
import { AbsenceIcon, CalendarIcon, DumbbellIcon, GearIcon, GradesIcon, ProjectIcon, UtensilsIcon, WalletIcon } from "./icons";
import { Chevron } from "./ui";

import { homeOrder, type Part, type Tab } from "./sections";

const MORNING = 7 * 60; // from 7:00 the home tile names the day's subjects, before that only its span
const plural = (n: number, one: string, few: string, many: string) => (n === 1 ? one : n > 1 && n < 5 ? few : many);

/**
 * The launcher: every part of the app is a tile on one full-screen grid, and each tile already answers the
 * question it stands for (what's on now, is the weight room free, the average, the absence), from the last
 * loaded data. Only the timetable is refreshed here; the rest refresh when opened.
 */
export function Home({ onOpen, onError, homeRef, inert }: {
  onOpen: (tab: Tab) => void;
  onError: (e: unknown) => boolean;
  homeRef: Ref<HTMLDivElement>;
  inert: boolean;
}) {
  const now = useNow();
  const today = todayPrague();
  const day = startDay(cache.get<TimetableResponse>(`timetable:${mondayOf(today)}`)?.days);
  const week = useCachedFetch<TimetableResponse>(`timetable:${mondayOf(day)}`, () => api.timetable(mondayOf(day)), onError);
  const me = cache.get<{ name: string | null }>("me");

  return (
    <div className="home" ref={homeRef} inert={inert}>
      <nav className="tiles" aria-label="Aplikace">
        <TimetableTile day={day} days={week.data?.week === mondayOf(day) ? week.data.days : undefined} now={now} loading={week.loading} onOpen={onOpen} />
        {homeOrder().map((part) => (
          <PartTile key={part} part={part} now={now} onOpen={onOpen} />
        ))}
        <button className="tile glass tile-settings tile-slim" data-tile="settings" data-lens="14" onClick={() => onOpen("settings")}>
          <span className="tile-icon">
            <GearIcon />
          </span>
          <span className="tile-body">
            <span className="tile-label">Nastavení</span>
            <span className="tile-sub">{me?.name ?? "Notifikace, Strava, vzhled"}</span>
          </span>
          <Chevron />
        </button>
      </nav>
    </div>
  );
}

function Tile({ id, label, icon, value, valueClass, sub, onOpen }: {
  id: Tab;
  label: string;
  icon: ReactNode;
  value?: string;
  valueClass?: string;
  sub?: string;
  onOpen: (tab: Tab) => void;
}) {
  return (
    <button className={`tile glass tile-${id}`} data-tile={id} data-lens="18" onClick={() => onOpen(id)}>
      <span className="tile-icon">{icon}</span>
      <span className="tile-body">
        {value && <span className={`tile-value ${valueClass ?? ""}`}>{value}</span>}
        <span className="tile-label">{label}</span>
        {sub && <span className="tile-sub">{sub}</span>}
      </span>
    </button>
  );
}

/** The tiles under the timetable, in the order picked in Settings. */
function PartTile({ part, now, onOpen }: { part: Part; now: number; onOpen: (tab: Tab) => void }) {
  if (part === "gym") return <GymTile now={now} onOpen={onOpen} />;
  if (part === "grades") return <GradesTile onOpen={onOpen} />;
  if (part === "absence") return <AbsenceTile onOpen={onOpen} />;
  if (part === "projects") return <ProjectsTile onOpen={onOpen} />;
  if (part === "lunch") return <LunchTile onOpen={onOpen} />;
  // No amounts on the home screen: whoever glances at the phone doesn't see what's owed.
  if (part === "finance") return <Tile id="finance" label="Finance" icon={<WalletIcon />} sub="Školné a fond" onOpen={onOpen} />;
  return null;
}

/** The next meal ordered in Strava: today's until lunch is over, then the next day's. */
function LunchTile({ onOpen }: { onOpen: (tab: Tab) => void }) {
  const data = cache.get<CanteenResponse>("canteen");
  const today = todayPrague();
  const late = nowMinutesPrague() >= 14 * 60;
  const day = data?.days.find((d) => (d.date > today || (d.date === today && !late)) && !d.closed);
  const meal = day?.meals.find((m) => m.ordered);
  const when = !day ? "" : day.date === today ? "Dnes" : day.date === addDays(today, 1) ? "Zítra" : WEEKDAYS[weekdayIndex(day.date)];
  return (
    <Tile
      id="lunch"
      label="Obědy"
      icon={<UtensilsIcon />}
      value={meal?.number ? String(meal.number) : day && !meal ? "Nic" : undefined}
      sub={!data?.connected ? "Objednávky ze Stravy" : meal ? `${when} · ${meal.name}` : day ? `${when} nemáš objednáno` : "Jídelníček"}
      onOpen={onOpen}
    />
  );
}

function TimetableTile({ day, days, now, loading, onOpen }: {
  day: string;
  days?: TimetableResponse["days"];
  now: number;
  loading: boolean;
  onOpen: (tab: Tab) => void;
}) {
  const today = todayPrague();
  const lessons = days?.find((d) => d.date === day)?.lessons ?? [];
  const live = day === today;
  const current = live ? lessons.find((l) => minutes(l.start) <= now && now < minutes(l.end)) : undefined;
  const next = lessons.find((l) => !live || minutes(l.start) > now);
  const dayName = day === addDays(today, 1) ? "Zítra" : WEEKDAYS[weekdayIndex(day)];

  let eyebrow = "";
  let title = "Rozvrh";
  let room = "";
  let meta = loading ? "Načítám…" : "Otevři rozvrh";
  let progress: number | null = null;
  // School's out for today, or it's the early morning before it: just how long the day is, subjects from 7:00.
  const overview = lessons.length > 0 && !current && (!live || now < MORNING);
  if (days && !lessons.length) {
    eyebrow = live ? "Dnes" : dayName;
    title = "Volno";
    meta = "Žádná hodina";
  } else if (overview) {
    eyebrow = live ? "Dnes" : dayName;
    const start = lessons.reduce((a, l) => (minutes(l.start) < minutes(a.start) ? l : a)).start;
    const end = lessons.reduce((a, l) => (minutes(l.end) > minutes(a.end) ? l : a)).end;
    title = `${start}–${end}`;
    meta = `${lessons.length} ${plural(lessons.length, "hodina", "hodiny", "hodin")}`;
  } else if (current) {
    eyebrow = `Teď · končí ${inMinutes(minutes(current.end) - now)}`;
    title = current.subject;
    room = current.room;
    meta = next ? `Pak ${next.subject} · ${next.room}` : "Poslední hodina";
    progress = (now - minutes(current.start)) / (minutes(current.end) - minutes(current.start));
  } else if (next && live) {
    eyebrow = `Další · ${inMinutes(minutes(next.start) - now)}`;
    title = next.subject;
    room = next.room;
    meta = `${next.start}–${next.end} · ${next.teacher}`;
  }

  return (
    <button className="tile glass tile-timetable" data-tile="timetable" onClick={() => onOpen("timetable")}>
      <span className="tile-top">
        <span className="tile-icon">
          <CalendarIcon />
        </span>
        <span className="tile-label">Rozvrh</span>
        {eyebrow && <span className="tile-eyebrow">{eyebrow}</span>}
      </span>
      <span className="tile-body">
        <span className="hero-main">
          <span className={`hero-subject ${overview ? "hero-span" : ""}`}>{title}</span>
          {room && <span className="hero-room">{room}</span>}
        </span>
        <span className="tile-sub">{meta}</span>
        {progress !== null && (
          <span className="hero-progress" aria-hidden="true">
            <span style={{ transform: `scaleX(${progress})` }} />
          </span>
        )}
      </span>
    </button>
  );
}

/** Weight room right now, from the last OCR of today's school timetable (the Gym screen keeps it fresh). */
function GymTile({ now, onOpen }: { now: number; onOpen: (tab: Tab) => void }) {
  const today = todayPrague();
  const week = cache.get<{ weeks: { start: string; end: string; week: number }[] }>("school-weeks")?.weeks.find((w) => w.start <= today && today <= w.end);
  const wd = weekdayIndex(today);
  const cells = week && wd < 5 ? cache.get<Cell[]>(`gym:${["PO", "UT", "ST", "CT", "PA"][wd]}${week.week}:${today}`) : null;
  let value: string | undefined;
  let cls = "";
  let sub = "Kdy je volno";
  if (cells) {
    const p = PERIODS.findIndex(([s, e]) => minutes(s) <= now && now < minutes(e));
    const lastEnd = minutes(PERIODS[Math.max(8, ...cells.map((c) => c.period))][1]);
    const busy = p >= 0 ? cells.filter((c) => c.period === p) : [];
    if (now >= lastEnd) {
      value = "Volno";
      cls = "free";
      sub = "Do konce dne";
    } else if (busy.length) {
      value = "Obsazeno";
      cls = "busy";
      sub = `${busy.map((c) => c.cls).join(", ")} · do ${PERIODS[p][1]}`;
    } else {
      value = "Volno";
      cls = "free";
      sub = "Právě teď";
    }
  }
  return <Tile id="gym" label="Posilovna" icon={<DumbbellIcon />} value={value} valueClass={cls} sub={sub} onOpen={onOpen} />;
}

function GradesTile({ onOpen }: { onOpen: (tab: Tab) => void }) {
  const subjects = cache.get<GradesResponse>("grades")?.subjects ?? [];
  const avgs = subjects.map((s) => s.average).filter((a): a is number => a !== null);
  const overall = avgs.length ? avgs.reduce((a, b) => a + b, 0) / avgs.length : null;
  const count = subjects.reduce((n, s) => n + s.grades.length, 0);
  return (
    <Tile
      id="grades"
      label="Známky"
      icon={<GradesIcon />}
      value={overall !== null ? overall.toFixed(2).replace(".", ",") : undefined}
      valueClass={tone(overall)}
      sub={overall !== null ? `Průměr · ${count} ${plural(count, "známka", "známky", "známek")}` : "Průměr a nové známky"}
      onOpen={onOpen}
    />
  );
}

function AbsenceTile({ onOpen }: { onOpen: (tab: Tab) => void }) {
  const data = cache.get<Absence>("absence");
  const over = (data?.subjects ?? []).filter((s) => s.percent >= LIMIT).map((s) => s.subject);
  const total = data?.total;
  const level = !total ? "" : over.length ? "high" : total.percent >= LIMIT - 10 ? "mid" : "low";
  return (
    <Tile
      id="absence"
      label="Absence"
      icon={<AbsenceIcon />}
      value={total ? `${total.percent.toLocaleString("cs-CZ")} %` : undefined}
      valueClass={`absence-${level}`}
      sub={over.length ? `Přes ${LIMIT} %: ${over.join(", ")}` : total ? `${total.missed} ${plural(total.missed, "hodina", "hodiny", "hodin")} celkem` : "Hranice 30 %"}
      onOpen={onOpen}
    />
  );
}

/** The approved project of the newest election (or the first choice while it waits), from the last load. */
function ProjectsTile({ onOpen }: { onOpen: (tab: Tab) => void }) {
  const data = cache.get<ProjectsResponse>("projects:latest");
  const mine = data?.mine.find((c) => c.approved) ?? data?.mine[0];
  return (
    <Tile
      id="projects"
      label="Projekty"
      icon={<ProjectIcon />}
      value={mine?.number}
      sub={mine?.name ?? "Projektové dny"}
      onOpen={onOpen}
    />
  );
}
