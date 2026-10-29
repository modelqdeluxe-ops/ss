'use strict';
// Motor 3D con WebGL2, al estilo de Core Keeper: el mundo son bloques con texturas de pixel art (tapa arriba,
// cara delante y a los lados), vistos con una cámara inclinada con poca perspectiva. Personajes y objetos son
// sprites de pie dentro del mundo. La luz viene de un mapa por casilla (mundo.js; no atraviesa paredes) que el
// sombreador lee en el centro de cada píxel del arte y reparte en escalones tramados. Después: resplandor,
// gradación, viñeta y grano.
//
// Unidades: 1 = una casilla. x hacia el este, z hacia el sur (hacia la cámara), y hacia arriba.
// El suelo se estira en z para que se vea cuadrado; las paredes miden lo justo para que su cara se vea con
// píxeles cuadrados. Se dibuja a una resolución interna de ~2 píxeles por píxel del arte y el navegador la
// escala sin suavizar: barato en el móvil y nítido.

const GL = (() => {
  const PITCH = (50 * Math.PI) / 180, FOV = (13 * Math.PI) / 180;
  const KZ = 1 / Math.sin(PITCH);
  const ALTO_CARA = 12;
  const ALTO = ALTO_CARA / 16 / Math.cos(PITCH);
  const KY = 1 / Math.cos(PITCH);
  const CHUNK = 16, CASILLAS_CORTO = 14;

  let gl = null, canvas = null, prog = null, progHalo = null, texAtlas = null, texNormales = null, texLuz = null, texHalo = null, texRayo = null;
  let progBrillo = null, progDesenfoque = null, progFinal = null, vaoVacio = null, fb = null;
  let W = 1, H = 1, dist = 22, luzW = 0, luzH = 0;
  const cam = { x: 0, z: 0, vp: new Float32Array(16), inv: new Float32Array(16) };
  const chunks = new Map();
  const loc = {}, locH = {}, locP = new Map();
  const UV = new Map();

  // ---------------------------------------------------------------- matrices (columna mayor)
  function mul(a, b) {
    const o = new Float32Array(16);
    for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) { let s = 0; for (let k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k]; o[c * 4 + r] = s; }
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
    let xx = zz, xz = -zx; const xl = Math.hypot(xx, xz); xx /= xl; xz /= xl;
    const yx = zy * xz, yy = zz * xx - zx * xz, yz = -zy * xx;
    return new Float32Array([xx, yx, zx, 0, 0, yy, zy, 0, xz, yz, zz, 0, -(xx * ex + xz * ez), -(yx * ex + yy * ey + yz * ez), -(zx * ex + zy * ey + zz * ez), 1]);
  }
  function invertir(a) {
    const o = new Float32Array(16);
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
  in vec3 aPos; in vec2 aUv; in vec4 aTinte; in vec2 aLuz; in vec2 aExtra; in vec3 aNorm;
  uniform mat4 uVP; uniform vec4 uLuzRect; uniform sampler2D uLuz;
  out vec2 vUv; out vec4 vTinte; out vec2 vLuzUv; out vec2 vExtra; out vec3 vNorm; out vec2 vPend;
  void main() {
    gl_Position = uVP * vec4(aPos, 1.0);
    vUv = aUv; vTinte = aTinte; vExtra = aExtra; vNorm = aNorm;
    vLuzUv = (aLuz - uLuzRect.xy) * uLuzRect.zw;
    // pendiente del mapa de luz en este vértice (de dónde viene la luz): se calcula aquí y no en cada píxel
    vec2 e = uLuzRect.zw;
    const vec3 Y = vec3(0.3, 0.5, 0.2);
    vPend = vec2(dot(textureLod(uLuz, vLuzUv + vec2(e.x, 0.0), 0.0).rgb - textureLod(uLuz, vLuzUv - vec2(e.x, 0.0), 0.0).rgb, Y),
                 dot(textureLod(uLuz, vLuzUv + vec2(0.0, e.y), 0.0).rgb - textureLod(uLuz, vLuzUv - vec2(0.0, e.y), 0.0).rgb, Y));
  }`;
  // aExtra.x: cuánto ignora la luz (1 = brilla solo); aExtra.y: destello blanco. aNorm: normal de la cara (el
  // suelo y las paredes); si es cero, la normal sale del atlas de normales (los modelos horneados).
  // Luz de lado: la pendiente del mapa de luz dice de dónde viene la luz; las caras que miran hacia allí se
  // aclaran y las que le dan la espalda se oscurecen (una antorcha a la izquierda alumbra el lado izquierdo).
  const FS = `#version 300 es
  precision highp float;
  uniform sampler2D uAtlas; uniform sampler2D uLuz; uniform sampler2D uNormales; uniform float uCorte; uniform float uAditivo; uniform vec2 uAtlasTam; uniform float uLado;
  in vec2 vUv; in vec4 vTinte; in vec2 vLuzUv; in vec2 vExtra; in vec3 vNorm; in vec2 vPend;
  out vec4 color;
  const float BAYER[16] = float[16](0.,8.,2.,10.,12.,4.,14.,6.,3.,11.,1.,9.,15.,7.,13.,5.);
  void main() {
    vec4 t = texture(uAtlas, vUv);
    if (t.a < uCorte) discard;
    vec3 base = mix(t.rgb * vTinte.rgb, vec3(1.0), vExtra.y);
    vec2 texel = floor(vUv * uAtlasTam);
    vec2 luzUv = vLuzUv;
    mat2 ju = mat2(dFdx(vUv), dFdy(vUv)), jl = mat2(dFdx(vLuzUv), dFdy(vLuzUv));
    if (abs(determinant(ju)) > 1e-14) luzUv += jl * inverse(ju) * ((texel + 0.5) / uAtlasTam - vUv);
    vec3 l = texture(uLuz, luzUv).rgb;
    vec3 n = vNorm;
    bool volteo = n.y < -2.5;
    if (volteo || dot(n, n) < 0.25) { vec4 nt = texture(uNormales, vUv); n = nt.a > 0.5 ? nt.rgb * 2.0 - 1.0 : vec3(0.0); if (volteo) n.x = -n.x; }
    if (abs(n.x) + abs(n.z) > 0.05) l *= clamp(1.0 + uLado * dot(n.xz, vPend), 0.55, 1.6);
    l = l * l * 2.4;
    // junto al fuego no se quema el dibujo: la luz fuerte se comprime (el personaje no se vuelve una mancha)
    l = min(l, 1.0) + max(l - 1.0, 0.0) * 0.35;
    vec2 m = mod(texel, 4.0);
    float b = (BAYER[int(m.y) * 4 + int(m.x)] + 0.5) / 16.0;
    // escalones de luz con trama: 12 y la trama solo en la mitad del escalón (con 7 escalones y trama entera, lo
    // oscuro se veía como una rejilla)
    vec3 lq = floor(l * 12.0 + 0.25 + b * 0.5) / 12.0;
    vec3 c = base * mix(lq, vec3(1.0), vExtra.x);
    color = vec4(c * mix(1.0, vTinte.a, uAditivo) * 0.5, t.a * vTinte.a);
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
  uniform sampler2D uEscena; uniform sampler2D uB1; uniform sampler2D uB2; uniform float uT; uniform float uBloom; uniform vec3 uTono;
  in vec2 vUv; out vec4 o;
  void main() {
    vec3 c = texture(uEscena, vUv).rgb * 2.0;
    c += (texture(uB1, vUv).rgb * 0.8 + texture(uB2, vUv).rgb * 1.1) * uBloom;
    vec3 e = max(c - 0.75, 0.0);
    c = min(c, 0.75) + 0.25 * (1.0 - exp(-e * 4.0));
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(l), c, 1.12);
    c *= mix(uTono, vec3(1.04, 1.0, 0.94), smoothstep(0.02, 0.5, l));
    vec2 q = (vUv - 0.5) * vec2(1.0, 1.25);
    c *= clamp(1.0 - dot(q, q) * 0.7, 0.0, 1.0);
    float n = fract(sin(dot(gl_FragCoord.xy + fract(uT) * 91.0, vec2(12.9898, 78.233))) * 43758.5453);
    c += (n - 0.5) * 0.02 * (0.3 + l);
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
  // x y z  u v  r g b a  lx lz  unlit blanco  nx ny nz → 16 floats por vértice, 4 por quad.
  const F = 16;
  class Lote {
    constructor(cap = 2048) { this.d = new Float32Array(cap * 4 * F); this.n = 0; this.nx = 0; this.ny = 0; this.nz = 0; }
    // normal de los quads que vengan (0,0,0: la del atlas de normales)
    normal(x, y, z) { this.nx = x; this.ny = y; this.nz = z; return this; }
    reservar() {
      if ((this.n + 1) * 4 * F > this.d.length) { const nd = new Float32Array(this.d.length * 2); nd.set(this.d); this.d = nd; }
    }
    // Quad sin crear basura. uv: [u0,v0,u1,v1] (ya calculado); tinte igual en las 4 esquinas.
    q(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, uv, flip, r, g, b, a, lx, lz, unlit, blanco) {
      this.reservar();
      const d = this.d; let o = this.n * 4 * F;
      const ua = flip ? uv[2] : uv[0], ub = flip ? uv[0] : uv[2], nx = flip ? -this.nx : this.nx, ny = flip && !this.nx && !this.ny && !this.nz ? -3 : this.ny, nz = this.nz;
      d[o++] = x0; d[o++] = y0; d[o++] = z0; d[o++] = ua; d[o++] = uv[1]; d[o++] = r; d[o++] = g; d[o++] = b; d[o++] = a; d[o++] = lx; d[o++] = lz; d[o++] = unlit; d[o++] = blanco; d[o++] = nx; d[o++] = ny; d[o++] = nz;
      d[o++] = x1; d[o++] = y1; d[o++] = z1; d[o++] = ub; d[o++] = uv[1]; d[o++] = r; d[o++] = g; d[o++] = b; d[o++] = a; d[o++] = lx; d[o++] = lz; d[o++] = unlit; d[o++] = blanco; d[o++] = nx; d[o++] = ny; d[o++] = nz;
      d[o++] = x2; d[o++] = y2; d[o++] = z2; d[o++] = ub; d[o++] = uv[3]; d[o++] = r; d[o++] = g; d[o++] = b; d[o++] = a; d[o++] = lx; d[o++] = lz; d[o++] = unlit; d[o++] = blanco; d[o++] = nx; d[o++] = ny; d[o++] = nz;
      d[o++] = x3; d[o++] = y3; d[o++] = z3; d[o++] = ua; d[o++] = uv[3]; d[o++] = r; d[o++] = g; d[o++] = b; d[o++] = a; d[o++] = lx; d[o++] = lz; d[o++] = unlit; d[o++] = blanco; d[o++] = nx; d[o++] = ny; d[o++] = nz;
      this.n++;
    }
    // Quad con tinte y punto de luz por esquina (para el mundo, que se construye pocas veces).
    qEsq(c, uv, flip, tintes, luces, unlit = 0) {
      this.q(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9], c[10], c[11], uv, flip, 1, 1, 1, 1, 0, 0, unlit, 0);
      const d = this.d, base = (this.n - 1) * 4 * F;
      for (let k = 0; k < 4; k++) {
        const o = base + k * F;
        d[o + 5] = d[o + 6] = d[o + 7] = tintes[k];
        d[o + 9] = luces[k * 2]; d[o + 10] = luces[k * 2 + 1];
      }
    }
  }
  let indices = null, nIndices = 0;
  function asegurarIndices(q) {
    if (q <= nIndices) return;
    nIndices = Math.max(q, nIndices * 2, 16384);
    const ix = new Uint32Array(nIndices * 6);
    for (let i = 0; i < nIndices; i++) { const b = i * 4, o = i * 6; ix[o] = b; ix[o + 1] = b + 1; ix[o + 2] = b + 2; ix[o + 3] = b; ix[o + 4] = b + 2; ix[o + 5] = b + 3; }
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indices);
    gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, ix, gl.STATIC_DRAW);
  }
  function crearVao(p) {
    const vao = gl.createVertexArray(), vbo = gl.createBuffer();
    gl.bindVertexArray(vao);
    gl.bindBuffer(gl.ARRAY_BUFFER, vbo);
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indices);
    const at = (n, k, off) => { const l = gl.getAttribLocation(p, n); if (l < 0) return; gl.enableVertexAttribArray(l); gl.vertexAttribPointer(l, k, gl.FLOAT, false, F * 4, off * 4); };
    at('aPos', 3, 0); at('aUv', 2, 3); at('aTinte', 4, 5); at('aLuz', 2, 9); at('aExtra', 2, 11); at('aNorm', 3, 13);
    gl.bindVertexArray(null);
    return { vao, vbo, n: 0, cap: 0 };
  }
  function subir(obj, lote, uso) {
    gl.bindBuffer(gl.ARRAY_BUFFER, obj.vbo);
    const datos = lote.d.subarray(0, lote.n * 4 * F);
    if (uso === gl.DYNAMIC_DRAW && obj.cap >= datos.length) gl.bufferSubData(gl.ARRAY_BUFFER, 0, datos);
    else { gl.bufferData(gl.ARRAY_BUFFER, uso === gl.DYNAMIC_DRAW ? lote.d : datos, uso); obj.cap = uso === gl.DYNAMIC_DRAW ? lote.d.length : datos.length; }
    obj.n = lote.n;
    asegurarIndices(lote.n);
  }

  // Dibujos que se piden y no existen (las pruebas los listan con window.FALTAN).
  function falta(n) { (window.FALTAN || (window.FALTAN = new Set())).add(n); }
  // UV de cada sprite del atlas (calculadas una vez), un poco hacia dentro para que no sangren.
  function uvDe(n) {
    let u = UV.get(n);
    if (u) return u;
    const r = SPR[n];
    if (!r) { falta(n); return null; }
    const aw = ATLAS.width, ah = ATLAS.height, e = 0.02;
    u = new Float32Array([(r.x + e) / aw, (r.y + e) / ah, (r.x + r.w - e) / aw, (r.y + r.h - e) / ah]);
    UV.set(n, u);
    return u;
  }

  // La zona del atlas que se vuelve a hornear en marcha (el personaje con su equipo): se sube solo ese trozo.
  function subirZona(x, y, w, h) {
    if (!gl) return;
    const dc = ATLAS.getContext('2d').getImageData(x, y, w, h).data, dn = ATLAS_N.getContext('2d').getImageData(x, y, w, h).data;
    gl.pixelStorei(gl.UNPACK_ALIGNMENT, 1);
    gl.bindTexture(gl.TEXTURE_2D, texAtlas); gl.texSubImage2D(gl.TEXTURE_2D, 0, x, y, w, h, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array(dc.buffer));
    gl.bindTexture(gl.TEXTURE_2D, texNormales); gl.texSubImage2D(gl.TEXTURE_2D, 0, x, y, w, h, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array(dn.buffer));
    gl.bindTexture(gl.TEXTURE_2D, null);
  }
  function olvidarUV(nombres) { for (const n of nombres) UV.delete(n); }
  const FUERZA_LADO = 2.4;

  // ---------------------------------------------------------------- iniciar

  function textura(filtro, datos, w, h) {
    const t = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, t);
    if (datos) gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, datos);
    for (const [k, v] of [[gl.TEXTURE_MIN_FILTER, filtro], [gl.TEXTURE_MAG_FILTER, filtro], [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, k, v);
    return t;
  }
  function iniciar(cv) {
    canvas = cv;
    gl = canvas.getContext('webgl2', { antialias: false, alpha: false, premultipliedAlpha: false, powerPreference: 'high-performance', preserveDrawingBuffer: false });
    if (!gl) return false;
    prog = compilar(VS, FS); progHalo = compilar(VSH, FSH);
    for (const n of ['uVP', 'uLuzRect', 'uAtlas', 'uLuz', 'uNormales', 'uCorte', 'uAditivo', 'uAtlasTam', 'uLado']) loc[n] = gl.getUniformLocation(prog, n);
    for (const n of ['uVP', 'uHalo']) locH[n] = gl.getUniformLocation(progHalo, n);
    indices = gl.createBuffer();
    asegurarIndices(16384);
    texAtlas = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, texAtlas);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, ATLAS);
    for (const [k, v] of [[gl.TEXTURE_MIN_FILTER, gl.NEAREST], [gl.TEXTURE_MAG_FILTER, gl.NEAREST], [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, k, v);
    texNormales = textura(gl.NEAREST);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, ATLAS_N);
    texLuz = textura(gl.LINEAR);
    const N = 64, hd = new Uint8Array(N * N * 4), rd = new Uint8Array(N * N * 4);
    for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
      const d = Math.hypot(x + 0.5 - N / 2, y + 0.5 - N / 2) / (N / 2), a = Math.max(0, 1 - d), v = Math.round(a * a * a * 255);
      hd.set([v, v, v, 255], (y * N + x) * 4);
      // rayo de luz: fuerte arriba, se apaga hacia abajo y hacia los lados
      const lado = 1 - Math.abs(x + 0.5 - N / 2) / (N / 2), alto = 1 - (y + 0.5) / N;
      const rv = Math.round(Math.pow(Math.max(0, lado), 1.6) * Math.pow(alto, 0.7) * 255);
      rd.set([rv, rv, rv, 255], (y * N + x) * 4);
    }
    texHalo = textura(gl.LINEAR, hd, N, N);
    texRayo = textura(gl.LINEAR, rd, N, N);
    progBrillo = compilar(VSP, FS_BRILLO); progDesenfoque = compilar(VSP, FS_DESENFOQUE); progFinal = compilar(VSP, FS_FINAL);
    vaoVacio = gl.createVertexArray();
    for (const n of ['spr', 'sombras', 'brillo']) dinamicos[n] = crearVao(prog);
    dinamicos.halos = crearVao(progHalo); dinamicos.rayos = crearVao(progHalo);
    return true;
  }
  const dinamicos = {};

  // El lado corto de la pantalla muestra unas 14 casillas. Se dibuja a ~2 píxeles por píxel del arte
  // (resolución interna) y el navegador lo agranda un número entero de veces, sin suavizar.
  function ajustar(wCss, hCss, dpr) {
    const wd = Math.round(wCss * dpr), hd = Math.round(hCss * dpr);
    // Píxeles del aparato por píxel del arte: entero, para que todos los píxeles midan igual (nítido como en
    // Core Keeper). Se pinta a 2 píxeles por píxel del arte y el navegador amplía sin suavizar.
    const pxArte = Math.max(2, Math.round(Math.min(wd, hd) / (CASILLAS_CORTO * 16)));
    W = Math.max(1, Math.round((wd * 2) / pxArte)); H = Math.max(1, Math.round((hd * 2) / pxArte));
    canvas.width = W; canvas.height = H;
    canvas.style.width = wCss + 'px'; canvas.style.height = hCss + 'px';
    dist = Math.min(wd, hd) / pxArte / 16 * (H / Math.min(W, H)) / (2 * Math.tan(FOV / 2));
    crearFbos();
  }

  function camara(x, z) {
    x = Math.round(x * 32) / 32; z = Math.round(z * 32) / 32; // a medio píxel del arte: el suelo no tiembla
    cam.x = x; cam.z = z;
    const tz = z * KZ;
    const v = mirar(x, Math.sin(PITCH) * dist, tz + Math.cos(PITCH) * dist, x, 0, tz);
    const s = new Float32Array([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, KZ, 0, 0, 0, 0, 1]);
    cam.vp = mul(mul(perspectiva(FOV, W / H, 1, dist * 3), v), s);
    cam.inv = invertir(cam.vp);
  }
  function aMundo(nx, ny, altura = 0) {
    const cx = nx * 2 - 1, cy = 1 - ny * 2, m = cam.inv;
    const p = (z) => { const x = m[0] * cx + m[4] * cy + m[8] * z + m[12], y = m[1] * cx + m[5] * cy + m[9] * z + m[13], zz = m[2] * cx + m[6] * cy + m[10] * z + m[14], w = m[3] * cx + m[7] * cy + m[11] * z + m[15]; return [x / w, y / w, zz / w]; };
    const a = p(-1), b = p(1), t = (altura - a[1]) / (b[1] - a[1]);
    return { x: a[0] + (b[0] - a[0]) * t, z: a[2] + (b[2] - a[2]) * t };
  }
  function aPantalla(x, y, z) {
    const m = cam.vp, cx = m[0] * x + m[4] * y + m[8] * z + m[12], cy = m[1] * x + m[5] * y + m[9] * z + m[13], w = m[3] * x + m[7] * y + m[11] * z + m[15];
    return { x: (cx / w + 1) / 2, y: (1 - cy / w) / 2 };
  }
  function vista() {
    let x0 = 1e9, x1 = -1e9, z0 = 1e9, z1 = -1e9;
    for (const [nx, ny] of [[0, 0], [1, 0], [0, 1], [1, 1]]) { const p = aMundo(nx, ny, 0); x0 = Math.min(x0, p.x); x1 = Math.max(x1, p.x); z0 = Math.min(z0, p.z); z1 = Math.max(z1, p.z); }
    return { x0: Math.floor(x0) - 1, x1: Math.ceil(x1) + 1, z0: Math.floor(z0) - 1, z1: Math.ceil(z1) + 3 };
  }

  // ---------------------------------------------------------------- trozos del mundo

  const AO = [0.42, 0.6, 0.8, 1.0];
  function ocupado(x, y) { return muroEn(x, y) ? 1 : 0; }
  function aoVertice(l1, l2, esq) { return AO[l1 && l2 ? 0 : 3 - (l1 + l2 + esq)]; }

  function construirTrozo(cx, cy) {
    const L = new Lote(CHUNK * CHUNK * 4);
    const T4 = [0, 0, 0, 0], LZ = [0, 0, 0, 0, 0, 0, 0, 0];
    for (let ty = cy * CHUNK; ty < cy * CHUNK + CHUNK; ty++) for (let tx = cx * CHUNK; tx < cx * CHUNK + CHUNK; tx++) {
      if (!enMundo(tx, ty)) continue;
      const i = idx(tx, ty), m = M.muro[i];
      if (!m) {
        const b = M.piso[i], h = hash2(tx, ty), info = INFO_SUELO[b];
        const n = ocupado(tx, ty - 1), s = ocupado(tx, ty + 1), o = ocupado(tx - 1, ty), e = ocupado(tx + 1, ty);
        const liquido = info.liquido, orillaN = liquido && !INFO_SUELO[M.piso[idx(tx, ty - 1)]].liquido && !n;
        const sombraN = n ? 0.78 : orillaN ? 0.55 : 1;
        T4[0] = aoVertice(o, n, ocupado(tx - 1, ty - 1)) * sombraN; T4[1] = aoVertice(e, n, ocupado(tx + 1, ty - 1)) * sombraN;
        T4[2] = aoVertice(e, s, ocupado(tx + 1, ty + 1)); T4[3] = aoVertice(o, s, ocupado(tx - 1, ty + 1));
        LZ[0] = tx; LZ[1] = ty; LZ[2] = tx + 1; LZ[3] = ty; LZ[4] = tx + 1; LZ[5] = ty + 1; LZ[6] = tx; LZ[7] = ty + 1;
        const y = liquido ? -0.06 : 0;
        L.normal(0, 1, 0);
        L.qEsq([tx, y, ty, tx + 1, y, ty, tx + 1, y, ty + 1, tx, y, ty + 1], uvDe(`s_${info.arte}_${Math.floor(h * 4)}`), false, T4, LZ, info.brilla || 0);
        // bordes orgánicos: los suelos vecinos con más prioridad se meten un poco en esta casilla
        if (!liquido) {
          const pri = PRIORIDAD_SUELO[info.arte] || 0;
          let capa = 0;
          const vecino = (xx, yy) => { if (!enMundo(xx, yy) || M.muro[idx(xx, yy)]) return 0; const b2 = M.piso[idx(xx, yy)]; if (b2 === b) return 0; const i2 = INFO_SUELO[b2]; return !i2.liquido && (PRIORIDAD_SUELO[i2.arte] || 0) > pri ? b2 : 0; };
          const poner = (b2, lado) => { const uv = uvDe(`borde_${INFO_SUELO[b2].arte}_${lado}`); if (!uv) return; const yy = 0.002 + 0.001 * capa++; L.qEsq([tx, yy, ty, tx + 1, yy, ty, tx + 1, yy, ty + 1, tx, yy, ty + 1], uv, false, T4, LZ, INFO_SUELO[b2].brilla || 0); };
          const vn = vecino(tx, ty - 1), ve = vecino(tx + 1, ty), vs = vecino(tx, ty + 1), vo = vecino(tx - 1, ty);
          if (vn) poner(vn, 'n'); if (ve) poner(ve, 'e'); if (vs) poner(vs, 's'); if (vo) poner(vo, 'o');
          for (const [dx, dy, lado, a, c] of [[-1, -1, 'no', vn, vo], [1, -1, 'ne', vn, ve], [1, 1, 'se', vs, ve], [-1, 1, 'so', vs, vo]]) { const d = vecino(tx + dx, ty + dy); if (d && d !== a && d !== c) poner(d, lado); }
        }
        if (liquido || M.objetos.has(i) || M.bloqueo[i]) continue;
        // adornos de pie (guijarros, hierba, setas, estalagmitas…), más junto a las paredes
        const h4 = hash2(tx, ty, 9), cerca = n + s + o + e, lista = info.decor;
        if (lista && h4 < info.densidad + cerca * 0.07) {
          const h5 = hash2(tx, ty, 11);
          let nom = lista[Math.floor(h5 * lista.length)];
          if (nom === 'guijarro') nom = `dc_guijarro_${info.piedra || b}_${Math.floor(hash2(tx, ty, 13) * 3)}`;
          const r = SPR[nom];
          L.normal(0, 0, 0);
          if (r) {
            const w = r.w / 16, hh = (r.h / 16) * KY, ax = tx + 0.2 + h5 * 0.6, az = ty + 0.35 + hash2(tx, ty, 12) * 0.55;
            const t = T4[3] * 0.5 + 0.5;
            L.q(ax - w / 2, hh, az, ax + w / 2, hh, az, ax + w / 2, 0, az, ax - w / 2, 0, az, uvDe(nom), h5 > 0.5, t, t, t, 1, ax, az, 0, 0);
          }
        }
        const h2 = hash2(tx, ty, 3);
        if (info.planos && h2 < 0.08) {
          const nom = info.planos[Math.floor(h2 * 1000) % info.planos.length], r = SPR[nom];
          L.normal(0, 1, 0);
          if (r) {
            const ax = tx + (2 + Math.floor(h * 10)) / 16, az = ty + (2 + (Math.floor(h2 * 997) % 10)) / 16, w = r.w / 16, d = r.h / 16, t = T4[0];
            L.q(ax, 0.004, az, ax + w, 0.004, az, ax + w, 0.004, az + d, ax, 0.004, az + d, uvDe(nom), h > 0.5, t, t, t, 1, ax + w / 2, az + d / 2, 0, 0);
          }
        }
        continue;
      }
      const im = INFO_MURO[m], arte = im.arte, v = hash2(tx, ty, 1) < 0.5 ? 0 : 1, alto = ALTO * (im.alto || 1);
      const N = !muroEn(tx, ty - 1), E = !muroEn(tx + 1, ty), S = !muroEn(tx, ty + 1), O = !muroEn(tx - 1, ty);
      const tapa = im.tapa ?? 0.42;
      T4[0] = T4[1] = T4[2] = T4[3] = tapa;
      const lzN = ty + 0.5 - (N ? 0.45 : 0), lzS = ty + 0.5 + (S ? 0.45 : 0);
      LZ[0] = tx + 0.5; LZ[1] = lzN; LZ[2] = tx + 0.5; LZ[3] = lzN; LZ[4] = tx + 0.5; LZ[5] = lzS; LZ[6] = tx + 0.5; LZ[7] = lzS;
      L.normal(0, 1, 0);
      L.qEsq([tx, alto, ty, tx + 1, alto, ty, tx + 1, alto, ty + 1, tx, alto, ty + 1], uvDe(`m_${arte}_${N | (E << 1) | (S << 2) | (O << 3)}_${v}`), false, T4, LZ, im.brilla || 0);
      const mena = M.mena[i];
      const CARA = [1, 1, 0.62, 0.62], LADO = [0.72, 0.72, 0.48, 0.48];
      if (S) {
        L.normal(0, 0, 1);
        LZ[0] = LZ[2] = LZ[4] = LZ[6] = tx + 0.5; LZ[1] = LZ[3] = LZ[5] = LZ[7] = ty + 1.5;
        L.qEsq([tx, alto, ty + 1, tx + 1, alto, ty + 1, tx + 1, 0, ty + 1, tx, 0, ty + 1], uvDe(`c_${arte}_${(O ? 1 : 0) | (E ? 2 : 0)}_${v}`), false, CARA, LZ, im.brilla || 0);
        if (mena) L.qEsq([tx, alto, ty + 1.002, tx + 1, alto, ty + 1.002, tx + 1, 0, ty + 1.002, tx, 0, ty + 1.002], uvDe(`menac_${INFO_MENA[mena].arte}_${v}`), false, CARA, LZ, 0.12);
      }
      if (E) { L.normal(1, 0, 0); LZ[0] = LZ[2] = LZ[4] = LZ[6] = tx + 1.5; LZ[1] = LZ[3] = LZ[5] = LZ[7] = ty + 0.5; L.qEsq([tx + 1, alto, ty + 1, tx + 1, alto, ty, tx + 1, 0, ty, tx + 1, 0, ty + 1], uvDe(`c_${arte}_0_${v}`), false, LADO, LZ, im.brilla || 0); }
      if (O) { L.normal(-1, 0, 0); LZ[0] = LZ[2] = LZ[4] = LZ[6] = tx - 0.5; LZ[1] = LZ[3] = LZ[5] = LZ[7] = ty + 0.5; L.qEsq([tx, alto, ty, tx, alto, ty + 1, tx, 0, ty + 1, tx, 0, ty], uvDe(`c_${arte}_0_${v}`), false, LADO, LZ, im.brilla || 0); }
      if (mena) {
        L.normal(0, 1, 0);
        const t = tapa * 1.8;
        L.q(tx, alto + 0.003, ty, tx + 1, alto + 0.003, ty, tx + 1, alto + 0.003, ty + 1, tx, alto + 0.003, ty + 1, uvDe(`mena_${INFO_MENA[mena].arte}_${v}`), false, t, t, t, 1, tx + 0.5, lzS, 0.08, 0);
      }
    }
    let t = chunks.get(cx + ',' + cy);
    if (!t) { t = crearVao(prog); chunks.set(cx + ',' + cy, t); }
    subir(t, L, gl.STATIC_DRAW);
    t.sucio = false;
  }
  function tocar(tx, ty) {
    if (!gl) return;
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
      const t = chunks.get(Math.floor((tx + dx) / CHUNK) + ',' + Math.floor((ty + dy) / CHUNK));
      if (t) t.sucio = true;
    }
  }
  function olvidarTodo() { if (!gl) return; for (const t of chunks.values()) { gl.deleteBuffer(t.vbo); gl.deleteVertexArray(t.vao); } chunks.clear(); }

  // ---------------------------------------------------------------- sprites del cuadro (sin crear basura)

  const lotes = { spr: new Lote(), sombras: new Lote(512), brillo: new Lote(), halos: new Lote(256), rayos: new Lote(64) };
  function limpiarLotes() { for (const k in lotes) lotes[k].n = 0; }
  const BLANCO = [1, 1, 1, 1];
  // Sprite de pie con los pies en (x, z). o: {y, flip, tinte, blanco, unlit, lote, escala, dx, lx, lz}
  // Para animar sin más cuadros: sx / sy (estirar o aplastar desde los pies), sesgo (la cabeza se va hacia un lado,
  // en casillas) y rot (gira sobre los pies, en radianes: para caer de lado).
  function sprite(n, x, z, o) {
    const r = SPR[n];
    if (!r) { falta(n); return; }
    const e = (o && o.escala) || 1, w = (r.w / 16) * e * ((o && o.sx) || 1), h = (r.h / 16) * KY * e * ((o && o.sy) || 1), y = (o && o.y) || 0, x0 = x - w / 2 + ((o && o.dx) || 0);
    const t = (o && o.tinte) || BLANCO, L = (o && o.lote) || lotes.spr, lx = o && o.lx !== undefined ? o.lx : x, lz = o && o.lz !== undefined ? o.lz : z;
    const sk = (o && o.sesgo) || 0, rot = (o && o.rot) || 0;
    if (!rot) { L.q(x0 + sk, y + h, z, x0 + w + sk, y + h, z, x0 + w, y, z, x0, y, z, uvDe(n), !!(o && o.flip), t[0], t[1], t[2], t[3], lx, lz, (o && o.unlit) || 0, (o && o.blanco) || 0); return; }
    const cx = x0 + w / 2, c = Math.cos(rot), s = Math.sin(rot), hw = w / 2;
    L.q(cx - hw * c - h * s, y - hw * s + h * c, z, cx + hw * c - h * s, y + hw * s + h * c, z, cx + hw * c, y + hw * s, z, cx - hw * c, y - hw * s, z,
      uvDe(n), !!(o && o.flip), t[0], t[1], t[2], t[3], lx, lz, (o && o.unlit) || 0, (o && o.blanco) || 0);
  }
  // Modelo horneado con el centro de su huella en (x, z): se coloca según lo que guardó el escultor.
  function modelo(n, x, z, o) {
    const m = META_SPR[n];
    if (!m) { sprite(n, x, z, o); return; }
    const e = (o && o.escala) || 1;
    if (o) { o.lx = o.lx ?? x; o.lz = o.lz ?? z; sprite(n, x + m.dx * e, z + m.dz * e, o); }
    else sprite(n, x + m.dx, z + m.dz, { lx: x, lz: z });
  }
  function plano(n, x, z, o) {
    const r = SPR[n];
    if (!r) { falta(n); return; }
    const e = (o && o.escala) || 1, w = (r.w / 16) * e, d = (r.h / 16) * e, y = o && o.y !== undefined ? o.y : 0.006, t = (o && o.tinte) || BLANCO;
    ((o && o.lote) || lotes.sombras).q(x - w / 2, y, z - d / 2, x + w / 2, y, z - d / 2, x + w / 2, y, z + d / 2, x - w / 2, y, z + d / 2, uvDe(n), false, t[0], t[1], t[2], t[3], x, z, o && o.unlit !== undefined ? o.unlit : 1, 0);
  }
  function cara(n, x, z, o) {
    if (!SPR[n]) return;
    const t = (o && o.tinte) || BLANCO;
    ((o && o.lote) || lotes.spr).q(x, ALTO, z, x + 1, ALTO, z, x + 1, 0, z, x, 0, z, uvDe(n), false, t[0], t[1], t[2], t[3], x + 0.5, z + 0.5, (o && o.unlit) || 0, 0);
  }
  function tapa(n, x, z, o) {
    if (!SPR[n]) return;
    const t = (o && o.tinte) || BLANCO, y = ALTO + 0.006;
    ((o && o.lote) || lotes.spr).q(x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1, uvDe(n), false, t[0], t[1], t[2], t[3], x + 0.5, z + 0.5, (o && o.unlit) || 0, 0);
  }
  // Quad libre: 12 números (4 esquinas) en el plano que quieras.
  function libre(n, p, t, lx, lz, unlit = 0, lote = lotes.spr) {
    const uv = uvDe(n);
    if (uv) lote.q(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11], uv, false, t[0], t[1], t[2], t[3], lx, lz, unlit, 0);
  }
  const UV_COMPLETO = new Float32Array([0, 0, 1, 1]);
  function halo(x, y, z, r, c, fuerza) {
    const uy = Math.cos(PITCH) * r, uz = (-Math.sin(PITCH) * r) / KZ;
    lotes.halos.q(x - r, y + uy, z + uz, x + r, y + uy, z + uz, x + r, y - uy, z - uz, x - r, y - uy, z - uz, UV_COMPLETO, false, c[0], c[1], c[2], fuerza, 0, 0, 0, 0);
  }
  // Rayo de luz que baja inclinado desde una grieta del techo.
  function rayo(x, z, ancho, largo, c, fuerza) {
    const y0 = largo, inc = largo * 0.35;
    lotes.rayos.q(x - ancho / 2 - inc, y0, z - 0.3, x + ancho / 2 - inc, y0, z - 0.3, x + ancho / 2, 0, z, x - ancho / 2, 0, z, UV_COMPLETO, false, c[0], c[1], c[2], fuerza, 0, 0, 0, 0);
  }

  // ---------------------------------------------------------------- posproceso

  function fbo(w, h, profundidad, filtro) {
    const tex = textura(filtro);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
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
    if (fb) for (const k in fb) { const o = fb[k]; gl.deleteFramebuffer(o.f); gl.deleteTexture(o.tex); if (o.rb) gl.deleteRenderbuffer(o.rb); }
    const w2 = Math.max(1, W >> 2), h2 = Math.max(1, H >> 2), w3 = Math.max(1, W >> 3), h3 = Math.max(1, H >> 3);
    fb = { escena: fbo(W, H, true, gl.NEAREST), a: fbo(w2, h2, false, gl.LINEAR), b: fbo(w2, h2, false, gl.LINEAR), c: fbo(w3, h3, false, gl.LINEAR), d: fbo(w3, h3, false, gl.LINEAR) };
  }
  function uni(p, n) { let m = locP.get(p); if (!m) { m = {}; locP.set(p, m); } if (!(n in m)) m[n] = gl.getUniformLocation(p, n); return m[n]; }
  function pasada(p, destino, t0, t1, t2, dx, dy) {
    gl.bindFramebuffer(gl.FRAMEBUFFER, destino ? destino.f : null);
    gl.viewport(0, 0, destino ? destino.w : W, destino ? destino.h : H);
    gl.useProgram(p);
    const nombres = p === progFinal ? ['uEscena', 'uB1', 'uB2'] : ['uTex'];
    [t0, t1, t2].forEach((t, k) => { if (!t) return; gl.activeTexture(gl.TEXTURE0 + k); gl.bindTexture(gl.TEXTURE_2D, t); gl.uniform1i(uni(p, nombres[k]), k); });
    if (dx !== undefined) gl.uniform2f(uni(p, 'uDir'), dx, dy);
    gl.bindVertexArray(vaoVacio);
    gl.drawArrays(gl.TRIANGLES, 0, 3);
  }
  function posproceso(t, tono) {
    gl.disable(gl.DEPTH_TEST); gl.disable(gl.BLEND);
    pasada(progBrillo, fb.a, fb.escena.tex);
    pasada(progDesenfoque, fb.b, fb.a.tex, null, null, 1 / fb.a.w, 0);
    pasada(progDesenfoque, fb.a, fb.b.tex, null, null, 0, 1 / fb.a.h);
    pasada(progDesenfoque, fb.c, fb.a.tex, null, null, 1.5 / fb.c.w, 0);
    pasada(progDesenfoque, fb.d, fb.c.tex, null, null, 0, 1.5 / fb.c.h);
    gl.useProgram(progFinal);
    gl.uniform1f(uni(progFinal, 'uT'), t); gl.uniform1f(uni(progFinal, 'uBloom'), 1.0);
    gl.uniform3f(uni(progFinal, 'uTono'), tono[0], tono[1], tono[2]);
    pasada(progFinal, null, fb.escena.tex, fb.a.tex, fb.d.tex);
    gl.activeTexture(gl.TEXTURE0);
    gl.enable(gl.DEPTH_TEST);
  }

  // ---------------------------------------------------------------- dibujar

  let trozosDibujados = 0;
  function dibujar(luz, t = 0, tono = [0.92, 0.95, 1.08]) {
    gl.bindFramebuffer(gl.FRAMEBUFFER, fb.escena.f);
    gl.viewport(0, 0, W, H);
    gl.clearColor(0, 0, 0, 1);
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
    gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, texLuz);
    gl.pixelStorei(gl.UNPACK_ALIGNMENT, 1);
    if (luz.w === luzW && luz.h === luzH) gl.texSubImage2D(gl.TEXTURE_2D, 0, 0, 0, luz.w, luz.h, gl.RGBA, gl.UNSIGNED_BYTE, luz.datos);
    else { gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, luz.w, luz.h, 0, gl.RGBA, gl.UNSIGNED_BYTE, luz.datos); luzW = luz.w; luzH = luz.h; }
    gl.activeTexture(gl.TEXTURE3); gl.bindTexture(gl.TEXTURE_2D, texNormales);
    gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, texAtlas);
    gl.useProgram(prog);
    gl.uniform1i(loc.uNormales, 3); gl.uniform1f(loc.uLado, FUERZA_LADO);
    gl.uniformMatrix4fv(loc.uVP, false, cam.vp);
    gl.uniform4f(loc.uLuzRect, luz.x0, luz.y0, 1 / luz.w, 1 / luz.h);
    gl.uniform1i(loc.uAtlas, 0); gl.uniform1i(loc.uLuz, 1);
    gl.uniform1f(loc.uCorte, 0.5); gl.uniform1f(loc.uAditivo, 0); gl.uniform2f(loc.uAtlasTam, ATLAS.width, ATLAS.height);
    gl.enable(gl.DEPTH_TEST); gl.depthFunc(gl.LEQUAL); gl.depthMask(true); gl.disable(gl.BLEND);
    gl.enable(gl.CULL_FACE); gl.cullFace(gl.BACK); gl.frontFace(gl.CW);
    const v = vista();
    trozosDibujados = 0;
    let rehechos = 0;
    for (let cy = Math.floor(v.z0 / CHUNK); cy <= Math.floor(v.z1 / CHUNK); cy++) for (let cx = Math.floor(v.x0 / CHUNK); cx <= Math.floor(v.x1 / CHUNK); cx++) {
      if (cx < 0 || cy < 0 || cx * CHUNK >= N_MUNDO || cy * CHUNK >= N_MUNDO) continue;
      let tr = chunks.get(cx + ',' + cy);
      if ((!tr || tr.sucio) && (rehechos < 3 || !tr)) { construirTrozo(cx, cy); tr = chunks.get(cx + ',' + cy); rehechos++; }
      if (!tr.n) continue;
      gl.bindVertexArray(tr.vao);
      gl.drawElements(gl.TRIANGLES, tr.n * 6, gl.UNSIGNED_INT, 0);
      trozosDibujados++;
    }
    gl.disable(gl.CULL_FACE);
    const pintar = (nombre) => {
      const o = dinamicos[nombre], l = lotes[nombre];
      if (!l.n) return;
      subir(o, l, gl.DYNAMIC_DRAW);
      gl.bindVertexArray(o.vao);
      gl.drawElements(gl.TRIANGLES, l.n * 6, gl.UNSIGNED_INT, 0);
    };
    gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA); gl.depthMask(false);
    gl.uniform1f(loc.uCorte, 0.01);
    pintar('sombras');
    gl.disable(gl.BLEND); gl.depthMask(true); gl.uniform1f(loc.uCorte, 0.5);
    pintar('spr');
    gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE); gl.depthMask(false); gl.uniform1f(loc.uCorte, 0.01); gl.uniform1f(loc.uAditivo, 1);
    pintar('brillo');
    gl.useProgram(progHalo);
    gl.uniformMatrix4fv(locH.uVP, false, cam.vp);
    gl.uniform1i(locH.uHalo, 2);
    gl.blendFunc(gl.ONE, gl.ONE); gl.disable(gl.DEPTH_TEST);
    gl.activeTexture(gl.TEXTURE2); gl.bindTexture(gl.TEXTURE_2D, texRayo);
    pintar('rayos');
    gl.bindTexture(gl.TEXTURE_2D, texHalo);
    pintar('halos');
    gl.activeTexture(gl.TEXTURE0);
    gl.depthMask(true); gl.disable(gl.BLEND); gl.enable(gl.DEPTH_TEST);
    posproceso(t, tono);
    gl.bindVertexArray(null);
  }

  return {
    iniciar, ajustar, camara, aMundo, aPantalla, vista, dibujar, sprite, modelo, plano, cara, tapa, libre, halo, rayo, tocar, olvidarTodo, subirZona, olvidarUV,
    limpiarLotes, lotes, cam, uv: uvDe, ALTO, KY,
    get W() { return W; }, get H() { return H; }, get trozos() { return trozosDibujados; },
  };
})();
