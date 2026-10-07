// node backs.cjs <outdir> <list.json> : perfil, espalda y 3/4 de cada cosmético de espalda (con armadura si hay)
const { chromium } = require(process.env.PW);
const fs = require('fs');
const [outdir, list] = process.argv.slice(2);
const items = JSON.parse(fs.readFileSync(list));
fs.mkdirSync(outdir, { recursive: true });
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await (await b.newContext({ viewport: { width: 1000, height: 1200 } })).newPage();
  await page.goto('http://127.0.0.1:8788/ayuda', { waitUntil: 'networkidle' });
  await page.evaluate(() => { document.body.innerHTML = '<div id="st" style="position:fixed;inset:0;background:#141a26"><canvas style="width:100%;height:100%"></canvas></div>'; });
  for (const [set, slug] of items) {
    let i = 0;
    for (const yaw of (process.env.YAWS ? JSON.parse(process.env.YAWS) : [Math.PI / 2, Math.PI, Math.PI * 0.75])) {
      await page.evaluate(async ({ set, slug, yaw }) => {
        const lib = await import('/wardrobe.js');
        window.wr ||= lib.createWardrobe(document.querySelector('#st canvas'));
        const data = await (await fetch(`/wear/${set}.json`)).json();
        await window.wr.showPlayer(data, { back: slug }, 'MHF_Steve');
        window.wr.setView(...(Array.isArray(yaw) ? yaw : [yaw, 0]));
      }, { set, slug, yaw });
      await page.waitForTimeout(250);
      fs.writeFileSync(`${outdir}/${set}__${slug}__${i++}.png`, await page.screenshot());
    }
  }
  await b.close();
})();
