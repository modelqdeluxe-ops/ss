"""Lector NBT mínimo (gzip)."""
import gzip, struct

def read(path):
    data = gzip.open(path).read()
    p = [0]
    def u(fmt):
        v = struct.unpack_from('>' + fmt, data, p[0]); p[0] += struct.calcsize('>' + fmt); return v[0]
    def s():
        n = u('H'); v = data[p[0]:p[0] + n].decode('utf-8', 'replace'); p[0] += n; return v
    def payload(t):
        if t == 1: return u('b')
        if t == 2: return u('h')
        if t == 3: return u('i')
        if t == 4: return u('q')
        if t == 5: return u('f')
        if t == 6: return u('d')
        if t == 7:
            n = u('i'); v = data[p[0]:p[0] + n]; p[0] += n; return v
        if t == 8: return s()
        if t == 9:
            et = u('b'); n = u('i'); return [payload(et) for _ in range(n)]
        if t == 10:
            o = {}
            while True:
                tt = u('b')
                if tt == 0: return o
                k = s(); o[k] = payload(tt)
        if t == 11:
            n = u('i'); v = struct.unpack_from('>%di' % n, data, p[0]); p[0] += 4 * n; return list(v)
        if t == 12:
            n = u('i'); v = struct.unpack_from('>%dq' % n, data, p[0]); p[0] += 8 * n; return list(v)
        raise ValueError(t)
    t = u('b'); name = s()
    return name, payload(t)
