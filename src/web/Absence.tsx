import { api } from "./api";
import type { Absence as AbsenceData, AbsenceSubject, MissedLesson } from "../worker/parse";
import { Empty, LargeHeader, SkeletonList } from "./ui";
import { WEEKDAYS, weekdayIndex } from "./time";
import { useCachedFetch } from "./useFetch";
import { AbsenceIcon } from "./icons";

const CODES: Record<string, string> = { n: "neomluveno", o: "omluveno" };

const plural = (n: number, one: string, few: string, many: string) => (n === 1 ? one : n > 1 && n < 5 ? few : many);

function level(percent: number) {
  if (percent <= 0) return "none";
  if (percent < 15) return "low";
  if (percent < 25) return "mid";
  return "high";
}

export function Absence({ onError }: { onError: (e: unknown) => boolean }) {
  const { data, loading, error } = useCachedFetch<AbsenceData>("absence", api.absence, onError);
  const subjects = [...(data?.subjects ?? [])].sort((a, b) => b.percent - a.percent || a.subject.localeCompare(b.subject));
  const total = data?.total;

  return (
    <div className="screen">
      <LargeHeader title="Absence" subtitle="Toto pololetí" loading={loading} />
      <div className="scroll">
        {error && <p className="banner">Absenci se nepodařilo obnovit. Zobrazuji poslední uloženou.</p>}
        {!data && loading ? (
          <SkeletonList count={6} side />
        ) : !data ? (
          <Empty icon={<AbsenceIcon size={40} />} title="Absence není k dispozici" />
        ) : (
          <>
            {total && (
              <div className="summary-card stagger">
                <div>
                  <p className="eyebrow">Zameškáno celkem</p>
                  <p className={`summary-value absence-${level(total.percent)}`}>
                    {total.missed}
                    <small> {plural(total.missed, "hodina", "hodiny", "hodin")}</small>
                  </p>
                </div>
                <div className="summary-side">
                  <span>{total.percent.toLocaleString("cs-CZ")} % z {total.taught}</span>
                  <span className={total.unexcused ? "warn" : ""}>{total.unexcused} neomluvených</span>
                </div>
              </div>
            )}

            <h2 className="list-title">Podle předmětů</h2>
            <div className="list">
              {subjects.map((s, i) => (
                <SubjectRow key={s.subject} s={s} index={i + 1} />
              ))}
            </div>

            <h2 className="list-title">Zameškané hodiny</h2>
            {data.missed.length ? (
              <MissedList lessons={data.missed} />
            ) : (
              <Empty icon={<AbsenceIcon size={40} />} title="Žádná absence" text="Zatím jsi nic nezameškal." />
            )}
            <p className="list-footer">Omluvenky se zatím zadávají přímo v SIS.</p>
          </>
        )}
      </div>
    </div>
  );
}

function SubjectRow({ s, index }: { s: AbsenceSubject; index: number }) {
  const lvl = level(s.percent);
  return (
    <div className={`row absence-row stagger absence-${lvl}`} style={{ "--i": index } as React.CSSProperties}>
      <span className="absence-subject">{s.subject}</span>
      <span className="absence-bar" aria-hidden="true">
        <span style={{ transform: `scaleX(${Math.min(1, s.percent / 30)})` }} />
      </span>
      <span className="absence-numbers">
        <b>{s.percent.toLocaleString("cs-CZ")} %</b>
        <small>
          {s.missed}/{s.taught}
        </small>
      </span>
    </div>
  );
}

function MissedList({ lessons }: { lessons: MissedLesson[] }) {
  const byDate = new Map<string, MissedLesson[]>();
  for (const l of lessons) byDate.set(l.date, [...(byDate.get(l.date) ?? []), l]);
  return (
    <div className="list">
      {[...byDate].map(([date, ls], i) => {
        const [, m, d] = date.split("-").map(Number);
        return (
          <div key={date} className="row missed-day stagger" style={{ "--i": i + 1 } as React.CSSProperties}>
            <span className="missed-date">
              <b>{d}. {m}.</b>
              <small>{WEEKDAYS[weekdayIndex(date)]}</small>
            </span>
            <span className="missed-lessons">
              {ls.map((l, j) => (
                <span key={j} className="missed-lesson">
                  <span className="missed-period">{l.period}.</span>
                  {l.subject}
                  <small>{l.teacher}</small>
                  <span className={`tag ${l.code === "n" ? "tag-warn" : ""}`}>{CODES[l.code] ?? l.code}</span>
                </span>
              ))}
            </span>
          </div>
        );
      })}
    </div>
  );
}
