import { useEffect, useRef, type ReactNode } from "react";
import { addDays, todayPrague, WEEKDAYS } from "./time";

export function LargeHeader({ title, subtitle, accessory, loading, children }: {
  title: string;
  subtitle?: string;
  accessory?: ReactNode;
  loading?: boolean;
  children?: ReactNode;
}) {
  return (
    <header className="large-header">
      <div className="large-header-row">
        <div>
          {subtitle && <p className="eyebrow">{subtitle}</p>}
          <h1 className="large-title">{title}</h1>
        </div>
        {accessory}
      </div>
      {children}
      {loading && <div className="progress" role="progressbar" aria-label="Načítání" />}
    </header>
  );
}

/** Mon–Fri strip for the week of `monday`; arrows move whole weeks. */
export function DayStrip({ monday, selected, onSelect }: { monday: string; selected: string; onSelect: (iso: string) => void }) {
  const today = todayPrague();
  return (
    <div className="day-strip">
      <button className="week-arrow" aria-label="Předchozí týden" onClick={() => onSelect(addDays(selected, -7))}>
        <Chevron dir="left" />
      </button>
      <div className="day-strip-days" role="tablist">
        {[0, 1, 2, 3, 4].map((i) => {
          const iso = addDays(monday, i);
          const active = iso === selected;
          return (
            <button
              key={iso}
              role="tab"
              aria-selected={active}
              className={`day ${active ? "active" : ""} ${iso === today ? "today" : ""}`}
              onClick={() => onSelect(iso)}
            >
              <span className="day-name">{WEEKDAYS[i].slice(0, 2)}</span>
              <span className="day-num">{Number(iso.slice(8))}</span>
            </button>
          );
        })}
      </div>
      <button className="week-arrow" aria-label="Další týden" onClick={() => onSelect(addDays(selected, 7))}>
        <Chevron dir="right" />
      </button>
    </div>
  );
}

/**
 * Horizontal pager over the 5 weekdays using native scroll-snap: it tracks the finger 1:1,
 * keeps momentum and can be grabbed mid-flight, which a scripted animation can't match.
 */
export function DayPager({ index, onIndex, children }: { index: number; onIndex: (i: number) => void; children: ReactNode[] }) {
  const ref = useRef<HTMLDivElement>(null);
  const fromScroll = useRef(false);

  useEffect(() => {
    const el = ref.current;
    if (!el || fromScroll.current) {
      fromScroll.current = false;
      return;
    }
    const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
    el.scrollTo({ left: index * el.clientWidth, behavior: reduce ? "instant" : "smooth" });
  }, [index]);

  return (
    <div
      ref={ref}
      className="pager"
      onScroll={(e) => {
        const el = e.currentTarget;
        const i = Math.round(el.scrollLeft / el.clientWidth);
        if (i !== index && Math.abs(el.scrollLeft - i * el.clientWidth) < 2) {
          fromScroll.current = true;
          onIndex(i);
        }
      }}
    >
      {children.map((child, i) => (
        <div className="page" key={i} aria-hidden={i !== index}>
          {child}
        </div>
      ))}
    </div>
  );
}

export function Segmented<T extends string>({ value, options, onChange, label }: {
  value: T;
  options: { value: T; label: string }[];
  onChange: (v: T) => void;
  label: string;
}) {
  const i = Math.max(0, options.findIndex((o) => o.value === value));
  return (
    <div className="segmented" role="radiogroup" aria-label={label} style={{ "--n": options.length, "--i": i } as React.CSSProperties}>
      <span className="segmented-thumb" aria-hidden="true" />
      {options.map((o) => (
        <button key={o.value} role="radio" aria-checked={o.value === value} onClick={() => onChange(o.value)}>
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function Switch({ checked, onChange, disabled, label }: { checked: boolean; onChange: (v: boolean) => void; disabled?: boolean; label: string }) {
  return (
    <button role="switch" aria-checked={checked} aria-label={label} disabled={disabled} className="switch" onClick={() => onChange(!checked)}>
      <span className="switch-knob" />
    </button>
  );
}

export function Section({ title, footer, children }: { title?: string; footer?: ReactNode; children: ReactNode }) {
  return (
    <section className="list-section">
      {title && <h2 className="list-title">{title}</h2>}
      <div className="list">{children}</div>
      {footer && <p className="list-footer">{footer}</p>}
    </section>
  );
}

export function Chevron({ dir = "right" }: { dir?: "left" | "right" | "down" }) {
  const rot = { left: 180, right: 0, down: 90 }[dir];
  return (
    <svg className="chevron" viewBox="0 0 24 24" aria-hidden="true" style={{ transform: `rotate(${rot}deg)` }}>
      <path d="M9 5l7 7-7 7" />
    </svg>
  );
}

export function Empty({ icon, title, text }: { icon: ReactNode; title: string; text?: string }) {
  return (
    <div className="empty">
      <div className="empty-icon">{icon}</div>
      <p className="empty-title">{title}</p>
      {text && <p className="empty-text">{text}</p>}
    </div>
  );
}
