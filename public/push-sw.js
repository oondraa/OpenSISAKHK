// Imported into the generated service worker. Pushes carry no payload (see src/worker/push.ts).
self.addEventListener("push", (event) => {
  event.waitUntil(
    self.registration.showNotification("Změna v rozvrhu", {
      body: "Škola upravila rozvrh. Klepni a podívej se, co se změnilo.",
      icon: "/icon.svg",
      badge: "/icon.svg",
      tag: "timetable-change",
      renotify: true,
    }),
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((wins) => {
      const win = wins.find((w) => "focus" in w);
      return win ? win.focus() : self.clients.openWindow("/");
    }),
  );
});
