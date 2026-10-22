// Saca, de cada página de la vista previa (tools/preview.mjs), los textos visibles con su color, tamaño y posición, y
// una captura de la página. Lo usa tools/contrast_audit.py, que mide el contraste (WCAG) contra el fondo REAL (los
// píxeles de la captura alrededor del texto, así sirve también sobre degradados e imágenes).
// Uso: PW=$(npm root -g)/playwright node tools/contrast_audit.cjs <carpeta> / /tienda "/tienda#rangos" ...
const { chromium } = require(process.env.PW || 'playwright');
const fs = require('fs');
(async () => {
  const out = process.argv[2];
  const pages = process.argv.slice(3);
  const browser = await chromium.launch();
  for (const [name, w, h] of [['d', 1440, 900], ['m', 390, 844]]) {
    const ctx = await browser.newContext({ viewport: { width: w, height: h }, isMobile: name === 'm', hasTouch: name === 'm' });
    await ctx.addInitScript(() => { try { sessionStorage.setItem('tf-seen', '1'); } catch {} });
    const page = await ctx.newPage();
    for (const p of pages) {
      await page.goto('http://127.0.0.1:8788' + p, { waitUntil: 'networkidle' });
      await page.evaluate(() => document.querySelectorAll('img[loading=lazy]').forEach((i) => (i.loading = 'eager')));
      await page.waitForTimeout(1500);
      const height = await page.evaluate(() => Math.min(9000, document.documentElement.scrollHeight));
      await page.setViewportSize({ width: w, height });
      await page.waitForTimeout(600);
      const items = await page.evaluate(() => {
        const res = [];
        const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
        const seen = new Set();
        while (walker.nextNode()) {
          const t = walker.currentNode;
          if (!t.textContent.trim()) continue;
          const el = t.parentElement;
          if (!el || seen.has(el)) continue;
          seen.add(el);
          const cs = getComputedStyle(el);
          if (cs.visibility === 'hidden' || +cs.opacity === 0 || el.closest('[hidden],[aria-hidden="true"],.sr-only,dialog:not([open]),.sheet,.loader')) continue;
          const r = document.createRange();
          r.selectNodeContents(t);
          const b = r.getBoundingClientRect();
          if (b.width < 2 || b.height < 2) continue;
          const btn = el.closest('button,.btn,a');
          res.push({ text: t.textContent.trim().slice(0, 40), color: cs.color, size: parseFloat(cs.fontSize), weight: +cs.fontWeight,
            x: b.left + scrollX, y: b.top + scrollY, w: b.width, h: b.height, cls: (btn || el).className.toString().slice(0, 50), tag: el.tagName });
        }
        return res;
      });
      const file = `${out}/${name}${p.replace(/[\/#?=]/g, '_') || '_home'}`;
      await page.screenshot({ path: file + '.png', timeout: 180000 });
      fs.writeFileSync(file + '.json', JSON.stringify(items));
      await page.setViewportSize({ width: w, height: h });
      console.log(file);
    }
    await ctx.close();
  }
  await browser.close();
})();
