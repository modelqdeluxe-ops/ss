// Probador de la web: el personaje del jugador (con su skin) llevando las piezas de un set, colocadas igual que en el
// juego (mano derecha, cabeza, espalda y armadura), y el visor de cada objeto con el botón «Equipar».
// Las posiciones salen de las mismas transformaciones que usa Minecraft 1.20.1 (PoseStack de los RenderLayer).
import * as THREE from '/vendor/three.module.min.js';
import { loadTextures, buildElements, buildGenerated, toMesh, uprightAngle } from '/viewer.js';

const DEG = Math.PI / 180;

// Minecraft PoseStack: cada operación se multiplica por la derecha.
class Pose {
  constructor(m) {
    this.m = m ? m.clone() : new THREE.Matrix4();
  }
  clone() {
    return new Pose(this.m);
  }
  translate(x, y, z) {
    this.m.multiply(new THREE.Matrix4().makeTranslation(x, y, z));
    return this;
  }
  scale(x, y, z) {
    this.m.multiply(new THREE.Matrix4().makeScale(x, y, z));
    return this;
  }
  rotX(d) {
    this.m.multiply(new THREE.Matrix4().makeRotationX(d * DEG));
    return this;
  }
  rotY(d) {
    this.m.multiply(new THREE.Matrix4().makeRotationY(d * DEG));
    return this;
  }
  rotZ(d) {
    this.m.multiply(new THREE.Matrix4().makeRotationZ(d * DEG));
    return this;
  }
  // Quaternionf.rotationXYZ: X, luego Y, luego Z
  rotXYZ(x, y, z) {
    this.m.multiply(new THREE.Matrix4().makeRotationFromEuler(new THREE.Euler(x * DEG, y * DEG, z * DEG, 'XYZ')));
    return this;
  }
}

// --- Cubos del modelo del jugador (ModelPart.Cube de Minecraft) ---
function cubeQuads(u, v, x, y, z, dx, dy, dz, grow, mirror, texW, texH, out) {
  let x0 = x - grow;
  let y0 = y - grow;
  let z0 = z - grow;
  let x1 = x + dx + grow;
  const y1 = y + dy + grow;
  const z1 = z + dz + grow;
  if (mirror) [x0, x1] = [x1, x0];
  const V = {
    7: [x0, y0, z0], 0: [x1, y0, z0], 1: [x1, y1, z0], 2: [x0, y1, z0],
    3: [x0, y0, z1], 4: [x1, y0, z1], 5: [x1, y1, z1], 6: [x0, y1, z1],
  };
  const f6 = u;
  const f7 = u + dz;
  const f8 = u + dz + dx;
  const f9 = u + dz + dx + dx;
  const f10 = u + dz + dx + dz;
  const f11 = u + dz + dx + dz + dx;
  const f12 = v;
  const f13 = v + dz;
  const f14 = v + dz + dy;
  const polys = [
    [[4, 3, 7, 0], f7, f12, f8, f13],
    [[1, 2, 6, 5], f8, f13, f9, f12],
    [[7, 3, 6, 2], f6, f13, f7, f14],
    [[0, 7, 2, 1], f7, f13, f8, f14],
    [[4, 0, 1, 5], f8, f13, f10, f14],
    [[3, 4, 5, 6], f10, f13, f11, f14],
  ];
  const shades = [0.55, 1, 0.7, 0.85, 0.7, 0.85];
  polys.forEach(([ids, u1, v1, u2, v2], i) => {
    let verts = [
      [V[ids[0]], u2, v1],
      [V[ids[1]], u1, v1],
      [V[ids[2]], u1, v2],
      [V[ids[3]], u2, v2],
    ];
    if (mirror) verts = verts.reverse();
    for (const k of [0, 1, 2, 0, 2, 3]) {
      const [p, uu, vv] = verts[k];
      out.pos.push(p[0] / 16, p[1] / 16, p[2] / 16);
      out.uv.push(uu / texW, vv / texH);
      out.col.push(shades[i], shades[i], shades[i]);
    }
  });
}

