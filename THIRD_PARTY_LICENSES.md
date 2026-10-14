# Third-party licenses

OpenSISAKHK is built with the following open-source components and fonts. Each remains under its own license
(listed below); the OpenSISAKHK license does not apply to them. Full license texts are in each project's repository,
the font licenses are also in `brand/fonts/`.

## Shipped in the app and the server

| Component | Version | License | Repository |
|---|---|---|---|
| hono | 4.13.13 | MIT | https://github.com/honojs/hono |
| node-html-parser | 9.0.4 | MIT | https://github.com/taoqf/node-html-parser |
| css-select | 5.2.2 | BSD-2-Clause | https://github.com/fb55/css-select |
| css-what | 6.2.2 | BSD-2-Clause | https://github.com/fb55/css-what |
| domhandler | 5.0.3 | BSD-2-Clause | https://github.com/fb55/domhandler |
| domutils | 3.2.2 | BSD-2-Clause | https://github.com/fb55/domutils |
| domelementtype | 2.3.0 | BSD-2-Clause | https://github.com/fb55/domelementtype |
| dom-serializer | 2.0.0 | MIT | https://github.com/cheeriojs/dom-serializer |
| entities | 8.1.0 | BSD-2-Clause | https://github.com/fb55/entities |
| nth-check | 2.1.1 | BSD-2-Clause | https://github.com/fb55/nth-check |
| boolbase | 1.0.0 | ISC | https://github.com/fb55/boolbase |
| react, react-dom | 19.3.0 | MIT | https://github.com/facebook/react |
| scheduler | 0.28.0 | MIT | https://github.com/facebook/react |
| workbox (window, core, precaching, routing, strategies, expiration) | 7.4.1 | MIT | https://github.com/GoogleChrome/workbox |

## Fonts and icons

| Asset | License | Source |
|---|---|---|
| Sora (logo and brand images only, the app uses the system font) | SIL Open Font License 1.1 (`OFL-Sora.txt`) | https://github.com/sora-xor/sora-font |
| JetBrains Mono (brand images only) | SIL Open Font License 1.1 (`OFL-JetBrainsMono.txt`) | https://github.com/JetBrains/JetBrainsMono |
| Gear icon (based on Lucide) | ISC | https://lucide.dev |

## Build and run tools only

Vite, vite-plugin-pwa, @cloudflare/vite-plugin, Wrangler, TypeScript, Vitest, tsx, web-push, jpeg-js, opentype.js and
@resvg/resvg-js are used to build, test, deploy or run the GitHub Action scripts. They are not shipped in the app and
each stays under its own license (see `node_modules/<package>/package.json`).
