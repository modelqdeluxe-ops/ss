// Visor 3D de los objetos de los crates: carga el modelo de Minecraft (elementos + texturas, con sus animaciones)
// y lo deja girar con el ratón o el dedo. Se carga solo cuando alguien abre un objeto.
import * as THREE from '/vendor/three.module.min.js';

const FACES = {
  north: (f, t) => [[t[0], t[1], f[2]], [f[0], t[1], f[2]], [t[0], f[1], f[2]]],
  south: (f, t) => [[f[0], t[1], t[2]], [t[0], t[1], t[2]], [f[0], f[1], t[2]]],
  east: (f, t) => [[t[0], t[1], t[2]], [t[0], t[1], f[2]], [t[0], f[1], t[2]]],
  west: (f, t) => [[f[0], t[1], f[2]], [f[0], t[1], t[2]], [f[0], f[1], f[2]]],
  up: (f, t) => [[f[0], t[1], f[2]], [t[0], t[1], f[2]], [f[0], t[1], t[2]]],
  down: (f, t) => [[f[0], f[1], t[2]], [t[0], f[1], t[2]], [f[0], f[1], f[2]]],
};

const ICON_X =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg>';
const ICON_PREV =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M15 6l-6 6 6 6"/></svg>';
const ICON_NEXT =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9 6l6 6-6 6"/></svg>';

function rotate(p, axis, deg, origin) {
  const a = THREE.MathUtils.degToRad(deg);
  const c = Math.cos(a);
  const s = Math.sin(a);
  const [x, y, z] = [p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]];
  let r;
  if (axis === 'x') r = [x, c * y - s * z, s * y + c * z];
  else if (axis === 'y') r = [c * x + s * z, y, -s * x + c * z];
  else r = [c * x - s * y, s * x + c * y, z];
  return [r[0] + origin[0], r[1] + origin[1], r[2] + origin[2]];
}

// Luz fija por cara, como Minecraft: arriba más clara, abajo más oscura.
function shadeFor(n) {
  if (n.y > 0.5) return 1;
  if (n.y < -0.5) return 0.55;
  return Math.abs(n.z) >= Math.abs(n.x) ? 0.85 : 0.7;
}

// Texturas (con animación de fotogramas como los .mcmeta del juego)
export async function loadTextures(model) {
  const out = {};
  await Promise.all(
    Object.entries(model.textures).map(async ([key, info]) => {
      const img = new Image();
      img.src = info.src;
      await img.decode();
      const canvas = document.createElement('canvas');
      canvas.width = info.fw;
      canvas.height = info.fh;
      const ctx = canvas.getContext('2d');
      const tex = new THREE.CanvasTexture(canvas);
      tex.magFilter = THREE.NearestFilter;
      tex.minFilter = THREE.NearestFilter;
      tex.generateMipmaps = false;
      tex.flipY = false;
      tex.colorSpace = THREE.SRGBColorSpace;
      const frames = info.frames || [0];
      const draw = (i) => {
        ctx.clearRect(0, 0, info.fw, info.fh);
        ctx.drawImage(img, 0, frames[i] * info.fh, info.fw, info.fh, 0, 0, info.fw, info.fh);
        tex.needsUpdate = true;
      };
      draw(0);
      out[key] = { tex, frames, frametime: Math.max(1, info.frametime || 1) * 50, draw, img, info };
    }),
  );
  return out;
}

