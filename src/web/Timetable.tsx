import { useEffect, useRef, useState } from "react";
import { api, cache, type LunchResponse, type TimetableDay, type TimetableResponse } from "./api";
import type { Lesson } from "../worker/parse";
import type { LunchDay } from "../worker/strava";
import { DayPager, DayStrip, Empty, LargeHeader, SkeletonList } from "./ui";
import { WEEKDAYS, addDays, defaultSchoolDay, inMinutes, longDate, minutes, mondayOf, nextSchoolDay, nowMinutesPrague, todayPrague, weekdayIndex } from "./time";
import { useCachedFetch } from "./useFetch";
import { useDesktop, useNow, useScrollToFocus } from "./hooks";
import { CalendarOffIcon, SunIcon, UtensilsIcon } from "./icons";

/** Today until its last lesson is over, then the next school day: nobody opens the app to see what already was. */
export function startDay(days: TimetableDay[] | undefined) {
  const today = todayPrague();
  const day = defaultSchoolDay();
  const lessons = days?.find((d) => d.date === today)?.lessons ?? [];
  if (day !== today || !lessons.length) return day;
  return nowMinutesPrague() >= Math.max(...lessons.map((l) => minutes(l.end))) ? nextSchoolDay(today) : today;
}

export function Timetable({ onError }: { onError: (e: unknown) => boolean }) {
  const [selected, setSelectedState] = useState(() => startDay(cache.get<TimetableResponse>(`timetable:${mondayOf(todayPrague())}`)?.days));
  const touched = useRef(false);
  const setSelected = (iso: string) => {
    touched.current = true;
    setSelectedState(iso);
  };
  const monday = mondayOf(selected);
  const today = todayPrague();
  const now = useNow();
  const { data, loading, error } = useCachedFetch<TimetableResponse>(`timetable:${monday}`, () => api.timetable(monday), onError);
  const days = data?.week === monday ? data.days : [];
  // Canteen orders (Strava), when connected in Settings. A failure just leaves the plain lunch card.
  const lunch = useCachedFetch<LunchResponse>("lunch", api.lunch, onError);
  // Strava lists days from today ~3 weeks ahead; outside that range there's nothing to say.
  const lunchFor = (date: string) => {
    const ds = lunch.data?.connected ? lunch.data.days : [];
    if (!ds.length || date < ds[0].date || date > ds[ds.length - 1].date) return undefined;
    return ds.find((d) => d.date === date) ?? { date, ordered: null };
  };

  // Without a cached timetable the first guess is today; correct it once this week's lessons arrive.
  useEffect(() => {
    if (touched.current || data?.week !== mondayOf(today)) return;
    const day = startDay(data.days);
    if (day !== selected) setSelectedState(day);
  }, [data]);

  const todayDay = selected === today ? days.find((d) => d.date === today) : undefined;
  const current = todayDay?.lessons.find((l) => minutes(l.start) <= now && now < minutes(l.end));
  const next = todayDay?.lessons.find((l) => minutes(l.start) > now);
  const desktop = useDesktop();
  const shown = useRef<{ current?: Lesson; next?: Lesson } | null>(null);
  if (current || next) shown.current = { current, next };

  return (
    <div className="screen">
      <LargeHeader title="Rozvrh" subtitle={longDate(selected)} loading={loading}>
        <DayStrip monday={monday} selected={selected} onSelect={setSelected} />
      </LargeHeader>
      {error && <p className="banner">Rozvrh se nepodařilo obnovit. Zobrazuji poslední uložený.</p>}
      {/* Stays mounted and collapses (or grows back) while the days slide, instead of vanishing on the first frame
          and yanking the list up mid-glide. While collapsing it keeps showing the last lesson it had. */}
      <div className="collapse now-collapse" data-open={!!(current || next)} aria-hidden={!(current || next)}>
        <div className="collapse-inner">{shown.current && <NowCard {...shown.current} now={now} />}</div>
      </div>
      {desktop ? (
        // A computer has room for the whole week side by side; the day strip still moves weeks and marks a day.
        <div className="scroll">
          <div className="week-grid">
            {[0, 1, 2, 3, 4].map((i) => {
              const date = addDays(monday, i);
              const day = days.find((d) => d.date === date);
              return (
                <section key={date} className={`week-day ${date === selected ? "selected" : ""} ${date === today ? "today" : ""}`}>
                  <button className="week-day-head" onClick={() => setSelected(date)}>
                    {WEEKDAYS[i]} <span>{Number(date.slice(8))}. {Number(date.slice(5, 7))}.</span>
                  </button>
                  {day ? (
                    <DayView day={day} live={date === today} now={now} meal={lunchFor(date)} scroll={false} />
                  ) : loading ? (
                    <SkeletonList count={6} />
                  ) : (
                    <Empty icon={<CalendarOffIcon size={32} />} title="Žádná data" />
                  )}
                </section>
              );
            })}
          </div>
        </div>
      ) : (
        <DayPager index={weekdayIndex(selected)} onIndex={(i) => setSelected(addDays(monday, i))}>
          {[0, 1, 2, 3, 4].map((i) => {
            const day = days.find((d) => d.date === addDays(monday, i));
            if (day) return <DayView key={day.date} day={day} live={day.date === today} now={now} meal={lunchFor(day.date)} />;
            return loading ? <SkeletonList key={i} count={7} side /> : <Empty key={i} icon={<CalendarOffIcon size={40} />} title="Žádná data" />;
          })}
        </DayPager>
      )}
    </div>
  );
}

