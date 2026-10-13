import { useCallback, useEffect, useRef, useState } from "react";
import { api, cache, Unauthorized } from "./api";
import { Login } from "./Login";
import { Timetable } from "./Timetable";
import { Gym } from "./Gym";
import { Grades } from "./Grades";
import { Settings } from "./Settings";
import { Absence } from "./Absence";
import { Home, TABS, type Tab } from "./Home";
import { BackContext, ScreenLayer, type LayerHandle } from "./layer";

export function App() {
  // The session cookie is HttpOnly, so "logged in" is remembered locally and corrected by the first 401.
  const [loggedIn, setLoggedIn] = useState(() => cache.get<boolean>("loggedIn") ?? false);
  // The app always lands on the home grid. Notifications open a screen right away (/?tab=grades), over the home.
  const [screen, setScreen] = useState<{ tab: Tab; animate: boolean } | null>(() => {
    const t = new URLSearchParams(location.search).get("tab");
    return TABS.includes(t as Tab) ? { tab: t as Tab, animate: false } : null;
  });
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
    cache.clear();
    if (theme) cache.set("theme", theme);
    setLoggedIn(false);
    setScreen(null);
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

  const tab = screen?.tab;
  return (
    <>
      <Home homeRef={home} onOpen={open} onError={onError} inert={!!screen} />
      {screen && (
        <BackContext.Provider value={back}>
          <ScreenLayer key={tab} tile={screen.tab} home={home} animate={screen.animate} handle={layer} onClosed={closed}>
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
          </ScreenLayer>
        </BackContext.Provider>
      )}
    </>
  );
}
