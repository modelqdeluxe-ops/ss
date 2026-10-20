#version 150

// La grieta del cielo (TF Client 1.3.95; rehecha entera: el dueño, de la 1.3.94: «la animación, el sonido de cristal, el
// tamaño y el diseño de la grieta son una mierda... esmérate por hacer algo chingón»). 1.3.96 (el dueño: «el diseño me
// gusta, hazlo un poquito más grandecito y más épico; mejora el efecto de apertura, aparecimiento y cierre»): ~20 % más
// grande, un anillo de choque que cruza el cielo al estallar (y se encoge al cerrarse: Ring), chispas en las puntas
// mientras se rasga o se cose, la tormenta que gira cada vez más rápido antes de romperse y al cerrarse (Spin), el borde
// que late, un túnel de luz dentro del Void y rayos de luz más anchos. La dibuja SkyRiftClient en un
// cuadro enorme que mira a la cámara; todo se calcula en ángulos de cielo alrededor de su centro (RiftF, con RiftR a la
// derecha y RiftU arriba: proyección azimutal, sin estirarse en los bordes), así que mide lo mismo la mires como la mires.
//
//  - El cielo se rompe como un cristal: una red de fracturas (celdas de Voronoi) que se extiende desde el centro (Crack),
//    con un brillo blanco y violeta en cada grieta y cada trozo de cielo reflejando la luz un poco distinto.
//  - El desgarro (Open) se abre arrancando trozos enteros: su borde son las grietas de los trozos que faltan (un borde de
//    cristal roto, nada liso) y arde: blanco en el filo, magenta y violeta hacia fuera, con llamas de energía.
//  - Dentro, el Void con profundidad: sus nebulosas en remolino hacia el centro, dos capas de estrellas que se mueven
//    al girar la cámara y, al fondo, el ojo del Void (un agujero negro con su anillo de oro).
//  - Alrededor, un vórtice de tormenta (Storm): nubes negras y violetas en espiral que giran despacio, iluminadas por la
//    grieta y con relámpagos dentro, que se apartan del desgarro.
//  - Trozos de cielo que flotan y el desgarro se traga; al romperse (Shatter: segundos desde que estalla) salen
//    disparados del centro. Rayos de energía que saltan desde el borde, rayos de luz que salen del desgarro.
//  - En el preludio (Dark), una sombra que crece y un resplandor violeta que late donde va a romperse.
//  - Sellado (Open 0, Crack bajo) queda la cicatriz: las fracturas, apagadas.
// Todo con bordes suaves (lo que mide un píxel, fwidth). Vista previa sin juego: tools/void/grieta_cielo_preview.py.

uniform sampler2D Sampler0;   // las nebulosas del cielo del Void (equirectangular)
uniform float STime;          // segundos
uniform float Crack;          // 0..1 la red de fracturas se extiende desde el centro
uniform float Open;           // 0..1 el desgarro se abre
uniform float Flash;          // 0..2 fogonazo
uniform float Alpha;          // 0..1 todo
uniform float AtFar;          // 1 = después del paquete de shaders: solo donde se ve el cielo
uniform float Dark;           // 0..1 la sombra del preludio
uniform float Storm;          // 0..1 el vórtice de tormenta
uniform float Shatter;        // segundos desde que estalla (los trozos salen disparados), o < 0
uniform vec3 RiftF;           // el centro de la grieta (dirección, relativa a la cámara)
uniform vec3 RiftR;           // su derecha
uniform vec3 RiftU;           // su arriba
uniform vec2 Ring;            // el anillo de choque: radio (radianes, < 0 = no) y fuerza
uniform float Spin;           // lo que ha girado la tormenta (radianes; más rápido al romperse y al cerrarse)
uniform float CloudMask;      // > 0: solo la máscara de las nubes de Minecraft (la base de las nubes, bloques sobre la cámara)
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

in vec2 uv;
in vec3 vDir;
out vec4 fragColor;

