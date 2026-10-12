// Imported into the generated service worker.
// Change notifications carry an encrypted JSON payload {title, body, tag} (scripts/school-changes.ts);
// the test notification from the Worker has no payload.
self.addEventListener("push", (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    data = { body: event.data && event.data.text() };
  }
  const title = data.title || "OpenSISAKHK";
  event.waitUntil(
    self.registration.showNotification(title, {
      body: data.body || "Notifikace fungují. Ozveme se, až se ti změní rozvrh.",
      icon: "/icon.svg",
      badge: "/icon.svg",
      tag: data.tag || "osis",
      renotify: true,
      data: { url: data.url || "/" },
    }),
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || "/";
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((wins) => {
      const win = wins.find((w) => "focus" in w);
      return win ? win.focus() : self.clients.openWindow(url);
    }),
  );
});
