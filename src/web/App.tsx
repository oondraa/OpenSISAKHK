import { useCallback, useState, type ReactNode } from "react";
import { api, cache, Unauthorized } from "./api";
import { Login } from "./Login";
import { Timetable } from "./Timetable";
import { Gym } from "./Gym";
import { Grades } from "./Grades";
import { Settings } from "./Settings";
import { Absence } from "./Absence";
import { AbsenceIcon, CalendarIcon, DumbbellIcon, GearIcon, GradesIcon } from "./icons";

type Tab = "timetable" | "gym" | "grades" | "absence" | "settings";

const TABS: { id: Tab; label: string; icon: ReactNode }[] = [
  { id: "timetable", label: "Rozvrh", icon: <CalendarIcon /> },
  { id: "gym", label: "Gym", icon: <DumbbellIcon /> },
  { id: "grades", label: "Známky", icon: <GradesIcon /> },
  { id: "absence", label: "Absence", icon: <AbsenceIcon /> },
  { id: "settings", label: "Nastavení", icon: <GearIcon /> },
];

export function App() {
  // The session cookie is HttpOnly, so "logged in" is remembered locally and corrected by the first 401.
  const [loggedIn, setLoggedIn] = useState(() => cache.get<boolean>("loggedIn") ?? false);
  const [tab, setTab] = useState<Tab>("timetable");

  const signOut = useCallback(() => {
    const theme = cache.get("theme");
    cache.clear();
    if (theme) cache.set("theme", theme);
    setLoggedIn(false);
    setTab("timetable");
  }, []);

  const onError = useCallback(
    (e: unknown) => {
      if (!(e instanceof Unauthorized)) return false;
      signOut();
      return true;
    },
    [signOut],
  );

  if (!loggedIn)
    return (
      <Login
        onDone={() => {
          cache.set("loggedIn", true);
          setLoggedIn(true);
        }}
      />
    );

  return (
    <div className="app">
      <main className="tab-content" key={tab}>
        {tab === "timetable" && <Timetable onError={onError} />}
        {tab === "gym" && <Gym onError={onError} />}
        {tab === "grades" && <Grades onError={onError} />}
        {tab === "absence" && <Absence onError={onError} />}
        {tab === "settings" && (
          <Settings
            onError={onError}
            onLogout={async () => {
              const sub = await navigator.serviceWorker?.ready.then((r) => r.pushManager.getSubscription()).catch(() => null);
              await sub?.unsubscribe().catch(() => {});
              await api.logout().catch(() => {});
              signOut();
            }}
          />
        )}
      </main>
      <nav className="tabbar" aria-label="Hlavní navigace">
        {TABS.map((t) => (
          <button key={t.id} className={tab === t.id ? "active" : ""} aria-current={tab === t.id ? "page" : undefined} onClick={() => setTab(t.id)}>
            {t.icon}
            <span>{t.label}</span>
          </button>
        ))}
      </nav>
    </div>
  );
}
