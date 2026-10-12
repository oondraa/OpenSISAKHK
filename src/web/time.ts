export const WEEKDAYS = ["Pondělí", "Úterý", "Středa", "Čtvrtek", "Pátek", "Sobota", "Neděle"];
const MONTHS = ["ledna", "února", "března", "dubna", "května", "června", "července", "srpna", "září", "října", "listopadu", "prosince"];

export const todayPrague = () => new Intl.DateTimeFormat("sv-SE", { timeZone: "Europe/Prague" }).format(new Date());

export function nowMinutesPrague() {
  const [h, m] = new Intl.DateTimeFormat("en-GB", { timeZone: "Europe/Prague", hour: "2-digit", minute: "2-digit", hourCycle: "h23" })
    .format(new Date())
    .split(":")
    .map(Number);
  return h * 60 + m;
}

export function addDays(iso: string, days: number) {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

export function mondayOf(iso: string) {
  const d = new Date(`${iso}T00:00:00Z`);
  return addDays(iso, -((d.getUTCDay() + 6) % 7));
}

/** 0 = Monday … 6 = Sunday */
export const weekdayIndex = (iso: string) => (new Date(`${iso}T00:00:00Z`).getUTCDay() + 6) % 7;

/** School day to show by default: today on weekdays, next Monday on weekends. */
export function defaultSchoolDay() {
  const t = todayPrague();
  const wd = weekdayIndex(t);
  return wd < 5 ? t : addDays(t, 7 - wd);
}

export const minutes = (t: string) => {
  const [h, m] = t.split(":").map(Number);
  return h * 60 + m;
};

export function longDate(iso: string) {
  const [, m, d] = iso.split("-").map(Number);
  return `${WEEKDAYS[weekdayIndex(iso)]} ${d}. ${MONTHS[m - 1]}`;
}

export function inMinutes(n: number) {
  if (n < 1) return "teď";
  if (n < 60) return `za ${n} min`;
  const h = Math.floor(n / 60);
  return `za ${h} h ${n % 60 ? `${n % 60} min` : ""}`.trim();
}
