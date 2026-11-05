"""NBT con tipos (lectura y escritura) y lector de regiones .mca de Anvil.

Cada valor va como (tipo, valor): compuesto → dict nombre→(t, v); lista → (tipo de los elementos, [valores]).
"""
import gzip, struct, zlib, io

def _rd(data):
    p = [0]
    def u(fmt):
        v = struct.unpack_from('>' + fmt, data, p[0]); p[0] += struct.calcsize('>' + fmt); return v[0]
    def s():
        n = u('H'); v = data[p[0]:p[0] + n].decode('utf-8', 'surrogatepass'); p[0] += n; return v
    def payload(t):
        if t == 1: return u('b')
        if t == 2: return u('h')
        if t == 3: return u('i')
        if t == 4: return u('q')
        if t == 5: return u('f')
        if t == 6: return u('d')
        if t == 7:
            n = u('i'); v = bytes(data[p[0]:p[0] + n]); p[0] += n; return v
        if t == 8: return s()
        if t == 9:
            et = u('b'); n = u('i'); return (et, [payload(et) for _ in range(n)])
        if t == 10:
            o = {}
            while True:
                tt = u('b')
                if tt == 0: return o
                k = s(); o[k] = (tt, payload(tt))
        if t == 11:
            n = u('i'); v = list(struct.unpack_from('>%di' % n, data, p[0])); p[0] += 4 * n; return v
        if t == 12:
            n = u('i'); v = list(struct.unpack_from('>%dq' % n, data, p[0])); p[0] += 8 * n; return v
        raise ValueError(t)
    t = u('b'); name = s()
    return name, (t, payload(t))

def read_bytes(data):
    return _rd(data)

def read_file(path):
    raw = open(path, 'rb').read()
    if raw[:2] == b'\x1f\x8b':
        raw = gzip.decompress(raw)
    return _rd(raw)

def _wr(out, t, v):
    w = out.write
    if t == 1: w(struct.pack('>b', v))
    elif t == 2: w(struct.pack('>h', v))
    elif t == 3: w(struct.pack('>i', v))
    elif t == 4: w(struct.pack('>q', v))
    elif t == 5: w(struct.pack('>f', v))
    elif t == 6: w(struct.pack('>d', v))
    elif t == 7: w(struct.pack('>i', len(v))); w(bytes(v))
    elif t == 8:
        b = v.encode('utf-8', 'surrogatepass'); w(struct.pack('>H', len(b))); w(b)
    elif t == 9:
        et, items = v
        if not items and et == 0: et = 0
        w(struct.pack('>bi', et, len(items)))
        for it in items: _wr(out, et, it)
    elif t == 10:
        for k, (tt, vv) in v.items():
            w(struct.pack('>b', tt)); _wr(out, 8, k); _wr(out, tt, vv)
        w(b'\x00')
    elif t == 11: w(struct.pack('>i', len(v))); w(struct.pack('>%di' % len(v), *v))
    elif t == 12: w(struct.pack('>i', len(v))); w(struct.pack('>%dq' % len(v), *v))
    else: raise ValueError(t)

def write_file(path, name, root):
    out = io.BytesIO()
    out.write(struct.pack('>b', 10)); _wr(out, 8, name); _wr(out, 10, root)
    with gzip.open(path, 'wb') as f:
        f.write(out.getvalue())

def plain(tv):
    """(t,v) -> python simple (sin tipos)."""
    t, v = tv
    if t == 10: return {k: plain(x) for k, x in v.items()}
    if t == 9: return [plain((v[0], x)) for x in v[1]]
    return v

class Region:
    def __init__(self, path):
        self.data = open(path, 'rb').read()
    def chunk(self, lx, lz):
        d = self.data
        if len(d) < 8192: return None
        i = 4 * (lx + 32 * lz)
        off = int.from_bytes(d[i:i + 3], 'big'); cnt = d[i + 3]
        if off == 0 or cnt == 0: return None
        o = off * 4096
        if o + 5 > len(d): return None
        ln = int.from_bytes(d[o:o + 4], 'big'); comp = d[o + 4]
        body = d[o + 5:o + 4 + ln]
        if comp == 2: raw = zlib.decompress(body)
        elif comp == 1: raw = gzip.decompress(body)
        elif comp == 3: raw = body
        else: raise ValueError('comp %d' % comp)
        return _rd(raw)[1]
