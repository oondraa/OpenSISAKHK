# OpenSISAKHK

Neoficiální open-source aplikace pro studenty **SŠ a VOŠ aplikované kybernetiky v Hradci Králové**. Bere data ze
školního systému [SIS (KybernaIS)](https://sis.ssakhk.cz) a ukazuje je tak, aby se dala používat na telefonu.

> Aplikace není spojená se školou ani s autory SIS. Používáš ji na vlastní zodpovědnost.

## Co umí

| Tab | Co ukazuje |
| --- | --- |
| **Rozvrh** | Tvůj osobní rozvrh včetně volitelných předmětů a skupin. Změněné hodiny jsou zvýrazněné, hodiny, které oproti stálému rozvrhu odpadly, jsou v sekci *Odpadá*. Karta „Teď / Další“, swipe mezi dny, oběd, odkaz na Meet. |
| **Gym** | Obsazenost **tělocvičny (T0)** a **posilovny (P0)** po hodinách: kdo tam je (třída, učitel), nebo že je volno. |
| **Známky** | Známky po předmětech s váhou, typem a komentářem a průměr, který počítá SIS. |
| **Nastavení** | Push notifikace o změnách rozvrhu, světlý/tmavý režim, informace o tom, co se ukládá, a odhlášení. |

Je to PWA: na telefonu ji přidáš na plochu (Android: *Nainstalovat aplikaci*, iPhone: *Sdílet → Přidat na plochu*)
a chová se jako normální aplikace, i offline s posledními načtenými daty.

## Jak funguje přihlášení

SIS nemá žádné API, takže aplikace dělá to samé co prohlížeč: přihlásí se formulářem a čte HTML stránky.

```
telefon ──(jméno + heslo, HTTPS)──▶ Cloudflare Worker ──▶ sis.ssakhk.cz/Account/Login
        ◀── zašifrovaná cookie ───┘                    ◀── cookies SIS relace
```

1. Zadáš jméno a heslo do aplikace. Worker si stáhne přihlašovací stránku SIS (kvůli anti-forgery tokenu) a odešle
   formulář s *Remember me*.
2. Cookies SIS relace, tvoje SIS ID **a přihlašovací údaje** zabalí do jedné cookie zašifrované **AES-GCM** klíčem,
   který zná jen server. Cookie je `HttpOnly`, `Secure`, `SameSite=Strict`, takže ji JavaScript na stránce
   nepřečte a jiný web ji nepošle.
3. **Server si nic z toho neukládá.** Cookie existuje jen v tvém zařízení a server ji při každém požadavku jen
   rozšifruje v paměti, použije a zapomene.
4. Když SIS relaci po čase ukončí, Worker se přihlásí znovu údaji z cookie. Proto tě aplikace sama neodhlásí.
   Když si heslo v SIS změníš, aplikace tě odhlásí a přihlásíš se znovu.
5. Odhlášení smaže cookie (a tím i uložené heslo) a odběr notifikací.

Celé je to v [`src/worker/sis.ts`](src/worker/sis.ts), [`src/worker/session.ts`](src/worker/session.ts) a
[`src/worker/index.ts`](src/worker/index.ts).

## Co se ukládá

| Ukládá se (databáze D1) | Neukládá se nikde na serveru |
| --- | --- |
| Jméno a profilová fotka ze SIS (kdo aplikaci používá) | Heslo |
| Kdy ses poprvé a naposledy přihlásil | Známky, rozvrh, absence |
| Adresa pro doručení notifikací (jen když je zapneš) | Cookies SIS relace |
| Otisky (SHA-256) veřejných obrázků rozvrhu školy | |

## Jak funguje rozvrh a Gym

- **Osobní rozvrh** se čte z `/TimeTable/PersonalNew` ve dvou variantách: aktuální a stálý. Z jejich porovnání vznikne
  seznam odpadlých hodin. Změněné hodiny SIS sám označuje.
- **Gym** používá veřejný rozvrh celé školy, který SIS zveřejňuje jen jako obrázky
  (`/TimeTable/{PO,UT,ST,CT,PA}{1,2}.jpg`). Mřížka je na všech obrázcích na stejných pixelech a text je bitmapové
  písmo, takže je čte malé OCR se šablonami znaků ([`src/shared/schoolOcr.ts`](src/shared/schoolOcr.ts)). Běží
  přímo v telefonu přes `<canvas>` a obrázek trvá ~150 ms. Růžové pozadí buňky = změna.
- **Notifikace:** cron ve Workeru každých 10 minut porovná otisky obrázků na zbytek tohoto a příštího týdne. Když se
  některý změní (škola rozvrh přepisuje hlavně v noci), pošle Web Push všem, kdo mají notifikace zapnuté.

## Technologie

- [Cloudflare Workers](https://workers.cloudflare.com) (free tier) servíruje PWA i API, plus [D1](https://developers.cloudflare.com/d1/) a Cron Triggers
- [Hono](https://hono.dev) v API, [node-html-parser](https://github.com/taoqf/node-html-parser) na parsování SIS
- [React](https://react.dev) + [Vite](https://vite.dev) + [vite-plugin-pwa](https://vite-pwa-org.netlify.app)
- Web Push s VAPID bez knihoven (WebCrypto)

## Vývoj

```bash
npm install
cp .dev.vars.example .dev.vars        # a doplň hodnoty (viz níž)
npx wrangler d1 migrations apply opensisakhk --local
npm run dev                           # http://localhost:5173
npm test
```

`.dev.vars` potřebuje:

- `SESSION_SECRET`: libovolný dlouhý náhodný řetězec, třeba `openssl rand -hex 32`
- `VAPID_PUBLIC_KEY` a `VAPID_PRIVATE_JWK`: vygeneruje je `npx tsx scripts/vapid.ts`

Test OCR potřebuje skutečný obrázek, který se do repa necommituje:
`curl -o test/fixtures/PO1-2026-10-12.jpg https://sis.ssakhk.cz/TimeTable/PO1.jpg`. Test čeká rozvrh z 12. 10. 2026,
s jiným dnem uprav očekávané hodnoty.

## Nasazení (zdarma)

```bash
npx wrangler login
npx wrangler d1 create opensisakhk            # id zkopíruj do wrangler.jsonc → database_id
npx wrangler d1 migrations apply opensisakhk --remote
npx wrangler secret put SESSION_SECRET
npx wrangler secret put VAPID_PUBLIC_KEY
npx wrangler secret put VAPID_PRIVATE_JWK
npm run deploy                                # → https://opensisakhk.<účet>.workers.dev
```

Všechno se vejde do free tieru Cloudflare (100 000 požadavků denně).

## Licence

[MIT](LICENSE)