type Item =
  | { kind: "lesson"; lesson: Lesson; start: string; end: string }
  | { kind: "lunch"; start: string; end: string }
  | { kind: "free"; start: string; end: string };

const LUNCH_MIN = 30; // eating takes about this long; SIS marks the whole window the canteen serves in
const FREE_MIN = 20; // a gap at least this long gets its own row; shorter ones are ordinary breaks
const hhmm = (m: number) => `${Math.floor(m / 60)}:${String(m % 60).padStart(2, "0")}`;
const span = (m: number) => (m >= 60 ? `${Math.floor(m / 60)} h${m % 60 ? ` ${m % 60} min` : ""}` : `${m} min`);

/**
 * Lessons with what's between them: a long gap shows as free time, and lunch takes only its ~30 minutes of it
 * (at the start of the gap, or when the canteen opens), so a two-hour break doesn't hide behind the lunch card.
 */
export function dayItems(day: TimetableDay): Item[] {
  const lessons = [...day.lessons].sort((a, b) => minutes(a.start) - minutes(b.start));
  const items: Item[] = [];
  const lunch = day.lunch && { start: minutes(day.lunch.start), end: minutes(day.lunch.end) };
  let lunchPlaced = false;
  const free = (a: number, b: number) => b - a >= FREE_MIN && items.push({ kind: "free", start: hhmm(a), end: hhmm(b) });
  lessons.forEach((lesson, i) => {
    items.push({ kind: "lesson", lesson, start: lesson.start, end: lesson.end });
    const next = lessons[i + 1];
    if (!next) return;
    const g0 = minutes(lesson.end);
    const g1 = minutes(next.start);
    if (lunch && !lunchPlaced && lunch.start < g1 && lunch.end > g0 && g1 - Math.max(g0, lunch.start) >= 15) {
      const ls = Math.max(g0, lunch.start);
      const le = Math.min(ls + LUNCH_MIN, g1);
      free(g0, ls);
      items.push({ kind: "lunch", start: hhmm(ls), end: hhmm(le) });
      free(le, g1);
      lunchPlaced = true;
    } else free(g0, g1);
  });
  // Lunch outside the lessons (after the last one, or no gap fits it): where SIS puts it, ~30 minutes long.
  if (lunch && !lunchPlaced) {
    items.push({ kind: "lunch", start: hhmm(lunch.start), end: hhmm(Math.min(lunch.start + LUNCH_MIN, lunch.end)) });
    items.sort((a, b) => minutes(a.start) - minutes(b.start));
  }
  return items;
}

