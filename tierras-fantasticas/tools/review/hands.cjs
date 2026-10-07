// node hands.cjs <outdir> <list.json> : cada objeto de mano en el personaje, de frente en 3/4
const { chromium } = require(process.env.PW);
const fs = require('fs');
const [outdir, list] = process.argv.slice(2);
const items = JSON.parse(fs.readFileSync(list));
fs.mkdirSync(outdir, { recursive: true });
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await (await b.newContext({ viewport: { width: 700, height: 800 } })).newPage();
  await page.goto('http://127.0.0.1:8788/ayuda', { waitUntil: 'networkidle' });
  await page.evaluate(() => { document.body.innerHTML = '<div id="st" style="position:fixed;inset:0;background:#141a26"><canvas style="width:100%;height:100%"></canvas></div>'; });
  for (const [set, slug, type] of items) {
    const f = `${outdir}/${set}__${slug}.png`;
    if (fs.existsSync(f)) continue;
    await page.evaluate(async ({ set, slug, type }) => {
      const lib = await import('/wardrobe.js');
      window.wr ||= lib.createWardrobe(document.querySelector('#st canvas'));
      window.cache ||= {};
      const data = window.cache[set] ||= await (await fetch(`/wear/${set}.json`)).json();
      const outfit = (type === 'shield' || type === 'balloon') ? { offhand: slug } : { hand: slug };
      await window.wr.showPlayer(data, outfit, 'MHF_Steve');
      window.wr.setView(-0.6, 0.1);
    }, { set, slug, type });
    await page.waitForTimeout(150);
    fs.writeFileSync(f, await page.screenshot());
  }
  await b.close();
})();
