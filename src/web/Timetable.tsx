import { useState } from "react";
import { api, type TimetableDay, type TimetableResponse } from "./api";
import type { Lesson } from "../worker/parse";
import { DayPager, DayStrip, Empty, LargeHeader, SkeletonList } from "./ui";
import { addDays, defaultSchoolDay, inMinutes, longDate, minutes, mondayOf, todayPrague, weekdayIndex } from "./time";
import { useCachedFetch } from "./useFetch";
import { useNow, useScrollToFocus } from "./hooks";
import { CalendarOffIcon, SunIcon, UtensilsIcon } from "./icons";

export function Timetable({ onError }: { onError: (e: unknown) => boolean }) {
  const [selected, setSelected] = useState(defaultSchoolDay);
  const monday = mondayOf(selected);
  const today = todayPrague();
  const now = useNow();
  const { data, loading, error } = useCachedFetch<TimetableResponse>(`timetable:${monday}`, () => api.timetable(monday), onError);
  const days = data?.week === monday ? data.days : [];

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
          if (day) return <DayView key={day.date} day={day} live={day.date === today} now={now} />;
          return loading ? <SkeletonList key={i} count={7} side /> : <Empty key={i} icon={<CalendarOffIcon size={40} />} title="Žádná data" />;
        })}
      </DayPager>
    </div>
  );
}

type Item = { kind: "lesson"; lesson: Lesson; start: string; end: string } | { kind: "lunch"; start: string; end: string };

function DayView({ day, live, now }: { day: TimetableDay; live: boolean; now: number }) {
  const items: Item[] = day.lessons.map((lesson) => ({ kind: "lesson" as const, lesson, start: lesson.start, end: lesson.end }));
  if (day.lunch) items.push({ kind: "lunch", ...day.lunch });
  items.sort((a, b) => minutes(a.start) - minutes(b.start));

  // First item that hasn't ended yet = what's on now, or next.
  const focus = live ? items.findIndex((it) => now < minutes(it.end)) : -1;
  const focusRef = useScrollToFocus(focus > 0);

  if (!day.lessons.length) return <Empty icon={<SunIcon size={40} />} title="Volno" text="Tenhle den nemáš žádnou hodinu." />;

  const state = (it: Item) => (!live ? "future" : now >= minutes(it.end) ? "past" : now >= minutes(it.start) ? "now" : "future");

  return (
    <ol className="timeline">
      {items.map((it, i) => {
        const ref = i === focus ? (el: HTMLLIElement | null) => void (focusRef.current = el) : undefined;
        return it.kind === "lunch" ? (
          <li key={`lunch-${i}`} ref={ref} className="stagger" style={{ "--i": i } as React.CSSProperties}>
            <div className={`lesson-card lunch ${state(it)}`}>
              <div className="lesson-time">
                <span>{it.start}</span>
                <span>{it.end}</span>
              </div>
              <div className="lesson-main">
                <div className="lesson-subject">Oběd</div>
                <div className="lesson-teacher">Jídelna</div>
              </div>
              <div className="lunch-icon">
                <UtensilsIcon size={20} />
              </div>
            </div>
          </li>
        ) : (
          <LessonCard key={`${it.lesson.id}-${i}`} liRef={ref} index={i} lesson={it.lesson} state={state(it)} />
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

function LessonCard({ lesson: l, state, index, liRef }: {
  lesson: Lesson;
  state: "past" | "now" | "future";
  index: number;
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
    <li ref={liRef} className="stagger" style={{ "--i": index } as React.CSSProperties}>
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
