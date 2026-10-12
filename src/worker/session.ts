import type { Jar } from "./sis";

// The session lives only inside this encrypted cookie on the student's device; the server stores none of it
// (except the sealed login of students who opt in to grade/absence notifications, see migrations/0003).
// The credentials are kept so an expired SIS session can be renewed silently instead of logging the user out.
export interface Session {
  jar: Jar;
  userId: string;
  cred: { u: string; p: string };
}

const enc = new TextEncoder();
const dec = new TextDecoder();

async function key(secret: string) {
  const raw = await crypto.subtle.digest("SHA-256", enc.encode(secret));
  return crypto.subtle.importKey("raw", raw, "AES-GCM", false, ["encrypt", "decrypt"]);
}

const b64url = (buf: Uint8Array) =>
  btoa(String.fromCharCode(...buf)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

const fromB64url = (s: string) =>
  Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));

export async function seal<T = Session>(value: T, secret: string): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const data = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, await key(secret), enc.encode(JSON.stringify(value)));
  const out = new Uint8Array(iv.length + data.byteLength);
  out.set(iv);
  out.set(new Uint8Array(data), iv.length);
  return b64url(out);
}

export async function unseal<T = Session>(token: string, secret: string): Promise<T | null> {
  try {
    const buf = fromB64url(token);
    const data = await crypto.subtle.decrypt({ name: "AES-GCM", iv: buf.slice(0, 12) }, await key(secret), buf.slice(12));
    return JSON.parse(dec.decode(data));
  } catch {
    return null;
  }
}