function partMesh(cubes, texture, matrix, texW, texH) {
  const b = { pos: [], uv: [], col: [] };
  for (const c of cubes) cubeQuads(...c, texW, texH, b);
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.Float32BufferAttribute(b.pos, 3));
  geo.setAttribute('uv', new THREE.Float32BufferAttribute(b.uv, 2));
  geo.setAttribute('color', new THREE.Float32BufferAttribute(b.col, 3));
  const mesh = new THREE.Mesh(
    geo,
    new THREE.MeshBasicMaterial({ map: texture, vertexColors: true, alphaTest: 0.1, side: THREE.DoubleSide, transparent: false }),
  );
  mesh.matrixAutoUpdate = false;
  mesh.matrix.copy(matrix);
  return mesh;
}

// Partes del jugador (PlayerModel, brazos anchos): pivote y cubos [u, v, x, y, z, dx, dy, dz, crecer, espejo]
function playerParts(slimLegacy) {
  const legacy = slimLegacy === 'legacy';
  return {
    head: { pivot: [0, 0, 0], cubes: [[0, 0, -4, -8, -4, 8, 8, 8, 0, false], [32, 0, -4, -8, -4, 8, 8, 8, 0.5, false]] },
    body: { pivot: [0, 0, 0], cubes: [[16, 16, -4, 0, -2, 8, 12, 4, 0, false], ...(legacy ? [] : [[16, 32, -4, 0, -2, 8, 12, 4, 0.25, false]])] },
    rightArm: { pivot: [-5, 2, 0], cubes: [[40, 16, -3, -2, -2, 4, 12, 4, 0, false], ...(legacy ? [] : [[40, 32, -3, -2, -2, 4, 12, 4, 0.25, false]])] },
    leftArm: {
      pivot: [5, 2, 0],
      cubes: legacy ? [[40, 16, -1, -2, -2, 4, 12, 4, 0, true]] : [[32, 48, -1, -2, -2, 4, 12, 4, 0, false], [48, 48, -1, -2, -2, 4, 12, 4, 0.25, false]],
    },
    rightLeg: { pivot: [-1.9, 12, 0], cubes: [[0, 16, -2, 0, -2, 4, 12, 4, 0, false], ...(legacy ? [] : [[0, 32, -2, 0, -2, 4, 12, 4, 0.25, false]])] },
    leftLeg: {
      pivot: [1.9, 12, 0],
      cubes: legacy ? [[0, 16, -2, 0, -2, 4, 12, 4, 0, true]] : [[16, 48, -2, 0, -2, 4, 12, 4, 0, false], [0, 48, -2, 0, -2, 4, 12, 4, 0.25, false]],
    },
  };
}

// Brazo levantado al sostener algo (ArmPose.ITEM); el izquierdo solo si lleva algo
const POSE = { rightArm: [-18, 0, 0], leftArm: [0, 0, 0] };
const POSE_HOLDING = { rightArm: [-18, 0, 0], leftArm: [-18, 0, 0] };

function partPose(base, name, pivot, poses = POSE) {
  const p = base.clone().translate(pivot[0] / 16, pivot[1] / 16, pivot[2] / 16);
  const r = poses[name];
  if (r) p.rotZ(r[2]).rotY(r[1]).rotX(r[0]);
  return p;
}

// Armadura (HumanoidArmorModel): exterior (capa 1, crece 1) e interior (capa 2, crece 0,5); textura de 64×32.
const ARMOR_PIECES = {
  helmet: { layer: '1', grow: 1, parts: ['head'] },
  chestplate: { layer: '1', grow: 1, parts: ['body', 'rightArm', 'leftArm'] },
  leggings: { layer: '2', grow: 0.5, parts: ['body', 'rightLeg', 'leftLeg'] },
  boots: { layer: '1', grow: 1, parts: ['rightLeg', 'leftLeg'] },
};
const ARMOR_CUBES = {
  head: [[0, 0, -4, -8, -4, 8, 8, 8, 0, false], [32, 0, -4, -8, -4, 8, 8, 8, 0.5, false]],
  body: [[16, 16, -4, 0, -2, 8, 12, 4, 0, false]],
  rightArm: [[40, 16, -3, -2, -2, 4, 12, 4, 0, false]],
  leftArm: [[40, 16, -1, -2, -2, 4, 12, 4, 0, true]],
  rightLeg: [[0, 16, -2, 0, -2, 4, 12, 4, 0, false]],
  leftLeg: [[0, 16, -2, 0, -2, 4, 12, 4, 0, true]],
};