export function buildElements(model, textures) {
  const buckets = {};
  const bucket = (key) => (buckets[key] ||= { pos: [], uv: [], col: [] });
  for (const el of model.elements) {
    const rot = el.rotation && el.rotation.angle ? el.rotation : null;
    for (const [face, fd] of Object.entries(el.faces || {})) {
      const key = String(fd.texture || '#0').replace('#', '');
      if (!textures[key]) continue;
      let [tl, tr, bl] = FACES[face](el.from, el.to);
      if (rot) [tl, tr, bl] = [tl, tr, bl].map((p) => rotate(p, rot.axis, rot.angle, rot.origin));
      const br = [tr[0] + bl[0] - tl[0], tr[1] + bl[1] - tl[1], tr[2] + bl[2] - tl[2]];
      const e1 = new THREE.Vector3(tr[0] - tl[0], tr[1] - tl[1], tr[2] - tl[2]);
      const e2 = new THREE.Vector3(bl[0] - tl[0], bl[1] - tl[1], bl[2] - tl[2]);
      const n = new THREE.Vector3().crossVectors(e2, e1).normalize();
      const shade = textures[key].info.emissive ? 1 : shadeFor(n);
      const [u1, v1, u2, v2] = fd.uv || [0, 0, 16, 16];
      const r = ((fd.rotation || 0) % 360 + 360) % 360;
      const uvAt = (s, t) => {
        let [a, b] = [s, t];
        if (r === 90) [a, b] = [t, 1 - s];
        else if (r === 180) [a, b] = [1 - s, 1 - t];
        else if (r === 270) [a, b] = [1 - t, s];
        return [(u1 + a * (u2 - u1)) / 16, (v1 + b * (v2 - v1)) / 16];
      };
      const b = bucket(key);
      for (const [p, s, t] of [
        [tl, 0, 0], [bl, 0, 1], [tr, 1, 0],
        [tr, 1, 0], [bl, 0, 1], [br, 1, 1],
      ]) {
        b.pos.push(...p);
        b.uv.push(...uvAt(s, t));
        b.col.push(shade, shade, shade);
      }
    }
  }
  return buckets;
}

// Objetos planos (item/generated): la textura se extruye 1 píxel de grosor, igual que en el juego.
export function buildGenerated(model, textures) {
  const key = model.generated;
  const t = textures[key];
  const { fw, fh } = t.info;
  const canvas = document.createElement('canvas');
  canvas.width = fw;
  canvas.height = fh;
  const ctx = canvas.getContext('2d');
  ctx.drawImage(t.img, 0, t.frames[0] * fh, fw, fh, 0, 0, fw, fh);
  const data = ctx.getImageData(0, 0, fw, fh).data;
  const solid = (x, y) => x >= 0 && y >= 0 && x < fw && y < fh && data[(y * fw + x) * 4 + 3] > 25;
  const px = 16 / fw;
  const z0 = 7.5;
  const z1 = 8.5;
  const b = { pos: [], uv: [], col: [] };
  const quad = (a, c, d, e, uvs, shade) => {
    for (const [p, uv] of [[a, uvs[0]], [d, uvs[2]], [c, uvs[1]], [c, uvs[1]], [d, uvs[2]], [e, uvs[3]]]) {
      b.pos.push(...p);
      b.uv.push(...uv);
      b.col.push(shade, shade, shade);
    }
  };
  // Caras delantera y trasera
  quad([0, 16, z1], [16, 16, z1], [0, 0, z1], [16, 0, z1], [[0, 0], [1, 0], [0, 1], [1, 1]], 1);
  quad([16, 16, z0], [0, 16, z0], [16, 0, z0], [0, 0, z0], [[1, 0], [0, 0], [1, 1], [0, 1]], 0.85);
  for (let y = 0; y < fh; y++) {
    for (let x = 0; x < fw; x++) {
      if (!solid(x, y)) continue;
      const u0 = x / fw;
      const v0 = y / fh;
      const u1 = (x + 1) / fw;
      const v1 = (y + 1) / fh;
      const X0 = x * px;
      const X1 = (x + 1) * px;
      const Y1 = 16 - y * px;
      const Y0 = 16 - (y + 1) * px;
      const uvs = [[u0, v0], [u1, v0], [u0, v1], [u1, v1]];
      if (!solid(x, y - 1)) quad([X0, Y1, z0], [X1, Y1, z0], [X0, Y1, z1], [X1, Y1, z1], uvs, 1);
      if (!solid(x, y + 1)) quad([X0, Y0, z1], [X1, Y0, z1], [X0, Y0, z0], [X1, Y0, z0], uvs, 0.55);
      if (!solid(x - 1, y)) quad([X0, Y1, z0], [X0, Y1, z1], [X0, Y0, z0], [X0, Y0, z1], uvs, 0.7);
      if (!solid(x + 1, y)) quad([X1, Y1, z1], [X1, Y1, z0], [X1, Y0, z1], [X1, Y0, z0], uvs, 0.7);
    }
  }
  return { [key]: b };
}

