import { useEffect, useState } from "react";
import { api, cache } from "./api";
import { LargeHeader, Section, Segmented, Switch } from "./ui";
import { savedTheme, setTheme, type Theme } from "./theme";
import { useCachedFetch } from "./useFetch";
import { BUILT, VERSION } from "./version";
import type { Layout } from "./App";

const b64ToBytes = (b64: string) => Uint8Array.from(atob(b64.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));

type PushState = "unsupported" | "denied" | "off" | "on" | "busy";

async function currentSubscription() {
  const reg = await navigator.serviceWorker?.ready;
  return reg?.pushManager.getSubscription() ?? null;
}

export function Settings({ onError, onLogout, layout, onLayout }: {
  onError: (e: unknown) => boolean;
  onLogout: () => void;
  layout: Layout;
  onLayout: (l: Layout) => void;
}) {
  const me = useCachedFetch("me", api.me, onError);
  const [theme, setThemeState] = useState<Theme>(savedTheme);
  const [push, setPush] = useState<PushState>("busy");
  const [pushMsg, setPushMsg] = useState<string | null>(null);
  const [watching, setWatching] = useState<boolean | null>(null);
  // One switch for everything: push on this device + background checks of timetable, grades and absence.
  const notifications = push === "on" && (watching ?? me.data?.watching ?? false);

  useEffect(() => {
    if (!("serviceWorker" in navigator) || !("PushManager" in window)) return setPush("unsupported");
    if (Notification.permission === "denied") return setPush("denied");
    currentSubscription().then((s) => setPush(s ? "on" : "off"));
  }, []);

  async function toggleNotifications(on: boolean) {
    setPush("busy");
    setPushMsg(null);
    try {
      const reg = await navigator.serviceWorker.ready;
      if (on) {
        if ((await Notification.requestPermission()) !== "granted") return setPush("denied");
        const sub = (await reg.pushManager.getSubscription()) ??
          (await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: b64ToBytes((await api.pushKey()).key) }));
        await api.pushSubscribe(sub.toJSON());
        await api.watch(true);
        setWatching(true);
        setPush("on");
      } else {
        await api.watch(false); // no notifications = no reason to keep the stored login
        setWatching(false);
        const sub = await reg.pushManager.getSubscription();
        if (sub) {
          await api.pushUnsubscribe(sub.endpoint).catch(() => {});
          await sub.unsubscribe();
        }
        setPush("off");
      }
    } catch (e) {
      onError(e);
      setPush((await currentSubscription().catch(() => null)) ? "on" : "off");
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

        <Section title="Vzhled" footer="Plocha: dlaždice na hlavní obrazovce, sekce se z nich otevírají. Lišta: sekce v liště dole.">
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
          <div className="row">
            <span className="row-title">Navigace</span>
            <Segmented<Layout>
              label="Navigace v aplikaci"
              value={layout}
              onChange={onLayout}
              options={[
                { value: "home", label: "Plocha" },
                { value: "tabs", label: "Lišta" },
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
                  "Přijde ti zpráva, když se ti změní rozvrh (i když ho škola přepíše ve 3 ráno), přibude známka nebo absence. Aby to server mohl hlídat, i když appku nemáš otevřenou, uloží si tvoje přihlášení do SIS zašifrované. Vypnutím nebo odhlášením se smaže.")
          }
        >
          <div className="row">
            <span className="row-title">Rozvrh, známky a absence</span>
            <Switch
              label="Notifikace o změnách rozvrhu, nových známkách a absenci"
              checked={notifications}
              disabled={push === "busy" || push === "unsupported" || push === "denied"}
              onChange={toggleNotifications}
            />
          </div>
          {notifications && (
            <button className="row row-button" onClick={() => api.pushTest().catch(onError)}>
              Poslat zkušební notifikaci
            </button>
          )}
        </Section>

        <StravaSection connected={me.data?.strava ?? false} onError={onError} />

        <Section title="Soukromí">
          <div className="privacy">
            <p>
              <b>Co ukládáme:</b> tvoje jméno a profilovou fotku ze SIS, abychom věděli, kdo aplikaci používá a mohli ti
              ji ukázat tady. U zapnutých notifikací adresu pro doručení do tohohle zařízení.
            </p>
            <p>
              <b>Heslo:</b> zůstává zašifrované v tomhle zařízení, aby tě aplikace nemusela odhlašovat, když SIS ukončí
              relaci. Do databáze se dostane jen tehdy, když si zapneš notifikace, a to zašifrované.
              Jinak by server nemohl sám zjistit, že ti něco přibylo.
            </p>
            <p>
              <b>Co neukládáme:</b> známky, rozvrh ani absenci. U notifikací o známkách si server pamatuje jen krátké
              otisky (hashe), podle kterých pozná, co je nové. Samotné známky z nich nevyčte.
            </p>
            <p>
              Odhlášením se smaže uložené heslo i odběr notifikací.{" "}
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

        <p className="list-footer center">
          OpenSISAKHK {VERSION} · sestaveno {BUILT}
          <br />
          neoficiální aplikace, není spojená se školou
          <br />
          <span className="diag">{layoutInfo()}</span>
        </p>
      </div>
    </div>
  );
}

