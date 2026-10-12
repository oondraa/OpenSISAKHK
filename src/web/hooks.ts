import { useEffect, useRef, useState } from "react";
import { nowMinutesPrague } from "./time";

export function useNow() {
  const [now, setNow] = useState(nowMinutesPrague);
  useEffect(() => {
    const t = setInterval(() => setNow(nowMinutesPrague()), 30_000);
    return () => clearInterval(t);
  }, []);
  return now;
}

/**
 * Scrolls the surrounding scroll container so `target` sits near its top: opening the app on a school
 * day shows what's on now and next, while earlier lessons stay one swipe up.
 */
export function useScrollToFocus(enabled: boolean) {
  const target = useRef<HTMLElement | null>(null);
  useEffect(() => {
    const el = target.current;
    const scroller = el?.closest<HTMLElement>(".page, .scroll");
    if (!enabled || !el || !scroller) return;
    const top = el.getBoundingClientRect().top - scroller.getBoundingClientRect().top + scroller.scrollTop - 12;
    if (top <= 0) return;
    const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
    // Let the cards rise in first, then glide to "now" so the motion reads as the list settling.
    const t = setTimeout(() => scroller.scrollTo({ top, behavior: reduce ? "instant" : "smooth" }), reduce ? 0 : 280);
    return () => clearTimeout(t);
  }, [enabled]);
  return target;
}