export function toMesh(buckets, textures) {
  const group = new THREE.Group();
  for (const [key, b] of Object.entries(buckets)) {
    if (!b.pos.length) continue;
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(b.pos, 3));
    geo.setAttribute('uv', new THREE.Float32BufferAttribute(b.uv, 2));
    geo.setAttribute('color', new THREE.Float32BufferAttribute(b.col, 3));
    const mat = new THREE.MeshBasicMaterial({
      map: textures[key].tex,
      vertexColors: true,
      alphaTest: 0.1,
      side: THREE.DoubleSide,
    });
    group.add(new THREE.Mesh(geo, mat));
  }
  return group;
}

// Pone de pie los objetos alargados (espadas, lanzas...) para que el giro se vea natural.
export function uprightAngle(object) {
  const pts = [];
  object.updateMatrixWorld(true);
  const v = new THREE.Vector3();
  object.traverse((o) => {
    if (!o.isMesh) return;
    const pos = o.geometry.attributes.position;
    for (let i = 0; i < pos.count; i += 3) pts.push(v.fromBufferAttribute(pos, i).applyMatrix4(o.matrixWorld).clone());
  });
  if (pts.length < 3) return 0;
  const mx = pts.reduce((a, p) => a + p.x, 0) / pts.length;
  const my = pts.reduce((a, p) => a + p.y, 0) / pts.length;
  let sxx = 0;
  let syy = 0;
  let sxy = 0;
  for (const p of pts) {
    sxx += (p.x - mx) ** 2;
    syy += (p.y - my) ** 2;
    sxy += (p.x - mx) * (p.y - my);
  }
  let theta = 0.5 * Math.atan2(2 * sxy, sxx - syy);
  // En la vista de inventario la punta del arma mira hacia arriba: conservamos ese sentido.
  if (Math.sin(theta) < 0) theta += Math.PI;
  const tr = sxx + syy;
  const det = sxx * syy - sxy * sxy;
  const l1 = tr / 2 + Math.sqrt(Math.max(0, (tr * tr) / 4 - det));
  const l2 = tr / 2 - Math.sqrt(Math.max(0, (tr * tr) / 4 - det));
  if (l2 <= 0 || l1 / l2 < 3) return 0;
  return Math.PI / 2 - theta;
}

let ui = null;

