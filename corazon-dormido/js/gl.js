'use strict';
// Motor 3D con WebGL2, al estilo de Core Keeper: el mundo son bloques con texturas de pixel art (tapa arriba,
// cara delante y a los lados), vistos con una cámara inclinada en perspectiva. Personajes y objetos son
// sprites de pie dentro del mundo. La luz viene de un mapa por casilla (calculado en mundo.js, no atraviesa
// paredes) que el sombreador lee suavizado; lo que brilla solo se suma encima, con halos.
//
// Unidades: 1 = una casilla. x hacia el este, z hacia el sur (hacia la cámara), y hacia arriba.
// Truco de proyección (como en los juegos 2.5D de pixel art): el mundo se estira en z para que el suelo se vea
// cuadrado, y los muros miden lo justo para que su cara se vea con píxeles cuadrados.

const GL = (() => {
  const PITCH = (50 * Math.PI) / 180, FOV = (13 * Math.PI) / 180;
  const KZ = 1 / Math.sin(PITCH);                // estiramiento del suelo
  const ALTO_CARA = 12;                          // píxeles que mide la cara de una pared en pantalla
  const ALTO = ALTO_CARA / 16 / Math.cos(PITCH); // altura de una pared en unidades del mundo
  const KY = 1 / Math.cos(PITCH);                // un sprite de h píxeles mide h/16*KY de alto
  const CHUNK = 16;

  let gl = null, canvas = null, prog = null, progHalo = null, texAtlas = null, texLuz = null, texHalo = null;
  let progBrillo = null, progDesenfoque = null, progFinal = null, vaoVacio = null, fb = null;
  let W = 1, H = 1, dist = 22;
  const cam = { x: 0, z: 0, vp: new Float32Array(16), inv: new Float32Array(16) };
  const chunks = new Map();
  const loc = {}, locH = {};

  // ---------------------------------------------------------------- matrices (columna mayor, como WebGL)
  function mul(a, b) {
    const o = new Float32Array(16);
    for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) {
      let s = 0;
      for (let k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k];
      o[c * 4 + r] = s;
    }
    return o;
  }
  function perspectiva(fov, asp, n, f) {
    const t = 1 / Math.tan(fov / 2), o = new Float32Array(16);
    o[0] = t / asp; o[5] = t; o[10] = (f + n) / (n - f); o[11] = -1; o[14] = (2 * f * n) / (n - f);
    return o;
  }
  function mirar(ex, ey, ez, tx, ty, tz) {
    let zx = ex - tx, zy = ey - ty, zz = ez - tz;
    const zl = Math.hypot(zx, zy, zz); zx /= zl; zy /= zl; zz /= zl;
    let xx = zz, xy = 0, xz = -zx; // arriba = (0,1,0) × z
    const xl = Math.hypot(xx, xy, xz); xx /= xl; xz /= xl;
    const yx = zy * xz - zz * xy, yy = zz * xx - zx * xz, yz = zx * xy - zy * xx;
    return new Float32Array([xx, yx, zx, 0, xy, yy, zy, 0, xz, yz, zz, 0,
      -(xx * ex + xy * ey + xz * ez), -(yx * ex + yy * ey + yz * ez), -(zx * ex + zy * ey + zz * ez), 1]);
  }
  function invertir(m) {
    const a = m, o = new Float32Array(16);
    const b00 = a[0] * a[5] - a[1] * a[4], b01 = a[0] * a[6] - a[2] * a[4], b02 = a[0] * a[7] - a[3] * a[4];
    const b03 = a[1] * a[6] - a[2] * a[5], b04 = a[1] * a[7] - a[3] * a[5], b05 = a[2] * a[7] - a[3] * a[6];
    const b06 = a[8] * a[13] - a[9] * a[12], b07 = a[8] * a[14] - a[10] * a[12], b08 = a[8] * a[15] - a[11] * a[12];
    const b09 = a[9] * a[14] - a[10] * a[13], b10 = a[9] * a[15] - a[11] * a[13], b11 = a[10] * a[15] - a[11] * a[14];
    const d = 1 / (b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06);
    o[0] = (a[5] * b11 - a[6] * b10 + a[7] * b09) * d; o[1] = (a[2] * b10 - a[1] * b11 - a[3] * b09) * d;
    o[2] = (a[13] * b05 - a[14] * b04 + a[15] * b03) * d; o[3] = (a[10] * b04 - a[9] * b05 - a[11] * b03) * d;
    o[4] = (a[6] * b08 - a[4] * b11 - a[7] * b07) * d; o[5] = (a[0] * b11 - a[2] * b08 + a[3] * b07) * d;
    o[6] = (a[14] * b02 - a[12] * b05 - a[15] * b01) * d; o[7] = (a[8] * b05 - a[10] * b02 + a[11] * b01) * d;
    o[8] = (a[4] * b10 - a[5] * b08 + a[7] * b06) * d; o[9] = (a[1] * b08 - a[0] * b10 - a[3] * b06) * d;
    o[10] = (a[12] * b04 - a[13] * b02 + a[15] * b00) * d; o[11] = (a[9] * b02 - a[8] * b04 - a[11] * b00) * d;
    o[12] = (a[5] * b07 - a[4] * b09 - a[6] * b06) * d; o[13] = (a[0] * b09 - a[1] * b07 + a[2] * b06) * d;
    o[14] = (a[13] * b01 - a[12] * b03 - a[14] * b00) * d; o[15] = (a[8] * b03 - a[9] * b01 + a[10] * b00) * d;
    return o;
  }

  // ---------------------------------------------------------------- sombreadores

  const VS = `#version 300 es
  in vec3 aPos; in vec2 aUv; in vec4 aTinte; in vec2 aLuz; in vec2 aExtra;
  uniform mat4 uVP; uniform vec4 uLuzRect;
  out vec2 vUv; out vec4 vTinte; out vec2 vLuzUv; out vec2 vExtra;
  void main() {
    gl_Position = uVP * vec4(aPos, 1.0);
    vUv = aUv; vTinte = aTinte; vExtra = aExtra;
    vLuzUv = (aLuz - uLuzRect.xy) * uLuzRect.zw;
  }`;
  // aExtra.x: cuánto ignora la luz (0 = toda la luz, 1 = brilla solo); aExtra.y: destello blanco.
  // La luz se lee en el centro de cada píxel del arte y se reparte en escalones con trama ordenada 4×4
  // alineada a los píxeles del dibujo: así se ve como en Core Keeper (degradados tramados, no lisos).
  const FS = `#version 300 es
  precision highp float;
  uniform sampler2D uAtlas; uniform sampler2D uLuz; uniform float uCorte; uniform float uAditivo; uniform vec2 uAtlasTam;
  in vec2 vUv; in vec4 vTinte; in vec2 vLuzUv; in vec2 vExtra;
  out vec4 color;
  const float BAYER[16] = float[16](0.,8.,2.,10.,12.,4.,14.,6.,3.,11.,1.,9.,15.,7.,13.,5.);
  void main() {
    vec4 t = texture(uAtlas, vUv);
    if (t.a < uCorte) discard;
    vec3 base = mix(t.rgb * vTinte.rgb, vec3(1.0), vExtra.y);
    vec2 texel = floor(vUv * uAtlasTam);
    vec2 luzUv = vLuzUv;
    mat2 ju = mat2(dFdx(vUv), dFdy(vUv)), jl = mat2(dFdx(vLuzUv), dFdy(vLuzUv));
    float det = determinant(ju);
    if (abs(det) > 1e-14) luzUv += jl * inverse(ju) * ((texel + 0.5) / uAtlasTam - vUv);
    vec3 l = texture(uLuz, luzUv).rgb;
    l = l * l * 2.4;
    vec2 m = mod(texel, 4.0);
    float b = (BAYER[int(m.y) * 4 + int(m.x)] + 0.5) / 16.0;
    vec3 lq = floor(l * 7.0 + b) / 7.0;
    vec3 c = base * mix(lq, vec3(1.0), vExtra.x);
    color = vec4(c * mix(1.0, vTinte.a, uAditivo) * 0.5, t.a * vTinte.a); // la escena guarda la mitad: hasta 2 de luz
  }`;
  const VSH = `#version 300 es
  in vec3 aPos; in vec2 aUv; in vec4 aTinte;
  uniform mat4 uVP; out vec2 vUv; out vec4 vTinte;
  void main() { gl_Position = uVP * vec4(aPos, 1.0); vUv = aUv; vTinte = aTinte; }`;
  const FSH = `#version 300 es
  precision mediump float;
  uniform sampler2D uHalo; in vec2 vUv; in vec4 vTinte; out vec4 color;
  void main() { float a = texture(uHalo, vUv).r; color = vec4(vTinte.rgb * a * vTinte.a * 0.5, 1.0); }`;

  const VSP = `#version 300 es
  out vec2 vUv;
  void main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); vUv = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }`;
  const FS_BRILLO = `#version 300 es
  precision mediump float;
  uniform sampler2D uTex; in vec2 vUv; out vec4 o;
  void main() { vec3 s = texture(uTex, vUv).rgb * 2.0; o = vec4(max(s - 1.0, 0.0), 1.0); }`;
  const FS_DESENFOQUE = `#version 300 es
  precision mediump float;
  uniform sampler2D uTex; uniform vec2 uDir; in vec2 vUv; out vec4 o;
  void main() {
    vec3 s = texture(uTex, vUv).rgb * 0.227;
    s += (texture(uTex, vUv + uDir * 1.385).rgb + texture(uTex, vUv - uDir * 1.385).rgb) * 0.316;
    s += (texture(uTex, vUv + uDir * 3.231).rgb + texture(uTex, vUv - uDir * 3.231).rgb) * 0.070;
    o = vec4(s, 1.0);
  }`;
  const FS_FINAL = `#version 300 es
  precision highp float;
  uniform sampler2D uEscena; uniform sampler2D uB1; uniform sampler2D uB2; uniform float uT; uniform float uBloom;
  in vec2 vUv; out vec4 o;
  void main() {
    vec3 c = texture(uEscena, vUv).rgb * 2.0;
    c += (texture(uB1, vUv).rgb * 0.8 + texture(uB2, vUv).rgb * 1.1) * uBloom;
    vec3 e = max(c - 0.75, 0.0);
    c = min(c, 0.75) + 0.25 * (1.0 - exp(-e * 4.0));            // los blancos se doblan suave
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(l), c, 1.1);                                   // un poco más de color
    c *= mix(vec3(0.92, 0.95, 1.08), vec3(1.04, 1.0, 0.94), smoothstep(0.02, 0.5, l)); // sombras frías, luces cálidas
    vec2 q = (vUv - 0.5) * vec2(1.0, 1.25);
    c *= clamp(1.0 - dot(q, q) * 1.05, 0.0, 1.0);               // viñeta
    float n = fract(sin(dot(gl_FragCoord.xy + fract(uT) * 91.0, vec2(12.9898, 78.233))) * 43758.5453);
    c += (n - 0.5) * 0.02 * (0.3 + l);                          // grano fino
    o = vec4(c, 1.0);
  }`;

  function compilar(vs, fs) {
    const p = gl.createProgram();
    for (const [tipo, src] of [[gl.VERTEX_SHADER, vs], [gl.FRAGMENT_SHADER, fs]]) {
      const s = gl.createShader(tipo);
      gl.shaderSource(s, src); gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error('sombreador: ' + gl.getShaderInfoLog(s));
      gl.attachShader(p, s);
    }
    gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error('programa: ' + gl.getProgramInfoLog(p));
    return p;
  }

  // ---------------------------------------------------------------- vértices
  // x y z  u v  r g b a  lx lz  unlit blanco  → 13 floats
  const F = 13;
  class Lote {
    constructor(cap = 4096) { this.d = new Float32Array(cap * 4 * F); this.n = 0; }
    reservar(q) {
      if ((this.n + q) * 4 * F > this.d.length) { const nd = new Float32Array(Math.max(this.d.length * 2, (this.n + q) * 4 * F)); nd.set(this.d); this.d = nd; }
    }
    // c: 4 esquinas [x,y,z]; uv: [u0,v0,u1,v1]; tinte por esquina o uno solo
    quad(c, uv, tinte, lx, lz, unlit = 0, blanco = 0, lxs = null) {
      this.reservar(1);
      const d = this.d; let o = this.n * 4 * F;
      const uvs = [[uv[0], uv[1]], [uv[2], uv[1]], [uv[2], uv[3]], [uv[0], uv[3]]];
      for (let k = 0; k < 4; k++) {
        const t = tinte.length === 4 && Array.isArray(tinte[0]) ? tinte[k] : tinte;
        d[o++] = c[k][0]; d[o++] = c[k][1]; d[o++] = c[k][2];
        d[o++] = uvs[k][0]; d[o++] = uvs[k][1];
        d[o++] = t[0]; d[o++] = t[1]; d[o++] = t[2]; d[o++] = t[3] ?? 1;
        d[o++] = lxs ? lxs[k][0] : lx; d[o++] = lxs ? lxs[k][1] : lz;
        d[o++] = unlit; d[o++] = blanco;
      }
      this.n++;
    }
  }
  let indices = null, nIndices = 0;
  function asegurarIndices(q) {
    if (q <= nIndices) return;
    nIndices = Math.max(q, nIndices * 2, 8192);
    const ix = new Uint32Array(nIndices * 6);
    for (let i = 0; i < nIndices; i++) ix.set([i * 4, i * 4 + 1, i * 4 + 2, i * 4, i * 4 + 2, i * 4 + 3], i * 6);
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indices);
    gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, ix, gl.STATIC_DRAW);
  }
  function crearVao(p) {
    const vao = gl.createVertexArray(), vbo = gl.createBuffer();
    gl.bindVertexArray(vao);
    gl.bindBuffer(gl.ARRAY_BUFFER, vbo);
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indices);
    const at = (n, k, off) => { const l = gl.getAttribLocation(p, n); if (l < 0) return; gl.enableVertexAttribArray(l); gl.vertexAttribPointer(l, k, gl.FLOAT, false, F * 4, off * 4); };
    at('aPos', 3, 0); at('aUv', 2, 3); at('aTinte', 4, 5); at('aLuz', 2, 9); at('aExtra', 2, 11);
    gl.bindVertexArray(null);
    return { vao, vbo, n: 0 };
  }
  function subir(obj, lote, uso) {
    gl.bindBuffer(gl.ARRAY_BUFFER, obj.vbo);
    gl.bufferData(gl.ARRAY_BUFFER, lote.d.subarray(0, lote.n * 4 * F), uso);
    obj.n = lote.n;
    asegurarIndices(lote.n);
  }

  // UV de un sprite del atlas, medio texel hacia dentro para que no sangre.
  function uvDe(n, flip = false) {
    const r = SPR[n];
    if (!r) return null;
    const aw = ATLAS.width, ah = ATLAS.height, e = 0.02;
    const u0 = (r.x + e) / aw, u1 = (r.x + r.w - e) / aw, v0 = (r.y + e) / ah, v1 = (r.y + r.h - e) / ah;
    return flip ? [u1, v0, u0, v1] : [u0, v0, u1, v1];
  }

  // ---------------------------------------------------------------- iniciar

  function iniciar(cv) {
    canvas = cv;
    gl = canvas.getContext('webgl2', { antialias: false, alpha: false, premultipliedAlpha: false, powerPreference: 'high-performance' });
    if (!gl) return false;
    prog = compilar(VS, FS); progHalo = compilar(VSH, FSH);
    for (const n of ['uVP', 'uLuzRect', 'uAtlas', 'uLuz', 'uCorte', 'uAditivo', 'uAtlasTam']) loc[n] = gl.getUniformLocation(prog, n);
    for (const n of ['uVP', 'uHalo']) locH[n] = gl.getUniformLocation(progHalo, n);
    indices = gl.createBuffer();
    asegurarIndices(8192);
    texAtlas = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, texAtlas);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, ATLAS);
    for (const [k, v] of [[gl.TEXTURE_MIN_FILTER, gl.NEAREST], [gl.TEXTURE_MAG_FILTER, gl.NEAREST], [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, k, v);
    texLuz = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, texLuz);
    for (const [k, v] of [[gl.TEXTURE_MIN_FILTER, gl.LINEAR], [gl.TEXTURE_MAG_FILTER, gl.LINEAR], [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, k, v);
    // halo: degradado redondo suave
    const N = 64, hd = new Uint8Array(N * N * 4);
    for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
      const d = Math.hypot(x + 0.5 - N / 2, y + 0.5 - N / 2) / (N / 2), a = Math.max(0, 1 - d), v = Math.round(a * a * a * 255);
      hd.set([v, v, v, 255], (y * N + x) * 4);
    }
    texHalo = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, texHalo);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, N, N, 0, gl.RGBA, gl.UNSIGNED_BYTE, hd);
    for (const [k, v] of [[gl.TEXTURE_MIN_FILTER, gl.LINEAR], [gl.TEXTURE_MAG_FILTER, gl.LINEAR], [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, k, v);
    progBrillo = compilar(VSP, FS_BRILLO); progDesenfoque = compilar(VSP, FS_DESENFOQUE); progFinal = compilar(VSP, FS_FINAL);
    vaoVacio = gl.createVertexArray();
    dinamicos.spr = crearVao(prog); dinamicos.sombras = crearVao(prog); dinamicos.brillo = crearVao(prog); dinamicos.halos = crearVao(progHalo);
    return true;
  }
  const dinamicos = {};

  // Tamaño del lienzo y cuántas casillas se ven: el lado corto muestra unas 12 casillas,
  // con un número entero de píxeles de pantalla por píxel de arte.
  function ajustar(wCss, hCss, dpr) {
    const k = Math.min(dpr, 2);
    W = Math.round(wCss * k); H = Math.round(hCss * k);
    canvas.width = W; canvas.height = H;
    canvas.style.width = wCss + 'px'; canvas.style.height = hCss + 'px';
    const corto = Math.min(W, H), escala = Math.max(1, Math.round(corto / (12 * 16)));
    const casillasAlto = H / (escala * 16);
    dist = casillasAlto / (2 * Math.tan(FOV / 2));
    crearFbos();
    return escala;
  }

  function camara(x, z) {
    cam.x = x; cam.z = z;
    const tz = z * KZ, ex = x, ey = Math.sin(PITCH) * dist, ez = tz + Math.cos(PITCH) * dist;
    const v = mirar(ex, ey, ez, x, 0, tz);
    const s = new Float32Array([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, KZ, 0, 0, 0, 0, 1]);
    cam.vp = mul(mul(perspectiva(FOV, W / H, 1, dist * 3), v), s);
    cam.inv = invertir(cam.vp);
  }
  // De un punto de la pantalla (0..1) al suelo (y = altura).
  function aMundo(nx, ny, altura = 0) {
    const cx = nx * 2 - 1, cy = 1 - ny * 2, m = cam.inv;
    const p = (z) => { const x = m[0] * cx + m[4] * cy + m[8] * z + m[12], y = m[1] * cx + m[5] * cy + m[9] * z + m[13], zz = m[2] * cx + m[6] * cy + m[10] * z + m[14], w = m[3] * cx + m[7] * cy + m[11] * z + m[15]; return [x / w, y / w, zz / w]; };
    const a = p(-1), b = p(1), t = (altura - a[1]) / (b[1] - a[1]);
    return { x: a[0] + (b[0] - a[0]) * t, z: a[2] + (b[2] - a[2]) * t };
  }
  // Casillas visibles (con margen para lo alto de las paredes).
  function vista() {
    const pts = [[0, 0], [1, 0], [0, 1], [1, 1]].map(([x, y]) => aMundo(x, y, 0));
    const xs = pts.map((p) => p.x), zs = pts.map((p) => p.z);
    return { x0: Math.floor(Math.min(...xs)) - 1, x1: Math.ceil(Math.max(...xs)) + 1, z0: Math.floor(Math.min(...zs)) - 1, z1: Math.ceil(Math.max(...zs)) + 3 };
  }

  // ---------------------------------------------------------------- trozos del mundo

  const ADORNOS3D = { 1: ['ad_piedras', 'ad_raices', 'ad_hierba', 'ad_terron'], 2: ['ad_piedras', 'ad_raices', 'ad_huesos', 'ad_terron'], 3: ['ad_piedras', 'ad_hongos', 'ad_guijarros'], 4: ['ad_hierba', 'ad_musgo'], 5: ['ad_huesos', 'ad_piedras', 'ad_raices', 'ad_garras'] };
  const AO = [0.42, 0.6, 0.8, 1.0];
  const DECOR = {
    1: ['dc_guijarro', 'dc_guijarro', 'dc_hierba_0', 'dc_hierba_1', 'dc_seta_0', 'dc_seta_1'],
    2: ['dc_guijarro', 'dc_guijarro', 'dc_guijarro', 'dc_huesos', 'dc_seta_1'],
    3: ['dc_guijarro', 'dc_guijarro', 'dc_estalagmita_0', 'dc_estalagmita_1', 'dc_cristalito'],
    4: ['dc_guijarro', 'dc_hierba_0', 'dc_hierba_1'],
    5: ['dc_huesos', 'dc_guijarro', 'dc_guijarro'],
  };
  function ocupado(x, y) { return muroEn(x, y) ? 1 : 0; }
  function aoVertice(lado1, lado2, esquina) { return AO[lado1 && lado2 ? 0 : 3 - (lado1 + lado2 + esquina)]; }

  function construirTrozo(cx, cy) {
    const L = new Lote(CHUNK * CHUNK * 3);
    for (let ty = cy * CHUNK; ty < cy * CHUNK + CHUNK; ty++) for (let tx = cx * CHUNK; tx < cx * CHUNK + CHUNK; tx++) {
      if (!enMundo(tx, ty)) continue;
      const i = idx(tx, ty), m = M.muro[i];
      if (!m) {
        const b = M.piso[i], h = hash2(tx, ty);
        const n = ocupado(tx, ty - 1), s = ocupado(tx, ty + 1), o = ocupado(tx - 1, ty), e = ocupado(tx + 1, ty);
        const ao = [aoVertice(o, n, ocupado(tx - 1, ty - 1)), aoVertice(e, n, ocupado(tx + 1, ty - 1)), aoVertice(e, s, ocupado(tx + 1, ty + 1)), aoVertice(o, s, ocupado(tx - 1, ty + 1))];
        const agua = b === AGUA, orillaN = agua && M.piso[idx(tx, ty - 1)] !== AGUA;
        const sombraN = n ? 0.78 : orillaN ? 0.55 : 1; // las paredes (y la orilla del agua) dan sombra hacia la cámara
        const tin = ao.map((a, k) => { const v = a * (k < 2 ? sombraN : 1); return [v, v, v, 1]; });
        L.quad([[tx, 0, ty], [tx + 1, 0, ty], [tx + 1, 0, ty + 1], [tx, 0, ty + 1]], uvDe(agua ? `agua_${Math.floor(h * 4)}` : `s_${SUELO_DE[b]}_${Math.floor(h * 4)}`), tin, 0, 0, 0, 0,
          [[tx, ty], [tx + 1, ty], [tx + 1, ty + 1], [tx, ty + 1]]);
        if (agua) continue;
        // adornos de pie: guijarros, hierba, setas, estalagmitas… (más cerca de las paredes)
        const h4 = hash2(tx, ty, 9), cerca = n + s + o + e;
        if (h4 < 0.1 + cerca * 0.07 && !M.objetos.has(i) && !M.bloqueo[i]) {
          const lista = DECOR[b], h5 = hash2(tx, ty, 11);
          let nom = lista[Math.floor(h5 * lista.length)];
          if (nom.startsWith('dc_guijarro')) nom = `dc_guijarro_${b}_${Math.floor(hash2(tx, ty, 13) * 3)}`;
          const r = SPR[nom];
          if (r) {
            const w = r.w / 16, hh = (r.h / 16) * KY, dx = 0.2 + h5 * 0.6, dz = 0.35 + hash2(tx, ty, 12) * 0.55;
            const x0 = tx + dx - w / 2, z0 = ty + dz;
            L.quad([[x0, hh, z0], [x0 + w, hh, z0], [x0 + w, 0, z0], [x0, 0, z0]], uvDe(nom, h5 > 0.5), [1, 1, 1, 1], tx + dx, z0);
          }
        }
        const h2 = hash2(tx, ty, 3);
        if (h2 < 0.08 && !M.objetos.has(i)) {
          const ad = ADORNOS3D[b], nom = ad[Math.floor(h2 * 1000) % ad.length], r = SPR[nom];
          if (r) {
            const ax = tx + (2 + Math.floor(h * 10)) / 16, az = ty + (2 + (Math.floor(h2 * 997) % 10)) / 16, w = r.w / 16, d = r.h / 16;
            const t = [ao[0], ao[0], ao[0], 1];
            L.quad([[ax, 0.004, az], [ax + w, 0.004, az], [ax + w, 0.004, az + d], [ax, 0.004, az + d]], uvDe(nom, h > 0.5), t, ax + w / 2, az + d / 2);
          }
        }
        continue;
      }
      const arte = INFO_MURO[m].arte, v = hash2(tx, ty, 1) < 0.5 ? 0 : 1;
      const N = !muroEn(tx, ty - 1), E = !muroEn(tx + 1, ty), S = !muroEn(tx, ty + 1), O = !muroEn(tx - 1, ty);
      const tapa = 0.42;
      L.quad([[tx, ALTO, ty], [tx + 1, ALTO, ty], [tx + 1, ALTO, ty + 1], [tx, ALTO, ty + 1]], uvDe(`m_${arte}_${N | (E << 1) | (S << 2) | (O << 3)}_${v}`), [tapa, tapa, tapa, 1], 0, 0, 0, 0,
        [[tx + 0.5, ty + 0.5 - (N ? 0.45 : 0)], [tx + 0.5, ty + 0.5 - (N ? 0.45 : 0)], [tx + 0.5, ty + 0.5 + (S ? 0.45 : 0)], [tx + 0.5, ty + 0.5 + (S ? 0.45 : 0)]]);
      const mena = M.mena[i];
      if (S) {
        const caraUv = uvDe(`c_${arte}_${(O ? 1 : 0) | (E ? 2 : 0)}_${v}`);
        const t = [[1, 1, 1, 1], [1, 1, 1, 1], [0.62, 0.62, 0.62, 1], [0.62, 0.62, 0.62, 1]];
        L.quad([[tx, ALTO, ty + 1], [tx + 1, ALTO, ty + 1], [tx + 1, 0, ty + 1], [tx, 0, ty + 1]], caraUv, t, tx + 0.5, ty + 1.5);
        if (mena) L.quad([[tx, ALTO, ty + 1.002], [tx + 1, ALTO, ty + 1.002], [tx + 1, 0, ty + 1.002], [tx, 0, ty + 1.002]], uvDe(`menac_${INFO_MENA[mena].arte}_${v}`), t, tx + 0.5, ty + 1.5, 0.12);
      }
      const lado = [[0.72, 0.72, 0.72, 1], [0.72, 0.72, 0.72, 1], [0.48, 0.48, 0.48, 1], [0.48, 0.48, 0.48, 1]];
      if (E) L.quad([[tx + 1, ALTO, ty + 1], [tx + 1, ALTO, ty], [tx + 1, 0, ty], [tx + 1, 0, ty + 1]], uvDe(`c_${arte}_0_${v}`), lado, tx + 1.5, ty + 0.5);
      if (O) L.quad([[tx, ALTO, ty], [tx, ALTO, ty + 1], [tx, 0, ty + 1], [tx, 0, ty]], uvDe(`c_${arte}_0_${v}`), lado, tx - 0.5, ty + 0.5);
      if (mena) L.quad([[tx, ALTO + 0.003, ty], [tx + 1, ALTO + 0.003, ty], [tx + 1, ALTO + 0.003, ty + 1], [tx, ALTO + 0.003, ty + 1]], uvDe(`mena_${INFO_MENA[mena].arte}_${v}`), [0.8, 0.8, 0.8, 1], tx + 0.5, ty + 0.5 + (S ? 0.45 : 0), 0.08);
    }
    let t = chunks.get(cx + ',' + cy);
    if (!t) { t = crearVao(prog); chunks.set(cx + ',' + cy, t); }
    subir(t, L, gl.STATIC_DRAW);
    t.sucio = false;
  }
  // Una casilla cambió: hay que rehacer su trozo (y el vecino si está en el borde).
  function tocar(tx, ty) {
    if (!gl) return;
    for (const [dx, dy] of [[0, 0], [1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const t = chunks.get(Math.floor((tx + dx) / CHUNK) + ',' + Math.floor((ty + dy) / CHUNK));
      if (t) t.sucio = true;
    }
  }
  function olvidarTodo() { if (!gl) return; for (const t of chunks.values()) { gl.deleteBuffer(t.vbo); gl.deleteVertexArray(t.vao); } chunks.clear(); }

  // ---------------------------------------------------------------- sprites del cuadro

  const lotes = { spr: new Lote(), sombras: new Lote(), brillo: new Lote(), halos: new Lote(256) };
  function limpiarLotes() { for (const l of Object.values(lotes)) l.n = 0; }
  // Sprite de pie, con los pies en (x, z) (casillas). o: {y, flip, tinte, blanco, unlit, lote, escala}
  function sprite(n, x, z, o = {}) {
    const r = SPR[n];
    if (!r) return;
    const e = o.escala || 1, w = (r.w / 16) * e, h = (r.h / 16) * KY * e, y = o.y || 0, x0 = x - w / 2 + (o.dx || 0);
    const tin = o.tinte || [1, 1, 1, 1];
    (o.lote || lotes.spr).quad([[x0, y + h, z], [x0 + w, y + h, z], [x0 + w, y, z], [x0, y, z]], uvDe(n, o.flip), tin, o.lx ?? x, o.lz ?? z, o.unlit || 0, o.blanco || 0);
  }
  // Plano en el suelo (sombras, marcas), centrado en (x, z).
  function plano(n, x, z, o = {}) {
    const r = SPR[n];
    if (!r) return;
    const w = (r.w / 16) * (o.escala || 1), d = (r.h / 16) * (o.escala || 1), y = o.y ?? 0.006;
    (o.lote || lotes.sombras).quad([[x - w / 2, y, z - d / 2], [x + w / 2, y, z - d / 2], [x + w / 2, y, z + d / 2], [x - w / 2, y, z + d / 2]], uvDe(n), o.tinte || [1, 1, 1, 1], x, z, o.unlit ?? 1, 0);
  }
  // Cara vertical pegada a la pared (grietas, marca de picar en la cara).
  function cara(n, x, z, o = {}) {
    const r = SPR[n];
    if (!r) return;
    (o.lote || lotes.spr).quad([[x, ALTO, z], [x + 1, ALTO, z], [x + 1, 0, z], [x, 0, z]], uvDe(n), o.tinte || [1, 1, 1, 1], x + 0.5, z + 0.5, o.unlit || 0, 0);
  }
  function tapa(n, x, z, o = {}) {
    const r = SPR[n];
    if (!r) return;
    const y = ALTO + 0.006;
    (o.lote || lotes.spr).quad([[x, y, z], [x + 1, y, z], [x + 1, y, z + 1], [x, y, z + 1]], uvDe(n), o.tinte || [1, 1, 1, 1], x + 0.5, z + 0.5, o.unlit || 0, 0);
  }
  function halo(x, y, z, radio, color, fuerza) {
    const L = lotes.halos, r = radio;
    L.reservar(1);
    // de cara a la cámara
    const uy = Math.cos(PITCH) * r, uz = -Math.sin(PITCH) * r / KZ;
    L.quad([[x - r, y + uy, z + uz], [x + r, y + uy, z + uz], [x + r, y - uy, z - uz], [x - r, y - uy, z - uz]], [0, 0, 1, 1], [color[0], color[1], color[2], fuerza], 0, 0);
  }

  // ---------------------------------------------------------------- posproceso

  function fbo(w, h, profundidad, filtro) {
    const tex = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, tex);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
    for (const [k, v] of [[gl.TEXTURE_MIN_FILTER, filtro], [gl.TEXTURE_MAG_FILTER, filtro], [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, k, v);
    const f = gl.createFramebuffer();
    gl.bindFramebuffer(gl.FRAMEBUFFER, f);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, tex, 0);
    let rb = null;
    if (profundidad) {
      rb = gl.createRenderbuffer();
      gl.bindRenderbuffer(gl.RENDERBUFFER, rb);
      gl.renderbufferStorage(gl.RENDERBUFFER, gl.DEPTH_COMPONENT24, w, h);
      gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.RENDERBUFFER, rb);
    }
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    return { f, tex, rb, w, h };
  }
  function crearFbos() {
    if (fb) for (const o of Object.values(fb)) { gl.deleteFramebuffer(o.f); gl.deleteTexture(o.tex); if (o.rb) gl.deleteRenderbuffer(o.rb); }
    const w2 = Math.max(1, W >> 2), h2 = Math.max(1, H >> 2), w3 = Math.max(1, W >> 3), h3 = Math.max(1, H >> 3);
    fb = {
      escena: fbo(W, H, true, gl.NEAREST),
      a: fbo(w2, h2, false, gl.LINEAR), b: fbo(w2, h2, false, gl.LINEAR),
      c: fbo(w3, h3, false, gl.LINEAR), d: fbo(w3, h3, false, gl.LINEAR),
    };
  }
  function pasada(p, destino, texs, unif = {}) {
    gl.bindFramebuffer(gl.FRAMEBUFFER, destino ? destino.f : null);
    gl.viewport(0, 0, destino ? destino.w : W, destino ? destino.h : H);
    gl.useProgram(p);
    texs.forEach(([nombre, t], k) => { gl.activeTexture(gl.TEXTURE0 + k); gl.bindTexture(gl.TEXTURE_2D, t); gl.uniform1i(gl.getUniformLocation(p, nombre), k); });
    for (const [n, v] of Object.entries(unif)) { const l = gl.getUniformLocation(p, n); if (Array.isArray(v)) gl.uniform2f(l, v[0], v[1]); else gl.uniform1f(l, v); }
    gl.bindVertexArray(vaoVacio);
    gl.drawArrays(gl.TRIANGLES, 0, 3);
  }
  function posproceso(t) {
    gl.disable(gl.DEPTH_TEST); gl.disable(gl.BLEND);
    pasada(progBrillo, fb.a, [['uTex', fb.escena.tex]]);
    pasada(progDesenfoque, fb.b, [['uTex', fb.a.tex]], { uDir: [1 / fb.a.w, 0] });
    pasada(progDesenfoque, fb.a, [['uTex', fb.b.tex]], { uDir: [0, 1 / fb.a.h] });
    pasada(progDesenfoque, fb.c, [['uTex', fb.a.tex]], { uDir: [1.5 / fb.c.w, 0] });
    pasada(progDesenfoque, fb.d, [['uTex', fb.c.tex]], { uDir: [0, 1.5 / fb.c.h] });
    pasada(progFinal, null, [['uEscena', fb.escena.tex], ['uB1', fb.a.tex], ['uB2', fb.d.tex]], { uT: t, uBloom: 1.0 });
    gl.activeTexture(gl.TEXTURE0);
    gl.enable(gl.DEPTH_TEST);
  }

  // ---------------------------------------------------------------- dibujar

  function dibujar(luz, t = 0) {
    gl.bindFramebuffer(gl.FRAMEBUFFER, fb.escena.f);
    gl.viewport(0, 0, W, H);
    gl.clearColor(0, 0, 0, 1);
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
    // textura de luz
    gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, texLuz);
    gl.pixelStorei(gl.UNPACK_ALIGNMENT, 1);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, luz.w, luz.h, 0, gl.RGBA, gl.UNSIGNED_BYTE, luz.datos);
    gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, texAtlas);
    gl.useProgram(prog);
    gl.uniformMatrix4fv(loc.uVP, false, cam.vp);
    gl.uniform4f(loc.uLuzRect, luz.x0, luz.y0, 1 / luz.w, 1 / luz.h);
    gl.uniform1i(loc.uAtlas, 0); gl.uniform1i(loc.uLuz, 1);
    gl.uniform1f(loc.uCorte, 0.5); gl.uniform1f(loc.uAditivo, 0); gl.uniform2f(loc.uAtlasTam, ATLAS.width, ATLAS.height);
    gl.enable(gl.DEPTH_TEST); gl.depthFunc(gl.LEQUAL); gl.depthMask(true); gl.disable(gl.BLEND);
    gl.enable(gl.CULL_FACE); gl.cullFace(gl.BACK); gl.frontFace(gl.CW);

    const v = vista();
    for (let cy = Math.floor(v.z0 / CHUNK); cy <= Math.floor(v.z1 / CHUNK); cy++) for (let cx = Math.floor(v.x0 / CHUNK); cx <= Math.floor(v.x1 / CHUNK); cx++) {
      if (cx < 0 || cy < 0 || cx * CHUNK >= N_MUNDO || cy * CHUNK >= N_MUNDO) continue;
      let t = chunks.get(cx + ',' + cy);
      if (!t || t.sucio) { construirTrozo(cx, cy); t = chunks.get(cx + ',' + cy); }
      if (!t.n) continue;
      gl.bindVertexArray(t.vao);
      gl.drawElements(gl.TRIANGLES, t.n * 6, gl.UNSIGNED_INT, 0);
    }
    gl.disable(gl.CULL_FACE);
    const pintar = (nombre, uso = gl.DYNAMIC_DRAW) => {
      const o = dinamicos[nombre], l = lotes[nombre];
      if (!l.n) return;
      subir(o, l, uso);
      gl.bindVertexArray(o.vao);
      gl.drawElements(gl.TRIANGLES, l.n * 6, gl.UNSIGNED_INT, 0);
    };
    // sombras (mezcla normal, sin escribir profundidad)
    gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA); gl.depthMask(false);
    gl.uniform1f(loc.uCorte, 0.01);
    pintar('sombras');
    // sprites
    gl.disable(gl.BLEND); gl.depthMask(true); gl.uniform1f(loc.uCorte, 0.5);
    pintar('spr');
    // lo que brilla: aditivo
    gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE); gl.depthMask(false); gl.uniform1f(loc.uCorte, 0.01); gl.uniform1f(loc.uAditivo, 1);
    pintar('brillo');
    // halos
    gl.useProgram(progHalo);
    gl.uniformMatrix4fv(locH.uVP, false, cam.vp);
    gl.activeTexture(gl.TEXTURE2); gl.bindTexture(gl.TEXTURE_2D, texHalo); gl.uniform1i(locH.uHalo, 2);
    gl.blendFunc(gl.ONE, gl.ONE); gl.disable(gl.DEPTH_TEST);
    pintar('halos');
    gl.activeTexture(gl.TEXTURE0);
    gl.depthMask(true); gl.disable(gl.BLEND); gl.enable(gl.DEPTH_TEST);
    posproceso(t);
    gl.bindVertexArray(null);
  }

  // Del mundo a la pantalla (0..1); y en unidades del mundo.
  function aPantalla(x, y, z) {
    const m = cam.vp, cx = m[0] * x + m[4] * y + m[8] * z + m[12], cy = m[1] * x + m[5] * y + m[9] * z + m[13], w = m[3] * x + m[7] * y + m[11] * z + m[15];
    return { x: (cx / w + 1) / 2, y: (1 - cy / w) / 2 };
  }
  return { iniciar, ajustar, camara, aMundo, aPantalla, vista, dibujar, sprite, plano, cara, tapa, halo, tocar, olvidarTodo, limpiarLotes, lotes, cam, uv: uvDe, ALTO, KY, get W() { return W; }, get H() { return H; } };
})();
