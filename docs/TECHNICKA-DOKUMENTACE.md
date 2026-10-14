# Technická dokumentace

Jak OpenSISAKHK funguje uvnitř, jak ho vyvíjet a nasadit. Představení appky je v [README](../README.md).

Aplikace bere data ze školního systému [SIS (KybernaIS)](https://sis.ssakhk.cz), který nemá žádné API, a ze
[Strava.cz](https://app.strava.cz).

> Aplikace není spojená se školou ani s autory SIS. Používáš ji na vlastní zodpovědnost.

## Co umí

| Tab | Co ukazuje |
| --- | --- |
| **Rozvrh** | Tvůj osobní rozvrh včetně volitelných předmětů a skupin. Změněné hodiny jsou zvýrazněné. Karta „Teď / Další“, swipe mezi dny, oběd (s propojenou Stravou i jaké číslo máš objednané), odkaz na Meet. |
| **Gym** | Jestli je **posilovna (P0)** teď volná, a po hodinách, která třída tam je. |
| **Známky** | Známky po předmětech s váhou, typem a komentářem a průměr, který počítá SIS. |
| **Absence** | Zameškané hodiny po předmětech proti hranici 30–35 % a kolik hodin ještě musíš odchodit (nebo můžeš vynechat) do konce pololetí. |
| **Nastavení** | Push notifikace o změnách rozvrhu, nových známkách a absenci, světlý/tmavý režim, informace o tom, co se ukládá, verze a odhlášení. |

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
   rozšifruje v paměti, použije a zapomene. Jediná výjimka je, když si zapneš notifikace
   ([viz níž](#notifikace)).
4. Když SIS relaci po čase ukončí, Worker se přihlásí znovu údaji z cookie. Proto tě aplikace sama neodhlásí.
   Když si heslo v SIS změníš, aplikace tě odhlásí a přihlásíš se znovu.
5. Odhlášení smaže cookie (a tím i uložené heslo), odběr notifikací a případně uložené přihlášení pro kontrolu známek.

Celé je to v [`src/worker/sis.ts`](../src/worker/sis.ts), [`src/worker/session.ts`](../src/worker/session.ts) a
[`src/worker/index.ts`](../src/worker/index.ts).

## Co se ukládá

| Ukládá se (databáze D1) | Neukládá se nikde na serveru |
| --- | --- |
| Z profilu v SIS: jméno, profilová fotka, školní e-mail, skupiny v rozdělení třídy, volitelné bloky a projekty (obnoví se při každém přihlášení) | Heslo (kromě zapnutých notifikací, viz níž) |
| Třída: SIS ji nikde neukazuje, aplikace ji odhadne porovnáním osobního rozvrhu s rozvrhem školy a student ji potvrdí nebo změní (`src/web/classGuess.ts`, `ClassPrompt.tsx`) |
| Kdy ses poprvé a naposledy přihlásil | Známky, rozvrh, absence, finance (ani v zařízení) |
| Otisky obrázků veřejného rozvrhu školy (aby se poznalo, že ho škola přepsala) | Cookies SIS relace |
| **Jen u zapnutých notifikací:** adresa pro doručení, přihlášení do SIS zašifrované AES-GCM a krátké hashe známek, absencí a hodin rozvrhu (z nich se nic vyčíst nedá) | Přihlášení do Stravy |

## Jak funguje rozvrh a Gym

- **Osobní rozvrh** se čte z `/TimeTable/PersonalNew`. Změněné hodiny SIS sám označuje.
- **Gym** používá veřejný rozvrh celé školy, který SIS zveřejňuje jen jako obrázky
  (`/TimeTable/{PO,UT,ST,CT,PA}{1,2}.jpg`). Mřížka je na všech obrázcích na stejných pixelech a text je bitmapové
  písmo, takže je čte malé OCR se šablonami znaků ([`src/shared/schoolOcr.ts`](../src/shared/schoolOcr.ts)). Běží
  přímo v telefonu přes `<canvas>` a obrázek trvá ~150 ms. Růžové pozadí buňky = změna.

## Obědy ze Strava.cz

V Nastavení jde propojit účet školní jídelny (Strava.cz, jídelna `0288`). Strava nemá veřejné API, takže Worker
používá JSON API, které je za webem app.strava.cz ([`src/worker/strava.ts`](../src/worker/strava.ts)). Jméno a heslo do
Stravy jsou jen ve stejné zašifrované cookie jako přihlášení do SIS, server je neukládá. Při otevření rozvrhu se
Worker přihlásí, přečte objednávky na příští ~3 týdny, odhlásí se a u oběda ukáže číslo a název jídla, nebo
„Nemáš objednáno“. Objednává se dál ve Stravě.

## Notifikace

V Nastavení je jeden přepínač: změny tvého rozvrhu, nové známky a nová absence. Server nemá tvoje heslo, takže sám
nepozná, že se ti něco změnilo. Proto se po zapnutí uloží tvoje přihlášení zašifrované do D1 a
[GitHub Action](../.github/workflows/watch.yml) se za každého, kdo to má zapnuté, přihlásí do SIS, porovná známky,
absenci a osobní rozvrh (tento a příští týden) s minulou kontrolou (jen přes hashe) a pošle push s tím, co je nové,
třeba *„Út 13. 10.: 8:00 MAT · 105 · NO, 8:55 odpadá“*.

```
Worker cron (každých 5 min) ── porovná SHA-256 obrázků rozvrhu školy ──▶ změna? ──▶ spustí Action hned
GitHub Action (+ každých 30 min přes den) ── přihlásí se za každého ── diff ──▶ Web Push
```

Škola rozvrh přepisuje hlavně večer a v noci, proto ho Worker hlídá nepřetržitě a Action spustí do pár minut od
změny. Vypnutím notifikací nebo odhlášením se přihlášení smaže. Když si změníš heslo v SIS, kontrola se vypne a
přijde ti o tom zpráva. Notifikace je šifrovaná (RFC 8291), takže push služba Googlu/Applu obsah nevidí.

Kontrola běží v GitHub Action, ne ve Workeru (10 ms CPU a 50 dotazů na spuštění by na desítky lidí nestačilo), takže
Cloudflare skoro nezatěžuje. Logy Action jsou veřejné, proto skript nikdy nevypisuje jména, známky ani adresy.

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

Jen pro oficiální instanci. Provozovat vlastní kopii pro ostatní licence nedovoluje (viz [Licence](#licence)).

Pokud máš ve wrangleru přihlášený jiný Cloudflare účet, nejdřív `npx wrangler logout`. Po přihlášení dej `account_id`
z `npx wrangler whoami` do `wrangler.jsonc`, ať se to omylem nenasadí jinam.

```bash
npx wrangler login
npx wrangler d1 create opensisakhk            # id zkopíruj do wrangler.jsonc → database_id
npx wrangler d1 migrations apply opensisakhk --remote
npx wrangler secret put SESSION_SECRET        # openssl rand -hex 32
npx wrangler secret put VAPID_PUBLIC_KEY      # npx tsx scripts/vapid.ts
npx wrangler secret put VAPID_PRIVATE_JWK
npx wrangler secret put INGEST_TOKEN          # openssl rand -hex 32
npx wrangler secret put GITHUB_TOKEN          # fine-grained token: jen tohle repo, Contents: Read and write
npm run deploy                                # → https://opensisakhk.<účet>.workers.dev
```

Do GitHub repa (*Settings → Secrets and variables → Actions*) přidej `API_URL` (adresa Workeru), `INGEST_TOKEN`,
`VAPID_PUBLIC_KEY` a `VAPID_PRIVATE_JWK`, stejné hodnoty jako ve Workeru. `GITHUB_TOKEN` ve Workeru je volitelný: bez
něj Action poběží jen každých 30 minut přes den, ne hned po změně rozvrhu.

Všechno se vejde do free tieru Cloudflare (100 000 požadavků denně) a GitHub Actions (veřejné repo).

## Brand

Barvy Ember `#FF5A1F`, Ember tmavá `#D9440F`, Ink `#0E0E10`, Paper `#F6F3EE`. Aplikace
píše systémovým písmem (SF Pro na iPhonu, Roboto na Androidu), čísla s tabulkovými číslicemi. Logo a obrázky značky
jsou vysázené v [Sora](https://fonts.google.com/specimen/Sora) (SIL OFL 1.1, `brand/fonts/`).
Ikony, wordmark a GitHub social preview generuje `npx tsx scripts/brand.ts` do `public/` a `brand/`.

## Licence

Zdrojový kód je veřejný k nahlédnutí, ale **není open source**. Platí [OpenSISAKHK Personal Use License](../LICENSE):
appku i kód smíš používat jen pro sebe a nekomerčně. Šířit, provozovat pro ostatní nebo komerčně používat je bez
písemného svolení autora zakázané. Verze před 1.5.0 byly pod licencí MIT.

Cizí knihovny, písma a ikona ozubeného kola (z [Lucide](https://lucide.dev)) mají vlastní licence, viz
[THIRD_PARTY_LICENSES.md](../THIRD_PARTY_LICENSES.md).
