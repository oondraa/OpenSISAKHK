import { cache } from "./api";

export type Theme = "system" | "light" | "dark";

export function applyTheme(theme: Theme) {
  const root = document.documentElement;
  if (theme === "system") root.removeAttribute("data-theme");
  else root.setAttribute("data-theme", theme);
  const dark = theme === "dark" || (theme === "system" && matchMedia("(prefers-color-scheme: dark)").matches);
  root.classList.toggle("sp-light", !dark); // splash colours (index.html), e.g. when an update shows it again
  document.querySelector('meta[name="theme-color"]:not([media])')?.setAttribute("content", dark ? "#0e0e10" : "#f6f3ee");
}

export const savedTheme = (): Theme => cache.get<Theme>("theme") ?? "system";

export function setTheme(theme: Theme) {
  cache.set("theme", theme);
  applyTheme(theme);
}
