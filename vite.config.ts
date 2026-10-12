import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { cloudflare } from "@cloudflare/vite-plugin";
import { VitePWA } from "vite-plugin-pwa";
import pkg from "./package.json" with { type: "json" };

// Shown in Settings and on the splash. Bump "version" in package.json with every deploy.
const BUILT = new Intl.DateTimeFormat("cs-CZ", { timeZone: "Europe/Prague", dateStyle: "short", timeStyle: "short" }).format(new Date());

export default defineConfig({
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
    __APP_BUILT__: JSON.stringify(BUILT),
  },
  plugins: [
    react(),
    cloudflare(),
    { name: "app-version", transformIndexHtml: (html) => html.replaceAll("%APP_VERSION%", pkg.version) },
    VitePWA({
      // "prompt" so main.tsx can show "Aktualizace" before the new version reloads the page.
      registerType: "prompt",
      includeAssets: ["icon.svg", "favicon-32.png", "apple-touch-icon.png", "fonts/*.woff2"],
      manifest: {
        name: "OpenSISAKHK",
        short_name: "OpenSISAKHK",
        description: "Rozvrh a známky SSAKHK",
        lang: "cs",
        theme_color: "#0e0e10",
        background_color: "#0e0e10",
        display: "standalone",
        start_url: "/",
        icons: [
          { src: "icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
          { src: "icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
          { src: "icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
          { src: "icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any" },
        ],
      },
      workbox: {
        navigateFallbackDenylist: [/^\/api\//],
        importScripts: ["push-sw.js"],
        globPatterns: ["**/*.{js,css,html,svg,png,woff2}"],
      },
    }),
  ],
});