const float PI = 3.14159265;
const float TAU = 6.2831853;
const float TILT = 0.42;      // el desgarro, inclinado (radianes)
const float HALF_LEN = 0.7;   // medio largo del desgarro abierto (radianes de cielo: ~80° de largo)
const float HALF_W = 0.25;    // medio ancho en el centro
const float STORM_R = 1.1;    // el radio de la tormenta

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x), mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p, int oct) {
    float s = 0.0;
    float a = 0.5;
    float n = 0.0;
    for (int i = 0; i < 6; i++) {
        if (i >= oct) break;
        s += a * noise2(p);
        n += a;
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return s / n;
}

mat2 rot(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat2(c, s, -s, c);
}

float tri(float x) {
    return abs(fract(x) - 0.5) * 4.0 - 1.0;
}

// Ruido «de cresta»: picos afilados (como hielo o cristal roto)
float ridged(vec2 p) {
    return 1.0 - abs(2.0 * noise2(p) - 1.0);
}

// Ruido a tramos rectos (alturas al azar unidas por líneas): bordes angulosos, como cristal o hielo roto
float jag(float x, float seed) {
    float i = floor(x);
    float f = x - i;
    return mix(hash11(i * 1.37 + seed), hash11((i + 1.0) * 1.37 + seed), f);
}

// El eje del desgarro (en sus ejes: x a lo largo): quebrado como un rayo, con quiebros secos
float tearAxis(float x) {
    return 0.05 * tri(x * 2.6 + 0.2) + 0.022 * tri(x * 6.7 + 0.55) + 0.008 * tri(x * 17.0 + 0.1);
}

// Su largo de ahora (se abre del centro hacia las puntas)
float tearLen() {
    return mix(0.03, HALF_LEN, pow(clamp(Open, 0.0, 1.0), 0.6));
}

// La forma del desgarro: < 0 dentro. Ancho en huso, bordes con dientes afilados (distintos arriba y abajo), y se
// acaba en las puntas
float tearShape(vec2 q) {
    float L = tearLen();
    float k = clamp(1.0 - (q.x / L) * (q.x / L), 0.0, 1.0);
    float w = HALF_W * smoothstep(0.0, 0.35, Open) * mix(0.35, 1.0, Open) * pow(k, 0.7) + 0.003;
    float dy = q.y - tearAxis(q.x);
    float side = dy > 0.0 ? 1.0 : -1.0;
    float seed = side > 0.0 ? 3.1 : 8.7;
    float teeth = jag(q.x * 17.0, seed) * 0.45 + jag(q.x * 43.0, seed + 5.0) * 0.27 + jag(q.x * 110.0, seed + 9.0) * 0.09;
    return max(abs(dy) - w * (1.12 - teeth * 0.8), abs(q.x) - L);
}

// La red de fracturas es una telaraña: celdas de Voronoi en coordenadas logarítmico-polares alrededor del impacto (el
// centro): NS sectores y anillos que se separan hacia fuera (cada uno 1,48 veces más lejos), así que los trozos son
// pequeños junto al centro y grandes lejos, con grietas radiales y en anillo, como un cristal golpeado.
const float NS = 15.0;

float wrapX(float x) {
    return mod(x, NS);
}

// El punto de la celda «c» (en las coordenadas de la telaraña), movido al azar; el ángulo da la vuelta (periódico)
vec2 cellPoint(vec2 c) {
    return c + 0.5 + (hash22(vec2(wrapX(c.x), c.y) + 0.37) - 0.5) * 0.82;
}

// De la telaraña a ángulos de cielo (y al revés)
vec2 webToSky(vec2 w) {
    float a = w.x * TAU / NS;
    float rr = exp(w.y * TAU / NS);
    return vec2(cos(a), sin(a)) * rr;
}

// La telaraña en el punto x (en sus coordenadas): distancia al borde de su trozo y el azar de su trozo
vec2 voronoi(vec2 x) {
    vec2 n = floor(x);
    float md = 1e9;
    vec2 mr = vec2(0.0);
    vec2 mg = vec2(0.0);
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 r = cellPoint(n + g) - x;
            float d = dot(r, r);
            if (d < md) {
                md = d;
                mr = r;
                mg = g;
            }
        }
    }
    float eAll = 8.0;
    for (int j = -2; j <= 2; j++) {
        for (int i = -2; i <= 2; i++) {
            vec2 g = mg + vec2(float(i), float(j));
            vec2 r = cellPoint(n + g) - x;
            vec2 dr = r - mr;
            if (dot(dr, dr) > 1e-5) eAll = min(eAll, dot(0.5 * (mr + r), normalize(dr)));
        }
    }
    return vec2(eAll, hash12(vec2(wrapX(n.x + mg.x), n.y + mg.y)));
}

