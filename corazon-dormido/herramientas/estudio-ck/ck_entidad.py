# ck_entidad.py <bundle> <nombre...>: los datos de cada componente de esos GameObjects (sin referencias vacías)
import sys, json, UnityPy
env = UnityPy.load(sys.argv[1]); quiero = set(sys.argv[2:])
scripts = {}
def limpiar(v):
    if isinstance(v, dict):
        if set(v) <= {'m_FileID', 'm_PathID'}: return None
        out = {k: limpiar(x) for k, x in v.items() if k not in ('m_GameObject', 'm_Script', 'm_Enabled', 'm_Name')}
        return {k: x for k, x in out.items() if x not in (None, [], {}, '')}
    if isinstance(v, list): return [y for y in (limpiar(x) for x in v[:12]) if y not in (None, [], {})]
    return v
for o in env.objects:
    if o.type.name != 'GameObject': continue
    g = o.read()
    if g.m_Name not in quiero: continue
    print('=====', g.m_Name)
    for c in g.m_Component:
        try: comp = (c.component if hasattr(c, 'component') else c).read()
        except Exception: continue
        if comp.object_reader.type.name != 'MonoBehaviour': continue
        tt = comp.object_reader.read_typetree()
        try: nombre = comp.m_Script.read().m_ClassName
        except Exception: nombre = '?'
        d = limpiar(tt)
        if d: print('--', nombre, json.dumps(d, ensure_ascii=False)[:1500])
