import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { registerSW } from "virtual:pwa-register";
import { App } from "./App";
import { applyTheme, savedTheme } from "./theme";
import "./fonts.css";
import "./styles.css";

applyTheme(savedTheme());
fixIosViewport();

/**
 * iOS (26) home-screen apps: the viewport ends env(safe-area-inset-top) short of the bottom of the screen, so
 * `position: fixed; bottom: 0` floats above an empty band. Measure the shortfall and move the tab
 * bar down over it (--vfix in styles.css). Only on iOS standalone, where the viewport should be the whole screen.
 */
function fixIosViewport() {
  const ios = /iPhone|iPad|iPod/.test(navigator.userAgent);
  const standalone = matchMedia("(display-mode: standalone)").matches || (navigator as { standalone?: boolean }).standalone;
  if (!ios || !standalone) return;
  const update = () => {
    const portrait = innerHeight > innerWidth;
    const screenHeight = portrait ? Math.max(screen.width, screen.height) : Math.min(screen.width, screen.height);
    const gap = screenHeight - innerHeight;
    document.documentElement.style.setProperty("--vfix", `${gap > 0 && gap < 120 ? gap : 0}px`);
  };
  update();
  addEventListener("resize", update);
}

// A new version was downloaded in the background: put the splash back up with "Aktualizace", then reload into it.
// The flag tells the next page load (index.html) to keep saying so while the new version starts.
const updateSW = registerSW({
  immediate: true,
  onNeedRefresh() {
    try {
      sessionStorage.setItem("osis:updating", "1");
    } catch {
      /* private mode: the new splash just won't say it */
    }
    document.getElementById("splash")?.classList.replace("hide", "again");
    setTimeout(() => updateSW(true), 400);
  },
});

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

/** Lets the splash intro (index.html) finish, then dissolves it into the app. */
function hideSplash() {
  const splash = document.getElementById("splash");
  if (!splash) return;
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  // "Open" finishes sliding in at ~1.25 s after navigation start; hold a beat so it reads.
  const introEnd = reduce ? 250 : 1500;
  // The splash already uses the app's theme (index.html), so the status bar colour stays as applyTheme set it.
  setTimeout(() => {
    splash.classList.remove("note");
    splash.classList.add("hide"); // stays in the DOM (hidden) so an update can show it again
  }, Math.max(0, introEnd - performance.now()));
}
hideSplash();
