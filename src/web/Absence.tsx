import { api } from "./api";
import type { Absence as AbsenceData, AbsenceSubject, Day, MissedLesson } from "../worker/parse";
import { Empty, LargeHeader, SkeletonList } from "./ui";
import { WEEKDAYS, addDays, mondayOf, todayPrague, weekdayIndex } from "./time";
import { useCachedFetch } from "./useFetch";
import { AbsenceIcon } from "./icons";
import { LIMIT, LIMIT_HARD, plan, remainingLessons, semesterEnd, type Plan } from "./absencePlan";

const CODES: Record<string, string> = { n: "neomluveno", o: "omluveno" };

const plural = (n: number, one: string, few: string, many: string) => (n === 1 ? one : n > 1 && n < 5 ? few : many);
/** Accusative: "odchodit 1 hodinu / 3 hodiny / 5 hodin". */
const hours = (n: number) => `${n} ${plural(n, "hodinu", "hodiny", "hodin")}`;
const pct = (n: number) => `${n.toLocaleString("cs-CZ")} %`;
const shortDate = (iso: string) => `${Number(iso.slice(8))}. ${Number(iso.slice(5, 7))}.`;

function level(percent: number) {
  if (percent <= 0) return "none";
  if (percent < LIMIT - 10) return "low";
  if (percent < LIMIT) return "mid";
  return "high";
}

/** Lessons left per subject until the end of the semester, from the permanent timetable of this and next week. */
function useRemaining(onError: (e: unknown) => boolean) {
  const today = todayPrague();
  const monday = mondayOf(today);
  const { data } = useCachedFetch<Day[][]>(
    `permanent:${monday}`,
    () => Promise.all([monday, addDays(monday, 7)].map((w) => api.permanentTimetable(w).then((t) => t.days))),
    onError,
  );
  return data ? remainingLessons(monday, data[0], data[1], today, semesterEnd(today)) : null;
}

export function Absence({ onError }: { onError: (e: unknown) => boolean }) {
  const { data, loading, error } = useCachedFetch<AbsenceData>("absence", api.absence, onError);
  const remaining = useRemaining(onError);
  const subjects = [...(data?.subjects ?? [])].sort((a, b) => b.percent - a.percent || a.subject.localeCompare(b.subject));
  const total = data?.total;
  const over = subjects.filter((s) => s.percent >= LIMIT);
  const close = subjects.filter((s) => level(s.percent) === "mid");

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
                  <span>{pct(total.percent)} z {total.taught}</span>
                  <span className={total.unexcused ? "warn" : ""}>{total.unexcused} neomluvených</span>
                </div>
              </div>
            )}

            {over.length > 0 ? (
              <div className="absence-alert high">
                <b>
                  Přes hranici {LIMIT} %: {over.map((s) => s.subject).join(", ")}
                </b>
                <span>
                  Od {LIMIT}–{LIMIT_HARD} % tě vyučující nemusí klasifikovat a hrozí opravné zkoušky.
                </span>
              </div>
            ) : close.length > 0 ? (
              <div className="absence-alert mid">
                <b>
                  Blízko hranice {LIMIT} %: {close.map((s) => s.subject).join(", ")}
                </b>
                <span>Hlídej si, kolik hodin ještě můžeš vynechat.</span>
              </div>
            ) : null}

            <h2 className="list-title">Podle předmětů</h2>
            <div className="list">
              {subjects.map((s) => (
                <SubjectRow key={s.subject} s={s} p={plan(s, remaining ? (remaining.get(s.subject) ?? null) : null)} />
              ))}
            </div>
            <p className="list-footer">
              Procenta jsou ze všech odučených hodin, omluvených i neomluvených. Svislá čára je hranice {LIMIT} %. Zbývající
              hodiny jsou odhad podle stálého rozvrhu do konce pololetí ({shortDate(semesterEnd(todayPrague()))}), bez
              suplování a akcí.
            </p>

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

function hint(s: AbsenceSubject, p: Plan): string | null {
  const left = p.remaining !== null ? ` Do konce pololetí zbývá asi ${p.remaining} h.` : "";
  if (p.needed > 0) {
    if (p.remaining !== null && p.needed > p.remaining)
      return `Do konce pololetí už pod ${LIMIT} % neklesneš, ani když přijdeš na všech ${p.remaining} zbývajících hodin (skončíš na ${pct(p.best ?? s.percent)}).`;
    return `Pod ${LIMIT} % se dostaneš, když odchodíš ještě ${hours(p.needed)} bez absence.${left}`;
  }
  if (p.reserve === null || p.remaining === null) return null;
  if (p.reserve === 0) return `Už nesmíš vynechat žádnou hodinu, jinak skončíš nad ${LIMIT} %.${left}`;
  return `Můžeš vynechat ještě ${hours(p.reserve)} z ${p.remaining} zbývajících.`;
}

function SubjectRow({ s, p }: { s: AbsenceSubject; p: Plan }) {
  const text = hint(s, p);
  return (
    <div className={`row absence-row absence-${level(s.percent)}`}>
      <div className="absence-line">
        <span className="absence-subject">{s.subject}</span>
        {/* Scale ends at 40 %, so the 30 % line sits at three quarters. */}
        <span className="absence-bar" aria-hidden="true">
          <span style={{ transform: `scaleX(${Math.min(1, s.percent / 40)})` }} />
          <i style={{ left: `${(LIMIT / 40) * 100}%` }} />
        </span>
        <span className="absence-numbers">
          <b>{pct(s.percent)}</b>
          <small>
            {s.missed}/{s.taught} h{s.unexcused ? ` · ${s.unexcused} neoml.` : ""}
          </small>
        </span>
      </div>
      {text && <p className="absence-hint">{text}</p>}
    </div>
  );
}

function MissedList({ lessons }: { lessons: MissedLesson[] }) {
  const byDate = new Map<string, MissedLesson[]>();
  for (const l of lessons) byDate.set(l.date, [...(byDate.get(l.date) ?? []), l]);
  return (
    <div className="list">
      {[...byDate].map(([date, ls]) => {
        const [, m, d] = date.split("-").map(Number);
        return (
          <div key={date} className="row missed-day">
            <span className="missed-date">
              <b>
                {d}. {m}.
              </b>
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
