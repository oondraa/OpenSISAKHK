import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { api, cache, Unauthorized } from "./api";
import { Login } from "./Login";
import { Timetable } from "./Timetable";
import { Gym } from "./Gym";
import { Grades } from "./Grades";
import { Settings } from "./Settings";
import { Absence } from "./Absence";
import { Home, TABS, type Tab } from "./Home";
import { ClassPrompt } from "./ClassPrompt";
import { BackContext, ScreenLayer, type LayerHandle } from "./layer";
import { AbsenceIcon, CalendarIcon, DumbbellIcon, GearIcon, GradesIcon } from "./icons";

/** "home": the tile grid, screens grow out of their tile. "tabs": the tab bar from before 1.6, picked in Settings. */
export type Layout = "home" | "tabs";

const TAB_BAR: { id: Tab; label: string; icon: ReactNode }[] = [
  { id: "timetable", label: "Rozvrh", icon: <CalendarIcon /> },
  { id: "gym", label: "Gym", icon: <DumbbellIcon /> },
  { id: "grades", label: "Známky", icon: <GradesIcon /> },
  { id: "absence", label: "Absence", icon: <AbsenceIcon /> },
  { id: "settings", label: "Nastavení", icon: <GearIcon /> },
];

export function App() {
  // The session cookie is HttpOnly, so "logged in" is remembered locally and corrected by the first 401.
  const [loggedIn, setLoggedIn] = useState(() => cache.get<boolean>("loggedIn") ?? false);
  const [layout, setLayout] = useState<Layout>(() => cache.get<Layout>("layout") ?? "home");
  // Notifications open their screen right away (/?tab=grades): over the home grid, or as the selected tab.
  const [linked] = useState(() => {
    const t = new URLSearchParams(location.search).get("tab");
    return TABS.includes(t as Tab) ? (t as Tab) : null;
  });
  // Home layout: the app lands on the grid, `screen` is the one opened over it.
  const [screen, setScreen] = useState<{ tab: Tab; animate: boolean } | null>(() =>
    layout === "home" && linked ? { tab: linked, animate: false } : null,
  );
  // Tab bar layout: the selected tab.
  const [tab, setTab] = useState<Tab>(linked ?? "timetable");
  // Home is in render state too, so it re-reads the cached glances once a screen closes.
  const [, setClosedAt] = useState(0);
  const home = useRef<HTMLDivElement>(null);
  const layer = useRef<LayerHandle>(null);
  const swiped = useRef(false);

  // Each open screen is a history entry, so the system back (Android, browser) closes it like the back button.
  useEffect(() => {
    if (screen && history.state?.screen !== screen.tab) {
      history.replaceState(null, "", "/");
      history.pushState({ screen: screen.tab }, "", "/");
    }
    const onPop = () => {
      if (swiped.current) swiped.current = false;
      else layer.current?.close();
    };
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && layer.current && history.back();
    addEventListener("popstate", onPop);
    addEventListener("keydown", onKey);
    return () => {
      removeEventListener("popstate", onPop);
      removeEventListener("keydown", onKey);
    };
  }, []);

  const open = useCallback((tab: Tab) => {
    history.pushState({ screen: tab }, "", "/");
    setScreen({ tab, animate: true });
  }, []);
  const back = useCallback(() => history.back(), []);
  const closed = useCallback((bySwipe: boolean) => {
    if (bySwipe) {
      swiped.current = true;
      history.back(); // drop the screen's history entry; onPop knows it's already closed
    }
    setScreen(null);
    setClosedAt(Date.now());
  }, []);

  const signOut = useCallback(() => {
    const theme = cache.get("theme");
    const savedLayout = cache.get("layout");
    cache.clear();
    if (theme) cache.set("theme", theme);
    if (savedLayout) cache.set("layout", savedLayout);
    setLoggedIn(false);
    setScreen(null);
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

  // Switched in Settings, so Settings stays on screen: as the selected tab, or opened over the home grid.
  const changeLayout = (next: Layout) => {
    cache.set("layout", next);
    setLayout(next);
    if (next === "tabs") {
      history.replaceState(null, "", "/"); // its screen entry, if any, now leads nowhere
      setTab(screen?.tab ?? "settings");
      setScreen(null);
    } else {
      history.pushState({ screen: tab }, "", "/");
      setScreen({ tab, animate: false });
    }
  };

  const content = (t: Tab) => (
    <>
      {t === "timetable" && <Timetable onError={onError} />}
      {t === "gym" && <Gym onError={onError} />}
      {t === "grades" && <Grades onError={onError} />}
      {t === "absence" && <Absence onError={onError} />}
      {t === "settings" && (
        <Settings
          onError={onError}
          layout={layout}
          onLayout={changeLayout}
          onLogout={async () => {
            const sub = await navigator.serviceWorker?.ready.then((r) => r.pushManager.getSubscription()).catch(() => null);
            await sub?.unsubscribe().catch(() => {});
            await api.logout().catch(() => {});
            signOut();
          }}
        />
      )}
    </>
  );

  if (layout === "tabs")
    return (
      <div className="app">
        <main className="tab-content" key={tab}>
          {content(tab)}
        </main>
        <nav className="tabbar" aria-label="Hlavní navigace">
          {TAB_BAR.map((t) => (
            <button key={t.id} className={tab === t.id ? "active" : ""} aria-current={tab === t.id ? "page" : undefined} onClick={() => setTab(t.id)}>
              {t.icon}
              <span>{t.label}</span>
            </button>
          ))}
        </nav>
        <ClassPrompt onError={onError} />
      </div>
    );

  return (
    <>
      <Home homeRef={home} onOpen={open} onError={onError} inert={!!screen} />
      {screen && (
        <BackContext.Provider value={back}>
          <ScreenLayer key={screen.tab} tile={screen.tab} home={home} animate={screen.animate} handle={layer} onClosed={closed}>
            {content(screen.tab)}
          </ScreenLayer>
        </BackContext.Provider>
      )}
      <ClassPrompt onError={onError} />
    </>
  );
}
