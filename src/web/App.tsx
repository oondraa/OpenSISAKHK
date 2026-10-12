import { useCallback, useState, type ReactNode } from "react";
import { api, cache, Unauthorized } from "./api";
import { Login } from "./Login";
import { Timetable } from "./Timetable";
import { Gym } from "./Gym";
import { Grades } from "./Grades";
import { Settings } from "./Settings";

type Tab = "timetable" | "gym" | "grades" | "settings";

const TABS: { id: Tab; label: string; icon: ReactNode }[] = [
  {
    id: "timetable",
    label: "Rozvrh",
    icon: (
      <>
        <rect x="3.5" y="5" width="17" height="15.5" rx="3.5" />
        <path d="M3.5 10h17M8 3v4M16 3v4" />
      </>
    ),
  },
  {
    id: "gym",
    label: "Gym",
    icon: <path d="M6.5 7v10M3.5 9.5v5M17.5 7v10M20.5 9.5v5M6.5 12h11" />,
  },
  {
    id: "grades",
    label: "Známky",
    icon: <path d="M5 20v-7M12 20V5M19 20v-10" />,
  },
  {
    id: "settings",
    label: "Nastavení",
    icon: (
      <>
        <circle cx="12" cy="12" r="3" />
        <path d="M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7" />
      </>
    ),
  },
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
            <svg viewBox="0 0 24 24" aria-hidden="true">
              {t.icon}
            </svg>
            <span>{t.label}</span>
          </button>
        ))}
      </nav>
    </div>
  );
}
