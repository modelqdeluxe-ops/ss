# ck_texturas.py <salida> <bundle...>: guarda cada Texture2D como PNG y dice su tamaño
import sys, os, UnityPy
sal = sys.argv[1]; os.makedirs(sal, exist_ok=True)
env = UnityPy.load(*sys.argv[2:])
for o in env.objects:
    if o.type.name != 'Texture2D': continue
    d = o.read()
    try:
        im = d.image; im.save(os.path.join(sal, d.m_Name + '.png')); print(d.m_Name, im.size)
    except Exception as e: print(d.m_Name, 'ERROR', e)