// Transformación de un objeto en un contexto (ItemTransform.apply) y su colocación en el bloque.
const DEFAULT_HAND = {
  generated: { rotation: [0, 0, 0], translation: [0, 3, 1], scale: [0.55, 0.55, 0.55] },
  handheld: { rotation: [0, -90, 55], translation: [0, 4, 0.5], scale: [0.85, 0.85, 0.85] },
};
// left: mano izquierda (ItemTransform.apply la refleja: x, giro y y giro z cambian de signo)
function applyDisplay(pose, t, left = false) {
  if (!t) return pose;
  const tr = t.translation || [0, 0, 0];
  const ro = t.rotation || [0, 0, 0];
  const sc = t.scale || [1, 1, 1];
  const clamp = (n) => Math.max(-80, Math.min(80, n)) / 16;
  const m = left ? -1 : 1;
  pose.translate(m * clamp(tr[0]), clamp(tr[1]), clamp(tr[2]));
  pose.rotXYZ(ro[0], m * ro[1], m * ro[2]);
  pose.scale(sc[0], sc[1], sc[2]);
  return pose;
}

async function itemMesh(model, pose, keep) {
  const textures = await loadTextures(model);
  const buckets = model.elements ? buildElements(model, textures) : buildGenerated(model, textures);
  const mesh = toMesh(buckets, textures);
  const p = pose.clone().translate(-0.5, -0.5, -0.5).scale(1 / 16, 1 / 16, 1 / 16);
  mesh.matrixAutoUpdate = false;
  mesh.matrix.copy(p.m);
  keep.push(...Object.values(textures));
  return mesh;
}

const HAND_TYPES = new Set(['sword', 'axe', 'heavy', 'pickaxe', 'shovel', 'hoe', 'bow', 'crossbow', 'fishing_rod', 'trident', 'shield', 'held']);

export function slotOf(item) {
  if (!item) return null;
  if (item.type === 'armor') return item.slot;
  // Los cascos cosméticos van en el hueco del casco, como en el juego: o uno o el otro.
  if (item.type === 'head') return 'helmet';
  if (item.type === 'back') return 'back';
  // Escudos y globos, en la mano izquierda
  if (item.type === 'shield' || item.type === 'balloon') return 'offhand';
  if (HAND_TYPES.has(item.type)) return 'hand';
  return null;
}

// Lo que lleva puesto por defecto al abrir el set: armadura completa, casco o sombrero, espalda y su mejor arma.
export function defaultOutfit(set) {
  const out = {};
  const items = set.items;
  for (const [slug, it] of Object.entries(items)) {
    const slot = slotOf(it);
    if (!slot || slot === 'offhand') continue;
    if (it.type === 'armor') out[slot] ??= slug;
    else if (it.type === 'head' && items[out.helmet]?.type !== 'head') out.helmet = slug;
    else if (slot === 'back' && !out.back) out.back = slug;
  }
  const pick = ['sword', 'greatsword', 'great_sword', 'big_sword', 'blade', 'scythe', 'axe', 'hammer'].find((s) => items[s]);
  const hand = pick || Object.keys(items).find((s) => slotOf(items[s]) === 'hand');
  if (hand) out.hand = hand;
  return out;
}

