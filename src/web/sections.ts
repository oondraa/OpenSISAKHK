import { cache } from "./api";

/** Every part of the app. "more" is the tab bar's overflow list, "settings" has no place in the custom orders. */
export type Tab = "timetable" | "lunch" | "gym" | "rooms" | "grades" | "absence" | "projects" | "finance" | "settings" | "more";
export type Part = Exclude<Tab, "settings" | "more">;

export const LABELS: Record<Tab, string> = {
  timetable: "Rozvrh",
  lunch: "Obědy",
  gym: "Posilovna",
  rooms: "Volné učebny",
  grades: "Známky",
  absence: "Absence",
  projects: "Projekty",
  finance: "Finance",
  settings: "Nastavení",
  more: "Více",
};

/** Screens a notification may open directly (/?tab=grades). Not Finance: nothing links straight to the money. */
export const LINKABLE: Tab[] = ["timetable", "lunch", "gym", "rooms", "grades", "absence", "projects", "settings"];

/** Home: the timetable is always the big tile on top, these follow in pairs (an odd last one as a slim bar). */
export const HOME_DEFAULT: Part[] = ["grades", "absence", "lunch", "rooms", "gym", "projects", "finance"];
/** Tab bar: the first TAB_SLOTS are in the bar, the rest under "Více" (with Settings). */
export const TABS_DEFAULT: Part[] = ["timetable", "lunch", "grades", "absence", "gym", "rooms", "projects", "finance"];
export const TAB_SLOTS = 4;

/** A saved order, repaired: unknown ids dropped, parts added in a later version appended. */
function load(key: string, all: Part[]): Part[] {
  const saved = (cache.get<string[]>(key) ?? []).filter((s): s is Part => all.includes(s as Part));
  return [...new Set([...saved, ...all])];
}

export const homeOrder = () => load("homeOrder", HOME_DEFAULT);
export const tabOrder = () => load("tabOrder", TABS_DEFAULT);
export const saveHomeOrder = (order: Part[]) => cache.set("homeOrder", order);
export const saveTabOrder = (order: Part[]) => cache.set("tabOrder", order);
