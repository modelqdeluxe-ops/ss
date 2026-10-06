// Capturas de la web local (tools/preview.mjs) en escritorio (1440×900) y móvil (390×844), página completa.
// Uso: node tools/screenshots.cjs <carpeta de salida> / /tienda "/tienda#ruleta" /ayuda ...
// Necesita playwright (en este entorno: PW=$(npm root -g)/playwright). Imprime el tamaño de cada página: si el ancho
// pasa de 1440 o 390, algo se sale de la pantalla.
const { chromium } = require(process.env.PW || 'playwright');
(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const out = process.argv[2];
  const pages = process.argv.slice(3);
  for (const [name, w, h] of [['d', 1440, 900], ['m', 390, 844]]) {
    const ctx = await browser.newContext({ viewport: { width: w, height: h }, isMobile: name === 'm', hasTouch: name === 'm' });
    const page = await ctx.newPage();
    page.on('pageerror', (e) => console.log('PAGEERROR', e.message));
    for (const p of pages) {
      await page.goto('http://127.0.0.1:8788' + p, { waitUntil: 'networkidle' });
      await page.evaluate(() => { document.querySelectorAll('.reveal').forEach((e) => e.classList.add('in')); document.querySelectorAll('img[loading=lazy]').forEach((i) => (i.loading = 'eager')); });
      await page.waitForTimeout(1500);
      const file = `${out}/${name}${p.replace(/[\/#?=]/g, '_') || '_home'}.png`;
      await page.screenshot({ path: file, fullPage: true });
      console.log(file, await page.evaluate(() => document.documentElement.scrollWidth + 'x' + document.documentElement.scrollHeight));
    }
    await ctx.close();
  }
  await browser.close();
})();
