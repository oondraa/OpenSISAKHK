import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { registerSW } from "virtual:pwa-register";
import { App } from "./App";
import { applyTheme, savedTheme } from "./theme";
import "./fonts.css";
import "./styles.css";

applyTheme(savedTheme());
registerSW({ immediate: true });

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
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", "#0e0e10");
  setTimeout(() => {
    splash.classList.add("hide");
    applyTheme(savedTheme()); // status bar back to the app's colour
    setTimeout(() => splash.remove(), 600);
  }, Math.max(0, introEnd - performance.now()));
}
hideSplash();