// Un rayo quebrado desde «o» hacia el ángulo «th», de largo «len»: su luz (núcleo, halo)
vec2 bolt(vec2 p, vec2 o, float th, float len, float seed, float aa) {
    vec2 d = rot(-th) * (p - o);
    if (d.x < 0.0 || d.x > len) return vec2(0.0);
    float s = d.x / len;
    float wig = (noise2(vec2(d.x * 30.0, seed * 7.1)) - 0.5) * 0.05 + (noise2(vec2(d.x * 90.0, seed * 3.3)) - 0.5) * 0.015;
    float lat = abs(d.y - wig * smoothstep(0.0, 0.3, s));
    float w = 0.0016 * (1.0 - s) + 0.0004;
    float ends = smoothstep(0.0, 0.05, s) * (1.0 - smoothstep(0.7, 1.0, s));
    float core = smoothstep(w + aa, max(w - aa, 0.0), lat) * ends;
    float halo = exp(-lat / 0.004) * ends * (1.0 - s);
    return vec2(core, halo);
}

float fk2(int k) {
    return float(k) * 2.3;
}

// La nebulosa del Void en la dirección dd
vec3 neb(vec3 dd) {
    vec2 tuv = vec2(fract(atan(dd.z, dd.x) / TAU + 0.5), clamp(0.5 - asin(clamp(dd.y, -1.0, 1.0)) / PI, 0.001, 0.999));
    return textureLod(Sampler0, tuv, 0.0).rgb;
}

// Estrellas en la dirección dd (una por celda, de al menos un píxel)
float stars(vec3 dd, float scale, float pix) {
    vec2 sp = vec2(atan(dd.z, dd.x), asin(clamp(dd.y, -1.0, 1.0))) * scale;
    vec2 c = floor(sp);
    float h = hash12(c);
    if (h < 0.9) return 0.0;
    vec2 o = hash22(c + 3.0) * 0.7 + 0.15;
    float rad = max(0.08, pix * scale * 0.9);
    float dd2 = length(fract(sp) - o);
    return smoothstep(rad, rad * 0.3, dd2) * (0.5 + 0.5 * hash12(c + 9.0)) * (0.7 + 0.3 * sin(STime * (1.0 + 3.0 * h) + h * 50.0));
}

