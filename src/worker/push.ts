// Payload-less Web Push with VAPID (RFC 8292). Without a payload nothing needs encrypting:
// the service worker shows a fixed "timetable changed" notification.

export interface VapidKeys {
  publicKey: string; // base64url, uncompressed P-256 point
  privateJwk: string;
}

const b64url = (buf: ArrayBuffer | Uint8Array) =>
  btoa(String.fromCharCode(...new Uint8Array(buf)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");

async function vapidJwt(audience: string, privateJwk: string): Promise<string> {
  const key = await crypto.subtle.importKey("jwk", JSON.parse(privateJwk), { name: "ECDSA", namedCurve: "P-256" }, false, [
    "sign",
  ]);
  const enc = (o: object) => b64url(new TextEncoder().encode(JSON.stringify(o)));
  const unsigned = `${enc({ typ: "JWT", alg: "ES256" })}.${enc({
    aud: audience,
    exp: Math.floor(Date.now() / 1000) + 12 * 3600,
    sub: "https://github.com/oondraa/OpenSISAKHK",
  })}`;
  const sig = await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, new TextEncoder().encode(unsigned));
  return `${unsigned}.${b64url(sig)}`;
}

/** Returns false when the subscription is gone and should be deleted. */
export async function sendPush(endpoint: string, keys: VapidKeys): Promise<boolean> {
  const jwt = await vapidJwt(new URL(endpoint).origin, keys.privateJwk);
  const res = await fetch(endpoint, {
    method: "POST",
    headers: { authorization: `vapid t=${jwt}, k=${keys.publicKey}`, ttl: String(12 * 3600), urgency: "normal" },
  });
  if (res.status === 404 || res.status === 410) return false;
  if (!res.ok) console.warn(`push ${res.status} for ${new URL(endpoint).host}`);
  return true;
}
