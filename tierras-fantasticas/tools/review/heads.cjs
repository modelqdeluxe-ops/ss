// node heads.cjs <outdir> <list.json> : cada casco o sombrero puesto en la cabeza, de frente y de perfil
// (mismo uso que hands.cjs: necesita tools/preview.mjs en el puerto 8788 y PW=$(npm root -g)/playwright)
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
  for (const [set, slug] of items) {
    for (const [tag, yaw] of [['f', 0], ['s', Math.PI / 2]]) {
      const f = `${outdir}/${set}__${slug}__${tag}.png`;
      if (fs.existsSync(f)) continue;
      await page.evaluate(async ({ set, slug, yaw }) => {
        const lib = await import('/wardrobe.js');
        window.wr ||= lib.createWardrobe(document.querySelector('#st canvas'));
        window.cache ||= {};
        const data = window.cache[set] ||= await (await fetch(`/wear/${set}.json`)).json();
        await window.wr.showPlayer(data, { helmet: slug }, 'MHF_Steve');
        window.wr.setView(yaw, 0.1);
      }, { set, slug, yaw });
      await page.waitForTimeout(150);
      fs.writeFileSync(f, await page.screenshot());
    }
  }
  await b.close();
})();