export function createWardrobe(canvas) {
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(30, 1, 0.05, 100);
  const pivot = new THREE.Group();
  scene.add(pivot);
  const playerRoot = new THREE.Group();
  const itemRoot = new THREE.Group();
  pivot.add(playerRoot, itemRoot);

  const state = { yaw: -0.45, pitch: 0.08, drag: null, idleAt: 0, raf: 0, anim: [], token: 0, mode: 'player', skin: null, skinTex: null };
  // Puntos del personaje que se siguen en pantalla (nombre sobre la cabeza, sombra en los pies)
  const anchors = { head: new THREE.Vector3(), feet: new THREE.Vector3(), cb: null, last: '' };
  const project = (v) => {
    const p = playerRoot.localToWorld(v.clone()).project(camera);
    const r = canvas.getBoundingClientRect();
    return { x: ((p.x + 1) / 2) * r.width, y: ((1 - p.y) / 2) * r.height };
  };

  const resize = () => {
    const rect = canvas.parentElement.getBoundingClientRect();
    renderer.setSize(rect.width, rect.height, false);
    camera.aspect = rect.width / Math.max(1, rect.height);
    camera.updateProjectionMatrix();
    frame();
  };
  // Al cambiar entre personaje y objeto se vuelve a mirar de frente.
  const resetView = (yaw) => {
    state.yaw = yaw;
    state.pitch = 0.08;
    state.idleAt = performance.now();
  };
  const frame = () => {
    const target = state.mode === 'player' ? playerRoot : itemRoot;
    // Se mide sin el giro del ratón y se centra en el origen
    const rot = pivot.rotation.clone();
    pivot.rotation.set(0, 0, 0);
    target.position.set(0, 0, 0);
    pivot.updateMatrixWorld(true);
    const box = new THREE.Box3().setFromObject(target);
    pivot.rotation.copy(rot);
    if (box.isEmpty()) return;
    target.position.copy(box.getCenter(new THREE.Vector3()).negate());
    const vfov = (camera.fov * DEG) / 2;
    const hfov = Math.atan(Math.tan(vfov) * camera.aspect);
    let dist;
    if (state.mode === 'player') {
      // El cuerpo manda: el ancho de las alas cabe al girar, pero no encoge al personaje.
      const size = box.getSize(new THREE.Vector3());
      const across = Math.max(size.x, size.z);
      dist = Math.max(size.y / 2 / Math.tan(vfov), across / 2 / Math.tan(hfov)) * 1.12 + Math.max(size.x, size.z) / 2;
    } else {
      const sphere = box.getBoundingSphere(new THREE.Sphere());
      dist = (sphere.radius / Math.sin(Math.min(vfov, hfov))) * 1.08;
    }
    camera.position.set(0, 0, dist);
    camera.lookAt(0, 0, 0);
  };
  const loop = (time) => {
    state.raf = requestAnimationFrame(loop);
    // Giro lento y por tiempo (igual en pantallas de 60 y de 144 Hz): una vuelta cada ~35 s
    const dt = Math.min(0.1, (time - (state.lastTime || time)) / 1000);
    state.lastTime = time;
    if (!state.drag && time - state.idleAt > 2500) state.yaw += dt * 0.18;
    pivot.rotation.set(state.pitch, state.yaw, 0);
    if (anchors.cb && state.mode === 'player' && playerRoot.children.length) {
      pivot.updateMatrixWorld(true);
      const head = project(anchors.head);
      const feet = project(anchors.feet);
      const key = `${head.x | 0},${head.y | 0},${feet.x | 0},${feet.y | 0}`;
      if (key !== anchors.last) {
        anchors.last = key;
        anchors.cb({ head, feet });
      }
    }
    for (const t of state.anim) {
      if (!t.frames || t.frames.length < 2) continue;
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
    state.pitch = THREE.MathUtils.clamp(state.pitch + (e.clientY - state.drag.y) * 0.006, -0.6, 0.8);
    state.drag = { x: e.clientX, y: e.clientY };
  });
  const endDrag = () => {
    state.drag = null;
    state.idleAt = performance.now();
  };
  canvas.addEventListener('pointerup', endDrag);
  canvas.addEventListener('pointercancel', endDrag);
  const ro = new ResizeObserver(resize);
  ro.observe(canvas.parentElement);

  const dispose = (group) => {
    for (const child of [...group.children]) {
      child.traverse((o) => {
        if (o.isMesh) {
          o.geometry.dispose();
          o.material.dispose();
        }
      });
      group.remove(child);
    }
  };

  async function loadSkin(name) {
    const url = `/api/skin/${encodeURIComponent(name || 'MHF_Steve')}`;
    const img = new Image();
    img.src = url;
    try {
      await img.decode();
    } catch {
      img.src = '/api/skin/MHF_Steve';
      await img.decode();
    }
    const tex = new THREE.Texture(img);
    tex.magFilter = THREE.NearestFilter;
    tex.minFilter = THREE.NearestFilter;
    tex.generateMipmaps = false;
    tex.flipY = false;
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.needsUpdate = true;
    return { tex, legacy: img.naturalHeight === 32 };
  }

  // El jugador con lo que lleva puesto. outfit: { hand, back, helmet (armadura o cosmético), chestplate, leggings, boots } → slug
  async function showPlayer(set, outfit, skinName) {
    const token = ++state.token;
    if (!state.skin || state.skinName !== skinName) {
      state.skin = await loadSkin(skinName);
      state.skinName = skinName;
    }
    const keep = [];
    const group = new THREE.Group();
    // LivingEntityRenderer: giro del cuerpo, espejo, tamaño del jugador (0,9375) y bajada a los pies
    const base = new Pose().rotY(180).scale(-1, -1, 1).scale(0.9375, 0.9375, 0.9375).translate(0, -1.501, 0);
    const parts = playerParts(state.skin.legacy ? 'legacy' : 'wide');
    const poses = {};
    const items0 = set.items;
    const holding = outfit.offhand && items0[outfit.offhand]?.model ? POSE_HOLDING : POSE;
    for (const [name, part] of Object.entries(parts)) {
      poses[name] = partPose(base, name, part.pivot, holding);
      group.add(partMesh(part.cubes, state.skin.tex, poses[name].m, 64, state.skin.legacy ? 32 : 64));
    }
    const items = set.items;
    // Encima de la cabeza (donde Minecraft pone el nombre) y entre los pies
    anchors.head.setFromMatrixPosition(poses.head.clone().translate(0, -0.95, 0).m);
    anchors.feet.setFromMatrixPosition(base.clone().translate(0, 1.5, 0).m);
    // Armadura
    for (const [slot, piece] of Object.entries(ARMOR_PIECES)) {
      const worn = set.items[outfit[slot]];
      // Cada pieza puede traer las capas de su set (el armario mezcla piezas de sets distintos)
      const layer = worn?.type === 'armor' ? (worn.armorLayers || set.armor)?.[piece.layer] : null;
      if (!layer) continue;
      const [loaded] = Object.values(await loadTextures({ textures: { a: layer } }));
      keep.push(loaded);
      for (const partName of piece.parts) {
        const cubes = ARMOR_CUBES[partName].map((c) => {
            const g = c[0] === 32 && c[1] === 0 ? piece.grow + 0.5 : piece.grow;
            return [c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], g, c[9]];
          });
        group.add(partMesh(cubes, loaded.tex, poses[partName].m, 64, 32));
      }
    }
    // Mano derecha (ItemInHandLayer)
    const handSlug = outfit.hand;
    if (handSlug && items[handSlug]?.model) {
      const model = items[handSlug].model;
      const p = poses.rightArm.clone().rotX(-90).rotY(180).translate(1 / 16, 0.125, -0.625);
      const t = model.display.thirdperson_righthand || (model.elements ? null : DEFAULT_HAND.handheld);
      group.add(await itemMesh(model, applyDisplay(p, t), keep));
    }
    // Mano izquierda: escudos y globos
    const offSlug = outfit.offhand;
    if (offSlug && items[offSlug]?.model) {
      const model = items[offSlug].model;
      const p = poses.leftArm.clone().rotX(-90).rotY(180).translate(-1 / 16, 0.125, -0.625);
      const t = model.display.thirdperson_lefthand || model.display.thirdperson_righthand || (model.elements ? null : DEFAULT_HAND.handheld);
      group.add(await itemMesh(model, applyDisplay(p, t, true), keep));
    }
    // Cabeza (CustomHeadLayer)
    const headSlug = items[outfit.helmet]?.type === 'head' ? outfit.helmet : null;
    if (headSlug && items[headSlug]?.model) {
      const model = items[headSlug].model;
      const p = poses.head.clone().translate(0, -0.25, 0).rotY(180).scale(0.625, -0.625, -0.625);
      group.add(await itemMesh(model, applyDisplay(p, model.display.head), keep));
    }
    // Espalda (BackLayer del TF Client: como en la cabeza pero siguiendo el cuerpo)
    const backSlug = outfit.back;
    if (backSlug && (items[backSlug]?.worn || items[backSlug]?.model)) {
      const model = items[backSlug].worn || items[backSlug].model;
      const armored = Boolean(outfit.chestplate);
      const p = poses.body.clone().translate(0, -0.25, armored ? 0.0625 : 0).rotY(180).scale(0.625, -0.625, -0.625);
      group.add(await itemMesh(model, applyDisplay(p, model.display.head), keep));
    }
    if (token !== state.token) {
      dispose(group);
      return;
    }
    dispose(playerRoot);
    playerRoot.position.set(0, 0, 0);
    playerRoot.add(group);
    state.anim = keep;
    if (state.mode !== 'player') resetView(-0.45);
    state.mode = 'player';
    playerRoot.visible = true;
    itemRoot.visible = false;
    frame();
  }

  // Un objeto suelto, como en el visor. upright: poner de pie lo alargado (armas y herramientas); los cascos, alas y
  // escudos se quedan como en el inventario.
  async function showItem(model, { upright: stand = true } = {}) {
    const token = ++state.token;
    const textures = await loadTextures(model);
    const buckets = model.elements ? buildElements(model, textures) : buildGenerated(model, textures);
    const mesh = toMesh(buckets, textures);
    if (token !== state.token) return;
    dispose(itemRoot);
    itemRoot.position.set(0, 0, 0);
    const box = new THREE.Box3().setFromObject(mesh);
    mesh.position.sub(box.getCenter(new THREE.Vector3()));
    const gui = model.elements && model.display?.gui?.rotation ? model.display.gui.rotation : [0, 0, 0];
    const guiGroup = new THREE.Group();
    guiGroup.add(mesh);
    guiGroup.rotation.set(...gui.map((d) => d * DEG), 'XYZ');
    const upright = new THREE.Group();
    upright.add(guiGroup);
    upright.rotation.z = stand ? uprightAngle(guiGroup) : 0;
    upright.scale.setScalar(1 / 16);
    itemRoot.add(upright);
    state.anim = Object.values(textures);
    resetView(0);
    state.mode = 'item';
    playerRoot.visible = false;
    itemRoot.visible = true;
    frame();
  }

  resize();
  state.raf = requestAnimationFrame(loop);

  return {
    showPlayer,
    showItem,
    /** Llama a cb({ head, feet }) con la posición en el lienzo (px) de esos puntos cuando cambian. */
    onAnchors(cb) {
      anchors.cb = cb;
      anchors.last = '';
    },
    /** Mira desde un ángulo fijo (sin girar solo). */
    setView(yaw, pitch = 0.08) {
      state.yaw = yaw;
      state.pitch = pitch;
      state.idleAt = Infinity;
    },
    get mode() {
      return state.mode;
    },
    destroy() {
      cancelAnimationFrame(state.raf);
      state.token++;
      ro.disconnect();
      dispose(playerRoot);
      dispose(itemRoot);
      renderer.dispose();
    },
  };
}
