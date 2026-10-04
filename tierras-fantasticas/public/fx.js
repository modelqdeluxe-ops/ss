// Efectos visuales: cielo animado con bloques flotantes y luciérnagas, destello del logo,
// letras del título que saltan e inclinación 3D de las tarjetas.
(() => {
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // ---------- Cielo animado ----------
  const canvas = document.getElementById('sky');
  if (canvas) {
    const ctx = canvas.getContext('2d');
    // Colores de cada material: [cara superior, cara izquierda, cara derecha]
    const MATERIALS = [
      ['#6cb83a', '#7a4a26', '#4a2c16'], // hierba
      ['#8a909b', '#5d636e', '#3f444e'], // piedra
      ['#8a909b', '#5d636e', '#3f444e'],
      ['#ffd667', '#f4b52e', '#b87412'], // oro
      ['#7fa6ff', '#3f74ea', '#1d3b94'], // zafiro
      ['#ffffff', '#d8dbe0', '#aab0ba'], // cuarzo
      ['#6cb83a', '#7a4a26', '#4a2c16'],
    ];
    let w = 0, h = 0, dpr = 1;
    let cubes = [], stars = [], flies = [];
    let mouseX = 0, mouseY = 0, targetX = 0, targetY = 0;

    // Brillo de luciérnaga pre-renderizado (más barato que un gradiente por frame).
    const glow = document.createElement('canvas');
    glow.width = glow.height = 64;
    const g = glow.getContext('2d');
    const grad = g.createRadialGradient(32, 32, 0, 32, 32, 32);
    grad.addColorStop(0, 'rgba(255, 214, 120, 1)');
    grad.addColorStop(0.25, 'rgba(255, 180, 74, 0.55)');
    grad.addColorStop(1, 'rgba(255, 180, 74, 0)');
    g.fillStyle = grad;
    g.fillRect(0, 0, 64, 64);

    const rand = (a, b) => a + Math.random() * (b - a);

    function makeCube(y) {
      const size = rand(5, 22);
      const depth = size / 22;
      return {
        x: rand(0, w), y: y ?? rand(0, h), size, depth,
        speed: 0.08 + depth * 0.32,
        sway: rand(4, 16), phase: rand(0, Math.PI * 2),
        mat: MATERIALS[Math.floor(Math.random() * MATERIALS.length)],
        alpha: 0.25 + depth * 0.6,
      };
    }

    function resize() {
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      w = window.innerWidth;
      h = window.innerHeight;
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const count = Math.max(14, Math.min(42, Math.round((w * h) / 36000)));
      cubes = Array.from({ length: count }, () => makeCube());
      cubes.sort((a, b) => a.depth - b.depth);
      stars = Array.from({ length: Math.round((w * h) / 9000) }, () => ({
        x: rand(0, w), y: rand(0, h * 0.75), r: rand(0.5, 1.6), phase: rand(0, Math.PI * 2), speed: rand(0.6, 1.8),
      }));
      flies = Array.from({ length: Math.max(10, Math.round(w / 60)) }, () => ({
        x: rand(0, w), y: rand(h * 0.2, h), r: rand(10, 22), phase: rand(0, Math.PI * 2), dx: rand(-0.25, 0.25), dy: rand(-0.2, 0.1),
      }));
    }

    function drawCube(c, x, y) {
      const s = c.size;
      const k = s * 0.866;
      ctx.globalAlpha = c.alpha;
      ctx.lineWidth = Math.max(1, s / 12);
      ctx.strokeStyle = '#07090f';
      const faces = [
        [c.mat[0], [[x, y - s], [x + k, y - s / 2], [x, y], [x - k, y - s / 2]]],
        [c.mat[1], [[x - k, y - s / 2], [x, y], [x, y + s], [x - k, y + s / 2]]],
        [c.mat[2], [[x, y], [x + k, y - s / 2], [x + k, y + s / 2], [x, y + s]]],
      ];
      for (const [color, pts] of faces) {
        ctx.beginPath();
        ctx.moveTo(pts[0][0], pts[0][1]);
        for (let i = 1; i < pts.length; i++) ctx.lineTo(pts[i][0], pts[i][1]);
        ctx.closePath();
        ctx.fillStyle = color;
        ctx.fill();
        if (s > 9) ctx.stroke();
      }
    }

    let last = performance.now();
    function frame(now) {
      const dt = Math.min(3, (now - last) / 16.67);
      last = now;
      const t = now / 1000;
      mouseX += (targetX - mouseX) * 0.05;
      mouseY += (targetY - mouseY) * 0.05;
      const scroll = window.scrollY;

      ctx.clearRect(0, 0, w, h);

      // Estrellas que titilan
      ctx.fillStyle = '#eef0f3';
      for (const s of stars) {
        ctx.globalAlpha = 0.25 + 0.6 * (0.5 + 0.5 * Math.sin(t * s.speed + s.phase));
        ctx.fillRect(s.x, s.y - (scroll * 0.03) % h, s.r, s.r);
      }

      // Bloques flotando hacia arriba, con parallax de ratón y scroll
      for (const c of cubes) {
        c.y -= c.speed * dt;
        if (c.y < -60) Object.assign(c, makeCube(h + 60));
        const span = h + 120;
        const y = ((((c.y - scroll * c.depth * 0.25) % span) + span) % span) - 60;
        const x = c.x + Math.sin(t * 0.5 + c.phase) * c.sway + mouseX * c.depth * 26;
        drawCube(c, x, y + mouseY * c.depth * 16);
      }

      // Luciérnagas del farol
      ctx.globalCompositeOperation = 'lighter';
      for (const f of flies) {
        f.x += (f.dx + Math.sin(t * 0.7 + f.phase) * 0.3) * dt;
        f.y += (f.dy + Math.cos(t * 0.5 + f.phase) * 0.25) * dt;
        if (f.x < -30) f.x = w + 30;
        if (f.x > w + 30) f.x = -30;
        if (f.y < -30) f.y = h + 30;
        if (f.y > h + 30) f.y = -30;
        ctx.globalAlpha = 0.25 + 0.55 * (0.5 + 0.5 * Math.sin(t * 2 + f.phase));
        ctx.drawImage(glow, f.x - f.r, f.y - f.r, f.r * 2, f.r * 2);
      }
      ctx.globalCompositeOperation = 'source-over';
      ctx.globalAlpha = 1;

      if (!reduceMotion && !document.hidden) requestAnimationFrame(frame);
      else running = false;
    }

    let running = false;
    function start() {
      if (running) return;
      running = true;
      last = performance.now();
      requestAnimationFrame(frame);
    }

    resize();
    window.addEventListener('resize', resize);
    window.addEventListener('pointermove', (e) => {
      targetX = (e.clientX / w) * 2 - 1;
      targetY = (e.clientY / h) * 2 - 1;
    });
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden && !reduceMotion) start();
    });
    start();
  }

  // ---------- Destello del logo: usa el propio logo como máscara ----------
  const logo = document.getElementById('hero-logo');
  const shine = document.getElementById('logo-shine');
  if (logo && shine) {
    const mask = `url("${logo.currentSrc || logo.src}")`;
    shine.style.webkitMaskImage = mask;
    shine.style.maskImage = mask;
  }

  // ---------- Letras del título que saltan una tras otra ----------
  window.tfSplitTitle = () => {
    const title = document.getElementById('hero-title');
    if (!title) return;
    const text = title.textContent.trim();
    const words = text.split(' ');
    title.setAttribute('aria-label', text);
    let i = 0;
    title.innerHTML = words
      .map((word, wi) =>
        `<span style="white-space:nowrap" aria-hidden="true">${[...word]
          .map((ch) => `<span class="ch${wi === words.length - 1 ? ' gold' : ''}" style="--i:${i++}">${ch.replace(/[&<>]/g, '')}</span>`)
          .join('')}</span>`,
      )
      .join(' ');
  };
  window.tfSplitTitle();

  // ---------- Inclinación 3D de tarjetas al pasar el ratón ----------
  if (!reduceMotion) {
    document.addEventListener('pointermove', (e) => {
      if (e.pointerType !== 'mouse') return;
      const card = e.target.closest?.('.feature, .product');
      document.querySelectorAll('[data-tilt]').forEach((el) => {
        if (el !== card) {
          el.style.removeProperty('--rx');
          el.style.removeProperty('--ry');
          el.removeAttribute('data-tilt');
        }
      });
      if (!card) return;
      const r = card.getBoundingClientRect();
      const px = (e.clientX - r.left) / r.width - 0.5;
      const py = (e.clientY - r.top) / r.height - 0.5;
      card.style.setProperty('--ry', `${(px * 10).toFixed(2)}deg`);
      card.style.setProperty('--rx', `${(-py * 10).toFixed(2)}deg`);
      card.setAttribute('data-tilt', '');
    });
  }
})();
