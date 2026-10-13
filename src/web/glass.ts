import { reducedMotion } from "./spring";

/**
 * Liquid glass, the parts CSS can't do alone (the material itself lives in styles.css, `.glass`).
 *
 * - Refraction: an element with `data-lens="<bezel px>"` bends what's behind it near its edges, like a thick
 *   convex pane, instead of only blurring it. That needs an SVG displacement filter inside backdrop-filter,
 *   which only Chromium renders; everywhere else the CSS material (blur, rim light, touch glow) stands alone.
 * - Touch glow: a pressed `.glass` lights up from the point under the finger (CSS reads --gx/--gy).
 */
export function initGlass() {
  addEventListener(
    "pointerdown",
    (e) => {
      const el = (e.target as Element | null)?.closest?.<HTMLElement>(".glass");
      if (!el) return;
      const r = el.getBoundingClientRect();
      el.style.setProperty("--gx", `${e.clientX - r.left}px`);
      el.style.setProperty("--gy", `${e.clientY - r.top}px`);
    },
    { capture: true, passive: true },
  );

  const brands = (navigator as { userAgentData?: { brands: { brand: string }[] } }).userAgentData?.brands ?? [];
  if (!brands.some((b) => b.brand === "Chromium")) return;
  if (matchMedia("(prefers-reduced-transparency: reduce)").matches) return;

  const svg = document.createElementNS(NS, "svg");
  svg.setAttribute("aria-hidden", "true");
  svg.style.cssText = "position:absolute;width:0;height:0;overflow:hidden";
  document.body.appendChild(svg);

  const filters = new Map<string, string>(); // size+shape -> filter id; equal tiles share one map
  const resize = new ResizeObserver((entries) => entries.forEach((e) => lens(e.target as HTMLElement)));
  const seen = new WeakSet<Element>();

  function lens(el: HTMLElement) {
    const w = Math.round(el.offsetWidth);
    const h = Math.round(el.offsetHeight);
    if (!w || !h) return;
    const radius = Math.min(parseFloat(getComputedStyle(el).borderTopLeftRadius) || 0, w / 2, h / 2);
    const bezel = Math.min(Number(el.dataset.lens) || 14, w / 2, h / 2);
    const key = `${w}x${h}r${radius}b${bezel}`;
    let id = filters.get(key);
    if (!id) {
      id = `lens-${filters.size}`;
      filters.set(key, id);
      svg.appendChild(filter(id, w, h, radius, bezel));
    }
    el.style.setProperty("--lens", `url(#${id})`);
  }

  const scan = (root: ParentNode) =>
    root.querySelectorAll<HTMLElement>("[data-lens]").forEach((el) => {
      if (seen.has(el)) return;
      seen.add(el);
      resize.observe(el);
    });
  scan(document);
  new MutationObserver(() => scan(document)).observe(document.body, { childList: true, subtree: true });
}

const NS = "http://www.w3.org/2000/svg";

/**
 * Displacement map for a rounded rect: neutral grey inside, and within `bezel` px of the edge each pixel samples
 * the backdrop from further in along the edge normal, strongest right at the rim (the slope of a rounded edge),
 * so straight lines behind the glass curve as they reach it.
 */
function filter(id: string, w: number, h: number, radius: number, bezel: number) {
  const canvas = document.createElement("canvas");
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext("2d")!;
  const img = ctx.createImageData(w, h);
  const hx = w / 2 - radius;
  const hy = h / 2 - radius;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const px = x + 0.5 - w / 2;
      const py = y + 0.5 - h / 2;
      const qx = Math.abs(px) - hx;
      const qy = Math.abs(py) - hy;
      const corner = qx > 0 && qy > 0;
      // Distance from the edge, inward: corners measure to the arc, sides to the straight edge.
      const d = corner ? radius - Math.hypot(qx, qy) : radius - Math.max(qx, qy);
      let dx = 0;
      let dy = 0;
      if (d >= 0 && d < bezel) {
        const t = 1 - d / bezel;
        const mag = 1 - Math.sqrt(1 - t * t); // circular bevel: flat inside, steep at the rim
        let nx: number;
        let ny: number;
        if (corner) {
          const l = Math.hypot(qx, qy) || 1;
          nx = qx / l;
          ny = qy / l;
        } else if (qx > qy) {
          nx = 1;
          ny = 0;
        } else {
          nx = 0;
          ny = 1;
        }
        dx = -Math.sign(px) * nx * mag;
        dy = -Math.sign(py) * ny * mag;
      }
      const i = (y * w + x) * 4;
      img.data[i] = 128 + Math.round(dx * 127);
      img.data[i + 1] = 128 + Math.round(dy * 127);
      img.data[i + 2] = 128;
      img.data[i + 3] = 255;
    }
  }
  ctx.putImageData(img, 0, 0);

  const f = document.createElementNS(NS, "filter");
  f.id = id;
  for (const [k, v] of Object.entries({ x: 0, y: 0, width: w, height: h, filterUnits: "userSpaceOnUse", "color-interpolation-filters": "sRGB" }))
    f.setAttribute(k, String(v));
  const image = document.createElementNS(NS, "feImage");
  for (const [k, v] of Object.entries({ href: canvas.toDataURL(), x: 0, y: 0, width: w, height: h, preserveAspectRatio: "none", result: "map" }))
    image.setAttribute(k, String(v));
  const displace = document.createElementNS(NS, "feDisplacementMap");
  for (const [k, v] of Object.entries({ in: "SourceGraphic", in2: "map", scale: Math.round(bezel * 1.6), xChannelSelector: "R", yChannelSelector: "G" }))
    displace.setAttribute(k, String(v));
  f.appendChild(image);
  f.appendChild(displace);
  return f;
}

/**
 * A selection pill on the move turns briefly into clear glass and swells, like a lens sliding over the labels,
 * then settles back into its solid colour.
 */
export function lift(el: HTMLElement | null) {
  if (!el || reducedMotion()) return;
  el.classList.add("lifted");
  el.animate([{ scale: "1" }, { scale: "1.14 1.2", offset: 0.35 }, { scale: "1" }], { duration: 460, easing: "cubic-bezier(0.32, 0.72, 0, 1)" });
  clearTimeout(Number(el.dataset.liftTimer));
  el.dataset.liftTimer = String(setTimeout(() => el.classList.remove("lifted"), 260));
}
