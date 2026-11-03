# ck_exportar.py <bundle> <salida> <regex>: lista todas las texturas (nombre, tamaño) en salida/_todas.txt y guarda las
# que casen con la regex
import sys, os, re, UnityPy
b, sal, pat = sys.argv[1], sys.argv[2], re.compile(sys.argv[3], re.I)
os.makedirs(sal, exist_ok=True)
env = UnityPy.load(b)
with open(os.path.join(sal, '_todas.txt'), 'w') as f:
    for o in env.objects:
        if o.type.name != 'Texture2D': continue
        d = o.read()
        f.write(f'{d.m_Name}\t{d.m_Width}x{d.m_Height}\n')
        if pat.search(d.m_Name):
            try: d.image.save(os.path.join(sal, d.m_Name + '.png'))
            except Exception as e: print('ERROR', d.m_Name, e)
print('hecho')
