// Shared by the GitHub Action scripts: talking to the Worker's internal API and sending Web Push.
// The repository is public and so are Action logs: never log names, logins, grades or endpoints.
//
// Env: API_URL, INGEST_TOKEN, VAPID_PUBLIC_KEY, VAPID_PRIVATE_JWK
import webpush from "web-push";

const env = (name: string) => {
  const v = process.env[name];
  if (!v) throw new Error(`Missing env ${name}`);
  return v;
};
const API = env("API_URL").replace(/\/$/, "");
const auth = { authorization: `Bearer ${env("INGEST_TOKEN")}` };
webpush.setVapidDetails("https://github.com/oondraa/OpenSISAKHK", env("VAPID_PUBLIC_KEY"), JSON.parse(env("VAPID_PRIVATE_JWK")).d);

export async function getInternal<T>(path: string): Promise<T> {
  const res = await fetch(`${API}/api/internal${path}`, { headers: auth });
  if (!res.ok) throw new Error(`GET ${path}: ${res.status}`);
  return res.json() as Promise<T>;
}

export async function postInternal(path: string, body: unknown) {
  const res = await fetch(`${API}/api/internal${path}`, {
    method: "POST",
    headers: { ...auth, "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`POST ${path}: ${res.status}`);
}

export interface PushTarget {
  endpoint: string;
  p256dh: string;
  auth: string;
}

/** Sends one encrypted notification. Returns "gone" when the subscription no longer exists. */
export async function sendPush(sub: PushTarget, payload: { title: string; body: string; tag: string; url?: string }) {
  try {
    await webpush.sendNotification({ endpoint: sub.endpoint, keys: { p256dh: sub.p256dh, auth: sub.auth } }, JSON.stringify(payload), {
      TTL: 12 * 3600,
      urgency: "high",
    });
    return "sent";
  } catch (e) {
    const status = (e as { statusCode?: number }).statusCode;
    if (status === 404 || status === 410) return "gone";
    console.warn(`push failed: ${status ?? (e as Error).message}`);
    return "failed";
  }
}
