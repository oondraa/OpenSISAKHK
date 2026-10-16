# Vyplatí se zvláštní .apk pro Android?

Stav k 16. 10. 2026, verze 1.17.0.

## Krátce

**Teď ne.** APK sekání nevyřeší, protože by appku vykresloval pořád stejný Chrome. Sekání způsobují konkrétní efekty
v našem CSS a animacích a ty se dají opravit přímo ve webové appce. Oprava pomůže všem Androidům hned, bez instalace
čehokoli. APK má smysl řešit až kvůli Google Play (aby appka šla najít a nainstalovat jako „normální“), ne kvůli výkonu.

## Proč APK sekání nespraví

Možnosti, jak z naší appky udělat APK:

| Varianta | Čím se vykresluje | Pomůže sekání? |
| --- | --- | --- |
| **TWA** (Bubblewrap, PWABuilder) | Přímo Chrome v telefonu, jen bez adresního řádku | Ne, je to doslova totéž jako PWA na ploše |
| **Capacitor** (WebView) | Android System WebView, tedy stejný engine Chromium | Ne, často je to ještě o kousek horší než Chrome |
| **Nativní appka** (Kotlin, Compose) | Nativní UI | Ano, ale znamená to napsat celou appku podruhé a pak dvě udržovat |

Prvním dvěma možnostem se změní jen obal, kód i vykreslování zůstanou stejné. iPhone nesekne, protože Safari
na iPhonu skládá rozmazání a animace na GPU velmi levně a iPhony mají silné GPU. Levnější Androidy (hlášení přišlo
z Androidu 10) mají slabší GPU a přesně tyhle efekty jsou na nich drahé.

## Co pravděpodobně seká (a jde opravit v kódu)

1. **Animace `clip-path` při otevírání obrazovky** (`src/web/layer.tsx`, ř. 81). Každý snímek mění `clip-path: inset(... round ...)`,
   takže prohlížeč musí celou obrazovku znovu vykreslit. Transform a opacity jdou přes GPU skoro zadarmo,
   `clip-path` ne. To je hlavní podezřelý.
2. **Skleněné dlaždice s `backdrop-filter: blur(12px)`** (`.glass` v `styles.css`). Šest dlaždic na ploše a každá
   rozmazává, co je pod ní. Během animace (plocha se zmenšuje přes `scale`) se rozmazání počítá znovu v každém snímku.
3. **Tab bar s `blur(24px)`** přes scrollující obsah. Při každém posunu seznamu se rozmazává znovu.
4. **Velké stíny a radiální gradienty** pod sklem dělají totéž, jen v menším.

## Doporučený postup

1. **Změřit na skutečném Androidu.** Telefon připojit kabelem, v Chromu na PC otevřít `chrome://inspect`, nahrát
   Performance záznam otevření obrazovky a scrollu. Ukáže se, jestli jde čas do „Paint“ (clip-path, blur), nebo do JS.
2. **Odlehčený režim pro Android.** Na Androidu (nebo podle `navigator.deviceMemory` / `hardwareConcurrency`)
   vypnout `backdrop-filter` a místo něj dát plnou poloprůhlednou barvu. Vizuální rozdíl je malý, protože
   pod sklem je stejně jen plynulý gradient. Stejnou úpravu už máme pro „omezit průhlednost“.
3. **Otevírání obrazovky bez `clip-path`.** Animovat jen `transform` a `opacity` (zvětšení karty z dlaždice), zaoblení
   řešit `border-radius` na vnitřním prvku. Pohyb zůstane stejný a GPU to zvládne bez překreslování.
4. Ověřit znovu na stejném telefonu. Pokud to pořád seká, teprve pak má smysl uvažovat o nativní appce.

Body 2 a 3 jsou zhruba jedno odpoledne práce a pomůžou všem Androidům najednou.

## Kdy by APK smysl mělo (nezávisle na výkonu)

- **Google Play.** Appku by šlo najít a nainstalovat „normálně“, bez kroku „Přidat na plochu“. Nejlevnější cesta je
  TWA přes Bubblewrap, kód appky zůstane stejný.
- **Náklady a starosti:** vývojářský účet Google Play stojí jednorázově 25 USD, což jde proti cíli nulových nákladů.
  Nové účty musí před zveřejněním projít uzavřeným testem s testery, Google každý rok zvedá požadovanou verzi
  Androidu a appka nesmí působit jako oficiální appka školy (název, ikona, popis).
- **APK mimo Play** (ke stažení z webu) náklady nemá, ale studenti musí povolit „neznámé zdroje“. Pro appku,
  do které se zadává školní heslo, to vypadá podezřele a sníží to důvěru. Navíc by se aktualizace musely řešit
  ručně, kdežto PWA se aktualizuje sama.
- Notifikace na Androidu fungují přes web push už teď, APK by je nijak výrazně nezlepšilo.

## Nativní appka v Android Studiu (Kotlin + Compose)

Tohle je jediná varianta APK, která opravdu něco změní: UI by vykresloval Android, ne Chrome.

**Co by přineslo**
- Plynulost na slabých telefonech: animace i seznamy nativně, žádné rozmazávání přes webový engine.
- **Widget na plochu** (aktuální hodina, učebna, volná posilovna). Na webu to vůbec nejde a pro studenty by to byla největší výhra.
- Spolehlivější notifikace přes FCM. Web push na Androidu se dá ztratit (kvůli tomu vzniklo 1.15.4), FCM je robustnější.
- Synchronizace na pozadí (WorkManager): rozvrh je načtený dřív, než appku otevřeš.
- Ukládání: Room nebo DataStore místo localStorage. Data, která ukládáme dnes (pár desítek kB), ale localStorage zvládá
  i teď, takže tady je zlepšení malé.

**Co by stálo**
- Napsat znovu celé UI: zhruba 15 obrazovek, plocha s dlaždicemi, animace. Řádově týdny práce.
- Logiku psát znovu nemusíme: přihlášení, čtení SIS, Strava i notifikace běží ve Workeru a nativní appka by volala
  stejné `/api/...`. Jedinou výjimkou je OCR rozvrhu školy, které teď běží v telefonu, takže by se muselo přepsat do Kotlinu
  nebo přesunout na server.
- Od té chvíle každou novou funkci psát dvakrát: web (iPhone, PC) a Android.
- APK z webu: studenti musí povolit instalaci z neznámých zdrojů a aktualizace si appka musí řešit sama
  (kontrola verze a stažení nového APK).
- Ověřování vývojářů od Googlu platí od 30. 9. 2026 zatím jen v Brazílii, Indonésii, Singapuru a Thajsku, globálně od roku 2027.
  Instalace APK mimo obchody zatím funguje dál, ale do roka bude nejspíš potřeba ověřený vývojářský účet i pro APK z webu.

**Rozumný postup, pokud do toho jít:** nejdřív levná oprava webu (výše), a pak nativní appku postupně:
1. Rozvrh, widget a notifikace přes FCM (to, co web neumí).
2. Ostatní obrazovky jako webové stránky ve WebView uvnitř nativní appky, nahrazované nativními postupně.

## Závěr

| | Odlehčit CSS a animace | TWA na Google Play | Nativní appka |
| --- | --- | --- | --- |
| Pomůže sekání | Ano, hlavní příčina | Ne | Ano |
| Cena | 0 Kč, asi den práce | 25 USD a údržba | Týdny práce, dvě appky |
| Dosah | Všichni hned | Jen kdo si stáhne z Play | Jen kdo si stáhne |

Nejdřív odlehčit appku pro Android a změřit to. APK (TWA) případně později jako cestu na Google Play,
ne jako opravu výkonu.