/** `scroll`: today's list jumps to what's on now. Off in the week view, where it would shift the other days too. */
function DayView({ day, live, now, meal, scroll = true }: { day: TimetableDay; live: boolean; now: number; meal?: LunchDay; scroll?: boolean }) {
  const items = dayItems(day);

  // First item that hasn't ended yet = what's on now, or next.
  const focus = live ? items.findIndex((it) => now < minutes(it.end)) : -1;
  const focusRef = useScrollToFocus(scroll && focus > 0);

  if (!day.lessons.length) return <Empty icon={<SunIcon size={40} />} title="Volno" text="Tenhle den nemáš žádnou hodinu." />;

  const state = (it: Item) => (!live ? "future" : now >= minutes(it.end) ? "past" : now >= minutes(it.start) ? "now" : "future");

  return (
    <ol className="timeline stagger">
      {items.map((it, i) => {
        const ref = i === focus ? (el: HTMLLIElement | null) => void (focusRef.current = el) : undefined;
        if (it.kind === "free")
          return (
            <li key={`free-${it.start}`} ref={ref}>
              <div className={`free-slot ${state(it)}`}>
                <span className="free-line" aria-hidden="true" />
                <span className="free-text">
                  {state(it) === "now" ? `Volno ještě ${span(Math.max(1, Math.ceil(minutes(it.end) - now)))}` : `Volno ${span(minutes(it.end) - minutes(it.start))}`}
                  <small>
                    {it.start}–{it.end}
                  </small>
                </span>
                <span className="free-line" aria-hidden="true" />
              </div>
            </li>
          );
        return it.kind === "lunch" ? (
          <li key="lunch" ref={ref}>
            <div className={`lesson-card lunch ${state(it)}`}>
              <div className="lesson-time">
                <span>{it.start}</span>
                <span>{it.end}</span>
              </div>
              <div className="lesson-main">
                <div className="lesson-subject">{meal?.ordered?.label ?? "Oběd"}</div>
                <div className="lesson-teacher">{meal ? (meal.ordered?.name ?? "Nemáš objednáno") : "Jídelna"}</div>
              </div>
              <div className={`lunch-icon ${meal && !meal.ordered ? "none" : ""}`}>
                {meal?.ordered?.number ? <span className="lunch-number">{meal.ordered.number}</span> : <UtensilsIcon size={20} />}
              </div>
            </div>
          </li>
        ) : (
          // Keyed by content, not SIS's debug id: a refresh must update cards in place, not remount them.
          <LessonCard key={`${it.start}-${it.lesson.subject}-${it.lesson.group}`} liRef={ref} lesson={it.lesson} state={state(it)} />
        );
      })}
    </ol>
  );
}

function NowCard({ current, next, now }: { current?: Lesson; next?: Lesson; now: number }) {
  const l = current ?? next!;
  const progress = current ? (now - minutes(current.start)) / (minutes(current.end) - minutes(current.start)) : 0;
  return (
    <div className="now-wrap">
      <div className="now-card stagger">
        <p className="eyebrow">{current ? `Teď · končí ${inMinutes(minutes(current.end) - now)}` : `Další · ${inMinutes(minutes(l.start) - now)}`}</p>
        <div className="now-main">
          <span className="now-subject">{l.subject}</span>
          <span className="now-room">{l.room}</span>
        </div>
        <p className="now-meta">
          {l.teacher} · {l.start}–{l.end}
          {current && next && ` · pak ${next.subject} v ${next.room}`}
        </p>
        {current && (
          <div className="now-progress" aria-hidden="true">
            <span style={{ transform: `scaleX(${progress})` }} />
          </div>
        )}
      </div>
    </div>
  );
}

function LessonCard({ lesson: l, state, liRef }: {
  lesson: Lesson;
  state: "past" | "now" | "future";
  liRef?: (el: HTMLLIElement | null) => void;
}) {
  const body = (
    <>
      <div className="lesson-time">
        <span>{l.start}</span>
        <span>{l.end}</span>
      </div>
      <div className="lesson-main">
        <div className="lesson-subject">
          {l.subject}
          {l.group && <span className="tag">sk. {l.group}</span>}
          {l.changed && <span className="tag tag-accent">změna</span>}
        </div>
        <div className="lesson-teacher">{l.teacher}</div>
      </div>
      <div className="lesson-room">{l.room}</div>
    </>
  );
  const cls = `lesson-card ${state} ${l.changed ? "changed" : ""}`;
  return (
    <li ref={liRef}>
      {l.meet ? (
        <a className={cls} href={l.meet} target="_blank" rel="noreferrer" aria-label={`${l.subject}, otevřít Meet`}>
          {body}
        </a>
      ) : (
        <div className={cls}>{body}</div>
      )}
    </li>
  );
}
