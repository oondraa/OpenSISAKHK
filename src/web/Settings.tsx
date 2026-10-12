import { useEffect, useState } from "react";
import { api, cache } from "./api";
import { CLASSES } from "../shared/schoolOcr";
import { detectClass } from "./detectClass";
import { LargeHeader, Section, Segmented, Switch } from "./ui";
import { savedTheme, setTheme, type Theme } from "./theme";
import { useCachedFetch } from "./useFetch";

const b64ToBytes = (b64: string) => Uint8Array.from(atob(b64.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));

type PushState = "unsupported" | "denied" | "off" | "on" | "busy";

async function currentSubscription() {
  const reg = await navigator.serviceWorker?.ready;
  return reg?.pushManager.getSubscription() ?? null;
}

export function Settings({ onError, onLogout }: { onError: (e: unknown) => boolean; onLogout: () => void }) {
  const me = useCachedFetch("me", api.me, onError);
  const [theme, setThemeState] = useState<Theme>(savedTheme);
  const [push, setPush] = useState<PushState>("busy");
  const [pushMsg, setPushMsg] = useState<string | null>(null);
  const [cls, setCls] = useState<string | null>(() => cache.get<string>("pushClass"));

  useEffect(() => {
    if (!("serviceWorker" in navigator) || !("PushManager" in window)) return setPush("unsupported");
    if (Notification.permission === "denied") return setPush("denied");
    currentSubscription().then((s) => setPush(s ? "on" : "off"));
  }, []);

  /** Subscribes (or re-registers with a new class) — the server keeps only class + subject codes. */
  async function subscribe(chosen: string | null) {
    const reg = await navigator.serviceWorker.ready;
    const detected = await detectClass().catch(() => ({ cls: null, subjects: [] as string[] }));
    const finalCls = chosen ?? detected.cls;
    if (!finalCls) {
      setPushMsg("Nepodařilo se poznat tvoji třídu. Vyber ji níž a notifikace se zapnou.");
      return false;
    }
    const sub = (await reg.pushManager.getSubscription()) ??
      (await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: b64ToBytes((await api.pushKey()).key) }));
    await api.pushSubscribe(sub.toJSON(), finalCls, detected.subjects);
    cache.set("pushClass", finalCls);
    setCls(finalCls);
    return true;
  }

  async function changeClass(next: string) {
    setCls(next);
    cache.set("pushClass", next);
    if (push !== "on" && push !== "off") return;
    setPush("busy");
    try {
      setPush((await subscribe(next)) ? "on" : "off");
    } catch (e) {
      onError(e);
      setPush("off");
    }
  }

  async function togglePush(on: boolean) {
    setPush("busy");
    setPushMsg(null);
    try {
      const reg = await navigator.serviceWorker.ready;
      if (on) {
        if ((await Notification.requestPermission()) !== "granted") return setPush("denied");
        setPush((await subscribe(cls)) ? "on" : "off");
      } else {
        const sub = await reg.pushManager.getSubscription();
        if (sub) {
          await api.pushUnsubscribe(sub.endpoint).catch(() => {});
          await sub.unsubscribe();
        }
        setPush("off");
      }
    } catch (e) {
      onError(e);
      setPush(on ? "off" : "on");
      setPushMsg("Nepodařilo se změnit notifikace.");
    }
  }

  const isIos = /iPhone|iPad/.test(navigator.userAgent);
  const standalone = matchMedia("(display-mode: standalone)").matches;

  return (
    <div className="screen">
      <LargeHeader title="Nastavení" />
      <div className="scroll">
        <div className="profile-card stagger">
          {me.data?.hasPhoto ? (
            <img className="avatar" src="/api/me/photo" alt="" />
          ) : (
            <span className="avatar avatar-fallback" aria-hidden="true">
              {(me.data?.name ?? "?").split(" ").map((p) => p[0]).slice(0, 2).join("")}
            </span>
          )}
          <div>
            <p className="profile-name">{me.data?.name ?? "Přihlášený student"}</p>
            <p className="profile-meta">Přihlášeno přes SIS SSAKHK</p>
          </div>
        </div>

        <Section title="Vzhled">
          <div className="row">
            <span className="row-title">Režim</span>
            <Segmented<Theme>
              label="Režim zobrazení"
              value={theme}
              onChange={(t) => {
                setThemeState(t);
                setTheme(t);
              }}
              options={[
                { value: "system", label: "Auto" },
                { value: "light", label: "Světlý" },
                { value: "dark", label: "Tmavý" },
              ]}
            />
          </div>
        </Section>

        <Section
          title="Notifikace"
          footer={
            push === "unsupported"
              ? isIos && !standalone
                ? "Na iPhonu fungují notifikace jen v aplikaci přidané na plochu (Sdílet → Přidat na plochu)."
                : "Tento prohlížeč push notifikace nepodporuje."
              : push === "denied"
                ? "Notifikace jsou v prohlížeči zakázané. Povol je v nastavení prohlížeče pro tuhle stránku."
                : (pushMsg ??
                  "Když škola přepíše rozvrh (i ve 3 ráno), dostaneš zprávu přesně s tím, co se změnilo u tvé třídy a tvých skupin.")
          }
        >
          <div className="row">
            <span className="row-title">Změny v rozvrhu</span>
            <Switch
              label="Notifikace o změnách v rozvrhu"
              checked={push === "on"}
              disabled={push === "busy" || push === "unsupported" || push === "denied"}
              onChange={togglePush}
            />
          </div>
          {(push === "on" || cls) && (
            <label className="row">
              <span className="row-title">Třída</span>
              <select className="select" value={cls ?? ""} onChange={(e) => changeClass(e.target.value)}>
                {!cls && <option value="">Vyber…</option>}
                {CLASSES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </label>
          )}
          {push === "on" && (
            <button className="row row-button" onClick={() => api.pushTest().catch(onError)}>
              Poslat zkušební notifikaci
            </button>
          )}
        </Section>

        <Section title="Soukromí">
          <div className="privacy">
            <p>
              <b>Co ukládáme:</b> tvoje jméno a profilovou fotku ze SIS, abychom věděli, kdo aplikaci používá a mohli ti
              ji ukázat tady. U zapnutých notifikací adresu pro doručení, tvoji třídu a zkratky tvých předmětů a skupin
              (např. AGJ1), aby ti chodily jen tvoje změny.
            </p>
            <p>
              <b>Co neukládáme:</b> heslo. Nikdy nepřijde do žádné databáze. Zůstává jen zašifrované v tomhle zařízení,
              aby tě aplikace nemusela odhlašovat, když SIS ukončí relaci. Známky, rozvrh ani absence se na serveru
              neukládají.
            </p>
            <p>
              Odhlášením se z tohoto zařízení smaže i uložené heslo a odběr notifikací.{" "}
              <a href="https://github.com/oondraa/OpenSISAKHK" target="_blank" rel="noreferrer">
                Zdrojový kód je veřejný.
              </a>
            </p>
          </div>
        </Section>

        <Section>
          <button className="row row-button destructive" onClick={onLogout}>
            Odhlásit se
          </button>
        </Section>

        <p className="list-footer center">OpenSISAKHK · neoficiální aplikace, není spojená se školou</p>
      </div>
    </div>
  );
}