function createUi() {
  const dialog = document.createElement('dialog');
  dialog.className = 'viewer';
  dialog.setAttribute('aria-labelledby', 'viewer-title');
  dialog.innerHTML = `
    <div class="viewer-top">
      <div><span class="cat" id="viewer-set"></span><h3 id="viewer-title"></h3></div>
      <button type="button" class="dialog-close" data-viewer-close aria-label="Cerrar">${ICON_X}</button>
    </div>
    <div class="viewer-stage">
      <canvas aria-label="Modelo 3D del objeto"></canvas>
      <p class="viewer-status" id="viewer-status">Cargando modelo…</p>
      <button type="button" class="viewer-nav prev" data-viewer-prev aria-label="Anterior">${ICON_PREV}</button>
      <button type="button" class="viewer-nav next" data-viewer-next aria-label="Siguiente">${ICON_NEXT}</button>
    </div>
    <p class="viewer-hint">Arrastra para girarlo · <span id="viewer-count"></span></p>`;
  document.body.append(dialog);
  const canvas = dialog.querySelector('canvas');
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(32, 1, 0.1, 500);
  const pivot = new THREE.Group();
  scene.add(pivot);

  const state = { items: [], index: 0, title: '', yaw: 0, pitch: 0.12, drag: null, idleAt: 0, raf: 0, textures: {}, token: 0 };

  const resize = () => {
    const rect = canvas.parentElement.getBoundingClientRect();
    renderer.setSize(rect.width, rect.height, false);
    camera.aspect = rect.width / Math.max(1, rect.height);
    camera.updateProjectionMatrix();
  };

  const loop = (time) => {
    state.raf = requestAnimationFrame(loop);
    if (!state.drag && time - state.idleAt > 1800) state.yaw += 0.008;
    pivot.rotation.set(state.pitch, state.yaw, 0);
    for (const t of Object.values(state.textures)) {
      if (t.frames.length < 2) continue;
      const i = Math.floor(time / t.frametime) % t.frames.length;
      if (i !== t.current) {
        t.current = i;
        t.draw(i);
      }
    }
    renderer.render(scene, camera);
  };

  canvas.addEventListener('pointerdown', (e) => {
    state.drag = { x: e.clientX, y: e.clientY };
    canvas.setPointerCapture(e.pointerId);
  });
  canvas.addEventListener('pointermove', (e) => {
    if (!state.drag) return;
    state.yaw += (e.clientX - state.drag.x) * 0.012;
    state.pitch = THREE.MathUtils.clamp(state.pitch + (e.clientY - state.drag.y) * 0.008, -1.2, 1.2);
    state.drag = { x: e.clientX, y: e.clientY };
  });
  const endDrag = () => {
    state.drag = null;
    state.idleAt = performance.now();
  };
  canvas.addEventListener('pointerup', endDrag);
  canvas.addEventListener('pointercancel', endDrag);

  const clear = () => {
    for (const child of [...pivot.children]) {
      child.traverse((o) => {
        if (o.isMesh) {
          o.geometry.dispose();
          o.material.dispose();
        }
      });
      pivot.remove(child);
    }
    for (const t of Object.values(state.textures)) t.tex.dispose();
    state.textures = {};
  };

  async function show(index) {
    const token = ++state.token;
    state.index = (index + state.items.length) % state.items.length;
    const item = state.items[state.index];
    dialog.querySelector('#viewer-title').textContent = item.name;
    dialog.querySelector('#viewer-set').textContent = state.title;
    dialog.querySelector('#viewer-count').textContent = `${state.index + 1} de ${state.items.length}`;
    const status = dialog.querySelector('#viewer-status');
    status.hidden = false;
    status.textContent = 'Cargando modelo…';
    try {
      const model = await (await fetch(item.model)).json();
      const textures = await loadTextures(model);
      if (token !== state.token) return;
      clear();
      state.textures = textures;
      const buckets = model.elements ? buildElements(model, textures) : buildGenerated(model, textures);
      const mesh = toMesh(buckets, textures);
      // Centrado en el origen
      const box = new THREE.Box3().setFromObject(mesh);
      const center = box.getCenter(new THREE.Vector3());
      mesh.position.sub(center);
      const gui = model.elements && model.gui && model.gui.rotation ? model.gui.rotation : [0, 0, 0];
      const guiGroup = new THREE.Group();
      guiGroup.add(mesh);
      guiGroup.rotation.set(...gui.map((d) => THREE.MathUtils.degToRad(d)), 'XYZ');
      const upright = new THREE.Group();
      upright.add(guiGroup);
      upright.rotation.z = item.upright === false ? 0 : uprightAngle(guiGroup);
      pivot.add(upright);
      const sphere = new THREE.Box3().setFromObject(upright).getBoundingSphere(new THREE.Sphere());
      const dist = (sphere.radius / Math.sin(THREE.MathUtils.degToRad(camera.fov / 2))) * 1.05;
      camera.position.set(0, 0, dist);
      camera.lookAt(0, 0, 0);
      state.yaw = -0.5;
      state.pitch = 0.12;
      state.idleAt = 0;
      status.hidden = true;
    } catch (err) {
      console.error(err);
      if (token === state.token) status.textContent = 'No se pudo cargar el modelo.';
    }
  }

  dialog.querySelector('[data-viewer-close]').addEventListener('click', () => dialog.close());
  dialog.querySelector('[data-viewer-prev]').addEventListener('click', () => show(state.index - 1));
  dialog.querySelector('[data-viewer-next]').addEventListener('click', () => show(state.index + 1));
  dialog.addEventListener('click', (e) => {
    if (e.target === dialog) dialog.close();
  });
  dialog.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowLeft') show(state.index - 1);
    if (e.key === 'ArrowRight') show(state.index + 1);
  });
  dialog.addEventListener('close', () => {
    cancelAnimationFrame(state.raf);
    state.token++;
    clear();
    window.removeEventListener('resize', resize);
  });

  return {
    open(items, index, title) {
      state.items = items;
      state.title = title;
      dialog.querySelector('[data-viewer-prev]').hidden = dialog.querySelector('[data-viewer-next]').hidden = items.length < 2;
      dialog.showModal();
      resize();
      window.addEventListener('resize', resize);
      cancelAnimationFrame(state.raf);
      state.raf = requestAnimationFrame(loop);
      show(index);
    },
  };
}

export function openViewer(items, index, title) {
  ui ||= createUi();
  ui.open(items, index, title);
}