void main() {
    gl_FragDepth = AtFar > 0.5 ? 0.9999995 : gl_FragCoord.z;
    vec3 d = normalize(vDir);
    // ángulos de cielo alrededor del centro (proyección azimutal equidistante): p en radianes
    float cf = clamp(dot(d, normalize(RiftF)), -1.0, 1.0);
    float th = acos(cf);
    vec2 dir2 = vec2(dot(d, normalize(RiftR)), dot(d, normalize(RiftU)));
    float l2 = length(dir2);
    vec2 p = l2 > 1e-6 ? dir2 / l2 * th : vec2(0.0);
    float aa = max(length(fwidth(p)), 1e-5);
    float pix = aa;
    float t = STime;
    float r = length(p);
    if (r > 1.25) discard;
    float ang = atan(p.y, p.x);
    vec2 q = rot(-TILT) * p;               // en los ejes del desgarro

    // --- La máscara de las nubes (SkyRiftClient.renderMask): sin color, donde está el desgarro (y su filo), la
    // profundidad de un punto 2 bloques antes de la capa de nubes; así las nubes quedan detrás. (El fogonazo no: su
    // recorte salía como un disco de borde duro.)
    if (CloudMask > 0.0) {
        float m = Open > 0.002 ? step(tearShape(q), 0.025) : 0.0;
        if (m * Alpha < 0.5 || d.y < 0.05) discard;
        vec4 clip = ProjMat * ModelViewMat * vec4(d * max(CloudMask / d.y - 2.0, 1.0), 1.0);
        gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 0.9999);
        fragColor = vec4(0.0);
        return;
    }

    vec3 col = vec3(0.0);
    float alpha = 0.0;

    // --- La sombra y el resplandor del preludio
    float shade = exp(-r * r * 3.0) * Dark;
    col += vec3(0.03, 0.0, 0.06) * shade;
    alpha += shade * 0.55;
    float pulse = 0.55 + 0.45 * sin(t * 3.4) * sin(t * 1.3 + 0.7);
    float ember = Dark * (1.0 - smoothstep(0.0, 0.5, Crack)) * exp(-r * r * 60.0) * pulse;
    col += vec3(0.75, 0.15, 1.0) * ember * 1.4;
    alpha += ember * 0.6;
    // (1.3.96) en la sombra, el cielo se adelgaza y asoman las estrellas del Void
    float leak = stars(d, 90.0, pix) * Dark * exp(-r * r * 5.0) * (1.0 - smoothstep(0.3, 0.9, Open));
    col += vec3(0.9, 0.85, 1.0) * leak * 0.8;
    alpha += leak * 0.5;

    // --- La red de fracturas (el cielo roto como un cristal golpeado en el centro)
    vec2 web = vec2(ang / TAU * NS, log(max(r, 1e-4)) * NS / TAU);
    vec2 vo = voronoi(web);
    float toRad = max(r, 0.004) * TAU / NS;           // una unidad de la telaraña, en radianes de cielo, aquí
    float eAll = vo.x * toRad;
    float cellH = vo.y;
    // el desgarro: dentro (holeK) y lo que falta hasta su borde (fuera, en radianes)
    float tsd = tearShape(q);
    float holeK = Open > 0.002 ? smoothstep(aa, -aa, tsd) : 0.0;
    // hasta dónde llegan las grietas (con el borde irregular); las de lejos, más finas y apagadas
    float reach = Crack * 0.72 * (0.8 + 0.3 * fbm(vec2(ang * 1.7, 3.1), 3)) + 0.005;
    float inNet = (1.0 - smoothstep(reach * 0.8, reach, r)) * smoothstep(0.004, 0.02, r);
    float fresh = exp(-max(reach - r, 0.0) * 9.0) * step(0.02, Crack) * (1.0 - step(0.99, Crack));
    float energy = clamp(0.4 + 0.6 * clamp(Open * 1.2 + Flash, 0.0, 1.0) + 0.6 * fresh, 0.0, 1.6);
    float far = 1.0 - 0.65 * smoothstep(0.1, 0.65, r);
    float lw = 0.0009 + 0.0022 * exp(-r * 4.0);
    float lineK = smoothstep(lw + aa, max(lw - aa, 0.0), eAll) * inNet * far;
    float lineGlow = exp(-eAll / (0.006 + 0.01 * exp(-r * 3.0))) * inNet * far;
    // cada trozo de cielo, con su reflejo (más claro u oscuro): se nota que está roto
    float sheen = (cellH - 0.5) * inNet * smoothstep(0.0, 0.3, Crack) * far;
    col += sheen > 0.0 ? vec3(0.95, 0.92, 1.0) * sheen * 0.3 : vec3(0.03, 0.0, 0.06) * -sheen * 0.5;
    alpha += abs(sheen) * 0.25;
    col += vec3(1.0, 0.94, 1.0) * lineK * 1.3 * energy + vec3(0.62, 0.22, 1.0) * lineGlow * 0.5 * energy;
    alpha += clamp(lineK * energy + lineGlow * 0.35 * energy, 0.0, 1.0);

    // --- La tormenta: nubes negras y violetas en espiral alrededor, que se apartan del desgarro
    if (Storm > 0.001) {
        float sR = STORM_R * mix(0.25, 1.0, Storm);
        float sw = 1.15 / (r + 0.25) - Spin;
        vec2 sp = rot(sw) * p * 2.6;
        // nubes con forma (el dominio deformado por otro ruido) y con detalle fino
        vec2 wq = sp + (vec2(fbm(sp * 1.3 + vec2(t * 0.02, 1.7), 4), fbm(sp * 1.3 + vec2(5.2, t * 0.017), 4)) - 0.5) * 1.6;
        float dens = fbm(wq * 1.5, 6) + 0.18 * (fbm(sp * 7.0 + 3.3, 4) - 0.5);
        float arms = 0.5 + 0.5 * sin(3.0 * ang + 7.0 * log(r + 0.06) - Spin * 4.2);
        float cloud = smoothstep(0.44, 0.6, dens * 0.82 + arms * 0.24);
        float ring = smoothstep(0.06, 0.22, r) * (1.0 - smoothstep(sR * 0.55, sR, r));
        float apart = smoothstep(0.01, 0.14, tsd + 0.03 * (1.0 - Open));
        float cA = cloud * ring * apart * Storm;
        // iluminadas por la grieta: más cerca, más violeta; el borde de cada nube, más claro
        // el borde de cada nube del lado de la grieta, más claro (el ruido un poco más hacia el centro)
        float dens2 = fbm((wq * 0.965) * 1.5, 6) + 0.18 * (fbm(sp * 0.965 * 7.0 + 3.3, 4) - 0.5);
        float rim = clamp((dens - dens2) * 7.0, 0.0, 1.0);
        float lit = exp(-r * 2.6) * (0.35 + 0.9 * Open + 0.5 * Dark);
        float shadeC = 0.5 + 0.5 * smoothstep(0.45, 0.8, dens);
        vec3 cc = vec3(0.02, 0.005, 0.04) * shadeC + vec3(0.42, 0.1, 0.7) * lit * (0.25 + rim) + vec3(0.95, 0.55, 1.0) * rim * lit * 0.4;
        // relámpagos dentro de la tormenta
        float flick = 0.0;
        for (int k = 0; k < 3; k++) {
            float fk = float(k);
            float slot = floor(t * 1.7 + fk * 0.37);
            float on = step(0.72, hash11(slot * 3.1 + fk * 11.0)) * max(0.0, 1.0 - fract(t * 1.7 + fk * 0.37) * 5.0);
            vec2 fp = vec2(cos(hash11(slot + fk) * TAU), sin(hash11(slot + fk) * TAU)) * (0.25 + 0.55 * hash11(slot * 1.7 + fk));
            flick += on * exp(-dot(p - fp, p - fp) / 0.02);
        }
        cc += vec3(0.85, 0.7, 1.0) * flick * cloud * 1.6;
        col = mix(col, cc, cA * 0.93);
        alpha = mix(alpha, 1.0, cA * 0.93);
    }

    // --- El desgarro: por dentro, el Void
    float inside = holeK;
    if (inside > 0.001) {
        // remolino hacia el centro (en ángulos del desgarro) y, detrás, lo que hay en la dirección en que miras
        float qr = length(q);
        float qa = atan(q.y, q.x) + 0.5 / (qr + 0.08) - t * 0.05;
        vec3 sw3 = normalize(vec3(cos(qa) * qr, sin(qa) * qr, 1.0) + vec3(0.0, 0.0, 0.0));
        vec3 n1 = neb(vec3(sw3.x, sw3.y * 0.7 + 0.15, sw3.z));
        vec3 n2 = neb(normalize(d + vec3(0.0, 0.0, 0.3)));
        vec3 voidC = pow(n1, vec3(1.1)) * 1.7 + pow(n2, vec3(1.3)) * 0.45;
        voidC *= 0.6 + 0.7 * exp(-qr * 2.2);
        // (1.3.96) profundidad: anillos de luz que caen hacia el ojo, como un túnel
        float tun = 0.5 + 0.5 * sin(log(qr + 0.015) * 13.0 + t * 2.6);
        voidC += vec3(0.42, 0.16, 0.85) * pow(tun, 6.0) * exp(-qr * 3.5) * 0.55 * smoothstep(0.4, 1.0, Open);
        voidC += vec3(0.95, 0.9, 1.0) * (stars(d, 70.0, pix) * 0.8 + stars(normalize(d + vec3(0.3, 0.1, 0.0)), 160.0, pix) * 0.45);
        // la luz del borde entra un poco (una neblina violeta junto al filo, por dentro)
        float inEdge = exp(tsd / 0.025);
        voidC += vec3(0.6, 0.15, 0.9) * inEdge * 0.6;
        // el ojo del Void: un agujero negro pequeño con su anillo de oro, que gira
        vec2 e = q - vec2(0.0, tearAxis(0.0));
        float er = length(e * vec2(1.0, 1.6));
        float hole = 0.042 * smoothstep(0.3, 1.0, Open);
        float eyeK = smoothstep(hole + aa * 1.5, hole - aa * 1.5, er);
        float ringR = exp(-pow((er - hole * 1.25) / (hole * 0.18 + aa), 2.0)) * step(0.001, hole);
        float ea = atan(e.y * 1.6, e.x) + t * 0.4;
        vec3 ringC = vec3(1.0, 0.75, 0.38) * (0.75 + 0.35 * sin(ea * 2.0)) * 2.2;
        float disk = exp(-pow(e.y * 1.6 / (hole * 0.35 + aa), 2.0)) * smoothstep(hole * 3.5, hole * 1.1, abs(e.x)) * step(hole * 1.1, abs(e.x)) * step(0.001, hole);
        voidC = voidC * (1.0 - eyeK) + ringC * (ringR + disk * 0.8);
        voidC *= 1.0 - eyeK;
        col = mix(col, voidC, inside);
        alpha = mix(alpha, 1.0, inside);
    }

    // --- El borde que arde: filo blanco, magenta y violeta hacia fuera, con llamas de energía
    if (Open > 0.002) {
        float ed = max(tsd, 0.0);                       // distancia (fuera) al borde del desgarro, en radianes
        float flame = fbm(vec2(atan(q.y, q.x) * 6.0 + ed * 20.0, ed * 30.0 - t * 2.2), 4);
        float fw = 0.02 + 0.045 * flame;
        float near = 1.0 - smoothstep(0.0, 0.2, ed);
        float hot = exp(-ed / (0.0025 + aa)) * near;
        float mag = exp(-ed / fw) * near;
        float vio = exp(-ed / (fw * 3.0)) * near;
        // (1.3.96) late, y de vez en cuando se enciende más
        float beat = 0.85 + 0.15 * sin(t * 1.9) + 0.35 * pow(max(0.0, sin(t * 0.7)), 14.0);
        float o = smoothstep(0.0, 0.25, Open) * (1.0 - holeK) * beat;
        col += (vec3(1.0, 0.95, 1.0) * hot * 2.2 + vec3(1.0, 0.25, 0.85) * mag * 1.3 + vec3(0.55, 0.15, 1.0) * vio * 0.7) * o;
        alpha += (hot + mag * 0.85 + vio * 0.45) * o;
        // las grietas siguen más allá de las puntas, quebradas como el desgarro
        float L = tearLen();
        float ax = abs(q.x);
        float ext = 0.4 * smoothstep(0.0, 0.6, Open);
        if (ax > L * 0.8 && ax < L + ext) {
            float s2 = (ax - L * 0.8) / (L * 0.2 + ext);
            float lat = abs(q.y - tearAxis(q.x));
            float w2 = 0.002 * (1.0 - s2) + 0.0005;
            float cl = smoothstep(w2 + aa, max(w2 - aa, 0.0), lat) * (1.0 - smoothstep(0.75, 1.0, s2)) * (1.0 - holeK);
            float gl = exp(-lat / 0.012) * (1.0 - s2) * (1.0 - holeK);
            col += vec3(1.0, 0.92, 1.0) * cl * 1.7 + vec3(0.9, 0.3, 1.0) * gl * 0.7;
            alpha += cl + gl * 0.45;
        }
        // rayos de luz que salen del desgarro
        float rays = (pow(noise2(vec2(ang * 22.0, t * 0.15)), 5.0) + 0.8 * pow(noise2(vec2(ang * 7.0 + t * 0.03, t * 0.08 + 4.0)), 4.0))
                * exp(-r * 2.2) * smoothstep(0.0, 0.05, tsd) * Open * (1.0 - holeK);
        col += vec3(0.8, 0.5, 1.0) * rays * 0.9;
        alpha += rays * 0.35;
        // (1.3.96) chispas en las puntas mientras se rasga (o se cose al cerrarse)
        float tipK = clamp(Open * (1.0 - Open) * 4.0, 0.0, 1.0);
        if (tipK > 0.01) {
            for (int k = 0; k < 2; k++) {
                float sgn = k == 0 ? 1.0 : -1.0;
                vec2 tip = rot(TILT) * vec2(sgn * L, tearAxis(sgn * L));
                vec2 dt = p - tip;
                float dd = length(dt);
                float ta = atan(dt.y, dt.x);
                float sp = exp(-dd * dd / 0.00045) + 0.5 * exp(-dd / 0.03) * (0.6 + 0.4 * pow(abs(cos(ta * 2.0 + t * 9.0)), 30.0));
                sp *= tipK * (0.8 + 0.2 * sin(t * 40.0 + fk2(k)));
                col += vec3(1.0, 0.85, 1.0) * sp * 1.8;
                alpha += sp * 0.9;
            }
        }
        // rayos de energía que saltan del borde hacia fuera
        for (int k = 0; k < 3; k++) {
            float fk = float(k);
            float slot = floor(t * 2.2 + fk * 0.31);
            float on = step(0.55, hash11(slot * 5.3 + fk * 7.0)) * max(0.0, 1.0 - fract(t * 2.2 + fk * 0.31) * 3.0);
            if (on > 0.0) {
                float sx = (hash11(slot + fk * 3.0) - 0.5) * 1.6 * HALF_LEN * Open;
                float side = hash11(slot * 2.0 + fk) > 0.5 ? 1.0 : -1.0;
                float wk = HALF_W * Open * pow(clamp(1.0 - pow(sx / (HALF_LEN * Open + 1e-3), 2.0), 0.0, 1.0), 0.62);
                vec2 o2 = rot(TILT) * vec2(sx, tearAxis(sx) + side * wk);
                float thb = TILT + side * (PI * 0.5) + (hash11(slot * 3.0 + fk) - 0.5) * 1.2;
                vec2 bl = bolt(p, o2, thb, 0.12 + 0.2 * hash11(slot + fk * 9.0), slot + fk, aa);
                col += (vec3(1.0, 0.95, 1.0) * bl.x * 1.6 + vec3(0.7, 0.3, 1.0) * bl.y * 0.7) * on;
                alpha += (bl.x + bl.y * 0.5) * on;
            }
        }
    }

    // --- Trozos de cielo: cristal oscuro con el filo encendido; flotan en espiral y el desgarro se los traga; al
    // estallar salen disparados del centro
    bool shattering = Shatter >= 0.0 && Shatter < 4.0;
    if (Open > 0.001 || shattering) {
        for (int k = 0; k < 12; k++) {
            float fk = float(k);
            float h1 = hash11(fk * 7.13 + 1.0);
            float h2 = hash11(fk * 3.71 + 2.0);
            float h3 = hash11(fk * 5.29 + 3.0);
            vec2 pos;
            float size;
            float fade;
            if (shattering && k < 12) {
                float a0 = h1 * TAU;
                float dist = (0.12 + 0.5 * h2) * (1.0 - exp(-Shatter * 1.4));
                pos = vec2(cos(a0), sin(a0)) * dist;
                size = 0.03 + 0.04 * h3;
                fade = 1.0 - smoothstep(2.5, 4.0, Shatter);
            } else {
                float life = 8.0 + 6.0 * h1;
                float u = fract(t / life + h2);
                float a0 = h3 * TAU + u * (1.2 + h1);
                float dist = mix(0.62, 0.06, u * u);
                pos = vec2(cos(a0), sin(a0)) * dist;
                size = (0.028 + 0.035 * h2) * (1.0 - 0.55 * u);
                // (al acabar el estallido entran poco a poco: si no, aparecían de golpe)
                fade = smoothstep(0.0, 0.08, u) * (1.0 - smoothstep(0.85, 1.0, u)) * Open * (Shatter < 0.0 ? 1.0 : smoothstep(4.0, 6.0, Shatter));
            }
            vec2 lp = p - pos;
            if (dot(lp, lp) > size * size * 4.0) continue;
            lp = rot(t * (0.4 + 0.6 * h1) + fk) * lp / size;
            vec2 v0 = vec2(-0.9 + 0.3 * h1, -0.7);
            vec2 v1 = vec2(0.9, -0.4 + 0.5 * h2);
            vec2 v2 = vec2(-0.1 + 0.5 * h3, 1.0);
            float e0 = ((v1.x - v0.x) * (lp.y - v0.y) - (v1.y - v0.y) * (lp.x - v0.x)) / length(v1 - v0);
            float e1 = ((v2.x - v1.x) * (lp.y - v1.y) - (v2.y - v1.y) * (lp.x - v1.x)) / length(v2 - v1);
            float e2 = ((v0.x - v2.x) * (lp.y - v2.y) - (v0.y - v2.y) * (lp.x - v2.x)) / length(v0 - v2);
            float ed2 = min(min(e0, e1), e2) * size;
            float inS = smoothstep(-aa, aa, ed2);
            if (inS > 0.0) {
                float edge = exp(-max(ed2, 0.0) / (0.0012 + aa));
                // el cristal: oscuro, con un reflejo que gira, y el filo magenta
                float refl = 0.5 + 0.5 * sin(dot(lp, vec2(1.3, 0.7)) * 2.0 + t * 1.5 + fk);
                vec3 sc = mix(vec3(0.06, 0.03, 0.12), vec3(0.55, 0.5, 0.85), refl * 0.55);
                sc = mix(sc, vec3(0.95, 0.75, 1.0) * 1.6, edge);
                col = mix(col, sc, inS * fade);
                alpha = mix(alpha, 1.0, inS * fade);
            }
        }
    }

    // --- La línea por donde se rasga (antes de abrirse) y, sellada, la cicatriz: quebrada como el desgarro
    float scarK = (1.0 - smoothstep(0.0, 0.3, Open)) * smoothstep(0.22, 0.4, Crack);
    if (scarK > 0.001) {
        float sl = abs(q.y - tearAxis(q.x));
        float sx = abs(q.x) / (HALF_LEN * 0.95);
        float sw2 = 0.0018 * (1.0 - sx) + 0.0005;
        float along = 1.0 - smoothstep(0.75, 1.0, sx);
        float core = smoothstep(sw2 + aa, max(sw2 - aa, 0.0), sl) * along;
        float glow = exp(-sl / 0.01) * along;
        float e2 = 0.6 + 0.4 * clamp(Flash + fresh, 0.0, 1.0);
        col += (vec3(1.0, 0.92, 1.0) * core * 1.5 + vec3(0.75, 0.25, 1.0) * glow * 0.7) * scarK * e2;
        alpha += (core + glow * 0.5) * scarK * e2;
    }

    // --- (1.3.96) El anillo de choque: cruza el cielo al estallar (y se encoge hacia el centro al cerrarse)
    if (Ring.x >= 0.0 && Ring.y > 0.001) {
        float dr = r - Ring.x;
        float rk = Ring.y * (exp(-dr * dr / (0.0002 + aa * aa)) + 0.4 * exp(-dr * dr / 0.004)) * (1.0 - smoothstep(0.9, 1.2, r));
        col += vec3(0.95, 0.85, 1.0) * rk * 1.5;
        alpha += rk * 0.8;
    }

    // --- El fogonazo, con un destello en estrella
    float star = pow(abs(cos(ang * 3.0 + 0.4)), 60.0) + 0.6 * pow(abs(cos(ang * 2.0 + 1.3)), 90.0);
    float fl = Flash * (exp(-r * r * 6.0) + 0.25 * exp(-r * 1.5) + star * exp(-r * 2.2) * 0.8);
    col += vec3(1.0, 0.92, 1.0) * fl * 1.6;
    alpha += fl;

    alpha = clamp(alpha, 0.0, 1.0) * Alpha * (1.0 - smoothstep(1.0, 1.2, r));
    vec3 rgb = alpha > 0.001 ? col / max(alpha, 0.001) : vec3(0.0);
    fragColor = vec4(min(rgb, vec3(4.0)), alpha);
}
