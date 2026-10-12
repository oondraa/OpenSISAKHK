import { useEffect, useRef, useState } from "react";
import { api, cache, type LunchResponse, type TimetableDay, type TimetableResponse } from "./api";
import type { Lesson } from "../worker/parse";
import type { LunchDay } from "../worker/strava";
import { DayPager, DayStrip, Empty, LargeHeader, SkeletonList } from "./ui";
import { addDays, defaultSchoolDay, inMinutes, longDate, minutes, mondayOf, nextSchoolDay, nowMinutesPrague, todayPrague, weekdayIndex } from "./time";
import { useCachedFetch } from "./useFetch";
import { useNow, useScrollToFocus } from "./hooks";
import { CalendarOffIcon, SunIcon, UtensilsIcon } from "./icons";

/** Today until its last lesson is over, then the next school day: nobody opens the app to see what already was. */
function startDay(days: TimetableDay[] | undefined) {
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

  return (
    <div className="screen">
      <LargeHeader title="Rozvrh" subtitle={longDate(selected)} loading={loading}>
        <DayStrip monday={monday} selected={selected} onSelect={setSelected} />
      </LargeHeader>
      {error && <p className="banner">Rozvrh se nepodařilo obnovit. Zobrazuji poslední uložený.</p>}
      {(current || next) && <NowCard current={current} next={next} now={now} />}
      <DayPager index={weekdayIndex(selected)} onIndex={(i) => setSelected(addDays(monday, i))}>
        {[0, 1, 2, 3, 4].map((i) => {
          const day = days.find((d) => d.date === addDays(monday, i));
          if (day) return <DayView key={day.date} day={day} live={day.date === today} now={now} meal={lunchFor(day.date)} />;
          return loading ? <SkeletonList key={i} count={7} side /> : <Empty key={i} icon={<CalendarOffIcon size={40} />} title="Žádná data" />;
        })}
      </DayPager>
    </div>
  );
}

type Item = { kind: "lesson"; lesson: Lesson; start: string; end: string } | { kind: "lunch"; start: string; end: string };

function DayView({ day, live, now, meal }: { day: TimetableDay; live: boolean; now: number; meal?: LunchDay }) {
  const items: Item[] = day.lessons.map((lesson) => ({ kind: "lesson" as const, lesson, start: lesson.start, end: lesson.end }));
  if (day.lunch) items.push({ kind: "lunch", ...day.lunch });
  items.sort((a, b) => minutes(a.start) - minutes(b.start));

  // First item that hasn't ended yet = what's on now, or next.
  const focus = live ? items.findIndex((it) => now < minutes(it.end)) : -1;
  const focusRef = useScrollToFocus(focus > 0);

  if (!day.lessons.length) return <Empty icon={<SunIcon size={40} />} title="Volno" text="Tenhle den nemáš žádnou hodinu." />;

  const state = (it: Item) => (!live ? "future" : now >= minutes(it.end) ? "past" : now >= minutes(it.start) ? "now" : "future");

  return (
    <ol className="timeline stagger">
      {items.map((it, i) => {
        const ref = i === focus ? (el: HTMLLIElement | null) => void (focusRef.current = el) : undefined;
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
