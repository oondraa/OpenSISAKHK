import { useEffect, useState } from "react";
import { api, cache } from "./api";
import { CLASSES } from "../shared/schoolOcr";
import { detectClass } from "./detectClass";
import { LargeHeader, Section, Segmented, Switch } from "./ui";
import { savedTheme, setTheme, type Theme } from "./theme";
import { useCachedFetch } from "./useFetch";
import { BUILT, VERSION } from "./version";

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
  const [watching, setWatching] = useState<boolean | null>(null);
  const watchOn = watching ?? me.data?.watching ?? false;

  async function toggleWatch(on: boolean) {
    setWatching(on);
    try {
      await api.watch(on);
    } catch (e) {
      onError(e);
      setWatching(!on);
    }
  }

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
        if (watchOn) await toggleWatch(false); // no notifications = no reason to keep the stored login
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
                  `Když škola přepíše rozvrh (i ve 3 ráno), dostaneš zprávu přesně s tím, co se změnilo u tvé třídy a tvých skupin.${
                    push === "on"
                      ? " Se zapnutými známkami a absencí hlídáme i přímo tvůj osobní rozvrh: přes den každých 30 minut a hned, když škola rozvrh přepíše. Kvůli tomu si server uloží tvoje zašifrované přihlášení do SIS. Vypnutím nebo odhlášením se smaže."
                      : ""
                  }`)
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
            <div className="row">
              <span className="row-title">Známky, absence a můj rozvrh</span>
              <Switch label="Notifikace o známkách, absenci a změnách mého rozvrhu" checked={watchOn} onChange={toggleWatch} />
            </div>
          )}
          {push === "on" && (
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
              ji ukázat tady. U zapnutých notifikací adresu pro doručení, tvoji třídu a zkratky tvých předmětů a skupin
              (např. AGJ1), aby ti chodily jen tvoje změny.
            </p>
            <p>
              <b>Heslo:</b> zůstává zašifrované v tomhle zařízení, aby tě aplikace nemusela odhlašovat, když SIS ukončí
              relaci. Do databáze se dostane jen tehdy, když si zapneš notifikace o známkách a absenci, a to zašifrované.
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
