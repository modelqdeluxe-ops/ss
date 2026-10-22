// PDF sencillo sin librerías (para los recibos): páginas A4 con texto en Helvetica, en negrita o normal, con varios
// tamaños, líneas separadoras y ajuste de línea. El texto va en WinAnsi (cubre el español: tildes, ñ, ¿, ¡, «, »).

const A4 = [595.28, 841.89];
const MARGIN = 56;
// Anchura media de un carácter de Helvetica en tanto por uno del tamaño de letra (para partir las líneas)
const AVG = { normal: 0.5, bold: 0.54 };

// Caracteres que WinAnsi pone fuera de Latin-1
const WIN = { '€': 0x80, '‚': 0x82, '„': 0x84, '…': 0x85, '‘': 0x91, '’': 0x92, '“': 0x93, '”': 0x94, '•': 0x95, '–': 0x96, '—': 0x97, '™': 0x99 };

function encode(text) {
  let out = '';
  for (const ch of String(text)) {
    let code = WIN[ch] ?? ch.codePointAt(0);
    if (code > 255) code = 0x3f; // «?» para lo que WinAnsi no tiene
    const c = String.fromCharCode(code);
    out += c === '(' || c === ')' || c === '\\' ? `\\${c}` : c;
  }
  return out;
}

function wrap(text, size, bold, width) {
  const max = Math.max(10, Math.floor(width / (size * (bold ? AVG.bold : AVG.normal))));
  const lines = [];
  for (const para of String(text).split('\n')) {
    let line = '';
    for (const word of para.split(/\s+/).filter(Boolean)) {
      if (!line) line = word;
      else if ((line + ' ' + word).length <= max) line += ' ' + word;
      else {
        lines.push(line);
        line = word;
      }
    }
    lines.push(line);
  }
  return lines;
}

// blocks: [{ text, size = 10.5, bold = false, gap = 4, color = [r, g, b] (0-1) } | { rule: true } | { space: n }]
export function makePdf(blocks, { title = 'Documento' } = {}) {
  const [W, H] = A4;
  const width = W - MARGIN * 2;
  const pages = [];
  let ops = [];
  let y = H - MARGIN;
  const newPage = () => {
    pages.push(ops);
    ops = [];
    y = H - MARGIN;
  };
  for (const b of blocks) {
    if (b.space) {
      y -= b.space;
      continue;
    }
    if (b.rule) {
      if (y < MARGIN + 20) newPage();
      y -= 6;
      ops.push(`0.85 0.78 0.7 RG 0.8 w ${MARGIN} ${y.toFixed(2)} m ${W - MARGIN} ${y.toFixed(2)} l S`);
      y -= 10;
      continue;
    }
    const size = b.size || 10.5;
    const lead = size * 1.35;
    const [r, g, bl] = b.color || [0.16, 0.11, 0.08];
    for (const line of wrap(b.text, size, b.bold, width)) {
      if (y - lead < MARGIN) newPage();
      y -= lead;
      ops.push(`BT /${b.bold ? 'F2' : 'F1'} ${size} Tf ${r} ${g} ${bl} rg ${MARGIN} ${y.toFixed(2)} Td (${encode(line)}) Tj ET`);
    }
    y -= b.gap ?? 4;
  }
  pages.push(ops);

  // Objetos: 1 catálogo, 2 páginas, 3-4 fuentes, 5 info, y para cada página: la página y su contenido
  const objects = [];
  const kids = pages.map((_, i) => `${6 + i * 2} 0 R`).join(' ');
  objects[1] = '<< /Type /Catalog /Pages 2 0 R >>';
  objects[2] = `<< /Type /Pages /Kids [${kids}] /Count ${pages.length} >>`;
  objects[3] = '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>';
  objects[4] = '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>';
  objects[5] = `<< /Title (${encode(title)}) /Producer (Tierras Fantasticas) >>`;
  pages.forEach((p, i) => {
    const content = p.join('\n');
    objects[6 + i * 2] =
      `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${W} ${H}] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${7 + i * 2} 0 R >>`;
    objects[7 + i * 2] = `<< /Length ${content.length} >>\nstream\n${content}\nendstream`;
  });

  let out = '%PDF-1.4\n%\xe2\xe3\xcf\xd3\n';
  const offsets = [];
  for (let i = 1; i < objects.length; i++) {
    offsets[i] = out.length;
    out += `${i} 0 obj\n${objects[i]}\nendobj\n`;
  }
  const xref = out.length;
  out += `xref\n0 ${objects.length}\n0000000000 65535 f \n`;
  for (let i = 1; i < objects.length; i++) out += `${String(offsets[i]).padStart(10, '0')} 00000 n \n`;
  out += `trailer\n<< /Size ${objects.length} /Root 1 0 R /Info 5 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  // Cada carácter es un byte (Latin-1/WinAnsi)
  const bytes = new Uint8Array(out.length);
  for (let i = 0; i < out.length; i++) bytes[i] = out.charCodeAt(i) & 0xff;
  return bytes;
}

export function toBase64(bytes) {
  let s = '';
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(s);
}
