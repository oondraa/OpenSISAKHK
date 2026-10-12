import { useState, type FormEvent } from "react";
import { api, Unauthorized } from "./api";

export function Login({ onDone }: { onDone: () => void }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.login(username.trim(), password);
      onDone();
    } catch (err) {
      setError(err instanceof Unauthorized ? "Špatné jméno nebo heslo." : "SIS teď neodpovídá. Zkus to za chvíli.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login">
      <img src="/icon.svg" alt="" className="login-icon" />
      <h1 className="large-title">OpenSISAKHK</h1>
      <p className="login-sub">Rozvrh, známky a volná tělocvična. Přihlas se stejně jako do školního SIS.</p>
      <form onSubmit={submit} className="list login-form">
        <input
          autoComplete="username"
          placeholder="Uživatelské jméno"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
        />
        <input
          type="password"
          autoComplete="current-password"
          placeholder="Heslo"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
      </form>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <button className="primary" onClick={submit} disabled={busy || !username || !password}>
        {busy ? "Přihlašuji…" : "Přihlásit se"}
      </button>
      <p className="fineprint">
        Heslo se ukládá jen zašifrované v tomhle zařízení, nikdy na serveru. Neoficiální open-source aplikace, není
        spojená se školou.
      </p>
    </main>
  );
}
