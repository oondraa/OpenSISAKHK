import { cache } from "./api";

export type Theme = "system" | "light" | "dark";

export function applyTheme(theme: Theme) {
  const root = document.documentElement;
  if (theme === "system") root.removeAttribute("data-theme");
  else root.setAttribute("data-theme", theme);
  const dark = theme === "dark" || (theme === "system" && matchMedia("(prefers-color-scheme: dark)").matches);
  document.querySelector('meta[name="theme-color"]:not([media])')?.setAttribute("content", dark ? "#000000" : "#f2f2f7");
}

export const savedTheme = (): Theme => cache.get<Theme>("theme") ?? "system";

export function setTheme(theme: Theme) {
  cache.set("theme", theme);
  applyTheme(theme);
}
