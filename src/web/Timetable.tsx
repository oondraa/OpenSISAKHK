import { useEffect, useState } from "react";
import { api, type TimetableDay, type TimetableResponse } from "./api";
import type { Lesson } from "../worker/parse";
import { DayPager, DayStrip, Empty, LargeHeader } from "./ui";
import { addDays, defaultSchoolDay, inMinutes, longDate, minutes, mondayOf, nowMinutesPrague, todayPrague, weekdayIndex } from "./time";
import { useCachedFetch } from "./useFetch";

export function Timetable({ onError }: { onError: (e: unknown) => boolean }) {
  const [selected, setSelected] = useState(defaultSchoolDay);
  const monday = mondayOf(selected);
  const isCurrentWeek = monday === mondayOf(defaultSchoolDay());
  const { data, loading, error } = useCachedFetch<TimetableResponse>(
    `timetable:${monday}`,
    () => api.timetable(monday),
    onError,
  );
  const days = data?.week === monday ? data.days : [];

  return (
    <div className="screen">
      <LargeHeader title="Rozvrh" subtitle={longDate(selected)} loading={loading}>
        <DayStrip monday={monday} selected={selected} onSelect={setSelected} />
      </LargeHeader>
      {error && <p className="banner">Rozvrh se nepodařilo obnovit. Zobrazuji poslední uložený.</p>}
      <DayPager index={weekdayIndex(selected)} onIndex={(i) => setSelected(addDays(monday, i))}>
        {[0, 1, 2, 3, 4].map((i) => {
          const day = days.find((d) => d.date === addDays(monday, i));
          return day ? <DayView key={day.date} day={day} live={isCurrentWeek && day.date === todayPrague()} /> : loading ? null : <Empty key={i} icon="📭" title="Žádná data" />;
        })}
      </DayPager>
    </div>
  );
}

type Item = { kind: "lesson"; lesson: Lesson } | { kind: "lunch"; start: string; end: string };

function DayView({ day, live }: { day: TimetableDay; live: boolean }) {
  const [now, setNow] = useState(nowMinutesPrague);
  useEffect(() => {
    if (!live) return;
    const t = setInterval(() => setNow(nowMinutesPrague()), 30_000);
    return () => clearInterval(t);
  }, [live]);

  if (!day.lessons.length) return <Empty icon="🌤️" title="Volno" text="Tenhle den nemáš žádnou hodinu." />;

  const items: Item[] = day.lessons.map((lesson) => ({ kind: "lesson" as const, lesson }));
  if (day.lunch) items.push({ kind: "lunch", ...day.lunch });
  const start = (it: Item) => minutes(it.kind === "lesson" ? it.lesson.start : it.start);
  items.sort((a, b) => start(a) - start(b));

  const current = live ? day.lessons.find((l) => minutes(l.start) <= now && now < minutes(l.end)) : undefined;
  const next = live ? day.lessons.find((l) => minutes(l.start) > now) : undefined;
  const changes = day.lessons.filter((l) => l.changed).length + day.cancelled.length;

  return (
    <div className="day-view">
      {live && (current || next) && <NowCard current={current} next={next} now={now} />}
      {changes > 0 && (
        <p className="change-note">
          <span className="dot" /> {changes === 1 ? "1 změna" : changes < 5 ? `${changes} změny` : `${changes} změn`} oproti stálému rozvrhu
        </p>
      )}
      <ol className="timeline">
        {items.map((it, i) =>
          it.kind === "lunch" ? (
            <li key={`lunch-${i}`} className="timeline-lunch">
              <span>🍽️ Oběd</span>
              <span>
                {it.start}–{it.end}
              </span>
            </li>
          ) : (
            <LessonCard
              key={`${it.lesson.id}-${i}`}
              lesson={it.lesson}
              state={!live ? "future" : now >= minutes(it.lesson.end) ? "past" : it.lesson === current ? "now" : "future"}
            />
          ),
        )}
      </ol>
      {day.cancelled.length > 0 && (
        <>
          <h2 className="list-title">Odpadá</h2>
          <ol className="timeline">
            {day.cancelled.map((l, i) => (
              <LessonCard key={`c-${i}`} lesson={l} state="cancelled" />
            ))}
          </ol>
        </>
      )}
    </div>
  );
}

function NowCard({ current, next, now }: { current?: Lesson; next?: Lesson; now: number }) {
  const l = current ?? next!;
  const progress = current ? (now - minutes(current.start)) / (minutes(current.end) - minutes(current.start)) : 0;
  return (
    <div className="now-card">
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
  );
}

function LessonCard({ lesson: l, state }: { lesson: Lesson; state: "past" | "now" | "future" | "cancelled" }) {
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
    <li>
      {l.meet && state !== "cancelled" ? (
        <a className={cls} href={l.meet} target="_blank" rel="noreferrer" aria-label={`${l.subject}, otevřít Meet`}>
          {body}
        </a>
      ) : (
        <div className={cls}>{body}</div>
      )}
    </li>
  );
}