/** Canteen account (Strava.cz): its login lives only in the encrypted session cookie, like the SIS one. */
function StravaSection({ connected, onError }: { connected: boolean; onError: (e: unknown) => boolean }) {
  const [on, setOn] = useState<boolean | null>(null);
  const [jmeno, setJmeno] = useState("");
  const [heslo, setHeslo] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const isOn = on ?? connected;

  async function connect(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      await api.stravaConnect(jmeno, heslo);
      cache.set("lunch", null); // the timetable reloads lunches with the new account
      setOn(true);
      setHeslo("");
    } catch (err) {
      if (!onError(err))
        setMsg((err as Error).message === "strava_bad_credentials" ? "Špatné jméno nebo heslo do Stravy." : "Stravu se nepodařilo propojit.");
    } finally {
      setBusy(false);
    }
  }

  async function disconnect() {
    setBusy(true);
    try {
      await api.stravaDisconnect();
      cache.set("lunch", null);
      setOn(false);
    } catch (err) {
      onError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Section
      title="Obědy (Strava)"
      footer={
        msg ??
        (isOn
          ? "V rozvrhu u oběda uvidíš, co máš objednané. Objednává se dál ve Stravě."
          : "Propoj účet jídelny a v rozvrhu u oběda uvidíš, jaké číslo máš objednané. Heslo zůstává jen zašifrované v tomhle zařízení.")
      }
    >
      {isOn ? (
        <>
          <div className="row">
            <span className="row-title">Účet jídelny</span>
            <span className="row-value">Propojeno</span>
          </div>
          <button className="row row-button destructive" disabled={busy} onClick={disconnect}>
            Odpojit Stravu
          </button>
        </>
      ) : (
        <form onSubmit={connect} className="login-form">
          <input placeholder="Uživatel ve Stravě" autoComplete="off" autoCapitalize="none" value={jmeno} onChange={(e) => setJmeno(e.target.value)} />
          <input type="password" placeholder="Heslo do Stravy" autoComplete="off" value={heslo} onChange={(e) => setHeslo(e.target.value)} />
          <button className="row row-button" type="submit" disabled={busy || !jmeno || !heslo}>
            {busy ? "Propojuji…" : "Propojit"}
          </button>
        </form>
      )}
    </Section>
  );
}

/** Viewport vs screen and safe-area insets, to tell layout problems on a phone apart (send this line with a screenshot). */
function layoutInfo() {
  const probe = document.createElement("div");
  probe.style.cssText =
    "position:fixed;visibility:hidden;padding-top:env(safe-area-inset-top);padding-bottom:env(safe-area-inset-bottom)";
  document.body.appendChild(probe);
  const { paddingTop, paddingBottom } = getComputedStyle(probe);
  probe.remove();
  const mode = matchMedia("(display-mode: standalone)").matches ? "app" : "web";
  return `${mode} · okno ${innerWidth}×${innerHeight} · displej ${screen.width}×${screen.height} · okraje ${parseInt(paddingTop)}/${parseInt(paddingBottom)}`;
}
